package com.checkdang.app.ui.auth.login

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.databinding.ActivityLoginBinding
import com.checkdang.app.ui.auth.onboarding.OnboardingActivity
import com.checkdang.app.ui.auth.signup.SignupActivity
import com.checkdang.app.ui.main.MainActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            // 기존 사용자 흐름: 더미 프로필 주입 후 온보딩 스킵
            SessionHolder.isLoggedIn = true
            SessionHolder.currentProfile = SessionHolder.dummyProfile
            navigateToMain()
        }

        binding.btnSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        binding.btnGuest.setOnClickListener {
            // 게스트는 온보딩을 거쳐 닉네임 등 기본 정보를 입력한 뒤 진입
            startActivity(
                Intent(this, OnboardingActivity::class.java).apply {
                    putExtra(OnboardingActivity.EXTRA_IS_GUEST, true)
                }
            )
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
}
