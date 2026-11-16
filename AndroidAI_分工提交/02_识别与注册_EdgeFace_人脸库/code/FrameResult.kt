package com.example.osai

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** 一帧的完整流水线结果,跨 Binder 返回给 UI 进程。 */
@Parcelize
class FrameResult(
    val requestId: Long,
    val faces: Array<FaceBox>,
    val imageWidth: Int,
    val imageHeight: Int,
    val detectMs: Long,
    val alignMs: Long,
    val recognizeMs: Long,
    val livenessMs: Long,
    val totalMs: Long,
    val label: String?,
    val similarity: Float,
    val liveReal: Float,
    val liveFake: Float,
    /** 0 未判定,1 真人,2 疑似攻击 */
    val liveState: Int,
    val servicePid: Int,
    val error: String?
) : Parcelable {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = requestId.hashCode()
}
