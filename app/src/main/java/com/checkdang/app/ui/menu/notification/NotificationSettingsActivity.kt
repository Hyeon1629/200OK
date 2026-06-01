package com.checkdang.app.ui.menu.notification

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.checkdang.app.databinding.ActivityNotificationSettingsBinding
import com.checkdang.app.util.NotificationPrefs

/**
 * 앱 내 혈당 알림 설정.
 *  - 위험 혈당 알림 마스터 ON/OFF
 *  - 주의 범위도 알림 (마스터 ON 일 때만 활성)
 *  - 시스템 알림 설정 바로가기(채널/권한은 OS 가 관리)
 *
 * 실제 알림 발송은 [com.checkdang.app.util.GlucoseAlertNotifier] 가 이 설정을 참조해 결정한다.
 */
class NotificationSettingsActivity : AppCompatActivity() {

    private val binding by lazy { ActivityNotificationSettingsBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupToolbar()
        bindSwitches()
        binding.btnSystemSettings.setOnClickListener { openAppNotificationSettings() }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun bindSwitches() {
        val enabled = NotificationPrefs.isAlertEnabled(this)
        binding.switchAlert.isChecked = enabled
        binding.switchWarning.isChecked = NotificationPrefs.isIncludeWarning(this)
        binding.switchWarning.isEnabled = enabled

        binding.switchAlert.setOnCheckedChangeListener { _, isChecked ->
            NotificationPrefs.setAlertEnabled(this, isChecked)
            binding.switchWarning.isEnabled = isChecked
        }
        binding.switchWarning.setOnCheckedChangeListener { _, isChecked ->
            NotificationPrefs.setIncludeWarning(this, isChecked)
        }
    }

    /** 시스템 앱별 알림 설정으로 이동. 실패 시 앱 정보 화면으로 fallback. */
    private fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        runCatching { startActivity(intent) }.onFailure {
            val fallback = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
            runCatching { startActivity(fallback) }.onFailure {
                Toast.makeText(this, "설정 화면을 열 수 없어요", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
