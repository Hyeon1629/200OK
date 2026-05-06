package com.checkdang.app.ui.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.checkdang.app.R
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.model.GlucoseSummary
import com.checkdang.app.data.model.LifestyleSummary
import com.checkdang.app.databinding.FragmentHomeBinding
import com.checkdang.app.util.GlucoseEvaluator
import com.checkdang.app.util.GlucoseStatus
import com.checkdang.app.util.MealTiming
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IFillFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupHeader()
        setupGlucoseCard(viewModel.glucoseSummary.value)
        setupLifestyleSection(viewModel.lifestyleSummary.value)
        setupWeeklyChart(viewModel.weeklyGlucose.value)
        setupClickListeners()
    }

    // ── 헤더 ──────────────────────────────────────────────────────────────
    private fun setupHeader() {
        val nickname = SessionHolder.currentProfile?.nickname ?: "체크당 사용자"
        binding.tvGreeting.text = "안녕하세요, ${nickname}님"

        val sdf = SimpleDateFormat("yyyy.MM.dd", Locale.KOREAN)
        val dayOfWeekSdf = SimpleDateFormat("E요일", Locale.KOREAN)
        val today = Calendar.getInstance().time
        binding.tvDate.text = "오늘 ${sdf.format(today)} ${dayOfWeekSdf.format(today)}"
    }

    // ── 메인 혈당 카드 ────────────────────────────────────────────────────
    private fun setupGlucoseCard(summary: GlucoseSummary?) {
        if (summary == null) {
            binding.layoutGlucoseData.visibility = View.GONE
            binding.layoutGlucoseEmpty.visibility = View.VISIBLE
            return
        }

        binding.layoutGlucoseData.visibility = View.VISIBLE
        binding.layoutGlucoseEmpty.visibility = View.GONE

        val status = GlucoseEvaluator.evaluate(summary.latestValue, summary.timing)
        val color  = GlucoseEvaluator.getColor(status, requireContext())
        val label  = GlucoseEvaluator.getStatusLabel(status)

        binding.tvGlucoseValue.text = summary.latestValue.toString()
        binding.tvGlucoseValue.setTextColor(color)

        binding.tvGlucoseStatus.text = label
        binding.tvGlucoseStatus.setTextColor(color)
        val bgColor = when (status) {
            GlucoseStatus.NORMAL  -> requireContext().getColor(R.color.status_normal_bg)
            GlucoseStatus.WARNING -> requireContext().getColor(R.color.status_warning_bg)
            GlucoseStatus.DANGER  -> requireContext().getColor(R.color.status_danger_bg)
        }
        binding.tvGlucoseStatus.backgroundTintList = ColorStateList.valueOf(bgColor)

        binding.tvGlucoseTime.text  = "${summary.measuredAt} · ${summary.timing.label}"
        binding.tvTodayCount.text   = "오늘 측정 ${summary.todayCount}회"
        binding.tvTodayAverage.text = "평균 ${summary.todayAverage} mg/dL"
    }

    // ── 라이프스타일 섹션 ──────────────────────────────────────────────────
    private fun setupLifestyleSection(s: LifestyleSummary?) {
        if (s == null) {
            binding.tvExerciseValue.text = "-"
            binding.progressExercise.progress = 0
            binding.tvMealValue.text = "-"
            binding.progressMeal.progress = 0
            binding.tvSleepValue.text = "-"
            binding.tvSleepEfficiency.text = "-"
            return
        }

        binding.tvExerciseValue.text = "${s.exerciseMinutes}/${s.exerciseGoalMinutes}분"
        binding.progressExercise.progress =
            (s.exerciseMinutes * 100 / s.exerciseGoalMinutes).coerceIn(0, 100)

        binding.tvMealValue.text = "${String.format(Locale.getDefault(), "%,d", s.mealKcal)} kcal"
        binding.progressMeal.progress =
            (s.mealKcal * 100 / s.mealGoalKcal).coerceIn(0, 100)

        binding.tvSleepValue.text      = "${s.sleepHours}시간"
        binding.tvSleepEfficiency.text = "효율 ${s.sleepEfficiency}%"
    }

    // ── 7일 혈당 차트 ──────────────────────────────────────────────────────
    private fun setupWeeklyChart(weeklyData: List<Float>) {
        val chart = binding.chartWeeklyGlucose

        chart.apply {
            description.isEnabled = false
            legend.isEnabled      = false
            setTouchEnabled(false)
            setDrawGridBackground(false)
        }

        if (weeklyData.isEmpty()) {
            chart.setNoDataText("혈당 기록이 없어요")
            chart.setNoDataTextColor(requireContext().getColor(R.color.text_secondary))
            chart.clear()
            return
        }

        // X축
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            val dayLabels = getLast7DayLabels()
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) =
                    dayLabels.getOrNull(value.toInt()) ?: ""
            }
        }

        // Y축
        chart.axisLeft.apply {
            axisMinimum = 60f
            axisMaximum = 260f
            granularity = 50f
            setDrawGridLines(true)
        }
        chart.axisRight.isEnabled = false

        // 정상 범위 채우기 (70~140)
        val fillColor = ContextCompat.getColor(requireContext(), R.color.brand_green_light)
        val fillEntries = (0..6).map { Entry(it.toFloat(), 140f) }
        val fillDataSet = LineDataSet(fillEntries, "").apply {
            color     = Color.TRANSPARENT
            lineWidth = 0f
            setDrawCircles(false)
            setDrawValues(false)
            setDrawFilled(true)
            this.fillColor = fillColor
            fillAlpha      = 80
            fillFormatter  = IFillFormatter { _, _ -> 70f }
        }

        // 혈당 라인
        val entries = weeklyData.mapIndexed { i, v -> Entry(i.toFloat(), v) }
        val dotColors = weeklyData.map { v ->
            GlucoseEvaluator.getColor(
                GlucoseEvaluator.evaluate(v.toInt(), MealTiming.POST_MEAL_2H),
                requireContext()
            )
        }
        val mainDataSet = LineDataSet(entries, "혈당").apply {
            color        = ContextCompat.getColor(requireContext(), R.color.brand_green)
            lineWidth    = 2.5f
            circleColors = dotColors
            circleRadius = 5f
            setDrawValues(false)
            setDrawFilled(false)
        }

        chart.data = LineData(fillDataSet, mainDataSet)
        chart.invalidate()
    }

    private fun getLast7DayLabels(): List<String> {
        val korDays = arrayOf("일", "월", "화", "수", "목", "금", "토")
        return (6 downTo 0).map { daysAgo ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
            korDays[cal.get(Calendar.DAY_OF_WEEK) - 1]
        }
    }

    // ── 클릭 리스너 ────────────────────────────────────────────────────────
    private fun setupClickListeners() {
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)

        binding.cardGlucose.setOnClickListener {
            bottomNav.selectedItemId = R.id.nav_glucose
        }
        binding.btnAddGlucose.setOnClickListener {
            bottomNav.selectedItemId = R.id.nav_glucose
        }
        val toLifestyle = View.OnClickListener {
            bottomNav.selectedItemId = R.id.nav_lifestyle
        }
        binding.cardExercise.setOnClickListener(toLifestyle)
        binding.cardMeal.setOnClickListener(toLifestyle)
        binding.cardSleep.setOnClickListener(toLifestyle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
