package com.checkdang.app.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.checkdang.app.R

class DonutProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 0.0 ~ 1.0 */
    var progress: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    private val strokePx = 12f * resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = strokePx
        color       = ContextCompat.getColor(context, R.color.divider)
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap   = Paint.Cap.ROUND
        color       = ContextCompat.getColor(context, R.color.brand_green)
    }

    private val oval = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val inset = strokePx / 2f
        oval.set(inset, inset, w - inset, h - inset)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawArc(oval, -90f, 360f, false, trackPaint)
        if (progress > 0f) {
            canvas.drawArc(oval, -90f, 360f * progress, false, progressPaint)
        }
    }
}
