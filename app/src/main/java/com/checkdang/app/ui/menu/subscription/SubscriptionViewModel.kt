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

    companion object {
        // TODO(mock): 사업자 등록 완료 후 false 로 변경
        const val KAKAO_MOCK = true
        const val KAKAO_MOCK_URL = "kakao_mock://pay"
    }

    private val repository: BillingRepository =
        (application as CheckDangApplication).billingRepository

    init {
        if (KAKAO_MOCK) repository.setSkipExistingPurchaseChecks(true)
    }

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
            // TODO(mock): 사업자 등록 완료 후 아래 mock 블록 제거 후 실제 API 호출로 교체
            if (KAKAO_MOCK) {
                kotlinx.coroutines.delay(500)
                SessionHolder.kakaoPendingOrderId = "mock_order_id"
                _kakaoState.value = KakaoPayState.ReadyToLaunch(KAKAO_MOCK_URL)
                return@launch
            }
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

    /**
     * 테스트 목적의 mock 헬퍼 — 백엔드 mock isPremium 플래그를 초기화해 반복 구매 테스트를 가능케 한다.
     * 이미 PAID 상태(실제 구독 완료)라면 화면을 다시 열었다는 이유만으로 구독을 풀어버리면 안 되므로
     * 그 경우엔 아무 것도 하지 않고 PAID 상태를 유지한다.
     */
    fun resetMockPaidState() {
        if (SessionHolder.tier == UserTier.PAID) return
        viewModelScope.launch {
            runCatching { PaymentApiClient.resetMockIsPremium() }
            SessionHolder.tier = UserTier.FREE
        }
    }
}
