package com.checkdang.app.ui.glucose.chart

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
                viewModel.filteredForChart.collect { records ->
                    updateChart(records)
                }
            }
        }
    }

    private fun updateChart(records: List<GlucoseRecord>) {
        val chart = binding.chartGlucose

        if (records.isEmpty()) {
            chart.clear()
            chart.invalidate()
            return
        }

        val sdf = SimpleDateFormat("MM/dd", Locale.KOREAN)
        val dateLabels = records.map { sdf.format(Date(it.measuredAt)) }

        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float) =
                dateLabels.getOrNull(value.toInt()) ?: ""
        }
        chart.xAxis.labelCount = minOf(records.size, 7)

        val entries = records.mapIndexed { i, r -> Entry(i.toFloat(), r.value.toFloat()) }
        val dotColors = records.map { r ->
            GlucoseEvaluator.getColor(r.status, requireContext())
        }

        val dataSet = LineDataSet(entries, "혈당").apply {
            color        = ContextCompat.getColor(requireContext(), R.color.brand_green)
            lineWidth    = 2f
            circleColors = dotColors
            circleRadius = 5f
            setDrawValues(false)
            setDrawFilled(false)
            setDrawCircles(true)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        chart.data = LineData(dataSet)
        chart.animateX(400)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
