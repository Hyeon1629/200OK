package com.checkdang.app.ui.lifestyle.meal

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.checkdang.app.R
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.model.MealItem
import com.checkdang.app.data.remote.AiAdviceApiClient
import com.checkdang.app.databinding.ActivityMealDetailBinding
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import kotlinx.coroutines.launch
import java.util.Locale

class MealDetailActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMealDetailBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupToolbar()

        val summary = MockDataProvider.getMealSummary()
        if (summary != null) {
            setupPieChart(summary.carbsG, summary.proteinG, summary.fatG)
            setupKcalCard(summary.totalKcal, summary.goalKcal)
            setupMealList(summary.meals)
        } else {
            setupPieChartEmpty()
            setupKcalCard(0, 2000)
            setupMealList(emptyList())
        }

        setupAiAdviceButton()
    }

    private fun setupAiAdviceButton() {
        binding.btnAiAdvice.setOnClickListener {
            binding.btnAiAdvice.isEnabled = false
            binding.tvAiAnswer.text = "Gemini 응답을 불러오는 중..."

            lifecycleScope.launch {
                val result = runCatching {
                    AiAdviceApiClient.getDietAdviceForDemo()
                }

                binding.tvAiAnswer.text = result.getOrElse {
                    "AI 조언 요청 실패: ${it.message}"
                }
                binding.btnAiAdvice.isEnabled = true

                if (result.isFailure) {
                    Toast.makeText(this@MealDetailActivity, "AI 조언 요청에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "식사 상세"
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setupPieChartEmpty() {
        val chart = binding.pieMacro
        chart.setNoDataText("식사 기록이 없어요")
        chart.setNoDataTextColor(getColor(R.color.text_secondary))
        chart.clear()
    }

    private fun setupPieChart(carbs: Int, protein: Int, fat: Int) {
        val chart = binding.pieMacro

        val entries = listOf(
            PieEntry(carbs.toFloat(),   "탄수화물"),
            PieEntry(protein.toFloat(), "단백질"),
            PieEntry(fat.toFloat(),     "지방")
        )
        val colors = listOf(
            ContextCompat.getColor(this, R.color.carbs_color),
            ContextCompat.getColor(this, R.color.protein_color),
            ContextCompat.getColor(this, R.color.fat_color)
        )
        val dataSet = PieDataSet(entries, "").apply {
            this.colors      = colors
            sliceSpace       = 2f
            setDrawValues(true)
            valueTextSize    = 12f
            valueTextColor   = Color.WHITE
        }

        chart.apply {
            data             = PieData(dataSet).apply { setValueFormatter(com.github.mikephil.charting.formatter.PercentFormatter(chart)) }
            holeRadius       = 50f
            transparentCircleRadius = 55f
            setUsePercentValues(true)
            description.isEnabled = false
            legend.isEnabled      = false
            setDrawEntryLabels(false)
            centerText       = "탄·단·지"
            setCenterTextSize(14f)
            setCenterTextColor(ContextCompat.getColor(this@MealDetailActivity, R.color.text_primary))
            animateY(600)
        }
    }

    private fun setupKcalCard(total: Int, goal: Int) {
        if (total == 0) {
            binding.tvKcalValue.text = "-"
            binding.tvKcalGoal.text  = "기록 없음"
            binding.progressKcal.progress = 0
            return
        }
        val pct = total * 100 / goal
        binding.tvKcalValue.text = "${String.format(Locale.getDefault(), "%,d", total)} kcal"
        binding.tvKcalGoal.text  = "/ ${String.format(Locale.getDefault(), "%,d", goal)} kcal (${pct}%)"
        binding.progressKcal.progress = pct.coerceIn(0, 100)
    }

    private fun setupMealList(meals: List<MealItem>) {
        val inflater = LayoutInflater.from(this)
        meals.forEach { item ->
            val row = inflater.inflate(android.R.layout.simple_list_item_2, binding.layoutMeals, false)

            val tvTitle = row.findViewById<TextView>(android.R.id.text1)
            val tvSub   = row.findViewById<TextView>(android.R.id.text2)

            tvTitle.text     = "[${item.type}] ${item.name}"
            tvTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            tvSub.text       = "${item.kcal} kcal  ·  ${item.time}"
            tvSub.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

            // 구분선 (마지막 제외)
            if (item != meals.last()) {
                val divider = android.view.View(this)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { setMargins(16, 0, 16, 0) }
                divider.layoutParams = lp
                divider.setBackgroundColor(ContextCompat.getColor(this, R.color.divider))
                binding.layoutMeals.addView(divider)
            }

            binding.layoutMeals.addView(row)
        }
    }
}
