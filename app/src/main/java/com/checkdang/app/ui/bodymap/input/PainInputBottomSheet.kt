package com.checkdang.app.ui.bodymap.input

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.checkdang.app.R
import com.checkdang.app.data.model.BodyPart
import com.checkdang.app.data.model.PainTaxonomy
import com.checkdang.app.databinding.BottomSheetPainInputBinding
import com.checkdang.app.ui.bodymap.analysis.AIAnalysisActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

class PainInputBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPainInputBinding? = null
    private val binding get() = _binding!!

    private lateinit var bodyPart: BodyPart
    private var currentIntensity: Int = 3

    private val selectedQuality = linkedSetOf<String>()
    private val selectedSituation = linkedSetOf<String>()

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

        // 성질/상황 아코디언 그룹 생성
        PainTaxonomy.QUALITY.forEachIndexed { i, g ->
            binding.containerQuality.addView(buildGroup(g, selectedQuality, expanded = i == 0))
        }
        PainTaxonomy.SITUATION.forEach { g ->
            binding.containerSituation.addView(buildGroup(g, selectedSituation, expanded = false))
        }

        binding.btnAnalyze.setOnClickListener { onSave() }
    }

    // ── 아코디언 그룹 한 개 (헤더 + 토글되는 ChipGroup) ───────────────────────
    private fun buildGroup(
        group: PainTaxonomy.Group, target: MutableSet<String>, expanded: Boolean
    ): View {
        val ctx = requireContext()
        val color = Color.parseColor(group.colorHex)

        val wrapper = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }

        // 그룹 칩들
        val chipGroup = ChipGroup(ctx).apply {
            isSingleLine = false
            isSelectionRequired = false
            isSingleSelection = false
            visibility = if (expanded) View.VISIBLE else View.GONE
            setPadding(dp(2), dp(2), dp(2), dp(8))
        }

        val badge = TextView(ctx).apply {
            textSize = 12f
            setTextColor(color)
            text = ""
        }
        val chevron = TextView(ctx).apply {
            textSize = 14f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            text = if (expanded) "▾" else "▸"
        }

        // 헤더
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(4), dp(10), dp(4), dp(10))
            val dot = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(8) }
                background = MaterialShapeDrawable(
                    ShapeAppearanceModel.builder().setAllCornerSizes(dp(5).toFloat()).build()
                ).apply { setTint(color) }
            }
            val label = TextView(ctx).apply {
                text = group.label
                textSize = 15f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(dot); addView(label); addView(badge); addView(chevron)
            setOnClickListener {
                val show = chipGroup.visibility != View.VISIBLE
                chipGroup.visibility = if (show) View.VISIBLE else View.GONE
                chevron.text = if (show) "▾" else "▸"
            }
        }

        // 칩 생성
        group.tags.forEach { tag ->
            val chip = Chip(ctx).apply {
                text = tag
                isCheckable = true
                isCheckedIconVisible = true
                styleByGroup(this, color)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) target.add(tag) else target.remove(tag)
                    val n = group.tags.count { it in target }
                    badge.text = if (n > 0) "$n" else ""
                }
            }
            chipGroup.addView(chip)
        }

        wrapper.addView(header)
        wrapper.addView(chipGroup)
        // 구분선
        wrapper.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            )
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.divider))
        })
        return wrapper
    }

    /** 칩을 그룹 색상으로 스타일링 (선택 시 색 채움 + 윤곽) */
    private fun styleByGroup(chip: Chip, color: Int) {
        val surface = ContextCompat.getColor(requireContext(), R.color.background_primary)
        val divider = ContextCompat.getColor(requireContext(), R.color.divider)
        val checkedFill = (color and 0x00FFFFFF) or (0x22 shl 24)  // alpha 0x22
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        chip.chipBackgroundColor = android.content.res.ColorStateList(states, intArrayOf(checkedFill, surface))
        chip.setChipStrokeColorResource(R.color.divider)
        chip.chipStrokeColor = android.content.res.ColorStateList(states, intArrayOf(color, divider))
        chip.chipStrokeWidth = dp(1).toFloat()
        chip.setTextColor(android.content.res.ColorStateList(states, intArrayOf(color,
            ContextCompat.getColor(requireContext(), R.color.text_primary))))
        chip.checkedIconTint = android.content.res.ColorStateList(states, intArrayOf(color, color))
    }

    private fun onSave() {
        val intent = Intent(requireContext(), AIAnalysisActivity::class.java).apply {
            putExtra(AIAnalysisActivity.EXTRA_BODY_PART, bodyPart.name)
            putExtra(AIAnalysisActivity.EXTRA_INTENSITY, currentIntensity)
            putExtra(AIAnalysisActivity.EXTRA_QUALITY_TAGS, selectedQuality.toTypedArray())
            putExtra(AIAnalysisActivity.EXTRA_SITUATION_TAGS, selectedSituation.toTypedArray())
        }
        startActivity(intent)
        dismiss()
    }

    private fun setupSeekBar() {
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
