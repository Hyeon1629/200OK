package com.checkdang.app.ui.bodymap.input

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import com.checkdang.app.R
import com.checkdang.app.data.model.BodyPart
import com.checkdang.app.data.model.PainRecord
import com.checkdang.app.data.model.PainType
import com.checkdang.app.databinding.BottomSheetPainInputBinding
import com.checkdang.app.ui.bodymap.analysis.AIAnalysisActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

class PainInputBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPainInputBinding? = null
    private val binding get() = _binding!!

    private lateinit var bodyPart: BodyPart
    private var currentIntensity: Int = 3  // 1-based

    companion object {
        private const val ARG_PART = "body_part"

        fun newInstance(part: BodyPart): PainInputBottomSheet =
            PainInputBottomSheet().apply {
                arguments = Bundle().apply { putString(ARG_PART, part.name) }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bodyPart = BodyPart.valueOf(requireArguments().getString(ARG_PART)!!)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPainInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyTopRoundedCorners()

        binding.tvPartTitle.text = "${bodyPart.label} 통증 기록"

        setupSeekBar()
        updateDots(currentIntensity)

        binding.btnAnalyze.setOnClickListener {
            val selectedTypes = buildSelectedTypes()
            val record = PainRecord(
                bodyPart   = bodyPart,
                intensity  = currentIntensity,
                painTypes  = selectedTypes,
            )
            val intent = Intent(requireContext(), AIAnalysisActivity::class.java).apply {
                putExtra(AIAnalysisActivity.EXTRA_BODY_PART, bodyPart.name)
                putExtra(AIAnalysisActivity.EXTRA_INTENSITY, currentIntensity)
                val typeNames = selectedTypes.map { it.name }.toTypedArray()
                putExtra(AIAnalysisActivity.EXTRA_PAIN_TYPES, typeNames)
            }
            startActivity(intent)
            dismiss()
        }
    }

    private fun setupSeekBar() {
        // SeekBar max=4 → 0..4 maps to intensity 1..5
        binding.seekbarIntensity.progress = currentIntensity - 1
        binding.seekbarIntensity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                currentIntensity = progress + 1
                updateDots(currentIntensity)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun updateDots(intensity: Int) {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4, binding.dot5)
        val activeColor = intensityColor(intensity)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.divider)
        dots.forEachIndexed { idx, dot ->
            dot.background.setTint(if (idx < intensity) activeColor else inactiveColor)
        }
    }

    private fun intensityColor(intensity: Int): Int {
        val colorRes = when (intensity) {
            1, 2 -> R.color.status_normal
            3    -> R.color.status_warning
            else -> R.color.status_danger
        }
        return ContextCompat.getColor(requireContext(), colorRes)
    }

    private fun buildSelectedTypes(): List<PainType> {
        val result = mutableListOf<PainType>()
        if (binding.chipSharp.isChecked)     result += PainType.SHARP
        if (binding.chipDull.isChecked)      result += PainType.DULL
        if (binding.chipBurning.isChecked)   result += PainType.BURNING
        if (binding.chipThrobbing.isChecked) result += PainType.THROBBING
        if (binding.chipStiffness.isChecked) result += PainType.STIFFNESS
        if (binding.chipNumbness.isChecked)  result += PainType.NUMBNESS
        return result.ifEmpty { listOf(PainType.DULL) }
    }

    private fun applyTopRoundedCorners() {
        val cornerRadius = resources.getDimension(R.dimen.bottom_sheet_corner_radius)
        val white = ContextCompat.getColor(requireContext(), R.color.white)
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog ?: return
        val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val shape = ShapeAppearanceModel.builder()
            .setTopLeftCornerSize(cornerRadius)
            .setTopRightCornerSize(cornerRadius)
            .build()
        sheet.background = MaterialShapeDrawable(shape).apply { setTint(white) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
