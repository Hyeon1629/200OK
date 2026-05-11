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
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
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

    private fun handlePurchase(purchase: Purchase) {
        // TODO(backend, billing): 서버에 영수증(purchaseToken) 검증 요청
        // POST /api/v1/billing/verify { purchaseToken, productId, packageName }
        // 응답 OK 시에만 tier 갱신하도록 변경
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        val productId = purchase.products.firstOrNull() ?: return

        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    SessionHolder.tier = UserTier.PAID
                    _state.value = BillingState.Success(productId)
                } else {
                    _state.value = BillingState.Error("결제 확인 실패", result.responseCode)
                }
            }
        } else {
            SessionHolder.tier = UserTier.PAID
            _state.value = BillingState.Success(productId)
        }
    }

    fun resetTransientState() {
        _state.value = if (productDetailsList.isNotEmpty()) {
            BillingState.Ready(productDetailsList)
        } else {
            BillingState.Idle
        }
    }
}
