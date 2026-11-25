package com.example.osai

import android.content.Context
import android.os.Process
import android.os.SystemClock
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 推理核心:线程池 + Semaphore 限流 + 计数器 + 三模型流水线。
 * 既是 :inference 服务的实现,也可以在单进程模式下被 UI 直接调用。
 */
class InferenceCore(context: Context) {

    companion object {
        private const val ACQUIRE_TIMEOUT_MS = 80L
        private const val TASK_TIMEOUT_MS = 5000L
        private const val RECOGNITION_THRESHOLD = 0.45f
        private const val LIVE_REAL_THRESHOLD = 0.60f
        private const val LIVE_FAKE_THRESHOLD = 0.30f
        private const val HISTORY_SIZE = 5
        private const val HISTORY_VOTES = 3
    }

    val engine = InferenceEngine(context)
    private val store = FaceStore(context)

    @Volatile
    var threadCount: Int = 2
        private set
    private var pool: ExecutorService = newPool(2)
    private val semaphore = Semaphore(2)
    private val threadSeq = AtomicInteger(0)

    private val submitted = AtomicLong(0)
    private val completed = AtomicLong(0)
    private val semTimeouts = AtomicLong(0)
    private val taskTimeouts = AtomicLong(0)

    @Volatile
    var identifyEnabled = true
    @Volatile
    var livenessEnabled = true

    private var lastEmbedding: FloatArray? = null
    private val liveHistory = ArrayDeque<Int>()
    private val latencyWindow = ArrayDeque<Long>()

    private fun newPool(n: Int): ExecutorService = Executors.newFixedThreadPool(n) { r ->
        Thread(r, "infer-${threadSeq.incrementAndGet()}").apply { isDaemon = true }
    }

    @Synchronized
    fun setThreadCount(n: Int) {
        val count = n.coerceIn(1, 4)
        if (count == threadCount) return
        pool.shutdownNow()
        threadCount = count
        pool = newPool(count)
        // Semaphore 的许可数不可变,直接换一个新的
        semaphoreRef.set(Semaphore(count))
        semaphoreField = semaphoreRef.get()
    }

    // 为了在运行中替换许可数,使用一个可变引用
    private val semaphoreRef = java.util.concurrent.atomic.AtomicReference(Semaphore(2))
    @Volatile
    private var semaphoreField: Semaphore = semaphoreRef.get()

    fun analyze(frame: FaceFrame): FrameResult {
        val begin = SystemClock.elapsedRealtimeNanos()
        submitted.incrementAndGet()
        val sem = semaphoreField
        if (!sem.tryAcquire(ACQUIRE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            semTimeouts.incrementAndGet()
            return errorResult(frame, begin, "推理繁忙:等待信号量超时")
        }
        try {
            val future = pool.submit(Callable { process(frame) })
            return try {
                val r = future.get(TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                completed.incrementAndGet()
                r
            } catch (e: Exception) {
                future.cancel(true)
                taskTimeouts.incrementAndGet()
                errorResult(frame, begin, "推理超时或异常:${e.javaClass.simpleName}")
            }
        } finally {
            sem.release()
        }
    }

    private fun process(frame: FaceFrame): FrameResult {
        val begin = SystemClock.elapsedRealtimeNanos()
        val needEmbedding = true
        val out = engine.process(frame.jpeg, needEmbedding, livenessEnabled)
        if (out.error != null) return errorResult(frame, begin, out.error, out)

        val emb = out.embedding
        var label: String? = null
        var similarity = 0f
        synchronized(this) {
            if (emb != null) {
                lastEmbedding = emb
                val best = store.best(emb)
                if (best != null) {
                    similarity = best.second
                    if (identifyEnabled && best.second >= RECOGNITION_THRESHOLD) label = best.first
                }
            }
        }

        var liveState = 0
        var liveReal = 0f
        var liveFake = 0f
        if (livenessEnabled && out.liveProbs != null) {
            liveReal = out.liveProbs[1]
            liveFake = 1f - liveReal
            val labelNow = when {
                liveReal >= LIVE_REAL_THRESHOLD -> 1
                liveReal <= LIVE_FAKE_THRESHOLD -> 2
                else -> 0
            }
            synchronized(this) {
                if (labelNow != 0) {
                    liveHistory.addLast(labelNow)
                    while (liveHistory.size > HISTORY_SIZE) liveHistory.removeFirst()
                }
                val realVotes = liveHistory.count { it == 1 }
                val fakeVotes = liveHistory.count { it == 2 }
                liveState = when {
                    realVotes >= HISTORY_VOTES -> 1
                    fakeVotes >= HISTORY_VOTES -> 2
                    else -> 0
                }
            }
        }

        val totalMs = (SystemClock.elapsedRealtimeNanos() - begin) / 1_000_000
        synchronized(this) {
            latencyWindow.addLast(totalMs)
            while (latencyWindow.size > 120) latencyWindow.removeFirst()
        }
        return FrameResult(
            requestId = frame.requestId,
            faces = out.faces.map { FaceBox(it.box[0], it.box[1], it.box[2], it.box[3], it.score, it.kps) }
                .toTypedArray(),
            imageWidth = out.width,
            imageHeight = out.height,
            detectMs = out.detectMs,
            alignMs = out.alignMs,
            recognizeMs = out.embedMs,
            livenessMs = out.liveMs,
            totalMs = totalMs,
            label = label,
            similarity = similarity,
            liveReal = liveReal,
            liveFake = liveFake,
            liveState = liveState,
            servicePid = Process.myPid(),
            error = null
        )
    }

    private fun errorResult(frame: FaceFrame, begin: Long, msg: String, out: EngineOutput? = null): FrameResult {
        val total = (SystemClock.elapsedRealtimeNanos() - begin) / 1_000_000
        return FrameResult(
            requestId = frame.requestId,
            faces = emptyArray(),
            imageWidth = out?.width ?: 0,
            imageHeight = out?.height ?: 0,
            detectMs = out?.detectMs ?: 0,
            alignMs = 0,
            recognizeMs = 0,
            livenessMs = 0,
            totalMs = total,
            label = null,
            similarity = 0f,
            liveReal = 0f,
            liveFake = 0f,
            liveState = 0,
            servicePid = Process.myPid(),
            error = msg
        )
    }

    @Synchronized
    fun registerCurrent(label: String): Boolean {
        val emb = lastEmbedding ?: return false
        if (label.isBlank()) return false
        store.save(label.trim(), emb)
        return true
    }

    @Synchronized
    fun listFaces(): Array<String> = store.labels()

    @Synchronized
    fun deleteFace(label: String): Boolean = store.delete(label)

    @Synchronized
    fun resetStats() {
        submitted.set(0)
        completed.set(0)
        semTimeouts.set(0)
        taskTimeouts.set(0)
        latencyWindow.clear()
        liveHistory.clear()
    }

    fun statusLine(): String {
        val sem = semaphoreField
        return buildString {
            append("pid=").append(Process.myPid())
            append(" threads=").append(threadCount)
            append(" permits=").append(sem.availablePermits())
            append(" submitted=").append(submitted.get())
            append(" completed=").append(completed.get())
            append(" semTimeout=").append(semTimeouts.get())
            if (identifyEnabled) append(" 识别=开")
            if (livenessEnabled) append(" 活体=开")
        }
    }

    fun debugSnapshot(): String {
        val sem = semaphoreField
        val latencies = synchronized(this) { latencyWindow.toList().sorted() }
        fun pct(p: Double): Long =
            if (latencies.isEmpty()) 0 else latencies[((latencies.size - 1) * p).toInt()]
        val obj = JSONObject()
        obj.put("pid", Process.myPid())
        obj.put("threads", threadCount)
        obj.put("permits", sem.availablePermits())
        obj.put("inFlight", threadCount - sem.availablePermits())
        obj.put("submitted", submitted.get())
        obj.put("completed", completed.get())
        obj.put("semTimeout", semTimeouts.get())
        obj.put("taskTimeout", taskTimeouts.get())
        obj.put("identify", identifyEnabled)
        obj.put("liveness", livenessEnabled)
        obj.put("faces", store.size())
        obj.put("p50", pct(0.50))
        obj.put("p95", pct(0.95))
        obj.put("avg", if (latencies.isEmpty()) 0 else latencies.average().toLong())
        obj.put("loadError", engine.loadError ?: "")
        obj.put("threadNames", Thread.getAllStackTraces().keys
            .filter { it.name.startsWith("infer-") }
            .map { it.name }
            .joinToString(","))
        return obj.toString()
    }
}
