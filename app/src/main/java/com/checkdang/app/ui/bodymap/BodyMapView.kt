package com.checkdang.app.ui.bodymap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.checkdang.app.R
import com.checkdang.app.data.model.BodyPart
import com.checkdang.app.data.model.BodyView

/**
 * 추상화된 인체 바디맵 커스텀 뷰.
 * 정면/후면 전환: setBodyView()
 * 선택된 부위: selectedPart
 * 부위 클릭 콜백: onPartSelected
 */
class BodyMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onPartSelected: ((BodyPart) -> Unit)? = null

    private var currentView: BodyView = BodyView.FRONT
    var selectedPart: BodyPart? = null
        private set

    // Highlight paint
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_green)
        alpha = 100
        style = Paint.Style.FILL
    }
    private val highlightStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_green)
        alpha = 200
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    // Body fill paint
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD0D0D0.toInt()
        style = Paint.Style.FILL
    }
    private val bodyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFA0A0A0.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    // Normalized regions: RectF values are 0..1 relative to 200x400 viewport
    // Format: left/top/right/bottom as fractions of width/height
    private val frontRegions: Map<BodyPart, RectF> = mapOf(
        BodyPart.HEAD               to nRect(74, 10, 126, 66),
        BodyPart.NECK_FRONT         to nRect(90, 66, 110, 82),
        BodyPart.LEFT_SHOULDER_FRONT  to nRect(30, 82, 60, 116),
        BodyPart.RIGHT_SHOULDER_FRONT to nRect(140, 82, 170, 116),
        BodyPart.CHEST              to nRect(52, 82, 148, 145),
        BodyPart.LEFT_ARM_FRONT     to nRect(16, 108, 52, 178),
        BodyPart.RIGHT_ARM_FRONT    to nRect(148, 108, 184, 178),
        BodyPart.ABDOMEN            to nRect(52, 145, 148, 200),
        BodyPart.LEFT_HIP_FRONT     to nRect(52, 190, 100, 224),
        BodyPart.RIGHT_HIP_FRONT    to nRect(100, 190, 148, 224),
        BodyPart.LEFT_THIGH_FRONT   to nRect(52, 224, 100, 294),
        BodyPart.RIGHT_THIGH_FRONT  to nRect(100, 224, 148, 294),
        BodyPart.LEFT_KNEE          to nRect(52, 294, 100, 316),
        BodyPart.RIGHT_KNEE         to nRect(100, 294, 148, 316),
        BodyPart.LEFT_SHIN          to nRect(54, 316, 100, 390),
        BodyPart.RIGHT_SHIN         to nRect(100, 316, 146, 390),
    )

    private val backRegions: Map<BodyPart, RectF> = mapOf(
        BodyPart.HEAD                  to nRect(74, 10, 126, 66),
        BodyPart.NECK_BACK             to nRect(90, 66, 110, 82),
        BodyPart.LEFT_SHOULDER_BACK    to nRect(28, 80, 62, 130),
        BodyPart.RIGHT_SHOULDER_BACK   to nRect(138, 80, 172, 130),
        BodyPart.UPPER_BACK            to nRect(52, 82, 148, 160),
        BodyPart.LOWER_BACK            to nRect(52, 160, 148, 200),
        BodyPart.LEFT_ARM_FRONT        to nRect(14, 118, 52, 186),
        BodyPart.RIGHT_ARM_FRONT       to nRect(148, 118, 186, 186),
        BodyPart.LEFT_THIGH_FRONT      to nRect(52, 234, 100, 304),
        BodyPart.RIGHT_THIGH_FRONT     to nRect(100, 234, 148, 304),
        BodyPart.LEFT_SHIN             to nRect(52, 304, 100, 390),
        BodyPart.RIGHT_SHIN            to nRect(100, 304, 148, 390),
    )

    private fun nRect(l: Int, t: Int, r: Int, b: Int) =
        RectF(l / 200f, t / 400f, r / 200f, b / 400f)

    private fun currentRegions() =
        if (currentView == BodyView.FRONT) frontRegions else backRegions

    fun setBodyView(view: BodyView) {
        if (currentView == view) return
        currentView = view
        // Clear selection when switching views
        selectedPart = null
        invalidate()
    }

    fun clearSelection() {
        selectedPart = null
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        // Maintain 1:2 aspect ratio
        setMeasuredDimension(w, w * 2)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        val regions = currentRegions()
        // Draw all body regions
        for ((part, nRectF) in regions) {
            val rect = nRectF.toScaled(w, h)
            if (part == selectedPart) {
                canvas.drawRoundRect(rect, 8f, 8f, highlightPaint)
                canvas.drawRoundRect(rect, 8f, 8f, highlightStrokePaint)
            } else {
                canvas.drawRoundRect(rect, 8f, 8f, bodyPaint)
                canvas.drawRoundRect(rect, 8f, 8f, bodyStrokePaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val w = width.toFloat()
        val h = height.toFloat()
        val nx = event.x / w
        val ny = event.y / h

        val hit = currentRegions().entries
            .firstOrNull { (_, rect) -> rect.contains(nx, ny) }
            ?.key

        if (hit != null) {
            selectedPart = hit
            invalidate()
            performClick()
            onPartSelected?.invoke(hit)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun RectF.toScaled(w: Float, h: Float) =
        RectF(left * w, top * h, right * w, bottom * h)
}
