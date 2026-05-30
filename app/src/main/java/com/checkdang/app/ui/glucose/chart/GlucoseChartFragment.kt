package com.checkdang.app.ui.glucose.chart

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.checkdang.app.R
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.databinding.FragmentGlucoseChartBinding
import com.checkdang.app.ui.glucose.GlucoseViewModel
import com.checkdang.app.ui.glucose.prediction.GlucosePredictor
import com.checkdang.app.util.GlucoseEvaluator
import com.checkdang.app.util.MealTiming
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GlucoseChartFragment : Fragment() {

    private var _binding: FragmentGlucoseChartBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GlucoseViewModel by viewModels(ownerProducer = { requireParentFragment() })

    // 차트는 기간 필터된 기록으로, 예측은 전체 기록으로 산출 → 둘을 보관해 함께 렌더
    private var chartRecords: List<GlucoseRecord> = emptyList()
    private var prediction: GlucosePredictor.Result? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGlucoseChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupChart()
        setupChipGroup()
        observeData()
    }

    private fun setupChipGroup() {
        binding.chipGroupPeriod.setOnCheckedStateChangeListener { _, checkedIds ->
            val days = when (checkedIds.firstOrNull()) {
                R.id.chip_7d -> 7
                R.id.chip_1m -> 30
                R.id.chip_3m -> 90
                else -> 7
            }
            viewModel.setFilter(days)
        }
    }

    private fun setupChart() {
        val chart = binding.chartGlucose

        chart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setScaleEnabled(false)
            setDrawGridBackground(false)
        }

        // 정상 범위 LimitLine
        val lowerLimit = LimitLine(70f, "저혈당 경계").apply {
            lineColor = ContextCompat.getColor(requireContext(), R.color.status_danger)
            lineWidth = 1f
            enableDashedLine(10f, 5f, 0f)
            textColor = ContextCompat.getColor(requireContext(), R.color.status_danger)
            textSize = 9f
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        }
        val upperLimit = LimitLine(140f, "식후 정상 상한").apply {
            lineColor = ContextCompat.getColor(requireContext(), R.color.status_warning)
            lineWidth = 1f
            enableDashedLine(10f, 5f, 0f)
            textColor = ContextCompat.getColor(requireContext(), R.color.status_warning)
            textSize = 9f
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        }

        chart.axisLeft.apply {
            axisMinimum = 40f
            axisMaximum = 280f
            granularity = 50f
            addLimitLine(lowerLimit)
            addLimitLine(upperLimit)
            setDrawGridLines(true)
            gridColor = ContextCompat.getColor(requireContext(), R.color.divider)
        }
        chart.axisRight.isEnabled = false

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            labelRotationAngle = -30f
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 기간 필터된 기록 → 차트
                launch {
                    viewModel.filteredForChart.collect { records ->
                        chartRecords = records
                        updateChart()
                    }
                }
                // 전체 기록 → 예측 산출(분석은 필터와 무관하게 전체 기록 기준)
                launch {
                    viewModel.records.collect { all ->
                        prediction = GlucosePredictor.predict(all)
                        bindPrediction()
                        updateChart()
                    }
                }
            }
        }
    }

    // ── AI 예측 카드 ──────────────────────────────────────────────────────────
    private fun bindPrediction() {
        val p = prediction
        if (p == null) {
            binding.cardPrediction.visibility = View.GONE
            return
        }
        binding.cardPrediction.visibility = View.VISIBLE

        val trendColor = when (p.trend) {
            GlucosePredictor.Trend.RISING -> R.color.status_warning
            GlucosePredictor.Trend.FALLING -> R.color.status_normal
            GlucosePredictor.Trend.STABLE -> R.color.text_secondary
        }
        binding.tvPredTrend.text = "${p.trend.arrow} ${p.trend.label}"
        binding.tvPredTrend.setTextColor(ContextCompat.getColor(requireContext(), trendColor))
        binding.tvPredHeadline.text = p.headline
        binding.tvPredConfidence.text = "예측 신뢰도 ${p.confidence}% · ${p.detail}"

        binding.layoutPredPoints.removeAllViews()
        p.points.forEach { binding.layoutPredPoints.addView(buildPointColumn(it)) }
    }

    private fun buildPointColumn(point: GlucosePredictor.Point): View {
        val ctx = requireContext()
        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val label = TextView(ctx).apply {
            text = point.label
            textSize = 12f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        }
        val value = TextView(ctx).apply {
            text = point.value.toString()
            textSize = 22f
            setTextColor(GlucoseEvaluator.getColor(point.status, ctx))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val unit = TextView(ctx).apply {
            text = "mg/dL"
            textSize = 10f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        }
        column.addView(label)
        column.addView(value)
        column.addView(unit)
        return column
    }

    private fun updateChart() {
        val chart = binding.chartGlucose
        val records = chartRecords

        if (records.isEmpty()) {
            chart.clear()
            chart.invalidate()
            return
        }

        val ctx = requireContext()
        val sdf = SimpleDateFormat("MM/dd", Locale.KOREAN)
        val labels = records.map { sdf.format(Date(it.measuredAt)) }.toMutableList()

        val entries = records.mapIndexed { i, r -> Entry(i.toFloat(), r.value.toFloat()) }
        val dotColors = records.map { GlucoseEvaluator.getColor(it.status, ctx) }

        val actualSet = LineDataSet(entries, "혈당").apply {
            color        = ContextCompat.getColor(ctx, R.color.brand_green)
            lineWidth    = 2f
            circleColors = dotColors
            circleRadius = 5f
            setDrawValues(false)
            setDrawFilled(false)
            setDrawCircles(true)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val dataSets = mutableListOf<com.github.mikephil.charting.interfaces.datasets.ILineDataSet>(actualSet)

        // 예측 점선 오버레이 — 마지막 실측값에서 이어지도록 connector 포함
        prediction?.let { p ->
            val base = records.size - 1
            val predEntries = mutableListOf(Entry(base.toFloat(), records.last().value.toFloat()))
            val predCircleColors = mutableListOf(ContextCompat.getColor(ctx, R.color.brand_green))
            p.points.forEachIndexed { i, pt ->
                predEntries.add(Entry((records.size + i).toFloat(), pt.value.toFloat()))
                predCircleColors.add(GlucoseEvaluator.getColor(pt.status, ctx))
                labels.add(pt.label)
            }
            val predSet = LineDataSet(predEntries, "예측").apply {
                color = ContextCompat.getColor(ctx, R.color.text_secondary)
                lineWidth = 2f
                enableDashedLine(10f, 6f, 0f)
                circleColors = predCircleColors
                circleRadius = 4f
                setDrawValues(false)
                setDrawFilled(false)
                setDrawCircles(true)
                mode = LineDataSet.Mode.LINEAR
            }
            dataSets.add(predSet)
        }

        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float) =
                labels.getOrNull(value.toInt()) ?: ""
        }
        chart.xAxis.labelCount = minOf(labels.size, 8)

        chart.data = LineData(dataSets)
        chart.animateX(400)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
