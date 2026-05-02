package com.checkdang.app.ui.auth.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.checkdang.app.R
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.mock.SocialProvider
import com.checkdang.app.data.mock.UserStore
import com.checkdang.app.data.mock.UserTier
import com.checkdang.app.data.remote.AuthApiClient
import com.checkdang.app.databinding.ActivityOnboardingBinding
import com.checkdang.app.ui.main.MainActivity
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IS_GUEST      = "extra_is_guest"
        const val EXTRA_AUTH_PROVIDER = "extra_auth_provider"
        const val EXTRA_EMAIL         = "extra_email"
        const val EXTRA_PASSWORD      = "extra_password"
        const val EXTRA_TERMS_AGREED  = "extra_terms_agreed"
    }

    private lateinit var binding: ActivityOnboardingBinding
    val viewModel: OnboardingViewModel by viewModels()

    private val totalSteps = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val isGuest      = intent.getBooleanExtra(EXTRA_IS_GUEST, false)
        val providerName = intent.getStringExtra(EXTRA_AUTH_PROVIDER)
        val provider     = providerName
            ?.let { runCatching { SocialProvider.valueOf(it) }.getOrNull() }
            ?: SocialProvider.NONE

        // 이메일 회원가입 흐름: SignupActivity에서 전달된 인증 정보 저장
        val email       = intent.getStringExtra(EXTRA_EMAIL) ?: ""
        val password    = intent.getStringExtra(EXTRA_PASSWORD) ?: ""
        val termsAgreed = intent.getBooleanExtra(EXTRA_TERMS_AGREED, false)
        if (email.isNotEmpty()) viewModel.setEmailCredentials(email, password, termsAgreed)

        viewModel.setGuestMode(isGuest)

        when {
            isGuest -> {
                binding.bannerGuest.visibility = View.VISIBLE
            }
            provider == SocialProvider.EMAIL -> {
                binding.bannerSocial.visibility = View.VISIBLE
                binding.tvSocialBannerText.text = "이메일로 가입하고 있어요. 프로필을 입력해 주세요."
            }
            provider != SocialProvider.NONE -> {
                binding.bannerSocial.visibility = View.VISIBLE
                binding.tvSocialBannerText.text =
                    "${provider.labelKr} 계정으로 가입되었어요. 환자 정보를 입력해 주세요."
            }
        }

        setupViewPager()
        setupDots()
    }

    private fun setupViewPager() {
        val adapter = OnboardingPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
            }
        })
    }

    private fun setupDots() {
        repeat(totalSteps) {
            val dot = ImageView(this).apply {
                setImageResource(R.drawable.ic_dot)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.dot_size),
                    resources.getDimensionPixelSize(R.dimen.dot_size)
                ).apply {
                    marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_s)
                }
            }
            binding.dotsContainer.addView(dot)
        }
        updateDots(0)
    }

    private fun updateDots(currentPage: Int) {
        val activeColor   = ContextCompat.getColor(this, R.color.brand_green)
        val inactiveColor = ContextCompat.getColor(this, R.color.divider)

        for (i in 0 until binding.dotsContainer.childCount) {
            val dot = binding.dotsContainer.getChildAt(i) as? ImageView ?: continue
            dot.setColorFilter(if (i == currentPage) activeColor else inactiveColor)
        }
    }

    fun goToNextPage() {
        val current = binding.viewPager.currentItem
        if (current < totalSteps - 1) {
            binding.viewPager.setCurrentItem(current + 1, true)
        }
    }

    fun finishOnboarding() {
        val profile = viewModel.buildProfile()
        SessionHolder.currentProfile = profile

        when {
            // ── 비회원 게스트: 기존 Mock 동작 유지 ──────────────────────────
            viewModel.isGuestMode() -> {
                SessionHolder.isGuest    = true
                SessionHolder.isLoggedIn = false
                SessionHolder.tier       = UserTier.GUEST
                navigateToMain()
            }

            // ── 이메일 회원가입: 실제 signup API 호출 ───────────────────────
            SessionHolder.authProvider == SocialProvider.EMAIL -> {
                lifecycleScope.launch {
                    try {
                        AuthApiClient.signupEmail(
                            email       = viewModel.email,
                            password    = viewModel.password,
                            name        = profile.nickname,
                            termsAgreed = viewModel.termsAgreed,
                            birthDate   = profile.birthDate,
                            gender      = profile.gender.name,
                            height      = profile.heightCm.toInt(),
                            weight      = profile.weightKg.toInt()
                        )
                        SessionHolder.isGuest    = false
                        SessionHolder.isLoggedIn = true
                        SessionHolder.tier       = UserTier.FREE
                        SessionHolder.socialEmail = viewModel.email
                        UserStore.saveProfile(SocialProvider.EMAIL, profile)
                        UserStore.markRegistered(SocialProvider.EMAIL)
                        navigateToMain()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@OnboardingActivity,
                            "회원가입 실패: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            // ── 소셜 로그인: 기존 Mock 동작 유지 ────────────────────────────
            else -> {
                SessionHolder.isGuest    = false
                SessionHolder.isLoggedIn = true
                SessionHolder.tier       = UserTier.FREE
                UserStore.saveProfile(SessionHolder.authProvider, profile)
                UserStore.markRegistered(SessionHolder.authProvider)
                navigateToMain()
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
}
