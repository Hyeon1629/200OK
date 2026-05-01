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
import com.checkdang.app.R
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.mock.SocialProvider
import com.checkdang.app.data.mock.UserStore
import com.checkdang.app.data.mock.UserTier
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
                token    = idToken,
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
                provider = "KAKAO",
                token    = accessToken,
                email    = user.kakaoAccount?.email,
                nickname = user.kakaoAccount?.profile?.nickname
            )
        }
    }

    // ── Guest ────────────────────────────────────────────────────────────────

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

    // ── 소셜 로그인 처리 (MVP: 백엔드 없이 Mock) ──────────────────────────────

    private fun callSocialLoginApi(provider: String, token: String, email: String?, nickname: String?) {
        dismissLoadingDialog()

        SessionHolder.authProvider   = if (provider == "GOOGLE") SocialProvider.GOOGLE else SocialProvider.KAKAO
        SessionHolder.isLoggedIn     = true
        SessionHolder.isGuest        = false
        SessionHolder.tier           = UserTier.FREE
        SessionHolder.socialEmail    = email
        SessionHolder.socialNickname = nickname
        SessionHolder.accessToken    = "mock_access_token"
        SessionHolder.refreshToken   = "mock_refresh_token"
        SessionHolder.userId         = "mock_user_id"

        // 프로바이더별 가입 여부 확인 → 신규면 온보딩, 기존이면 프로필 복원 후 홈
        if (!UserStore.isRegistered(SessionHolder.authProvider)) {
            startActivity(
                Intent(this, OnboardingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(OnboardingActivity.EXTRA_IS_GUEST, false)
                    putExtra(OnboardingActivity.EXTRA_AUTH_PROVIDER, provider)
                }
            )
        } else {
            SessionHolder.currentProfile = UserStore.getProfile(SessionHolder.authProvider)
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
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
