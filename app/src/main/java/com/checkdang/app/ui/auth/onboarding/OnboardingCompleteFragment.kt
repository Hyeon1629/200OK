package com.checkdang.app.ui.auth.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.checkdang.app.data.model.Gender
import com.checkdang.app.databinding.FragmentOnboardingCompleteBinding
import kotlinx.coroutines.launch

class OnboardingCompleteFragment : Fragment() {

    private var _binding: FragmentOnboardingCompleteBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOnboardingCompleteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.profile.collect { profile ->
                    binding.tvWelcome.text = "환영합니다, ${profile.nickname}님!"
                    val genderLabel = when (profile.gender) {
                        Gender.MALE   -> "남성"
                        Gender.FEMALE -> "여성"
                        Gender.NONE   -> "-"
                    }
                    binding.tvSummary.text = """
                        닉네임: ${profile.nickname}
                        생년월일: ${profile.birthDate.ifEmpty { "-" }}
                        성별: $genderLabel
                        키: ${if (profile.heightCm > 0) "${profile.heightCm}cm" else "-"}
                        몸무게: ${if (profile.weightKg > 0) "${profile.weightKg}kg" else "-"}
                    """.trimIndent()
                }
            }
        }

        binding.btnStart.setOnClickListener {
            (requireActivity() as OnboardingActivity).finishOnboarding()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
