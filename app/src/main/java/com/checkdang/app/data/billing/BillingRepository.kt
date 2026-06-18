package com.checkdang.app.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.mock.UserTier
import com.checkdang.app.data.remote.PaymentApiClient
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BillingRepository(private val appContext: Context) :
    PurchasesUpdatedListener, BillingClientStateListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<BillingState>(BillingState.Idle)
    val state: StateFlow<BillingState> = _state.asStateFlow()

    private var productDetailsList: List<ProductDetails> = emptyList()
    private var reconnectAttempt = 0
    private var skipExistingPurchaseChecks = false

    fun setSkipExistingPurchaseChecks(skip: Boolean) {
        skipExistingPurchaseChecks = skip
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun startConnection() {
        if (billingClient.isReady) {
            queryProducts()
            queryExistingPurchases()
            return
        }
        _state.value = BillingState.Loading
        billingClient.startConnection(this)
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            reconnectAttempt = 0
            queryProducts()
            queryExistingPurchases()
        } else {
            _state.value = BillingState.Error("결제 서비스 연결 실패", billingResult.responseCode)
        }
    }

    override fun onBillingServiceDisconnected() {
        scope.launch {
            // exponential backoff: 1s, 2s, 4s, 8s, 16s, 32s → 상한 60s
            val backoff = (1L shl reconnectAttempt.coerceAtMost(5)).coerceAtMost(60L)
            reconnectAttempt += 1
            delay(backoff * 1000L)
            startConnection()
        }
    }

    private fun queryProducts() {
        val productList = ProductIds.ALL.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        billingClient.queryProductDetailsAsync(params) { billingResult, productDetails ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsList = productDetails
                _state.value = BillingState.Ready(productDetails)
            } else {
                _state.value = BillingState.Error(
                    "상품 정보를 불러오지 못했어요", billingResult.responseCode
                )
            }
        }
    }

    private fun queryExistingPurchases() {
        if (skipExistingPurchaseChecks) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { handlePurchase(it) }
            }
        }
    }

    fun launchBillingFlow(activity: Activity, productId: String) {
        val product = productDetailsList.find { it.productId == productId } ?: run {
            _state.value = BillingState.Error("상품 정보를 찾을 수 없어요")
            return
        }
        val offerToken = product.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: run {
            _state.value = BillingState.Error("구독 옵션이 설정되지 않았어요")
            return
        }
        val builder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
        // 결제-사용자 매칭/어뷰징 방지용 식별자. userId 원문 노출을 피해 해시로 부착.
        SessionHolder.userId?.let { builder.setObfuscatedAccountId(obfuscate(it)) }
        val params = builder.build()
        _state.value = BillingState.Purchasing
        val result = billingClient.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.value = BillingState.Error("결제 시작 실패", result.responseCode)
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases?.forEach { handlePurchase(it) }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                _state.value = BillingState.Error("결제가 취소되었어요", billingResult.responseCode)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryExistingPurchases()
                _state.value = BillingState.Error("이미 구독 중이에요", billingResult.responseCode)
            }
            else -> _state.value = BillingState.Error("결제 오류", billingResult.responseCode)
        }
    }

    /**
     * 결제 처리 (2026-05-24 백엔드 회신 반영):
     * 1) 백엔드 `/api/payment/google/verify` 가 200 응답한 경우에만 PAID 승격.
     *    서버는 Google Play Developer API 로 token 유효성 확인 후 payment_records 저장 + users.isPremium 갱신까지 처리.
     * 2) verify 성공 시 Google Play 측 acknowledge 진행 (acknowledge 누락 시 3일 뒤 환불 처리됨).
     * 3) verify 실패 시 tier 미갱신 + Error 상태. purchaseToken 은 acknowledge 전까지 재처리 가능하므로
     *    재시도 가능 상태 유지.
     */
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val productId = purchase.products.firstOrNull() ?: return

        scope.launch {
            val verified = runCatching {
                PaymentApiClient.verifyGooglePurchase(
                    purchaseToken  = purchase.purchaseToken,
                    subscriptionId = productId
                )
            }
            if (verified.isFailure) {
                _state.value = BillingState.Error(
                    "결제 검증 실패: ${verified.exceptionOrNull()?.message ?: "서버 오류"}"
                )
                return@launch
            }

            val result = verified.getOrThrow()
            if (!purchase.isAcknowledged) {
                acknowledgePurchase(purchase, productId, result.isPremium)
            } else {
                SessionHolder.tier = if (result.isPremium) UserTier.PAID else UserTier.FREE
                _state.value = BillingState.Success(productId)
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase, productId: String, isPremium: Boolean) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { ack ->
            if (ack.responseCode == BillingClient.BillingResponseCode.OK) {
                SessionHolder.tier = if (isPremium) UserTier.PAID else UserTier.FREE
                _state.value = BillingState.Success(productId)
            } else {
                _state.value = BillingState.Error("결제 확인 실패", ack.responseCode)
            }
        }
    }

    /** userId 를 SHA-256 hex(64자)로 변환. Play obfuscatedAccountId 제약(≤64자, 비PII) 충족. */
    private fun obfuscate(userId: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(userId.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(64)

    fun resetTransientState() {
        _state.value = if (productDetailsList.isNotEmpty()) {
            BillingState.Ready(productDetailsList)
        } else {
            BillingState.Idle
        }
    }
}
