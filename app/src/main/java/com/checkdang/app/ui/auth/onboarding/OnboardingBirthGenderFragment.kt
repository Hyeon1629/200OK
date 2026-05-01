package com.checkdang.app.ui.auth.onboarding

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.checkdang.app.R
import com.checkdang.app.data.model.Gender
import com.checkdang.app.databinding.FragmentOnboardingBirthGenderBinding
import java.util.Calendar

class OnboardingBirthGenderFragment : Fragment() {

    private var _binding: FragmentOnboardingBirthGenderBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBirthGenderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // DatePicker
        binding.etBirthDate.setOnClickListener { showDatePicker() }
        binding.tilBirthDate.setEndIconOnClickListener { showDatePicker() }

        // 성별 토글 — MaterialButtonToggleGroup
        binding.toggleGender.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            // 선택된 버튼 색상 업데이트
            refreshToggleColors(checkedId)
            validateAndEnableNext()
        }

        binding.btnNext.isEnabled = false
        binding.btnNext.setOnClickListener {
            val birthDate   = binding.etBirthDate.text?.toString().orEmpty().trim()
            val checkedId   = binding.toggleGender.checkedButtonId
            var hasError    = false

            if (birthDate.isEmpty()) {
                binding.tilBirthDate.error = "생년월일을 선택해 주세요"
                hasError = true
            } else {
                binding.tilBirthDate.error = null
            }

            if (checkedId == View.NO_ID) {
                Toast.makeText(requireContext(), "성별을 선택해 주세요", Toast.LENGTH_SHORT).show()
                hasError = true
            }

            if (!hasError) {
                val gender = if (checkedId == R.id.btn_male) Gender.MALE else Gender.FEMALE
                viewModel.updateBirthGender(birthDate, gender)
                (requireActivity() as OnboardingActivity).goToNextPage()
            }
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val formatted = "%04d-%02d-%02d".format(year, month + 1, day)
                binding.etBirthDate.setText(formatted)
                validateAndEnableNext()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun refreshToggleColors(checkedId: Int) {
        val green    = requireContext().getColor(R.color.brand_green)
        val white    = requireContext().getColor(R.color.white)
        val divider  = requireContext().getColor(R.color.divider)
        val textSec  = requireContext().getColor(R.color.text_secondary)

        listOf(binding.btnMale to R.id.btn_male, binding.btnFemale to R.id.btn_female).forEach { (btn, id) ->
            val selected = id == checkedId
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (selected) green else android.graphics.Color.TRANSPARENT
            )
            btn.strokeColor = android.content.res.ColorStateList.valueOf(
                if (selected) green else divider
            )
            btn.setTextColor(if (selected) white else textSec)
        }
    }

    private fun validateAndEnableNext() {
        val hasDate   = binding.etBirthDate.text?.toString().orEmpty().isNotEmpty()
        val hasGender = binding.toggleGender.checkedButtonId != View.NO_ID
        binding.btnNext.isEnabled = hasDate && hasGender
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
