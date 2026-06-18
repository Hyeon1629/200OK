package com.checkdang.app.ui.glucose.input

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.checkdang.app.R
import com.checkdang.app.data.mock.MockDataProvider
import com.checkdang.app.data.model.InsulinRecord
import com.checkdang.app.data.model.InsulinType
import com.checkdang.app.databinding.BottomSheetInsulinInputBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class InsulinInputBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "InsulinInputBottomSheet"
    }

    private var _binding: BottomSheetInsulinInputBinding? = null
    private val binding get() = _binding!!

    var onRecordSaved: ((InsulinRecord) -> Unit)? = null

    private val injectedCal: Calendar = Calendar.getInstance()
    private val timeSdf = SimpleDateFormat("HH:mm", Locale.KOREAN)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetInsulinInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        applyTopRoundedCorners()

        binding.etUnits.setText("4")
        binding.tvInjectedTime.text = timeSdf.format(injectedCal.time)

        setupTimePicker()
        setupSaveButton()
    }

    private fun setupTimePicker() {
        binding.tvInjectedTime.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    injectedCal.set(Calendar.HOUR_OF_DAY, hour)
                    injectedCal.set(Calendar.MINUTE, minute)
                    binding.tvInjectedTime.text = timeSdf.format(injectedCal.time)
                },
                injectedCal.get(Calendar.HOUR_OF_DAY),
                injectedCal.get(Calendar.MINUTE),
                true
            ).show()
        }
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val units = binding.etUnits.text?.toString()?.toFloatOrNull()
            if (units == null || units < 0.5f || units > 100f) {
                binding.tilUnits.error = "0.5~100 U 범위로 입력해 주세요"
                return@setOnClickListener
            }
            binding.tilUnits.error = null

            val type = getSelectedType()
            val memo = binding.etMemo.text?.toString()?.takeIf { it.isNotBlank() }

            val record = InsulinRecord(
                id         = UUID.randomUUID().toString(),
                units      = units,
                type       = type,
                injectedAt = injectedCal.timeInMillis,
                memo       = memo
            )

            MockDataProvider.addInsulinRecord(record)
            dismiss()
            onRecordSaved?.invoke(record)
        }
    }

    private fun getSelectedType(): InsulinType = when (binding.chipGroupType.checkedChipId) {
        R.id.chip_type_long  -> InsulinType.LONG
        R.id.chip_type_mixed -> InsulinType.MIXED
        R.id.chip_type_other -> InsulinType.OTHER
        else                 -> InsulinType.RAPID
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
