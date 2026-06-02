package com.checkdang.app.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.checkdang.app.R
import com.checkdang.app.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    // 알림 권한 결과는 무관하게 진행(푸시/알림은 부가 기능 — 거부해도 앱 흐름을 막지 않음).
    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        maybeRequestNotificationPermission()

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        NavigationUI.setupWithNavController(binding.bottomNav, navController)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (navController.currentDestination?.id == R.id.nav_home) {
                    showExitDialog()
                } else {
                    binding.bottomNav.selectedItemId = R.id.nav_home
                }
            }
        })
    }

    /** API 33+ 에서 알림 권한 미허용 시 1회 요청(FCM 푸시·로컬 알림 공용). 그 이하는 불요. */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) return
        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun showExitDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("앱 종료")
            .setMessage("체크당을 종료할까요?")
            .setPositiveButton("종료") { _, _ -> finish() }
            .setNegativeButton("취소", null)
            .show()
    }
}
