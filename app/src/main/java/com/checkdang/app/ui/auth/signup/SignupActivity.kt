package com.checkdang.app.ui.auth.signup

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.checkdang.app.data.mock.SocialProvider
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.databinding.ActivitySignupBinding
import com.checkdang.app.ui.auth.onboarding.OnboardingActivity

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnNext.setOnClickListener { attemptNext() }
    }

    private fun attemptNext() {
        val email     = binding.etEmail.text?.toString().orEmpty().trim()
        val pw        = binding.etPassword.text?.toString().orEmpty()
        val pwConfirm = binding.etPasswordConfirm.text?.toString().orEmpty()
        val agreed    = binding.cbTerms.isChecked
        var hasError  = false

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "올바른 이메일을 입력해 주세요"
            hasError = true
        } else {
            binding.tilEmail.error = null
        }

        if (pw.length < 8) {
            binding.tilPassword.error = "비밀번호는 8자 이상이어야 해요"
            hasError = true
        } else {
            binding.tilPassword.error = null
        }

        if (pw != pwConfirm) {
            binding.tilPasswordConfirm.error = "비밀번호가 일치하지 않아요"
            hasError = true
        } else {
            binding.tilPasswordConfirm.error = null
        }

        if (!agreed) {
            Toast.makeText(this, "이용약관에 동의해 주세요", Toast.LENGTH_SHORT).show()
            hasError = true
        }

        if (hasError) return

        SessionHolder.authProvider = SocialProvider.EMAIL

        startActivity(
            Intent(this, OnboardingActivity::class.java).apply {
                putExtra(OnboardingActivity.EXTRA_IS_GUEST,     false)
                putExtra(OnboardingActivity.EXTRA_AUTH_PROVIDER, SocialProvider.EMAIL.name)
                putExtra(OnboardingActivity.EXTRA_EMAIL,         email)
                putExtra(OnboardingActivity.EXTRA_PASSWORD,      pw)
                putExtra(OnboardingActivity.EXTRA_TERMS_AGREED,  agreed)
            }
        )
    }
}
