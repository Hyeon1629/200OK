package com.checkdang.app.ui.profile

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.checkdang.app.data.model.Gender
import com.checkdang.app.data.model.PatientProfile
import com.checkdang.app.databinding.FragmentProfileEditBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class ProfileEditFragment : Fragment() {

    private var _binding: FragmentProfileEditBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupBirthDatePicker()
        setupSaveButton()
        observeUiState()
    }

    private fun setupToolbar() {
        // navigationIcon이 chevron_right(→)이므로 회전으로 뒤로가기 처럼 보이게 하거나,
        // 실제로는 뒤로 가는 동작을 수행한다
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupBirthDatePicker() {
        binding.etBirthDate.setOnClickListener { showDatePicker() }
        binding.tilBirthDate.setEndIconOnClickListener { showDatePicker() }
    }

    private fun showDatePicker() {
        val current = binding.etBirthDate.text?.toString()
        val calendar = Calendar.getInstance()

        if (!current.isNullOrBlank()) {
            runCatching {
                val parts = current.split("-")
                calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
        }

        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                binding.etBirthDate.setText(
                    "%04d-%02d-%02d".format(year, month + 1, day)
                )
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).also { dialog ->
            dialog.datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val nickname = binding.etNickname.text?.toString()?.trim() ?: ""
            if (nickname.isBlank()) {
                binding.tilNickname.error = "닉네임을 입력해 주세요"
                return@setOnClickListener
            }
            binding.tilNickname.error = null

            val birthDate = binding.etBirthDate.text?.toString()?.trim() ?: ""
            val gender = if (binding.rbFemale.isChecked) Gender.FEMALE else Gender.MALE
            val heightCm = binding.etHeight.text?.toString()?.toFloatOrNull() ?: 0f
            val weightKg = binding.etWeight.text?.toString()?.toFloatOrNull() ?: 0f

            viewModel.saveProfile(nickname, birthDate, gender, heightCm, weightKg)
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ProfileUiState.Loading -> showLoading(true)
                        is ProfileUiState.Success -> {
                            showLoading(false)
                            bindProfile(state.profile)
                        }
                        is ProfileUiState.SaveSuccess -> {
                            showLoading(false)
                            Toast.makeText(requireContext(), "프로필이 저장되었어요", Toast.LENGTH_SHORT).show()
                            findNavController().popBackStack()
                        }
                        is ProfileUiState.Error -> {
                            showLoading(false)
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun bindProfile(profile: PatientProfile) {
        binding.etNickname.setText(profile.nickname)
        binding.etBirthDate.setText(profile.birthDate)
        when (profile.gender) {
            Gender.FEMALE -> binding.rbFemale.isChecked = true
            else          -> binding.rbMale.isChecked = true
        }
        if (profile.heightCm > 0f) binding.etHeight.setText(profile.heightCm.toInt().toString())
        if (profile.weightKg > 0f) binding.etWeight.setText(profile.weightKg.toInt().toString())
    }

    private fun showLoading(show: Boolean) {
        binding.loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
