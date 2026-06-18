package com.checkdang.app.ui.glucose.input

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.checkdang.app.R
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.model.GlucoseRecord
import com.checkdang.app.databinding.BottomSheetGlucoseInputBinding
import com.checkdang.app.util.GlucoseEvaluator
import com.checkdang.app.util.GlucoseStatus
import com.checkdang.app.util.MealTiming
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class GlucoseInputBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "GlucoseInputBottomSheet"
    }

    private var _binding: BottomSheetGlucoseInputBinding? = null
    private val binding get() = _binding!!

    var onRecordSaved: ((GlucoseRecord) -> Unit)? = null

    private val measuredCal: Calendar = Calendar.getInstance()
    private val timeSdf = SimpleDateFormat("HH:mm", Locale.KOREAN)
    private var debounceJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetGlucoseInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // 상단 16dp 라운딩 적용
        applyTopRoundedCorners()

        // 기본값
        binding.etValue.setText("100")
        binding.tvMeasuredTime.text = timeSdf.format(measuredCal.time)

        setupValueWatcher()
        setupTimePicker()
        setupSaveButton()

        // 초기 상태 칩 표시
        updateStatusChip(100)
    }

    private fun setupValueWatcher() {
        binding.etValue.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                debounceJob?.cancel()
                debounceJob = lifecycleScope.launch {
                    delay(200)
                    val value = s?.toString()?.toIntOrNull() ?: return@launch
                    updateStatusChip(value)
                }
            }
        })
    }

    private fun updateStatusChip(value: Int) {
        val timing = getSelectedTiming()
        val status = GlucoseEvaluator.evaluate(value, timing)
        binding.chipStatusPreview.setStatus(status)
        binding.chipStatusPreview.visibility = View.VISIBLE
    }

    private fun setupTimePicker() {
        binding.tvMeasuredTime.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    measuredCal.set(Calendar.HOUR_OF_DAY, hour)
                    measuredCal.set(Calendar.MINUTE, minute)
                    binding.tvMeasuredTime.text = timeSdf.format(measuredCal.time)
                },
                measuredCal.get(Calendar.HOUR_OF_DAY),
                measuredCal.get(Calendar.MINUTE),
                true
            ).show()
        }
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val valueStr = binding.etValue.text?.toString()
            val value = valueStr?.toIntOrNull()

            if (value == null || value < 50 || value > 600) {
                binding.tilValue.error = "50~600 범위로 입력해 주세요"
                return@setOnClickListener
            }
            binding.tilValue.error = null

            val timing = getSelectedTiming()
            val memo   = binding.etMemo.text?.toString()?.takeIf { it.isNotBlank() }

            val record = GlucoseRecord(
                id         = UUID.randomUUID().toString(),
                value      = value,
                timing     = timing,
                measuredAt = measuredCal.timeInMillis,
                memo       = memo
            )

            if (record.status == GlucoseStatus.DANGER) {
                binding.btnSave.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }

            MockDataProvider.addRecord(record)
            dismiss()
            onRecordSaved?.invoke(record)
        }
    }

    private fun getSelectedTiming(): MealTiming {
        return when (binding.chipGroupTiming.checkedChipId) {
            R.id.chip_timing_pre_meal    -> MealTiming.PRE_MEAL
            R.id.chip_timing_post30m     -> MealTiming.POST_MEAL_30M
            R.id.chip_timing_post1h      -> MealTiming.POST_MEAL_1H
            R.id.chip_timing_post2h      -> MealTiming.POST_MEAL_2H
            R.id.chip_timing_before_sleep -> MealTiming.BEFORE_SLEEP
            R.id.chip_timing_other       -> MealTiming.OTHER
            else                         -> MealTiming.FASTING
        }
    }

    private fun applyTopRoundedCorners() {
        val dialog = dialog as? BottomSheetDialog ?: return
        val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val cornerRadius = resources.getDimension(R.dimen.corner_radius_card)  // 16dp
        val shape = ShapeAppearanceModel.builder()
            .setTopLeftCornerSize(cornerRadius)
            .setTopRightCornerSize(cornerRadius)
            .build()
        val drawable = MaterialShapeDrawable(shape).apply {
            setTint(requireContext().getColor(android.R.color.white))
        }
        sheet.background = drawable
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
