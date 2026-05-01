package com.checkdang.app.ui.lifestyle.sleep

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.checkdang.app.R
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.databinding.ActivitySleepDetailBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter

class SleepDetailActivity : AppCompatActivity() {

    private val binding by lazy { ActivitySleepDetailBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupToolbar()

        val summary = MockDataProvider.getSleepSummary()
        if (summary != null) {
            setupSummaryCard(summary)
        } else {
            setupSummaryEmpty()
        }
        setupWeeklyChart()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "수면 상세"
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setupSummaryEmpty() {
        binding.tvSleepTotal.text  = "-"
        binding.tvEfficiency.text  = "-"
        binding.tvDeepHours.text   = "-"
        binding.tvLightHours.text  = "-"
        binding.tvRemHours.text    = "-"
        binding.tvBedtime.text     = "-"
        binding.tvWakeTime.text    = "-"
        setWeight(binding.viewDeep,  1f / 3)
        setWeight(binding.viewLight, 1f / 3)
        setWeight(binding.viewRem,   1f / 3)
    }

    private fun setupSummaryCard(s: com.checkdang.app.data.model.SleepSummary) {
        val hours   = s.totalHours.toInt()
        val minutes = ((s.totalHours - hours) * 60).toInt()
        binding.tvSleepTotal.text  = "${hours}시간 ${minutes}분"
        binding.tvEfficiency.text  = "${s.efficiency}%"
        binding.tvDeepHours.text   = "${s.deepHours}h"
        binding.tvLightHours.text  = "${s.lightHours}h"
        binding.tvRemHours.text    = "${s.remHours}h"
        binding.tvBedtime.text     = s.bedtime
        binding.tvWakeTime.text    = s.wakeTime

        // 수면 단계 막대 가중치
        val total = s.deepHours + s.lightHours + s.remHours
        setWeight(binding.viewDeep,  s.deepHours  / total)
        setWeight(binding.viewLight, s.lightHours / total)
        setWeight(binding.viewRem,   s.remHours   / total)
    }

    private fun setWeight(view: View, weight: Float) {
        (view.layoutParams as LinearLayout.LayoutParams).weight = weight
        view.requestLayout()
    }

    private fun setupWeeklyChart() {
        val data  = MockDataProvider.getWeeklySleepHours()
        val chart = binding.chartWeeklySleep

        chart.apply {
            description.isEnabled = false
            legend.isEnabled      = false
            setTouchEnabled(false)
            setDrawGridBackground(false)
            axisRight.isEnabled = false
        }

        if (data.isEmpty()) {
            chart.setNoDataText("수면 기록이 없어요")
            chart.setNoDataTextColor(getColor(R.color.text_secondary))
            chart.clear()
            return
        }

        val dayLabels = getLast7DayLabels()
        chart.xAxis.apply {
            position       = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity    = 1f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) =
                    dayLabels.getOrNull(value.toInt()) ?: ""
            }
        }
        chart.axisLeft.apply {
            axisMinimum = 0f
            axisMaximum = 10f
            granularity = 2f
        }

        val entries = data.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }
        val dataSet = BarDataSet(entries, "수면(시간)").apply {
            color = ContextCompat.getColor(this@SleepDetailActivity, R.color.sleep_light)
            setDrawValues(false)
        }
        chart.data = BarData(dataSet).apply { barWidth = 0.6f }
        chart.animateY(400)
    }

    private fun getLast7DayLabels(): List<String> {
        val korDays = arrayOf("일", "월", "화", "수", "목", "금", "토")
        return (6 downTo 0).map { daysAgo ->
            val cal = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
            }
            korDays[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
        }
    }
}
