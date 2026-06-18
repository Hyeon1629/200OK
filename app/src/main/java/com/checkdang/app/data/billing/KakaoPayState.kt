package com.checkdang.app.data.billing

sealed class KakaoPayState {
    data object Idle : KakaoPayState()
    data object Loading : KakaoPayState()
    data class ReadyToLaunch(val redirectUrl: String) : KakaoPayState()
    data object WaitingForCallback : KakaoPayState()
    data object Approving : KakaoPayState()
    data object Success : KakaoPayState()
    data object Cancelled : KakaoPayState()
    data class Error(val message: String) : KakaoPayState()
}
