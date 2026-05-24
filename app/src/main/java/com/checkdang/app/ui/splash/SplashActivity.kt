package com.checkdang.app.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.mock.SocialProvider
import com.checkdang.app.data.mock.UserStore
import com.checkdang.app.data.mock.UserTier
import com.checkdang.app.data.remote.CognitoGuestSession
import com.checkdang.app.databinding.ActivitySplashBinding
import com.checkdang.app.ui.auth.login.LoginActivity
import com.checkdang.app.ui.main.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            delay(1500L)
            val target = if (UserStore.isGuestSession()) {
                restoreGuestSession()
                // 게스트 보호 API 호출에 필요한 Cognito Identity ID 를 미리 확보(2026-05-24).
                CognitoGuestSession.ensureIdentityId()
                MainActivity::class.java
            } else {
                LoginActivity::class.java
            }
            startActivity(Intent(this@SplashActivity, target))
            finish()
        }
    }

    /**
     * 디스크의 게스트 프로필을 메모리 [SessionHolder] 로 복원. 이전 콜드 스타트에서
     * 비회원으로 진입했던 사용자가 LoginActivity 를 다시 거치지 않도록 하기 위함.
     */
    private fun restoreGuestSession() {
        SessionHolder.authProvider   = SocialProvider.NONE
        SessionHolder.isGuest        = true
        SessionHolder.isLoggedIn     = false
        SessionHolder.tier           = UserTier.GUEST
        SessionHolder.currentProfile = UserStore.getProfile(SocialProvider.NONE)
    }
}
