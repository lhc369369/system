package com.example.osai

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log

/**
 * 运行在独立进程 :inference 的推理服务。
 * 真正的模型与调度在 [InferenceCore],这里只做 Binder 转发。
 */
class FaceInferenceService : Service() {

    companion object {
        private const val TAG = "FaceInference"
    }

    private lateinit var core: InferenceCore

    private val binder = object : IFaceInference.Stub() {
        override fun analyzeFrame(frame: FaceFrame): FrameResult = core.analyze(frame)

        override fun registerCurrent(label: String): Boolean = core.registerCurrent(label)

        override fun listFaces(): Array<String> = core.listFaces()

        override fun deleteFace(label: String): Boolean = core.deleteFace(label)

        override fun setIdentifyEnabled(enabled: Boolean): Boolean {
            core.identifyEnabled = enabled
            return core.identifyEnabled
        }

        override fun setLivenessEnabled(enabled: Boolean): Boolean {
            core.livenessEnabled = enabled
            return core.livenessEnabled
        }

        override fun setThreadCount(count: Int) {
            core.setThreadCount(count)
        }

        override fun getStatus(): String = core.statusLine()

        override fun getDebugSnapshot(): String = core.debugSnapshot()

        override fun resetStats() {
            core.resetStats()
        }

        override fun crashForTest() {
            Log.w(TAG, "crashForTest: 主动杀死推理进程 pid=${Process.myPid()}")
            Handler(Looper.getMainLooper()).postDelayed({
                Process.killProcess(Process.myPid())
            }, 200)
        }
    }

    override fun onCreate() {
        super.onCreate()
        core = InferenceCore(this)
        Log.i(TAG, "FaceInferenceService onCreate pid=${Process.myPid()}")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Log.i(TAG, "FaceInferenceService onDestroy pid=${Process.myPid()}")
        super.onDestroy()
    }
}
