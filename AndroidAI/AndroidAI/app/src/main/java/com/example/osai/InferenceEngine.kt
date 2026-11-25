package com.example.osai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.SystemClock
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class Detection(
    val box: FloatArray,      // x1,y1,x2,y2(原图坐标)
    val kps: FloatArray,      // 10 个值
    val score: Float
)

class EngineOutput(
    val faces: List<Detection>,
    val width: Int,
    val height: Int,
    val detectMs: Long,
    val embedding: FloatArray?,
    val alignMs: Long,
    val embedMs: Long,
    val liveProbs: FloatArray?,
    val liveMs: Long,
    val error: String? = null
)

/**
 * 三个模型的加载与前后处理。
 * 纯计算,不做进程/线程调度;调度在 InferenceCore 中完成。
 */
class InferenceEngine(private val context: Context) {

    companion object {
        private const val SCRFD_INPUT = 320
        private const val EDGEFACE_SIZE = 112
        private const val LIVENESS_SIZE = 80
        private const val CONF_THRESHOLD = 0.5f
        private const val NMS_THRESHOLD = 0.4f
        private val STRIDES = intArrayOf(8, 16, 32)
        private const val NUM_ANCHORS = 2
        // ArcFace 五点模板
        private val ARC_TEMPLATE = floatArrayOf(
            38.2946f, 51.6963f, 73.5318f, 51.5014f, 56.0252f, 71.7366f,
            41.5493f, 92.3655f, 70.7299f, 92.2041f
        )
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var scrfd: OrtSession? = null
    private var edgeface: OrtSession? = null
    private var minifas: OrtSession? = null

    var loadError: String? = null
        private set

    init {
        try {
            scrfd = createSession("models/scrfd.onnx", useNnapi = true)
            edgeface = createSession("models/edgeface.onnx")
            minifas = createSession("models/minifasnet.onnx")
        } catch (t: Throwable) {
            loadError = "模型加载失败:${t.javaClass.simpleName}:${t.message}"
        }
    }

    private fun createSession(asset: String, useNnapi: Boolean = false): OrtSession {
        val bytes = context.assets.open(asset).use { it.readBytes() }
        if (useNnapi) {
            try {
                val nnapiOptions = OrtSession.SessionOptions().apply {
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    setSessionLogLevel(ai.onnxruntime.OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR)
                    addNnapi()
                }
                return env.createSession(bytes, nnapiOptions)
            } catch (t: Throwable) {
                android.util.Log.w("FaceInference", "NNAPI 不可用,回退 CPU:${t.message}")
            }
        }
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setSessionLogLevel(ai.onnxruntime.OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR)
        }
        return env.createSession(bytes, options)
    }

    /**
     * @param jpeg 已按屏幕方向转正或带 rotation 信息
     */
    fun process(
        jpeg: ByteArray,
        needEmbedding: Boolean,
        needLiveness: Boolean
    ): EngineOutput {
        if (loadError != null) {
            return EngineOutput(emptyList(), 0, 0, 0, null, 0, 0, null, 0, loadError)
        }
        val t0 = SystemClock.elapsedRealtimeNanos()
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            ?: return EngineOutput(emptyList(), 0, 0, 0, null, 0, 0, null, 0, "JPEG 解码失败")
        val rgb = bitmapToRgb(bitmap)
        val w = bitmap.width
        val h = bitmap.height
        val decodeMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000

        val faces = detect(rgb, w, h)
        val detectMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
        if (faces.isEmpty()) {
            logThrottled({ "frame ${w}x$h decode=${decodeMs}ms det=$detectMs ms faces=0" })
            return EngineOutput(emptyList(), w, h, detectMs, null, 0, 0, null, 0)
        }

        // 只处理最大的一张脸
        val main = faces.maxByOrNull { it.box[2] - it.box[0] }!!
        var alignMs = 0L
        var embedMs = 0L
        var embedding: FloatArray? = null
        var liveMs = 0L
        var liveProbs: FloatArray? = null

        if (needEmbedding || needLiveness) {
            val a0 = SystemClock.elapsedRealtimeNanos()
            val aligned = alignFace(rgb, w, h, main.kps)
            alignMs = (SystemClock.elapsedRealtimeNanos() - a0) / 1_000_000

            if (needEmbedding) {
                val e0 = SystemClock.elapsedRealtimeNanos()
                embedding = extractEmbedding(aligned)
                embedMs = (SystemClock.elapsedRealtimeNanos() - e0) / 1_000_000
            }
            if (needLiveness) {
                val l0 = SystemClock.elapsedRealtimeNanos()
                liveProbs = liveness(rgb, w, h, main.box)
                liveMs = (SystemClock.elapsedRealtimeNanos() - l0) / 1_000_000
            }
        }
        logThrottled({
            "frame ${w}x$h det=$detectMs ms align=$alignMs emb=$embedMs live=$liveMs " +
                "faces=${faces.size} score=${"%.2f".format(faces[0].score)} " +
                "liveProbs=${liveProbs?.joinToString(",") { "%.3f".format(it) }}"
        })
        return EngineOutput(faces, w, h, detectMs, embedding, alignMs, embedMs, liveProbs, liveMs)
    }

    /**
     * 用直接缓冲区创建张量。堆内 FloatBuffer 在较新的 onnxruntime-android 上
     * 走 JNI 拷贝会退化成逐元素操作(实测 300k 个 float 要 700+ ms)。
     */
    private fun createTensor(data: FloatArray, shape: LongArray): OnnxTensor {
        val buffer = java.nio.ByteBuffer
            .allocateDirect(data.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(data)
        buffer.rewind()
        return OnnxTensor.createTensor(env, buffer, shape)
    }

    // ---------------------------------------------------------------- SCRFD

    private fun detect(rgb: ByteArray, w: Int, h: Int): List<Detection> {
        val session = scrfd ?: return emptyList()
        val input = SCRFD_INPUT
        val ratio = h.toFloat() / w
        val newW: Int
        val newH: Int
        if (ratio > 1f) {
            newH = input
            newW = (input / ratio).toInt()
        } else {
            newW = input
            newH = (input * ratio).toInt()
        }
        val detScale = newH.toFloat() / h

        val size = input * input
        val data = FloatArray(3 * size) // 0 初始化,letterbox 黑边为 0
        val fillStart = SystemClock.elapsedRealtimeNanos()
        for (y in 0 until newH) {
            val sy = (y / detScale).toInt().coerceIn(0, h - 1)
            for (x in 0 until newW) {
                val sx = (x / detScale).toInt().coerceIn(0, w - 1)
                val si = (sy * w + sx) * 3
                val di = y * input + x
                data[di] = (rgb[si] - 127.5f) / 128f
                data[size + di] = (rgb[si + 1] - 127.5f) / 128f
                data[2 * size + di] = (rgb[si + 2] - 127.5f) / 128f
            }
        }

        val tensor = createTensor(data, longArrayOf(1, 3, input.toLong(), input.toLong()))
        val result = session.run(mapOf(session.inputNames.first() to tensor))
        val byLastDim = HashMap<Long, MutableList<Pair<Int, FloatArray>>>()
        for (entry in result) {
            val t = entry.value as OnnxTensor
            val shape = t.info.shape
            val last = shape.last()
            val count = shape.fold(1L) { acc, v -> acc * v }.toInt()
            byLastDim.getOrPut(last) { mutableListOf() }.add(count to toFlat(t))
        }
        val scoreTensors = byLastDim[1L]?.sortedByDescending { it.first } ?: return emptyList()
        val boxTensors = byLastDim[4L]?.sortedByDescending { it.first } ?: return emptyList()
        val kpsTensors = byLastDim[10L]?.sortedByDescending { it.first } ?: return emptyList()

        val anchors = anchorCenters(input)
        val boxes = ArrayList<FloatArray>()
        val kpss = ArrayList<FloatArray>()
        val scores = ArrayList<Float>()

        for (i in STRIDES.indices) {
            val stride = STRIDES[i]
            val sc = scoreTensors.getOrNull(i)?.second ?: continue
            val bb = boxTensors.getOrNull(i)?.second ?: continue
            val kp = kpsTensors.getOrNull(i)?.second ?: continue
            val centers = anchors[i]
            val n = sc.size
            for (j in 0 until n) {
                if (sc[j] < CONF_THRESHOLD) continue
                val cx = centers[j * 2]
                val cy = centers[j * 2 + 1]
                val b = floatArrayOf(
                    cx - bb[j * 4] * stride,
                    cy - bb[j * 4 + 1] * stride,
                    cx + bb[j * 4 + 2] * stride,
                    cy + bb[j * 4 + 3] * stride
                )
                val k = FloatArray(10)
                for (p in 0 until 5) {
                    k[p * 2] = cx + kp[j * 10 + p * 2] * stride
                    k[p * 2 + 1] = cy + kp[j * 10 + p * 2 + 1] * stride
                }
                boxes.add(b)
                kpss.add(k)
                scores.add(sc[j])
            }
        }

        val keep = nms(boxes, scores, NMS_THRESHOLD)
        return keep.map {
            val b = boxes[it]
            val outBox = floatArrayOf(
                (b[0] / detScale).coerceIn(0f, w.toFloat()),
                (b[1] / detScale).coerceIn(0f, h.toFloat()),
                (b[2] / detScale).coerceIn(0f, w.toFloat()),
                (b[3] / detScale).coerceIn(0f, h.toFloat())
            )
            val k = FloatArray(10) { idx -> kpss[it][idx] / detScale }
            Detection(outBox, k, scores[it])
        }
    }

    private fun anchorCenters(input: Int): Array<FloatArray> {
        return Array(STRIDES.size) { si ->
            val stride = STRIDES[si]
            val fm = input / stride
            val out = FloatArray(fm * fm * NUM_ANCHORS * 2)
            var k = 0
            for (y in 0 until fm) {
                for (x in 0 until fm) {
                    repeat(NUM_ANCHORS) {
                        out[k++] = (x * stride).toFloat()
                        out[k++] = (y * stride).toFloat()
                    }
                }
            }
            out
        }
    }

    private fun nms(boxes: List<FloatArray>, scores: List<Float>, threshold: Float): List<Int> {
        val order = scores.indices.sortedByDescending { scores[it] }
        val keep = ArrayList<Int>()
        val suppressed = BooleanArray(scores.size)
        for (i in order) {
            if (suppressed[i]) continue
            keep.add(i)
            for (j in order) {
                if (j == i || suppressed[j]) continue
                if (iou(boxes[i], boxes[j]) > threshold) suppressed[j] = true
            }
        }
        return keep
    }

    private fun iou(a: FloatArray, b: FloatArray): Float {
        val xx1 = max(a[0], b[0])
        val yy1 = max(a[1], b[1])
        val xx2 = min(a[2], b[2])
        val yy2 = min(a[3], b[3])
        val w = max(0f, xx2 - xx1)
        val h = max(0f, yy2 - yy1)
        val inter = w * h
        val areaA = (a[2] - a[0]) * (a[3] - a[1])
        val areaB = (b[2] - b[0]) * (b[3] - b[1])
        val union = areaA + areaB - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun toFlat(t: OnnxTensor): FloatArray {
        return when (val v = t.value) {
            is Array<*> -> {
                @Suppress("UNCHECKED_CAST")
                val rows = v as Array<FloatArray>
                val cols = if (rows.isNotEmpty()) rows[0].size else 0
                val out = FloatArray(rows.size * cols)
                var k = 0
                for (r in rows) for (x in r) out[k++] = x
                out
            }
            is FloatArray -> v
            else -> FloatArray(0)
        }
    }

    // ------------------------------------------------------------- 对齐

    private fun alignFace(rgb: ByteArray, w: Int, h: Int, kps: FloatArray): FloatArray {
        val size = EDGEFACE_SIZE
        val tf = solveSimilarity(kps, ARC_TEMPLATE)
        val a = tf[0]
        val b = tf[1]
        val tx = tf[2]
        val ty = tf[3]
        val det = a * a + b * b
        val out = FloatArray(3 * size * size)

        for (y in 0 until size) {
            for (x in 0 until size) {
                val u = x - tx
                val v = y - ty
                val sx = (a * u + b * v) / det
                val sy = (-b * u + a * v) / det
                val di = y * size + x
                val (r, g, bl) = bilinear(rgb, w, h, sx, sy)
                out[di] = r
                out[size * size + di] = g
                out[2 * size * size + di] = bl
            }
        }
        return out
    }

    /** 最小二乘求相似变换:x' = a*x - b*y + tx , y' = b*x + a*y + ty */
    private fun solveSimilarity(src: FloatArray, dst: FloatArray): FloatArray {
        val n = 5
        val ata = Array(4) { DoubleArray(4) }
        val atb = DoubleArray(4)
        for (i in 0 until n) {
            val x = src[i * 2].toDouble()
            val y = src[i * 2 + 1].toDouble()
            val dx = dst[i * 2].toDouble()
            val dy = dst[i * 2 + 1].toDouble()
            val r1 = doubleArrayOf(x, -y, 1.0, 0.0)
            val r2 = doubleArrayOf(y, x, 0.0, 1.0)
            for (r in 0 until 4) {
                for (c in 0 until 4) ata[r][c] += r1[r] * r1[c] + r2[r] * r2[c]
                atb[r] += r1[r] * dx + r2[r] * dy
            }
        }
        return solve4(ata, atb)
    }

    private fun solve4(a: Array<DoubleArray>, b: DoubleArray): FloatArray {
        val m = Array(4) { i -> DoubleArray(5) { j -> if (j < 4) a[i][j] else b[i] } }
        for (col in 0 until 4) {
            var pivot = col
            for (r in col + 1 until 4) if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
            val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
            val pv = m[col][col]
            if (abs(pv) < 1e-9) return FloatArray(4)
            for (j in col until 5) m[col][j] /= pv
            for (r in 0 until 4) {
                if (r == col) continue
                val f = m[r][col]
                for (j in col until 5) m[r][j] -= f * m[col][j]
            }
        }
        return FloatArray(4) { m[it][4].toFloat() }
    }

    private fun bilinear(rgb: ByteArray, w: Int, h: Int, x: Float, y: Float): Triple<Float, Float, Float> {
        val x0 = kotlin.math.floor(x).toInt().coerceIn(0, w - 1)
        val y0 = kotlin.math.floor(y).toInt().coerceIn(0, h - 1)
        val x1 = min(x0 + 1, w - 1)
        val y1 = min(y0 + 1, h - 1)
        val fx = (x - x0).coerceIn(0f, 1f)
        val fy = (y - y0).coerceIn(0f, 1f)
        fun at(px: Int, py: Int, c: Int): Float {
            val v = rgb[(py * w + px) * 3 + c].toInt() and 0xFF
            return v.toFloat()
        }
        fun mix(c: Int): Float {
            val v00 = at(x0, y0, c)
            val v10 = at(x1, y0, c)
            val v01 = at(x0, y1, c)
            val v11 = at(x1, y1, c)
            val top = v00 + (v10 - v00) * fx
            val bottom = v01 + (v11 - v01) * fx
            return top + (bottom - top) * fy
        }
        return Triple(mix(0), mix(1), mix(2))
    }

    // ---------------------------------------------------------- EdgeFace

    private fun extractEmbedding(aligned: FloatArray): FloatArray? {
        val session = edgeface ?: return null
        val size = EDGEFACE_SIZE
        // 0-255 -> (x/255-0.5)/0.5
        val input = FloatArray(aligned.size) { aligned[it] / 127.5f - 1f }
        val tensor = createTensor(input, longArrayOf(1, 3, size.toLong(), size.toLong()))
        val result = session.run(mapOf(session.inputNames.first() to tensor))
        val outTensor = result.first().value as? OnnxTensor ?: return null
        val out = outTensor.value as? Array<FloatArray> ?: return null
        val vec = out[0]
        var norm = 0f
        for (v in vec) norm += v * v
        norm = sqrt(norm)
        if (norm < 1e-6f) return vec
        return FloatArray(vec.size) { vec[it] / norm }
    }

    // -------------------------------------------------------- MiniFASNet

    private fun liveness(rgb: ByteArray, w: Int, h: Int, box: FloatArray): FloatArray? {
        val session = minifas ?: return null
        // 与官方 generate_patches.CropImage 一致:限幅后平移取景框,而不是直接截断
        var bx = box[0]
        var by = box[1]
        var bw = box[2] - box[0]
        var bh = box[3] - box[1]
        if (bw <= 1f || bh <= 1f) return null
        val scale = minOf((h - 1) / bh, minOf((w - 1) / bw, 2.7f))
        val nw = bw * scale
        val nh = bh * scale
        val cx = bx + bw / 2f
        val cy = by + bh / 2f
        var left = cx - nw / 2f
        var top = cy - nh / 2f
        var right = cx + nw / 2f
        var bottom = cy + nh / 2f
        if (left < 0f) {
            right -= left
            left = 0f
        }
        if (top < 0f) {
            bottom -= top
            top = 0f
        }
        if (right > w - 1f) {
            left -= right - (w - 1f)
            right = (w - 1f)
        }
        if (bottom > h - 1f) {
            top -= bottom - (h - 1f)
            bottom = (h - 1f)
        }
        val nx1 = left.toInt().coerceAtLeast(0)
        val ny1 = top.toInt().coerceAtLeast(0)
        val nx2 = right.toInt().coerceAtMost(w - 1)
        val ny2 = bottom.toInt().coerceAtMost(h - 1)
        if (nx2 <= nx1 || ny2 <= ny1) return null

        val size = LIVENESS_SIZE
        val data = FloatArray(3 * size * size)
        val sxRatio = (nx2 - nx1).toFloat() / size
        val syRatio = (ny2 - ny1).toFloat() / size
        for (y in 0 until size) {
            for (x in 0 until size) {
                val sx = nx1 + (x + 0.5f) * sxRatio - 0.5f
                val sy = ny1 + (y + 0.5f) * syRatio - 0.5f
                val (r, g, b) = bilinear(rgb, w, h, sx, sy)
                val di = y * size + x
                // MiniFASNet 官方输入是 BGR 0-255
                data[di] = b
                data[size * size + di] = g
                data[2 * size * size + di] = r
            }
        }
        maybeDumpCrop(data, size)
        val tensor = createTensor(data, longArrayOf(1, 3, size.toLong(), size.toLong()))
        val result = session.run(mapOf(session.inputNames.first() to tensor))
        val outTensor = result.first().value as? OnnxTensor ?: return null
        val logits = (outTensor.value as? Array<FloatArray>)?.get(0) ?: return null
        val mx = logits.maxOrNull() ?: 0f
        val exps = FloatArray(logits.size) { exp(logits[it] - mx) }
        val sum = exps.sum()
        return FloatArray(logits.size) { exps[it] / sum }
    }

    private var lastLogAt = 0L

    /** 每秒最多一条,避免触发 logd 限流导致日志全部丢失 */
    private fun logThrottled(msg: () -> String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLogAt < 1000) return
        lastLogAt = now
        android.util.Log.i("FaceInference", msg())
    }

    private var lastDump = 0L

    /** 每 3 秒把送进 MiniFASNet 的 80x80 裁剪存一份,便于排查 */
    private fun maybeDumpCrop(data: FloatArray, size: Int) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastDump < 3000) return
        lastDump = now
        try {
            val px = IntArray(size * size)
            for (i in px.indices) {
                val b = data[i].toInt().coerceIn(0, 255)
                val g = data[size * size + i].toInt().coerceIn(0, 255)
                val r = data[2 * size * size + i].toInt().coerceIn(0, 255)
                px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            bmp.setPixels(px, 0, size, 0, 0, size, size)
            java.io.File(context.filesDir, "liveness_crop.jpg").outputStream().use {
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, it)
            }
        } catch (t: Throwable) {
            // 仅调试用途
        }
    }

    // ------------------------------------------------------------ 工具

    private fun bitmapToRgb(bmp: Bitmap): ByteArray {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = ByteArray(w * h * 3)
        var j = 0
        for (p in pixels) {
            out[j++] = ((p shr 16) and 0xFF).toByte()
            out[j++] = ((p shr 8) and 0xFF).toByte()
            out[j++] = (p and 0xFF).toByte()
        }
        return out
    }

    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }
}
