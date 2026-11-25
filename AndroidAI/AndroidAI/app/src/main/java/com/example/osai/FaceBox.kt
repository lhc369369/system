package com.example.osai

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** 一张人脸的检测结果,坐标基于转正后的原图。 */
@Parcelize
class FaceBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val score: Float,
    /** 5 个关键点,x/y 交替,共 10 个值。 */
    val landmarks: FloatArray
) : Parcelable {
    val width: Float get() = x2 - x1
    val height: Float get() = y2 - y1
}
