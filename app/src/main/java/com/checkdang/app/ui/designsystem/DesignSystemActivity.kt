package com.checkdang.app.ui.designsystem

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.checkdang.app.R
import com.checkdang.app.databinding.ActivityDesignSystemBinding
import com.checkdang.app.util.GlucoseEvaluator
import com.checkdang.app.util.GlucoseStatus
import com.checkdang.app.util.MealTiming

class DesignSystemActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDesignSystemBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDesignSystemBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindGlucoseCards()
    }

    private fun bindGlucoseCards() {
        val cases = listOf(
            Triple(80,  MealTiming.POST_MEAL_2H, binding.cardGlucose80),
            Triple(150, MealTiming.POST_MEAL_2H, binding.cardGlucose150),
            Triple(220, MealTiming.POST_MEAL_2H, binding.cardGlucose220),
        )

        cases.forEach { (value, timing, card) ->
            val status = GlucoseEvaluator.evaluate(value, timing)
            val color  = GlucoseEvaluator.getColor(status, this)
            val label  = GlucoseEvaluator.getStatusLabel(status)

            val bgColor = when (status) {
                GlucoseStatus.NORMAL  -> getColor(R.color.status_normal_bg)
                GlucoseStatus.WARNING -> getColor(R.color.status_warning_bg)
                GlucoseStatus.DANGER  -> getColor(R.color.status_danger_bg)
            }

            card.tvGlucoseValue.text  = "${value} mg/dL"
            card.tvGlucoseStatus.text = label
            card.tvGlucoseStatus.setTextColor(color)
            card.tvGlucoseTiming.text = "식후 2시간"
            card.viewStatusIndicator.setBackgroundColor(color)
            card.root.setCardBackgroundColor(bgColor)
        }
    }
}
