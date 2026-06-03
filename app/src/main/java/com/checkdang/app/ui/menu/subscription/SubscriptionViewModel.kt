package com.checkdang.app.ui.menu.subscription

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.checkdang.app.CheckDangApplication
import com.checkdang.app.data.billing.BillingRepository
import com.checkdang.app.data.billing.BillingState
import com.checkdang.app.data.billing.KakaoPayState
import com.checkdang.app.data.mock.SessionHolder
import com.checkdang.app.data.mock.UserTier
import com.checkdang.app.data.remote.PaymentApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BillingRepository =
        (application as CheckDangApplication).billingRepository

    val state: StateFlow<BillingState> = repository.state

    private val _kakaoState = MutableStateFlow<KakaoPayState>(KakaoPayState.Idle)
    val kakaoState: StateFlow<KakaoPayState> = _kakaoState.asStateFlow()

    // ── Google Play Billing ────────────────────────────────────────────────

    fun startPurchase(activity: Activity, productId: String) {
        repository.launchBillingFlow(activity, productId)
    }

    fun retry() {
        repository.startConnection()
    }

    fun consumeTransientState() {
        repository.resetTransientState()
    }

    // ── KakaoPay ──────────────────────────────────────────────────────────

    fun startKakaoPay(subscriptionId: String) {
        viewModelScope.launch {
            _kakaoState.value = KakaoPayState.Loading
            val result = runCatching { PaymentApiClient.kakaoReady(subscriptionId) }
            if (result.isFailure) {
                _kakaoState.value = KakaoPayState.Error(
                    result.exceptionOrNull()?.message ?: "결제 준비에 실패했어요"
                )
                return@launch
            }
            val ready = result.getOrThrow()
            SessionHolder.kakaoPendingOrderId = ready.orderId
            _kakaoState.value = KakaoPayState.ReadyToLaunch(ready.nextRedirectAppUrl)
        }
    }

    fun onKakaoAppLaunched() {
        _kakaoState.value = KakaoPayState.WaitingForCallback
    }

    fun approveKakaoPay(pgToken: String) {
        val orderId = SessionHolder.kakaoPendingOrderId ?: run {
            _kakaoState.value = KakaoPayState.Error("결제 정보를 찾을 수 없어요")
            return
        }
        viewModelScope.launch {
            _kakaoState.value = KakaoPayState.Approving
            val result = runCatching { PaymentApiClient.kakaoApprove(orderId, pgToken) }
            SessionHolder.kakaoPendingOrderId = null
            if (result.isFailure) {
                _kakaoState.value = KakaoPayState.Error(
                    result.exceptionOrNull()?.message ?: "결제 승인에 실패했어요"
                )
                return@launch
            }
            if (result.getOrThrow().isPremium) {
                SessionHolder.tier = UserTier.PAID
                _kakaoState.value = KakaoPayState.Success
            } else {
                _kakaoState.value = KakaoPayState.Error("구독 상태 업데이트에 실패했어요")
            }
        }
    }

    fun handleKakaoCancel() {
        SessionHolder.kakaoPendingOrderId = null
        _kakaoState.value = KakaoPayState.Cancelled
    }

    fun resetKakaoState() {
        _kakaoState.value = KakaoPayState.Idle
    }
}
