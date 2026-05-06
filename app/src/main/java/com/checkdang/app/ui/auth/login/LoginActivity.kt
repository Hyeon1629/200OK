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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.checkdang.app.R
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.mock.SocialProvider
import com.checkdang.app.data.mock.UserStore
import com.checkdang.app.data.mock.UserTier
import com.checkdang.app.data.remote.AuthApiClient
import com.checkdang.app.data.remote.ProfileApiClient
import com.checkdang.app.databinding.ActivityLoginBinding
import com.checkdang.app.databinding.DialogSocialLoadingBinding
import com.checkdang.app.ui.auth.onboarding.OnboardingActivity
import com.checkdang.app.ui.main.MainActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private var loadingDialog: Dialog? = null

    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken == null) {
                setButtonsEnabled(true)
                Toast.makeText(this, "Google idToken을 가져오지 못했어요", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            callSocialLoginApi(
                provider = "GOOGLE",
                idToken  = idToken,
                email    = account.email,
                nickname = account.displayName
            )
        } catch (e: ApiException) {
            dismissLoadingDialog()
            setButtonsEnabled(true)
            Toast.makeText(this, "Google 로그인 실패 (코드: ${e.statusCode})", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGoogle()
        binding.btnGoogleLogin.setOnClickListener { startGoogleLogin() }
        binding.btnKakaoLogin.setOnClickListener  { startKakaoLogin() }
        binding.btnGuestStart.setOnClickListener  { startGuestFlow() }
        setupTermsNotice()
    }

    // ── Google ──────────────────────────────────────────────────────────────

    private fun setupGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken("1060017596857-ma0noia4ll2fb00msospc7aut1o84n1q.apps.googleusercontent.com")
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun startGoogleLogin() {
        setButtonsEnabled(false)
        showLoadingDialog("Google에 연결 중…")
        googleSignInClient.signOut().addOnCompleteListener {
            dismissLoadingDialog()
            googleLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    // ── Kakao ───────────────────────────────────────────────────────────────

    private fun startKakaoLogin() {
        setButtonsEnabled(false)

        val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
            if (error != null) {
                setButtonsEnabled(true)
                Toast.makeText(this, "카카오 로그인 실패: ${error.message}", Toast.LENGTH_SHORT).show()
            } else if (token != null) {
                fetchKakaoUserInfo(token.accessToken)
            }
        }

        if (UserApiClient.instance.isKakaoTalkLoginAvailable(this)) {
            showLoadingDialog("카카오에 연결 중…")
            UserApiClient.instance.loginWithKakaoTalk(this, callback = callback)
        } else {
            UserApiClient.instance.loginWithKakaoAccount(this, callback = callback)
        }
    }

    private fun fetchKakaoUserInfo(accessToken: String) {
        UserApiClient.instance.me { user, error ->
            if (error != null || user == null) {
                setButtonsEnabled(true)
                Toast.makeText(this, "카카오 사용자 정보를 불러오지 못했어요", Toast.LENGTH_SHORT).show()
                return@me
            }
            callSocialLoginApi(
                provider   = "KAKAO",
                kakaoToken = accessToken,
                email      = user.kakaoAccount?.email,
                nickname   = user.kakaoAccount?.profile?.nickname
            )
        }
    }

    // ── 비회원으로 시작하기 ────────────────────────────────────────────────────

    private fun startGuestFlow() {
        SessionHolder.authProvider = SocialProvider.NONE
        SessionHolder.isGuest      = true
        SessionHolder.tier         = UserTier.GUEST
        startActivity(
            Intent(this, OnboardingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(OnboardingActivity.EXTRA_IS_GUEST, true)
                putExtra(OnboardingActivity.EXTRA_AUTH_PROVIDER, SocialProvider.NONE.name)
            }
        )
    }

    // ── 소셜 로그인 처리 ─────────────────────────────────────────────────────

    private fun callSocialLoginApi(
        provider: String,
        idToken: String? = null,      // Google
        kakaoToken: String? = null,   // Kakao
        email: String?,
        nickname: String?
    ) {
        showLoadingDialog("로그인 중…")
        setButtonsEnabled(false)

        val socialProvider = if (provider == "GOOGLE") SocialProvider.GOOGLE else SocialProvider.KAKAO

        lifecycleScope.launch {
            // ── 실제 API 호출 시도 ──────────────────────────────────────────
            val apiResult = runCatching {
                AuthApiClient.socialLogin(
                    provider    = provider,
                    idToken     = idToken,
                    accessToken = kakaoToken
                )
            }

            // API 성공: 실제 토큰 저장 / 실패: Mock 토큰으로 유지 (기존 동작 보장)
            if (apiResult.isSuccess) {
                val result = apiResult.getOrThrow()
                SessionHolder.accessToken    = result.accessToken
                SessionHolder.refreshToken   = result.refreshToken
                SessionHolder.userId         = result.userId
                SessionHolder.socialEmail    = result.email ?: email
                SessionHolder.socialNickname = result.name  ?: nickname
                android.util.Log.d("SocialLogin", "✅ API 성공 | userId=${result.userId} | token=${result.accessToken.take(20)}…")

                val profile = runCatching {
                    ProfileApiClient.fetchProfile(result.accessToken)
                }.getOrNull()

                if (profile != null) {
                    SessionHolder.currentProfile = profile
                    android.util.Log.d("SocialLogin", "✅ 프로필 로드 성공 | nickname=${profile.nickname}")
                } else {
                    SessionHolder.currentProfile = UserStore.getProfile(socialProvider)
                    android.util.Log.d("SocialLogin", "⚠️ 프로필 로드 실패 → 로컬 프로필 또는 null 사용")
                }
            } else {
                SessionHolder.accessToken    = "mock_access_token"
                SessionHolder.refreshToken   = "mock_refresh_token"
                SessionHolder.userId         = "mock_user_id"
                SessionHolder.socialEmail    = email
                SessionHolder.socialNickname = nickname
                SessionHolder.currentProfile  = UserStore.getProfile(socialProvider)
                android.util.Log.d("SocialLogin", "⚠️ API 실패 → Mock 사용 | 원인: ${apiResult.exceptionOrNull()?.message}")
            }

            // ── 아래 네비게이션 로직은 기존과 동일 ──────────────────────────
            SessionHolder.authProvider = socialProvider
            SessionHolder.isLoggedIn   = true
            SessionHolder.isGuest      = false
            SessionHolder.tier         = UserTier.FREE

            dismissLoadingDialog()

            if (!UserStore.isRegistered(socialProvider)) {
                startActivity(
                    Intent(this@LoginActivity, OnboardingActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra(OnboardingActivity.EXTRA_IS_GUEST, false)
                        putExtra(OnboardingActivity.EXTRA_AUTH_PROVIDER, provider)
                    }
                )
            } else {
                SessionHolder.currentProfile = UserStore.getProfile(socialProvider)
                startActivity(
                    Intent(this@LoginActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
        }
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

        binding.tvTermsNotice.text           = span
        binding.tvTermsNotice.movementMethod = LinkMovementMethod.getInstance()
        binding.tvTermsNotice.highlightColor = android.graphics.Color.TRANSPARENT
    }
}
