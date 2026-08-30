package com.mrzgaming.ezbox

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * View custom sederhana untuk menampilkan progress melingkar (ring), murni Kotlin
 * pakai Canvas.drawArc - tidak butuh NDK/library eksternal sama sekali.
 */
class RingProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var progress: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    var ringColor: Int = Color.CYAN
        set(value) {
            field = value
            invalidate()
        }

    private val strokeWidthPx = 18f

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        color = Color.parseColor("#1F2937")
        strokeCap = Paint.Cap.ROUND
    }

    private val foregroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
    }

    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = strokeWidthPx / 2 + 2f
        rect.set(pad, pad, width - pad, height - pad)

        canvas.drawArc(rect, 0f, 360f, false, backgroundPaint)

        foregroundPaint.color = ringColor
        val sweepAngle = 360f * (progress / 100f)
        // Mulai dari atas (-90 derajat), searah jarum jam
        canvas.drawArc(rect, -90f, sweepAngle, false, foregroundPaint)
    }
}
