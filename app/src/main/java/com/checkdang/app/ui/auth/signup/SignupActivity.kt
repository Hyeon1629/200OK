package com.checkdang.app.ui.auth.signup

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.checkdang.app.databinding.ActivitySignupBinding
import com.checkdang.app.ui.auth.onboarding.OnboardingActivity

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "회원가입"
        }

        binding.btnNext.setOnClickListener { validateAndProceed() }
    }

    private fun validateAndProceed() {
        val email    = binding.etEmail.text?.toString().orEmpty().trim()
        val password = binding.etPassword.text?.toString().orEmpty()
        val confirm  = binding.etPasswordConfirm.text?.toString().orEmpty()

        var hasError = false

        if (email.isEmpty()) {
            binding.tilEmail.error = "이메일을 입력해 주세요"
            hasError = true
        } else {
            binding.tilEmail.error = null
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "비밀번호를 입력해 주세요"
            hasError = true
        } else {
            binding.tilPassword.error = null
        }

        if (confirm.isEmpty()) {
            binding.tilPasswordConfirm.error = "비밀번호를 다시 입력해 주세요"
            hasError = true
        } else if (password != confirm) {
            binding.tilPasswordConfirm.error = "비밀번호가 일치하지 않습니다"
            hasError = true
        } else {
            binding.tilPasswordConfirm.error = null
        }

        if (!binding.cbTerms.isChecked) {
            Toast.makeText(this, "약관에 동의해 주세요", Toast.LENGTH_SHORT).show()
            hasError = true
        }

        if (!hasError) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
