package com.checkdang.app.ui.auth.login

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.checkdang.app.R
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.mock.SocialProvider
import com.checkdang.app.data.mock.UserTier
import com.checkdang.app.databinding.ActivityLoginBinding
import com.checkdang.app.databinding.DialogSocialLoadingBinding
import com.checkdang.app.ui.auth.onboarding.OnboardingActivity
import com.checkdang.app.ui.main.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var loadingDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGoogleLogin.setOnClickListener { startSocialLogin(SocialProvider.GOOGLE) }
        binding.btnKakaoLogin.setOnClickListener  { startSocialLogin(SocialProvider.KAKAO) }
        binding.btnGuestStart.setOnClickListener  { startGuestFlow() }

        setupTermsNotice()
    }

    private fun startSocialLogin(provider: SocialProvider) {
        setButtonsEnabled(false)
        showLoadingDialog("${provider.labelKr}에 연결 중…")

        // TODO(backend, auth): 실제 소셜 로그인 SDK 연동
        //  - Google: implementation("com.google.android.gms:play-services-auth:21.x")
        //    GoogleSignInClient + GetSignInIntent + idToken 추출
        //  - Kakao: implementation("com.kakao.sdk:v2-user:2.x")
        //    UserApiClient.instance.loginWithKakaoTalk / loginWithKakaoAccount
        //  - 성공 시 서버에 POST /api/v1/auth/social
        //    { provider: "google|kakao", idToken: "..." }
        //  - 응답: { isNewUser, accessToken, refreshToken, profile? }

        lifecycleScope.launch {
            delay(1500L)
            dismissLoadingDialog()
            setButtonsEnabled(true)

            val isNewUser = !SessionHolder.hasEverLoggedIn

            SessionHolder.authProvider = provider
            SessionHolder.isLoggedIn = true
            SessionHolder.isGuest = false
            SessionHolder.tier = UserTier.FREE

            if (isNewUser) {
                SessionHolder.hasEverLoggedIn = true
                startActivity(
                    Intent(this@LoginActivity, OnboardingActivity::class.java).apply {
                        putExtra(OnboardingActivity.EXTRA_IS_GUEST, false)
                        putExtra(OnboardingActivity.EXTRA_AUTH_PROVIDER, provider.name)
                    }
                )
            } else {
                startActivity(
                    Intent(this@LoginActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
            finish()
        }
    }

    private fun startGuestFlow() {
        SessionHolder.authProvider = SocialProvider.NONE
        SessionHolder.isGuest = true
        SessionHolder.tier = UserTier.GUEST

        startActivity(
            Intent(this, OnboardingActivity::class.java).apply {
                putExtra(OnboardingActivity.EXTRA_IS_GUEST, true)
            }
        )
        finish()
    }

    private fun showLoadingDialog(message: String) {
        val dlgBinding = DialogSocialLoadingBinding.inflate(layoutInflater)
        dlgBinding.tvLoadingMessage.text = message
        loadingDialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            setContentView(dlgBinding.root)
            setCancelable(true)
            setOnCancelListener {
                setButtonsEnabled(true)
                loadingDialog = null
            }
            show()
        }
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnGoogleLogin.isEnabled = enabled
        binding.btnKakaoLogin.isEnabled  = enabled
        binding.btnGuestStart.isEnabled  = enabled
    }

    private fun setupTermsNotice() {
        val full  = "로그인하면 이용약관 및 개인정보처리방침에 동의하게 됩니다"
        val green = ContextCompat.getColor(this, R.color.brand_green)
        val span  = SpannableString(full)

        fun applySpan(word: String) {
            val start = full.indexOf(word).takeIf { it >= 0 } ?: return
            val end   = start + word.length
            span.setSpan(ForegroundColorSpan(green), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    Toast.makeText(this@LoginActivity, "$word (준비 중)", Toast.LENGTH_SHORT).show()
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        applySpan("이용약관")
        applySpan("개인정보처리방침")

        binding.tvTermsNotice.text            = span
        binding.tvTermsNotice.movementMethod  = LinkMovementMethod.getInstance()
        binding.tvTermsNotice.highlightColor  = android.graphics.Color.TRANSPARENT
    }
}
