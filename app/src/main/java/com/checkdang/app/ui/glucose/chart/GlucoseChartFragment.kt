package com.checkdang.app.ui.glucose.chart

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
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.data.remote.BloodGlucosePrediction
import com.checkdang.app.databinding.FragmentGlucoseChartBinding
import com.checkdang.app.ui.glucose.GlucoseViewModel
import com.checkdang.app.ui.glucose.PredictionUiState
import com.checkdang.app.util.GlucoseEvaluator
import com.checkdang.app.util.MealTiming
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

class GlucoseChartFragment : Fragment() {

    private var _binding: FragmentGlucoseChartBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GlucoseViewModel by viewModels(ownerProducer = { requireParentFragment() })

    private var chartRecords: List<GlucoseRecord> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGlucoseChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupChart()
        setupPredictionChart()
        setupChipGroup()
        if (SessionHolder.isGuest) {
            // 혈당 예측은 로그인 사용자 전용(백엔드 정책). 게스트는 사전 차단한다.
            showPredictionLoginRequired()
        } else {
            binding.btnRunPrediction.setOnClickListener { viewModel.runPrediction() }
        }
        observeData()
    }

    /** 게스트: 혈당 예측 섹션을 비활성화하고 로그인 유도 안내를 고정 표시. */
    private fun showPredictionLoginRequired() {
        binding.btnRunPrediction.isEnabled = false
        binding.tvPredStatus.visibility = View.VISIBLE
        binding.tvPredStatus.text = "혈당 예측은 로그인 후 이용할 수 있어요."
        binding.tvPredSummary.visibility = View.GONE
        binding.chartPrediction.visibility = View.GONE
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
                launch {
                    viewModel.filteredForChart.collect { records ->
                        chartRecords = records
                        updateChart()
                    }
                }
                // 혈당 예측은 로그인 사용자 전용 — 게스트는 조회/구독하지 않는다(고정 안내 유지).
                if (!SessionHolder.isGuest) {
                    // 예측은 on-demand 전용 — 진입 시엔 '예측하기' 유도 상태만 둔다(결과 보유 시 유지)
                    viewModel.loadLatestPrediction()
                    launch {
                        viewModel.prediction.collect { bindPrediction(it) }
                    }
                }
            }
        }
    }

    // ── AI 예측 (향후 3시간·36점) ──────────────────────────────────────────────

    private fun setupPredictionChart() {
        val chart = binding.chartPrediction
        chart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setScaleEnabled(false)
            setDrawGridBackground(false)
        }
        // 명세 권장: 정상범위(70~180) 가이드라인과 함께 표시
        val low = LimitLine(70f).apply {
            lineColor = ContextCompat.getColor(requireContext(), R.color.status_danger)
            lineWidth = 1f
            enableDashedLine(10f, 5f, 0f)
        }
        val high = LimitLine(180f).apply {
            lineColor = ContextCompat.getColor(requireContext(), R.color.status_warning)
            lineWidth = 1f
            enableDashedLine(10f, 5f, 0f)
        }
        chart.axisLeft.apply {
            axisMinimum = 40f
            axisMaximum = 280f
            granularity = 50f
            addLimitLine(low)
            addLimitLine(high)
            setDrawGridLines(true)
            gridColor = ContextCompat.getColor(requireContext(), R.color.divider)
        }
        chart.axisRight.isEnabled = false
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
        }
    }

    private fun bindPrediction(state: PredictionUiState) {
        val statusView = binding.tvPredStatus
        val summaryView = binding.tvPredSummary
        val chart = binding.chartPrediction

        fun showMessage(msg: String) {
            statusView.visibility = View.VISIBLE
            statusView.text = msg
            summaryView.visibility = View.GONE
            chart.visibility = View.GONE
            chart.clear()
        }

        // Loading 중에도 버튼 중복 클릭 방지
        binding.btnRunPrediction.isEnabled = state !is PredictionUiState.Loading

        when (state) {
            is PredictionUiState.Idle,
            is PredictionUiState.Loading -> showMessage(
                if (state is PredictionUiState.Loading) "예측을 불러오는 중…" else ""
            )
            is PredictionUiState.Empty -> showMessage("아직 예측이 없어요. ‘예측하기’를 눌러보세요.")
            is PredictionUiState.Error -> showMessage(state.message)
            is PredictionUiState.Loaded -> renderPrediction(state.prediction)
        }
    }

    private fun renderPrediction(p: BloodGlucosePrediction) {
        val ctx = requireContext()
        val values = p.predictions
        if (values.isEmpty()) {
            binding.tvPredStatus.visibility = View.VISIBLE
            binding.tvPredStatus.text = "예측 결과가 비어 있어요."
            binding.tvPredSummary.visibility = View.GONE
            binding.chartPrediction.visibility = View.GONE
            return
        }
        binding.tvPredStatus.visibility = View.GONE

        // i번째 예측값 시각 = 예측 기준 시각 + interval × (i+1)
        val baseTime = runCatching {
            LocalDateTime.parse(p.predictedAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }.getOrNull()
        val hhmm = DateTimeFormatter.ofPattern("HH:mm")

        val entries = values.mapIndexed { i, v -> Entry(i.toFloat(), v) }
        val dotColors = values.map {
            GlucoseEvaluator.getColor(
                GlucoseEvaluator.evaluate(it.toInt(), MealTiming.FASTING), ctx
            )
        }
        val set = LineDataSet(entries, "예측").apply {
            color = ContextCompat.getColor(ctx, R.color.brand_green)
            lineWidth = 2f
            enableDashedLine(10f, 6f, 0f)
            circleColors = dotColors
            circleRadius = 2.5f
            setDrawValues(false)
            setDrawCircles(true)
            setDrawFilled(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        // X축 라벨: 예측 기준 시각이 파싱되면 HH:mm, 아니면 +분
        binding.chartPrediction.xAxis.apply {
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val i = value.toInt()
                    if (i < 0 || i >= values.size) return ""
                    val minutes = (p.intervalMinutes * (i + 1)).toLong()
                    return baseTime?.plusMinutes(minutes)?.format(hhmm) ?: "+${minutes}m"
                }
            }
            labelCount = 6
            labelRotationAngle = -30f
        }

        binding.chartPrediction.visibility = View.VISIBLE
        binding.chartPrediction.data = LineData(set)
        binding.chartPrediction.animateX(400)

        val max = values.maxOrNull()?.toInt() ?: 0
        val min = values.minOrNull()?.toInt() ?: 0
        val baseLabel = baseTime?.format(hhmm)?.let { "예측 기준 $it · " } ?: ""
        val hours = p.horizonMinutes / 60
        binding.tvPredSummary.visibility = View.VISIBLE
        binding.tvPredSummary.text =
            "${baseLabel}향후 ${hours}시간(${p.intervalMinutes}분 간격) · 최고 $max / 최저 $min mg/dL"
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
        val labels = records.map { sdf.format(Date(it.measuredAt)) }

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

        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float) =
                labels.getOrNull(value.toInt()) ?: ""
        }
        chart.xAxis.labelCount = minOf(labels.size, 8)

        chart.data = LineData(actualSet)
        chart.animateX(400)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
