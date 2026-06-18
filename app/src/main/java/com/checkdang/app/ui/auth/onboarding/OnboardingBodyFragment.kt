package com.checkdang.app.ui.auth.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.checkdang.app.databinding.FragmentOnboardingBodyBinding

class OnboardingBodyFragment : Fragment() {

    private var _binding: FragmentOnboardingBodyBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOnboardingBodyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnNext.setOnClickListener {
            val heightStr = binding.etHeight.text?.toString().orEmpty().trim()
            val weightStr = binding.etWeight.text?.toString().orEmpty().trim()
            var hasError = false

            if (heightStr.isEmpty()) {
                binding.tilHeight.error = "키를 입력해 주세요"
                hasError = true
            } else {
                binding.tilHeight.error = null
            }

            if (weightStr.isEmpty()) {
                binding.tilWeight.error = "몸무게를 입력해 주세요"
                hasError = true
            } else {
                binding.tilWeight.error = null
            }

            if (!hasError) {
                viewModel.updateBody(
                    heightCm = heightStr.toFloat(),
                    weightKg = weightStr.toFloat()
                )
                (requireActivity() as OnboardingActivity).goToNextPage()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
