package com.checkdang.app.ui.menu.subscription

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.checkdang.app.R
import com.checkdang.app.data.billing.BillingState
import com.checkdang.app.data.billing.KakaoPayState
import com.checkdang.app.data.billing.ProductIds
import com.checkdang.app.databinding.ActivitySubscriptionBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SubscriptionActivity : AppCompatActivity() {

    private val binding by lazy { ActivitySubscriptionBinding.inflate(layoutInflater) }
    private val viewModel: SubscriptionViewModel by viewModels()

    private var selectedYearly = true

    // 이번 세션에서 사용자가 직접 "구독 시작"을 눌렀는지. 복원(기존 구매 재조회)으로 들어온
    // Success 와 신규 구매 Success 를 구분해, 복원 시엔 자동 종료/"시작되었어요" 문구를 막는다.
    private var purchaseInitiated = false

    private val mockKakaoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.approveKakaoPay("mock_token")
        } else {
            viewModel.handleKakaoCancel()
        }
    }

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
        setupRetryButton()
        observeBillingState()
        observeKakaoState()

        if (SubscriptionViewModel.KAKAO_MOCK) viewModel.resetMockPaidState()

        // 딥링크로 직접 실행된 경우 처리
        handlePaymentDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePaymentDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        // Play 결제 시트에서 돌아왔거나 연결이 끊겼을 수 있으니 상품/구매 상태 동기화.
        // 단, 구매 진행/완료 중에는 재조회(Ready)로 결과 상태를 덮어쓰지 않도록 스킵(경합 방지).
        if (!purchaseInitiated && viewModel.state.value !is BillingState.Purchasing) {
            viewModel.retry()
        }
        // KakaoPay 앱에서 결제 없이 돌아온 경우(뒤로 가기) → 대기 상태 해제
        if (viewModel.kakaoState.value is KakaoPayState.WaitingForCallback) {
            viewModel.resetKakaoState()
        }
    }

    private fun handlePaymentDeepLink(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme != "checkdang" || data.host != "payment") return

        when (data.lastPathSegment) {
            "success" -> {
                val pgToken = data.getQueryParameter("pg_token") ?: run {
                    showSnackbar("결제 토큰을 받지 못했어요")
                    return
                }
                viewModel.approveKakaoPay(pgToken)
            }
            "cancel" -> viewModel.handleKakaoCancel()
            "fail" -> {
                viewModel.resetKakaoState()
                showSnackbar("결제에 실패했어요")
            }
        }
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
        val benefitBindings = listOf(
            binding.benefit1, binding.benefit2, binding.benefit3,
            binding.benefit4, binding.benefit5, binding.benefit6,
        )
        benefitBindings.forEachIndexed { idx, row ->
            row.tvBenefit.text = benefits.getOrElse(idx) { "" }
        }
    }

    private fun setupPricePlanToggle() {
        refreshBorder()
        binding.cardYearly.setOnClickListener { selectedYearly = true; refreshBorder() }
        binding.cardMonthly.setOnClickListener { selectedYearly = false; refreshBorder() }
    }

    private fun refreshBorder() {
        val greenStroke = ContextCompat.getColor(this, R.color.brand_green)
        val dividerStroke = ContextCompat.getColor(this, R.color.divider)
        binding.cardYearly.strokeColor = if (selectedYearly) greenStroke else dividerStroke
        binding.cardMonthly.strokeColor = if (!selectedYearly) greenStroke else dividerStroke
    }

    private fun setupSubscribeButton() {
        binding.btnSubscribe.setOnClickListener {
            showPaymentMethodDialog()
        }
    }

    private fun showPaymentMethodDialog() {
        AlertDialog.Builder(this)
            .setTitle("결제 수단 선택")
            .setItems(arrayOf("Google Play", "카카오페이")) { _, which ->
                when (which) {
                    0 -> startGooglePlay()
                    1 -> startKakaoPay()
                }
            }
            .show()
    }

    private fun startGooglePlay() {
        val productId = if (selectedYearly) ProductIds.PREMIUM_YEARLY else ProductIds.PREMIUM_MONTHLY
        purchaseInitiated = true
        viewModel.startPurchase(this, productId)
    }

    private fun startKakaoPay() {
        val productId = if (selectedYearly) ProductIds.PREMIUM_YEARLY else ProductIds.PREMIUM_MONTHLY
        viewModel.startKakaoPay(productId)
    }

    private fun setupRetryButton() {
        binding.btnRetry.setOnClickListener {
            binding.layoutError.visibility = View.GONE
            viewModel.retry()
        }
    }

    // ── 상태 관찰 ──────────────────────────────────────────────────────────

    private fun observeBillingState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun observeKakaoState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.kakaoState.collect { renderKakao(it) }
            }
        }
    }

    // ── Google Play 상태 렌더 (기존 로직 그대로) ────────────────────────────

    private fun render(state: BillingState) {
        when (state) {
            BillingState.Idle, BillingState.Loading -> {
                binding.pbLoading.visibility = View.VISIBLE
                binding.layoutError.visibility = View.GONE
                binding.btnSubscribe.isEnabled = false
                binding.btnSubscribe.text = "불러오는 중..."
            }
            is BillingState.Ready -> {
                binding.pbLoading.visibility = View.GONE
                binding.layoutError.visibility = View.GONE
                if (viewModel.kakaoState.value == KakaoPayState.Idle) {
                    binding.btnSubscribe.isEnabled = true
                    binding.btnSubscribe.text = "구독 시작하기"
                }
                applyPrices(state.products)
            }
            BillingState.Purchasing -> {
                binding.pbLoading.visibility = View.GONE
                binding.layoutError.visibility = View.GONE
                binding.btnSubscribe.isEnabled = false
                binding.btnSubscribe.text = "결제 진행 중..."
            }
            is BillingState.Success -> {
                binding.pbLoading.visibility = View.GONE
                binding.layoutError.visibility = View.GONE
                binding.btnSubscribe.isEnabled = false
                if (purchaseInitiated) {
                    binding.btnSubscribe.text = "구독 완료"
                    Snackbar.make(binding.root, "구독이 시작되었어요", Snackbar.LENGTH_SHORT).show()
                    binding.root.postDelayed({
                        viewModel.consumeTransientState()
                        finish()
                    }, 1500)
                } else {
                    binding.btnSubscribe.text = "구독 중"
                }
            }
            is BillingState.Error -> {
                purchaseInitiated = false
                binding.pbLoading.visibility = View.GONE
                binding.btnSubscribe.isEnabled = true
                binding.btnSubscribe.text = "구독 시작하기"
                when (state.code) {
                    BillingClient.BillingResponseCode.USER_CANCELED,
                    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_SHORT).show()
                        viewModel.consumeTransientState()
                    }
                    else -> {
                        binding.layoutError.visibility = View.VISIBLE
                        binding.tvError.text = state.message
                    }
                }
            }
        }
    }

    // ── KakaoPay 상태 렌더 ─────────────────────────────────────────────────

    private fun renderKakao(state: KakaoPayState) {
        when (state) {
            KakaoPayState.Idle -> Unit

            KakaoPayState.Loading -> {
                binding.pbLoading.visibility = View.VISIBLE
                binding.btnSubscribe.isEnabled = false
                binding.btnSubscribe.text = "결제 준비 중..."
            }

            is KakaoPayState.ReadyToLaunch -> {
                binding.pbLoading.visibility = View.GONE
                if (state.redirectUrl == SubscriptionViewModel.KAKAO_MOCK_URL) {
                    val intent = Intent(this, MockKakaoPayActivity::class.java)
                        .putExtra(
                            MockKakaoPayActivity.EXTRA_AMOUNT,
                            if (selectedYearly) "₩49,000" else "₩5,900",
                        )
                    mockKakaoLauncher.launch(intent)
                } else {
                    openKakaoPayApp(state.redirectUrl)
                }
                viewModel.onKakaoAppLaunched()
            }

            KakaoPayState.WaitingForCallback -> {
                binding.pbLoading.visibility = View.GONE
                binding.btnSubscribe.isEnabled = false
                binding.btnSubscribe.text = "결제 진행 중..."
            }

            KakaoPayState.Approving -> {
                binding.pbLoading.visibility = View.VISIBLE
                binding.btnSubscribe.isEnabled = false
                binding.btnSubscribe.text = "결제 확인 중..."
            }

            KakaoPayState.Success -> {
                binding.pbLoading.visibility = View.GONE
                binding.btnSubscribe.isEnabled = false
                binding.btnSubscribe.text = "구독 완료"
                Snackbar.make(binding.root, "구독이 시작되었어요", Snackbar.LENGTH_SHORT).show()
                binding.root.postDelayed({
                    viewModel.resetKakaoState()
                    finish()
                }, 1500)
            }

            KakaoPayState.Cancelled -> {
                binding.pbLoading.visibility = View.GONE
                binding.btnSubscribe.isEnabled = true
                binding.btnSubscribe.text = "구독 시작하기"
                Snackbar.make(binding.root, "결제가 취소되었어요", Snackbar.LENGTH_SHORT).show()
                viewModel.resetKakaoState()
            }

            is KakaoPayState.Error -> {
                binding.pbLoading.visibility = View.GONE
                binding.btnSubscribe.isEnabled = true
                binding.btnSubscribe.text = "구독 시작하기"
                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                viewModel.resetKakaoState()
            }
        }
    }

    private fun openKakaoPayApp(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun showSnackbar(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
    }

    private fun applyPrices(products: List<ProductDetails>) {
        products.forEach { product ->
            val price = product.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()
                ?.formattedPrice
                ?: return@forEach
            when (product.productId) {
                ProductIds.PREMIUM_MONTHLY -> binding.tvPriceMonthly.text = price
                ProductIds.PREMIUM_YEARLY  -> binding.tvPriceYearly.text  = price
            }
        }
    }
}
