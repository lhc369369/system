package com.example.osai

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS = "osai"
        const val KEY_SINGLE_PROCESS = "single_process"
        private const val JPEG_QUALITY = 90
    }

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: FaceOverlayView
    private lateinit var statusMain: TextView
    private lateinit var statusDetail: TextView
    private lateinit var modeChip: TextView
    private lateinit var registerButton: Button
    private lateinit var manageButton: Button
    private lateinit var identifyButton: Button
    private lateinit var livenessButton: Button
    private lateinit var statusButton: Button

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

    private var remoteProxy: IFaceInference? = null
    private var api: FaceApi? = null
    private var serviceBound = false
    private var singleProcess = false
    private var recreating = false

    private val handler = Handler(Looper.getMainLooper())
    private val frameBusy = AtomicBoolean(false)
    private val requestId = AtomicLong(0)
    private val droppedFrames = AtomicLong(0)
    private var resultCount = 0L
    private var fpsWindowStart = SystemClock.elapsedRealtime()
    private var lastFps = 0f

    private val deathRecipient = IBinder.DeathRecipient {
        runOnUiThread { onServiceLost("推理进程意外退出") }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else setStatus("相机权限被拒绝,无法预览。")
        }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val proxy = IFaceInference.Stub.asInterface(service)
            try {
                service?.linkToDeath(deathRecipient, 0)
            } catch (e: RemoteException) {
                // 服务已经退出
            }
            remoteProxy = proxy
            serviceBound = true
            api = RemoteFaceApi(proxy)
            ApiHolder.current = api
            val pid = runCatching { proxy.status.substringAfter("pid=").substringBefore(" ") }.getOrDefault("?")
            setStatus("已连接推理服务 :inference (pid=$pid)")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            onServiceLost("推理服务连接断开")
        }
    }

    private fun onServiceLost(reason: String) {
        remoteProxy = null
        api = null
        ApiHolder.current = null
        serviceBound = false
        if (singleProcess || isFinishing) return
        setStatus("$reason,1 秒后自动重连…")
        handler.postDelayed({ if (!serviceBound && !isFinishing) bindInference() }, 1000)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        singleProcess = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SINGLE_PROCESS, false)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        statusMain = findViewById(R.id.statusMain)
        statusDetail = findViewById(R.id.statusDetail)
        modeChip = findViewById(R.id.modeChip)
        registerButton = findViewById(R.id.registerButton)
        manageButton = findViewById(R.id.manageButton)
        identifyButton = findViewById(R.id.identifyButton)
        livenessButton = findViewById(R.id.livenessButton)
        statusButton = findViewById(R.id.statusButton)

        registerButton.setOnClickListener { registerCurrentFace() }
        manageButton.setOnClickListener { showFaceLibrary() }
        identifyButton.setOnClickListener { toggleIdentify() }
        livenessButton.setOnClickListener { toggleLiveness() }
        statusButton.setOnClickListener {
            startActivity(Intent(this, SystemStatusActivity::class.java))
        }

        cameraExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "camera-analyzer").apply { isDaemon = true }
        }

        setStatus(
            if (singleProcess) "单进程模式:模型在 UI 进程内运行"
            else "应用进程 pid=${Process.myPid()},等待推理服务…"
        )
        requestCameraIfNeeded()

        // 绑定保持到 Activity 销毁,这样状态页打开期间服务仍然可用
        if (singleProcess) {
            ensureLocalApi()
        } else {
            bindInference()
        }
    }

    override fun onResume() {
        super.onResume()
        val pref = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SINGLE_PROCESS, false)
        if (pref != singleProcess && !recreating) {
            recreating = true
            recreate()
        }
    }

    override fun onStop() {
        super.onStop()
        // 保留 api 与绑定:状态页需要访问推理服务
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            try {
                remoteProxy?.asBinder()?.unlinkToDeath(deathRecipient, 0)
            } catch (e: Exception) {
                // ignore
            }
            unbindService(connection)
            serviceBound = false
        }
        if (!singleProcess) {
            ApiHolder.current = null
        }
        cameraExecutor.shutdown()
        handler.removeCallbacksAndMessages(null)
    }

    // ------------------------------------------------------------- 服务

    private fun bindInference() {
        if (serviceBound) return
        bindService(
            Intent(this, FaceInferenceService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun ensureLocalApi() {
        if (api != null) return
        val thread = Thread({
            val local = LocalFaceApi(applicationContext)
            runOnUiThread {
                api = local
                ApiHolder.current = local
                setStatus("单进程模式就绪 (pid=${Process.myPid()})")
            }
        }, "local-infer-init")
        thread.isDaemon = true
        thread.start()
    }

    // ------------------------------------------------------------- 相机

    private fun requestCameraIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, ::analyzeFrame) }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                setStatus("相机启动失败:${e.javaClass.simpleName} ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        val currentApi = api
        if (currentApi == null) {
            imageProxy.close()
            return
        }
        if (!frameBusy.compareAndSet(false, true)) {
            droppedFrames.incrementAndGet()
            imageProxy.close()
            return
        }
        try {
            val raw = imageProxy.toBitmap()
            val rotated = rotateBitmap(raw, imageProxy.imageInfo.rotationDegrees)
            val jpeg = compressJpeg(rotated)
            val frame = FaceFrame(
                jpeg = jpeg,
                rotationDegrees = 0,
                timestampNanos = SystemClock.elapsedRealtimeNanos(),
                requestId = requestId.incrementAndGet()
            )
            val t0 = SystemClock.elapsedRealtimeNanos()
            val result = try {
                currentApi.analyze(frame)
            } catch (e: RemoteException) {
                runOnUiThread { onServiceLost("Binder 调用失败") }
                return
            }
            val ipcMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
            resultCount++
            runOnUiThread { render(result, ipcMs) }
        } catch (t: Throwable) {
            runOnUiThread { setStatus("帧处理异常:${t.javaClass.simpleName}") }
        } finally {
            imageProxy.close()
            frameBusy.set(false)
        }
    }

    private fun render(result: FrameResult, ipcMs: Long) {
        overlayView.faces = result.faces
        overlayView.imageWidth = result.imageWidth
        overlayView.imageHeight = result.imageHeight
        overlayView.captionState = result.liveState
        overlayView.caption = when {
            result.error != null -> null
            result.liveState == 2 -> "疑似攻击 ${"%.2f".format(result.liveReal)}"
            result.label != null -> "${result.label} ${"%.2f".format(result.similarity)}"
            result.liveState == 1 -> "真人 ${"%.2f".format(result.liveReal)}"
            result.faces.isNotEmpty() -> "未注册"
            else -> null
        }

        val now = SystemClock.elapsedRealtime()
        if (now - fpsWindowStart >= 1000) {
            lastFps = resultCount * 1000f / (now - fpsWindowStart)
            resultCount = 0
            fpsWindowStart = now
        }

        modeChip.text = if (api?.isRemote == true) "双进程" else "单进程"
        val liveText = when (result.liveState) {
            1 -> "真人"
            2 -> "疑似攻击"
            else -> "判定中"
        }
        statusMain.text = buildString {
            append("FPS ").append("%.1f".format(lastFps))
            append(" · 人脸 ").append(result.faces.size)
            append(" · ").append(result.totalMs).append(" ms")
        }
        statusDetail.text = when {
            result.error != null -> "错误:${result.error}"
            result.faces.isEmpty() -> "未检测到人脸 · 请正对镜头"
            else -> buildString {
                val hasTemplate = result.similarity > 0.001f
                append("身份 ")
                when {
                    result.label != null -> append(result.label).append(" ").append("%.2f".format(result.similarity))
                    hasTemplate -> append("未知 ").append("%.2f".format(result.similarity))
                    else -> append("未注册")
                }
                append(" · 活体 ").append(liveText).append(" ").append("%.2f".format(result.liveReal))
                append("\n检测 ").append(result.detectMs).append(" · 识别 ").append(result.recognizeMs)
                append(" · 活体 ").append(result.livenessMs).append(" ms")
                append(" · IPC ").append(ipcMs)
                append(" · pid ").append(result.servicePid)
            }
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun compressJpeg(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return out.toByteArray()
    }

    // ------------------------------------------------------------- 交互

    private fun registerCurrentFace() {
        val currentApi = api ?: return toast("推理服务未连接")
        val input = EditText(this).apply { hint = "输入姓名" }
        AlertDialog.Builder(this)
            .setTitle("注册当前人脸")
            .setMessage("请让画面中只有一张清晰的正脸")
            .setView(input)
            .setPositiveButton("注册") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    toast("姓名不能为空")
                    return@setPositiveButton
                }
                Thread {
                    val result = runCatching { currentApi.registerCurrent(name) }
                    runOnUiThread {
                        toast(
                            when {
                                result.isFailure -> "注册失败:${result.exceptionOrNull()?.javaClass?.simpleName}"
                                result.getOrDefault(false) -> "已注册:$name"
                                else -> "注册失败:当前没有可用的人脸特征"
                            }
                        )
                    }
                }.start()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showFaceLibrary() {
        val currentApi = api ?: return toast("推理服务未连接")
        Thread {
            val names = runCatching { currentApi.listFaces() }.getOrDefault(emptyArray())
            runOnUiThread {
                if (names.isEmpty()) {
                    toast("人脸库为空")
                    return@runOnUiThread
                }
                AlertDialog.Builder(this)
                    .setTitle("人脸库(${names.size})")
                    .setItems(names) { _, which ->
                        val name = names[which]
                        AlertDialog.Builder(this)
                            .setTitle("删除 $name ?")
                            .setPositiveButton("删除") { _, _ ->
                                Thread {
                                    val ok = runCatching { currentApi.deleteFace(name) }.getOrDefault(false)
                                    runOnUiThread { toast(if (ok) "已删除 $name" else "删除失败") }
                                }.start()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                    .show()
            }
        }.start()
    }

    private var identifyEnabled = true
    private var livenessEnabled = true

    private fun toggleIdentify() {
        val currentApi = api ?: return toast("推理服务未连接")
        identifyEnabled = !identifyEnabled
        identifyButton.text = if (identifyEnabled) "识别:开" else "识别:关"
        Thread {
            runCatching { currentApi.setIdentifyEnabled(identifyEnabled) }
        }.start()
    }

    private fun toggleLiveness() {
        val currentApi = api ?: return toast("推理服务未连接")
        livenessEnabled = !livenessEnabled
        livenessButton.text = if (livenessEnabled) "活体:开" else "活体:关"
        overlayView.captionState = 0
        Thread {
            runCatching { currentApi.setLivenessEnabled(livenessEnabled) }
        }.start()
    }

    private fun setStatus(text: String) {
        statusMain.text = text
        statusDetail.text = ""
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
