package com.example.osai

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** 一帧待分析画面(已按屏幕方向转正的 JPEG)。 */
@Parcelize
class FaceFrame(
    val jpeg: ByteArray,
    val rotationDegrees: Int,
    val timestampNanos: Long,
    val requestId: Long
) : Parcelable {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = requestId.hashCode()
}
