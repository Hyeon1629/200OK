package com.checkdang.app.ui.auth.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.checkdang.app.databinding.FragmentOnboardingNicknameBinding

class OnboardingNicknameFragment : Fragment() {

    private var _binding: FragmentOnboardingNicknameBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOnboardingNicknameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnNext.setOnClickListener {
            val nickname = binding.etNickname.text?.toString().orEmpty().trim()
            if (nickname.isEmpty()) {
                binding.tilNickname.error = "닉네임을 입력해 주세요"
                return@setOnClickListener
            }
            binding.tilNickname.error = null
            viewModel.updateNickname(nickname)
            (requireActivity() as OnboardingActivity).goToNextPage()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
