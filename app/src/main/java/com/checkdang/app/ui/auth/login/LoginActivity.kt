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
import com.amplifyframework.auth.AuthProvider
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.kotlin.core.Amplify
import com.checkdang.app.R
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.mock.SocialProvider
import com.checkdang.app.data.mock.UserStore
import com.checkdang.app.data.mock.UserTier
import com.checkdang.app.data.remote.AuthApiClient
import com.checkdang.app.data.remote.CognitoGuestSession
import com.checkdang.app.databinding.ActivityLoginBinding
import com.checkdang.app.databinding.DialogSocialLoadingBinding
import com.checkdang.app.push.PushTokenStore
import com.checkdang.app.ui.auth.onboarding.OnboardingActivity
import com.checkdang.app.ui.legal.LegalDocument
import com.checkdang.app.ui.legal.LegalDocumentActivity
import com.checkdang.app.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * Cognito Hosted UI 로 Google·Kakao Federated Sign-In(2026-05-24 백엔드 회신 반영).
 *
 * 기존 Google Sign-In SDK / Kakao SDK 직접 호출 경로는 제거. Amplify 가 Hosted UI(브라우저)
 * 로 IdP 인증을 위임하고 Cognito 가 발급한 ID Token 을 받아 백엔드 `/api/auth/social-login`
 * 호출에 Bearer 헤더로 사용한다. 백엔드는 더 이상 자체 access/refresh JWT 를 발급하지 않는다.
 *
 * mock 토큰 fallback 제거 — 실패는 곧 로그인 실패. 사용자는 재시도 가능 상태로 유지된다.
 */
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

    // ── Cognito Hosted UI 로 Google·Kakao Federated Sign-In ───────────────────

    private fun startSocialLogin(provider: SocialProvider) {
        setButtonsEnabled(false)
        showLoadingDialog("${provider.labelKr}에 연결 중…")

        val authProvider = when (provider) {
            SocialProvider.GOOGLE -> AuthProvider.google()
            // 백엔드(kgh) 회신: Cognito 에 `KakaoOIDC` 라는 custom IdP 로 등록됨.
            SocialProvider.KAKAO  -> AuthProvider.custom("KakaoOIDC")
            else -> error("Guest 는 Hosted UI 로 진입하지 않음")
        }

        lifecycleScope.launch {
            // 0. Amplify 잔존 세션 정리 — signInWithSocialWebUI 는 기존 세션이 있으면 SignedInException 던짐.
            //    앱 재시작/이전 로그인 후 LoginActivity 재진입 시 발생.
            runCatching {
                val session = Amplify.Auth.fetchAuthSession()
                if (session.isSignedIn) {
                    android.util.Log.d("SocialLogin", "기존 Amplify 세션 발견 → signOut 후 재로그인")
                    Amplify.Auth.signOut()
                }
            }.onFailure { android.util.Log.w("SocialLogin", "세션 사전 정리 실패: ${it.message}") }

            // 1. Hosted UI 로 IdP 인증
            val signInResult = runCatching {
                Amplify.Auth.signInWithSocialWebUI(authProvider, this@LoginActivity)
            }
            if (signInResult.isFailure) {
                onLoginFailed("${provider.labelKr} 로그인", signInResult.exceptionOrNull())
                return@launch
            }

            // 2. 현 세션의 Cognito ID Token 추출
            val idToken = runCatching {
                val session = Amplify.Auth.fetchAuthSession() as AWSCognitoAuthSession
                session.userPoolTokensResult.value?.idToken ?: error("Cognito ID Token 누락")
            }
            if (idToken.isFailure) {
                onLoginFailed("토큰 획득", idToken.exceptionOrNull())
                runCatching { Amplify.Auth.signOut() }
                return@launch
            }
            SessionHolder.accessToken = idToken.getOrThrow()
            logTokenClaims(idToken.getOrThrow())

            // 3. 백엔드 /api/auth/social-login (body 없음, Bearer 만)
            val apiResult = runCatching { AuthApiClient.socialLogin() }
            if (apiResult.isFailure) {
                SessionHolder.accessToken = null
                runCatching { Amplify.Auth.signOut() }
                onLoginFailed("백엔드 등록", apiResult.exceptionOrNull())
                return@launch
            }

            // 4. 세션 상태 갱신 + 네비게이션
            val info = apiResult.getOrThrow()
            SessionHolder.userId         = info.userId
            SessionHolder.socialEmail    = info.email
            SessionHolder.socialNickname = info.name
            SessionHolder.authProvider   = provider
            SessionHolder.isLoggedIn     = true
            SessionHolder.isGuest        = false
            SessionHolder.tier           = if (info.isPremium) UserTier.PAID else UserTier.FREE
            // 백엔드가 자체 refresh 토큰을 발급하지 않음 (Cognito 가 관리)
            SessionHolder.refreshToken   = null

            android.util.Log.d("SocialLogin", "✅ Cognito + 백엔드 등록 | userId=${info.userId}")

            // 콜드 스타트 때 세션이 없어 건너뛴 FCM 토큰 등록을 로그인 완료 후 마무리.
            PushTokenStore.syncCachedToken(applicationContext)

            dismissLoadingDialog()
            navigateAfterLogin(provider)
        }
    }

    /** Cognito ID Token 의 payload 만 디코드해 logcat 으로 출력 (401 진단용). */
    private fun logTokenClaims(jwt: String) {
        runCatching {
            val payload = jwt.split(".").getOrNull(1) ?: return@runCatching
            val bytes = android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
            val json  = org.json.JSONObject(String(bytes))
            android.util.Log.d(
                "SocialLogin",
                "ID Token claims: iss=${json.optString("iss")} aud=${json.optString("aud")} " +
                "token_use=${json.optString("token_use")} sub=${json.optString("sub")} " +
                "email=${json.optString("email")} email_verified=${json.optString("email_verified")} " +
                "identities=${json.optString("identities")} exp=${json.optString("exp")}"
            )
        }.onFailure { android.util.Log.w("SocialLogin", "토큰 디코드 실패: ${it.message}") }
    }

    private fun onLoginFailed(stage: String, e: Throwable?) {
        android.util.Log.w("SocialLogin", "$stage 실패: ${e?.message}", e)
        dismissLoadingDialog()
        setButtonsEnabled(true)
        Toast.makeText(
            this,
            "$stage 실패: ${e?.message ?: "알 수 없는 오류"}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun navigateAfterLogin(provider: SocialProvider) {
        if (!UserStore.isRegistered(provider)) {
            startActivity(
                Intent(this, OnboardingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(OnboardingActivity.EXTRA_IS_GUEST, false)
                    putExtra(OnboardingActivity.EXTRA_AUTH_PROVIDER, provider.name)
                }
            )
        } else {
            SessionHolder.currentProfile = UserStore.getProfile(provider)
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }
    }

    // ── 비회원으로 시작하기 ────────────────────────────────────────────────────

    private fun startGuestFlow() {
        SessionHolder.authProvider = SocialProvider.NONE
        SessionHolder.isGuest      = true
        SessionHolder.tier         = UserTier.GUEST
        // 게스트 Identity Pool ID 발급은 백그라운드로 진행(2026-05-24). 온보딩 진행을 막지 않음.
        lifecycleScope.launch { CognitoGuestSession.ensureIdentityId() }
        startActivity(
            Intent(this, OnboardingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(OnboardingActivity.EXTRA_IS_GUEST, true)
                putExtra(OnboardingActivity.EXTRA_AUTH_PROVIDER, SocialProvider.NONE.name)
            }
        )
    }

    // ── UI 헬퍼 ──────────────────────────────────────────────────────────────

    private fun showLoadingDialog(message: String) {
        if (loadingDialog?.isShowing == true) return
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

        fun applySpan(word: String, onClick: () -> Unit) {
            val start = full.indexOf(word).takeIf { it >= 0 } ?: return
            val end   = start + word.length
            span.setSpan(ForegroundColorSpan(green), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) = onClick()
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        applySpan("이용약관") {
            startActivity(LegalDocumentActivity.intent(this, LegalDocument.TERMS))
        }
        applySpan("개인정보처리방침") {
            startActivity(LegalDocumentActivity.intent(this, LegalDocument.PRIVACY))
        }

        binding.tvTermsNotice.text           = span
        binding.tvTermsNotice.movementMethod = LinkMovementMethod.getInstance()
        binding.tvTermsNotice.highlightColor = android.graphics.Color.TRANSPARENT
    }
}
