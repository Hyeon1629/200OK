package com.checkdang.app.ui.profile

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.checkdang.app.R
import com.checkdang.app.data.model.DiabetesType
import com.checkdang.app.data.model.Gender
import com.checkdang.app.data.model.PatientProfile
import com.checkdang.app.databinding.ActivityProfileBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.Calendar

class ProfileActivity : AppCompatActivity() {

    private val binding by lazy { ActivityProfileBinding.inflate(layoutInflater) }
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupToolbar()
        bindInitial(viewModel.profile.value)
        setupListeners()
        observeSaved()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // ── 초기 바인딩 ──────────────────────────────────────────────────────────
    private fun bindInitial(p: PatientProfile) {
        binding.etNickname.setText(p.nickname)
        binding.etBirthDate.setText(p.birthDate)
        binding.etDiagnosedAt.setText(p.diagnosedAt)
        binding.etHeight.setText(if (p.heightCm > 0f) formatNumber(p.heightCm) else "")
        binding.etWeight.setText(if (p.weightKg > 0f) formatNumber(p.weightKg) else "")
        binding.etFastingTarget.setText(if (p.fastingTargetMgdl > 0) p.fastingTargetMgdl.toString() else "")
        binding.etPostMealTarget.setText(if (p.postMealTargetMgdl > 0) p.postMealTargetMgdl.toString() else "")

        when (p.gender) {
            Gender.MALE   -> binding.toggleGender.check(R.id.btn_male)
            Gender.FEMALE -> binding.toggleGender.check(R.id.btn_female)
            Gender.NONE   -> binding.toggleGender.clearChecked()
        }
        refreshGenderColors(binding.toggleGender.checkedButtonId)

        when (p.diabetesType) {
            DiabetesType.TYPE_1      -> binding.toggleDiabetesType.check(R.id.btn_dm_type1)
            DiabetesType.TYPE_2      -> binding.toggleDiabetesType.check(R.id.btn_dm_type2)
            DiabetesType.GESTATIONAL -> binding.toggleDiabetesType.check(R.id.btn_dm_gestational)
            DiabetesType.PRE         -> binding.toggleDiabetesType.check(R.id.btn_dm_pre)
            DiabetesType.NONE        -> binding.toggleDiabetesType.clearChecked()
        }
        refreshDiabetesColors(binding.toggleDiabetesType.checkedButtonId)
    }

    // ── 리스너 ───────────────────────────────────────────────────────────────
    private fun setupListeners() {
        binding.etBirthDate.setOnClickListener { showBirthDatePicker() }
        binding.tilBirthDate.setEndIconOnClickListener { showBirthDatePicker() }

        binding.etDiagnosedAt.setOnClickListener { showDiagnosedAtPicker() }
        binding.tilDiagnosedAt.setEndIconOnClickListener { showDiagnosedAtPicker() }

        binding.toggleGender.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            refreshGenderColors(checkedId)
        }

        binding.toggleDiabetesType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            refreshDiabetesColors(checkedId)
        }

        binding.btnSave.setOnClickListener { onSaveClicked() }
    }

    private fun onSaveClicked() {
        val nickname = binding.etNickname.text?.toString().orEmpty().trim()
        if (nickname.isEmpty()) {
            binding.tilNickname.error = "닉네임을 입력해 주세요"
            return
        }
        binding.tilNickname.error = null

        viewModel.updateNickname(nickname)
        viewModel.updateBirthDate(binding.etBirthDate.text?.toString().orEmpty().trim())
        viewModel.updateGender(currentGender())
        viewModel.updateBody(
            heightCm = binding.etHeight.text?.toString()?.toFloatOrNull() ?: 0f,
            weightKg = binding.etWeight.text?.toString()?.toFloatOrNull() ?: 0f
        )
        viewModel.updateDiabetesType(currentDiabetesType())
        viewModel.updateDiagnosedAt(binding.etDiagnosedAt.text?.toString().orEmpty().trim())
        viewModel.updateTargets(
            fasting  = binding.etFastingTarget.text?.toString()?.toIntOrNull() ?: 0,
            postMeal = binding.etPostMealTarget.text?.toString()?.toIntOrNull() ?: 0
        )
        viewModel.save()
    }

    private fun observeSaved() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saved.collect { saved ->
                    if (saved) {
                        Snackbar.make(binding.root, "프로필이 저장되었어요", Snackbar.LENGTH_SHORT).show()
                        binding.root.postDelayed({ finish() }, 600L)
                    }
                }
            }
        }
    }

    // ── DatePicker ──────────────────────────────────────────────────────────
    private fun showBirthDatePicker() {
        val current = parseDate(binding.etBirthDate.text?.toString())
        DatePickerDialog(
            this,
            { _, year, month, day ->
                binding.etBirthDate.setText("%04d-%02d-%02d".format(year, month + 1, day))
            },
            current.get(Calendar.YEAR),
            current.get(Calendar.MONTH),
            current.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showDiagnosedAtPicker() {
        val current = parseYearMonth(binding.etDiagnosedAt.text?.toString())
        DatePickerDialog(
            this,
            { _, year, month, _ ->
                binding.etDiagnosedAt.setText("%04d-%02d".format(year, month + 1))
            },
            current.get(Calendar.YEAR),
            current.get(Calendar.MONTH),
            current.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun parseDate(text: String?): Calendar {
        val cal = Calendar.getInstance()
        text?.split("-")?.let { parts ->
            if (parts.size == 3) {
                val y = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull()
                val d = parts[2].toIntOrNull()
                if (y != null && m != null && d != null) cal.set(y, m - 1, d)
            }
        }
        return cal
    }

    private fun parseYearMonth(text: String?): Calendar {
        val cal = Calendar.getInstance()
        text?.split("-")?.let { parts ->
            if (parts.size >= 2) {
                val y = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull()
                if (y != null && m != null) cal.set(y, m - 1, 1)
            }
        }
        return cal
    }

    // ── 토글 색상 ───────────────────────────────────────────────────────────
    private fun refreshGenderColors(checkedId: Int) {
        val ids = listOf(R.id.btn_male, R.id.btn_female)
        applyToggleColors(binding.toggleGender, ids, checkedId)
    }

    private fun refreshDiabetesColors(checkedId: Int) {
        val ids = listOf(
            R.id.btn_dm_type1, R.id.btn_dm_type2,
            R.id.btn_dm_gestational, R.id.btn_dm_pre
        )
        applyToggleColors(binding.toggleDiabetesType, ids, checkedId)
    }

    private fun applyToggleColors(
        group: View,
        ids: List<Int>,
        checkedId: Int
    ) {
        val green   = getColor(R.color.brand_green)
        val white   = getColor(R.color.white)
        val divider = getColor(R.color.divider)
        val textSec = getColor(R.color.text_secondary)

        ids.forEach { id ->
            val btn = group.findViewById<MaterialButton>(id) ?: return@forEach
            val selected = id == checkedId
            btn.backgroundTintList = ColorStateList.valueOf(
                if (selected) green else Color.TRANSPARENT
            )
            btn.strokeColor = ColorStateList.valueOf(if (selected) green else divider)
            btn.setTextColor(if (selected) white else textSec)
        }
    }

    // ── 현재 토글 값 → 도메인 enum ─────────────────────────────────────────
    private fun currentGender(): Gender = when (binding.toggleGender.checkedButtonId) {
        R.id.btn_male   -> Gender.MALE
        R.id.btn_female -> Gender.FEMALE
        else            -> Gender.NONE
    }

    private fun currentDiabetesType(): DiabetesType = when (binding.toggleDiabetesType.checkedButtonId) {
        R.id.btn_dm_type1       -> DiabetesType.TYPE_1
        R.id.btn_dm_type2       -> DiabetesType.TYPE_2
        R.id.btn_dm_gestational -> DiabetesType.GESTATIONAL
        R.id.btn_dm_pre         -> DiabetesType.PRE
        else                    -> DiabetesType.NONE
    }

    private fun formatNumber(v: Float): String =
        if (v % 1f == 0f) v.toInt().toString() else v.toString()
}
