package com.checkdang.app.ui.lifestyle.exercise

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.checkdang.app.R
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.model.ExerciseSession
import com.checkdang.app.databinding.ActivityExerciseDetailBinding
import com.checkdang.app.databinding.ItemExerciseSessionBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

class ExerciseDetailActivity : AppCompatActivity() {

    private val binding by lazy { ActivityExerciseDetailBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupToolbar()

        val summary = MockDataProvider.getExerciseSummary()
        if (summary != null) {
            setupDonut(summary.totalMinutes, summary.goalMinutes)
            setupStats(summary.totalMinutes, summary.goalMinutes, summary.totalCalories)
            setupSessionList(summary.sessions)
        } else {
            setupDonut(0, 60)
            setupStats(0, 60, 0)
            setupSessionList(emptyList())
        }
        setupWeeklyChart()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        // 화살표 방향 반전 (뒤로가기 아이콘으로 사용)
        binding.toolbar.setNavigationIcon(R.drawable.ic_chevron_right)
        binding.toolbar.navigationIcon?.apply {
            // rotate 180 to point left
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "운동 상세"
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setupDonut(minutes: Int, goal: Int) {
        val progress = minutes.toFloat() / goal
        binding.donutExerciseDetail.progress = progress
        binding.tvDetailPercent.text = "${(progress * 100).toInt()}%"
        binding.tvDetailSub.text    = "$minutes / ${goal}분"
    }

    private fun setupStats(minutes: Int, goal: Int, calories: Int) {
        binding.tvTotalMinutes.text  = "${minutes}분"
        binding.tvTotalCalories.text = "${calories} kcal"
    }

    private fun setupSessionList(sessions: List<ExerciseSession>) {
        binding.recyclerSessions.apply {
            layoutManager = LinearLayoutManager(this@ExerciseDetailActivity)
            isNestedScrollingEnabled = false
            adapter = SessionAdapter(sessions)
        }
    }

    private fun setupWeeklyChart() {
        val data  = MockDataProvider.getWeeklyExerciseMinutes()
        val chart = binding.chartWeeklyExercise

        chart.apply {
            description.isEnabled = false
            legend.isEnabled      = false
            setTouchEnabled(false)
            setDrawGridBackground(false)
            axisRight.isEnabled = false
        }

        if (data.isEmpty()) {
            chart.setNoDataText("운동 기록이 없어요")
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
            axisMaximum = 80f
            granularity = 20f
        }

        val entries = data.mapIndexed { i, v -> Entry(i.toFloat(), v.toFloat()) }
        val dataSet = LineDataSet(entries, "운동(분)").apply {
            color        = ContextCompat.getColor(this@ExerciseDetailActivity, R.color.brand_green)
            lineWidth    = 2.5f
            circleRadius = 5f
            setCircleColor(ContextCompat.getColor(this@ExerciseDetailActivity, R.color.brand_green))
            setDrawValues(false)
            setDrawFilled(true)
            fillColor    = ContextCompat.getColor(this@ExerciseDetailActivity, R.color.brand_green_light)
            fillAlpha    = 80
        }
        chart.data = LineData(dataSet)
        chart.invalidate()
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

    // ── 세션 어댑터 ────────────────────────────────────────────────────────

    private inner class SessionAdapter(private val sessions: List<ExerciseSession>) :
        RecyclerView.Adapter<SessionAdapter.VH>() {

        inner class VH(val b: ItemExerciseSessionBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemExerciseSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = sessions.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val s = sessions[position]
            holder.b.tvExerciseIcon.text   = s.type.first().toString()
            holder.b.tvSessionType.text    = s.type
            holder.b.tvSessionStats.text   = "${s.durationMin}분 · ${s.calories} kcal"
            holder.b.tvSessionTime.text    = s.startedAt
        }
    }
}
