package com.checkdang.app.ui.menu.subscription

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.checkdang.app.R
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.mock.UserTier
import com.checkdang.app.databinding.ActivitySubscriptionBinding
import com.google.android.material.snackbar.Snackbar

class SubscriptionActivity : AppCompatActivity() {

    private val binding by lazy { ActivitySubscriptionBinding.inflate(layoutInflater) }

    private val benefits = listOf(
        "무제한 데이터 백업",
        "AI 바디맵 분석 무제한",
        "가족 실시간 공유",
        "광고 제거",
        "PDF 리포트 고급 양식",
        "우선 고객지원",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupToolbar()
        setupBenefits()
        setupPricePlanToggle()
        setupSubscribeButton()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "프리미엄 구독"
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setupBenefits() {
        // binding.benefitN is ItemBenefitRowBinding (ViewBinding from <include>)
        val benefitBindings = listOf(
            binding.benefit1, binding.benefit2, binding.benefit3,
            binding.benefit4, binding.benefit5, binding.benefit6,
        )
        benefitBindings.forEachIndexed { idx, row ->
            row.tvBenefit.text = benefits.getOrElse(idx) { "" }
        }
    }

    private fun setupPricePlanToggle() {
        var selectedYearly = true
        fun refreshBorder() {
            val greenStroke = ContextCompat.getColor(this, R.color.brand_green)
            val dividerStroke = ContextCompat.getColor(this, R.color.divider)
            binding.cardYearly.strokeColor  = if (selectedYearly) greenStroke else dividerStroke
            binding.cardMonthly.strokeColor = if (!selectedYearly) greenStroke else dividerStroke
        }
        refreshBorder()

        binding.cardYearly.setOnClickListener  { selectedYearly = true;  refreshBorder() }
        binding.cardMonthly.setOnClickListener { selectedYearly = false; refreshBorder() }
    }

    private fun setupSubscribeButton() {
        binding.btnSubscribe.setOnClickListener {
            SessionHolder.tier = UserTier.PAID
            Snackbar.make(binding.root, "구독이 시작되었어요 (모의)", Snackbar.LENGTH_SHORT)
                .setAction("확인") { finish() }
                .show()
            // 잠시 후 자동 종료
            binding.root.postDelayed({ finish() }, 1800)
        }
    }
}
