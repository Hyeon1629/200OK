package com.checkdang.app.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.checkdang.app.R
import com.checkdang.app.util.GlucoseEvaluator
import com.checkdang.app.util.GlucoseStatus

class StatusChip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        val h = (6 * resources.displayMetrics.density).toInt()
        val v = (12 * resources.displayMetrics.density).toInt()
        setPadding(v, h, v, h)
        setBackgroundResource(R.drawable.bg_chip)
        textSize = 12f
    }

    fun setStatus(status: GlucoseStatus) {
        setTextColor(GlucoseEvaluator.getColor(status, context))
        val bgRes = when (status) {
            GlucoseStatus.NORMAL  -> R.color.status_normal_bg
            GlucoseStatus.WARNING -> R.color.status_warning_bg
            GlucoseStatus.DANGER  -> R.color.status_danger_bg
        }
        backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, bgRes))
        text = GlucoseEvaluator.getStatusLabel(status)
    }
}
