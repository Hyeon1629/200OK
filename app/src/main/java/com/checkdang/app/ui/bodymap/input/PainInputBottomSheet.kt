package com.checkdang.app.ui.bodymap.input

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.checkdang.app.R
import com.checkdang.app.data.model.BodyPart
import com.checkdang.app.data.model.MusclePart
import com.checkdang.app.data.model.PainRecord
import com.checkdang.app.data.model.PainType
import com.checkdang.app.data.model.muscles
import com.checkdang.app.databinding.BottomSheetPainInputBinding
import com.checkdang.app.ui.bodymap.BodyMapViewModel
import com.checkdang.app.ui.bodymap.PainSaveState
import com.checkdang.app.ui.bodymap.analysis.AIAnalysisActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.launch

class PainInputBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPainInputBinding? = null
    private val binding get() = _binding!!

    private lateinit var bodyPart: BodyPart
    private var currentIntensity: Int = 5  // 1-based, 1–10

    private val viewModel: BodyMapViewModel by lazy {
        ViewModelProvider(requireParentFragment())[BodyMapViewModel::class.java]
    }

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

        setupMuscleChips()
        setupSeekBar()
        updateDots(currentIntensity)

        binding.btnSave.setOnClickListener {
            viewModel.addPainRecord(buildRecord())
        }

        binding.btnAnalyze.setOnClickListener {
            pendingAnalyze = true
            viewModel.addPainRecord(buildRecord())
        }

        observeSaveState()
    }

    private var pendingAnalyze = false

    private fun observeSaveState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveState.collect { state ->
                    when (state) {
                        is PainSaveState.Loading -> setSaving(true)
                        is PainSaveState.Success -> {
                            setSaving(false)
                            viewModel.resetSaveState()
                            if (pendingAnalyze) {
                                launchAIAnalysis()
                            } else {
                                Toast.makeText(requireContext(), "통증 기록이 저장되었어요", Toast.LENGTH_SHORT).show()
                                dismiss()
                            }
                        }
                        is PainSaveState.Error -> {
                            setSaving(false)
                            pendingAnalyze = false
                            viewModel.resetSaveState()
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        }
                        is PainSaveState.Idle -> Unit
                    }
                }
            }
        }
    }

    private fun setSaving(saving: Boolean) {
        binding.btnSave.isEnabled = !saving
        binding.btnAnalyze.isEnabled = !saving
        binding.btnSave.text = if (saving) "저장 중..." else "저장하기"
    }

    private fun launchAIAnalysis() {
        val record = buildRecord()
        val intent = Intent(requireContext(), AIAnalysisActivity::class.java).apply {
            putExtra(AIAnalysisActivity.EXTRA_BODY_PART, bodyPart.name)
            putExtra(AIAnalysisActivity.EXTRA_INTENSITY, currentIntensity)
            putExtra(AIAnalysisActivity.EXTRA_PAIN_TYPES, record.painTypes.map { it.name }.toTypedArray())
        }
        startActivity(intent)
        dismiss()
    }

    private fun setupMuscleChips() {
        val muscles = bodyPart.muscles()
        if (muscles.isEmpty()) {
            binding.chipGroupMuscle.visibility = View.GONE
            return
        }
        muscles.forEach { muscle ->
            val chip = Chip(requireContext()).apply {
                text = muscle.label
                isCheckable = true
                tag = muscle
                setChipBackgroundColorResource(R.color.background_secondary)
                setTextColor(ContextCompat.getColorStateList(requireContext(), android.R.color.black))
            }
            binding.chipGroupMuscle.addView(chip)
        }
    }

    private fun selectedMuscle(): MusclePart? {
        val checkedId = binding.chipGroupMuscle.checkedChipId
        if (checkedId == View.NO_ID) return null
        return binding.chipGroupMuscle.findViewById<Chip>(checkedId)?.tag as? MusclePart
    }

    private fun setupSeekBar() {
        binding.seekbarIntensity.progress = currentIntensity - 1  // 0..9 → 1..10
        updateIntensityLabel(currentIntensity)
        binding.seekbarIntensity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                currentIntensity = progress + 1
                updateDots(currentIntensity)
                updateIntensityLabel(currentIntensity)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun updateIntensityLabel(intensity: Int) {
        binding.tvIntensityValue.text = "$intensity / 10"
        binding.tvIntensityValue.setTextColor(intensityColor(intensity))
    }

    private fun updateDots(intensity: Int) {
        val dots = listOf(
            binding.dot1, binding.dot2, binding.dot3, binding.dot4, binding.dot5,
            binding.dot6, binding.dot7, binding.dot8, binding.dot9, binding.dot10
        )
        val activeColor = intensityColor(intensity)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.divider)
        dots.forEachIndexed { idx, dot ->
            dot.background.setTint(if (idx < intensity) activeColor else inactiveColor)
        }
    }

    private fun intensityColor(intensity: Int): Int {
        val colorRes = when {
            intensity <= 3 -> R.color.status_normal
            intensity <= 6 -> R.color.status_warning
            else           -> R.color.status_danger
        }
        return ContextCompat.getColor(requireContext(), colorRes)
    }

    private fun buildRecord(): PainRecord {
        return PainRecord(
            bodyPart  = bodyPart,
            musclePart = selectedMuscle(),
            intensity = currentIntensity,
            painTypes = buildSelectedTypes(),
        )
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
