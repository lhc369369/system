package com.example.osai

import android.content.Context

/** 单进程/双进程两种运行配置的统一入口。 */
interface FaceApi {
    val isRemote: Boolean
    fun analyze(frame: FaceFrame): FrameResult
    fun registerCurrent(label: String): Boolean
    fun listFaces(): Array<String>
    fun deleteFace(label: String): Boolean
    fun setIdentifyEnabled(enabled: Boolean): Boolean
    fun setLivenessEnabled(enabled: Boolean): Boolean
    fun setThreadCount(count: Int)
    fun statusLine(): String
    fun debugSnapshot(): String
    fun resetStats()
    fun crashForTest()
}

class RemoteFaceApi(private val proxy: IFaceInference) : FaceApi {
    override val isRemote = true
    override fun analyze(frame: FaceFrame) = proxy.analyzeFrame(frame)
    override fun registerCurrent(label: String) = proxy.registerCurrent(label)
    override fun listFaces() = proxy.listFaces()
    override fun deleteFace(label: String) = proxy.deleteFace(label)
    override fun setIdentifyEnabled(enabled: Boolean) = proxy.setIdentifyEnabled(enabled)
    override fun setLivenessEnabled(enabled: Boolean) = proxy.setLivenessEnabled(enabled)
    override fun setThreadCount(count: Int) = proxy.setThreadCount(count)
    override fun statusLine() = proxy.status
    override fun debugSnapshot() = proxy.debugSnapshot
    override fun resetStats() = proxy.resetStats()
    override fun crashForTest() = proxy.crashForTest()
}

/** 单进程基线:模型直接跑在 UI 进程里,没有 Binder 开销。 */
class LocalFaceApi(context: Context) : FaceApi {
    override val isRemote = false
    private val core = InferenceCore(context)

    override fun analyze(frame: FaceFrame) = core.analyze(frame)
    override fun registerCurrent(label: String) = core.registerCurrent(label)
    override fun listFaces() = core.listFaces()
    override fun deleteFace(label: String) = core.deleteFace(label)
    override fun setIdentifyEnabled(enabled: Boolean): Boolean {
        core.identifyEnabled = enabled
        return core.identifyEnabled
    }
    override fun setLivenessEnabled(enabled: Boolean): Boolean {
        core.livenessEnabled = enabled
        return core.livenessEnabled
    }
    override fun setThreadCount(count: Int) = core.setThreadCount(count)
    override fun statusLine() = core.statusLine()
    override fun debugSnapshot() = core.debugSnapshot()
    override fun resetStats() = core.resetStats()
    override fun crashForTest() {
        // 单进程模式没有独立推理进程可杀
    }
}

object ApiHolder {
    @Volatile
    var current: FaceApi? = null
}
