package com.example.osai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** 把检测框/关键点画在预览上(前置摄像头需要左右镜像)。 */
class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var faces: Array<FaceBox> = emptyArray()
        set(value) {
            field = value
            invalidate()
        }

    var imageWidth: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var imageHeight: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var mirrored: Boolean = true

    var caption: String? = null
        set(value) {
            field = value
            invalidate()
        }

    var captionState: Int = 0

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.parseColor("#3FB950")
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F85149")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        isFakeBoldText = true
    }

    private val textBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC10141C.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageWidth <= 0 || imageHeight <= 0 || faces.isEmpty()) return

        val scale = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val dispW = imageWidth * scale
        val dispH = imageHeight * scale
        val dx = (width - dispW) / 2f
        val dy = (height - dispH) / 2f

        fun vx(x: Float): Float = dx + (if (mirrored) (imageWidth - x) else x) * scale
        fun vy(y: Float): Float = dy + y * scale

        val color = when (captionState) {
            1 -> Color.parseColor("#3FB950")
            2 -> Color.parseColor("#F85149")
            else -> Color.parseColor("#D29922")
        }
        boxPaint.color = color

        for (face in faces) {
            val left = min(vx(face.x1), vx(face.x2))
            val right = max(vx(face.x1), vx(face.x2))
            val top = vy(face.y1)
            val bottom = vy(face.y2)
            canvas.drawRect(RectF(left, top, right, bottom), boxPaint)

            var i = 0
            while (i < face.landmarks.size - 1) {
                canvas.drawCircle(vx(face.landmarks[i]), vy(face.landmarks[i + 1]), 6f, pointPaint)
                i += 2
            }
        }

        caption?.let { text ->
            val face = faces.maxByOrNull { it.width * it.height } ?: return
            val left = min(vx(face.x1), vx(face.x2))
            val top = vy(face.y1)
            val textWidth = textPaint.measureText(text)
            val pad = 10f
            val boxTop = (top - textPaint.textSize - pad * 2).coerceAtLeast(0f)
            canvas.drawRect(left, boxTop, left + textWidth + pad * 2, boxTop + textPaint.textSize + pad * 2, textBg)
            canvas.drawText(text, left + pad, boxTop + textPaint.textSize + pad * 0.4f, textPaint)
        }
    }
}
