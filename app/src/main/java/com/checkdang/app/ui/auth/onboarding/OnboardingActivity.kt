package com.checkdang.app.ui.auth.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.checkdang.app.R
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.mock.SocialProvider
import com.checkdang.app.data.mock.UserTier
import com.checkdang.app.databinding.ActivityOnboardingBinding
import com.checkdang.app.ui.main.MainActivity

class OnboardingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IS_GUEST       = "extra_is_guest"
        const val EXTRA_AUTH_PROVIDER  = "extra_auth_provider"
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

        viewModel.setGuestMode(isGuest)

        when {
            isGuest -> {
                binding.bannerGuest.visibility = View.VISIBLE
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
        SessionHolder.currentProfile = viewModel.buildProfile()
        if (viewModel.isGuestMode()) {
            SessionHolder.isGuest    = true
            SessionHolder.isLoggedIn = false
            SessionHolder.tier       = UserTier.GUEST
        } else {
            SessionHolder.isGuest    = false
            SessionHolder.isLoggedIn = true
            SessionHolder.tier       = UserTier.FREE
            // authProvider 는 LoginActivity 에서 이미 SessionHolder 에 세팅되어 있음
        }
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
}
