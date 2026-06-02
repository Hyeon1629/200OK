package com.checkdang.app.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkdang.app.data.remote.AiReportApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Gemini 종합 리포트 화면 상태. */
sealed interface ReportUiState {
    /** 조회 전(초기). */
    object Idle : ReportUiState
    object Loading : ReportUiState
    /** 마크다운 본문 표시. */
    data class Loaded(val markdown: String) : ReportUiState
    data class Error(val message: String) : ReportUiState
}

class ComprehensiveReportViewModel : ViewModel() {

    private val _state = MutableStateFlow<ReportUiState>(ReportUiState.Idle)
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    /** 종합 리포트 조회. 진입 시 + 재시도 버튼에서 호출. */
    fun loadReport() {
        if (_state.value is ReportUiState.Loading) return
        viewModelScope.launch {
            _state.value = ReportUiState.Loading
            _state.value = runCatching { AiReportApiClient.getComprehensiveReport() }
                .map { ReportUiState.Loaded(it) }
                .getOrElse { ReportUiState.Error(messageFor(it)) }
        }
    }

    // 에러 형식: Spring GlobalExceptionHandler { success:false, data:null, message:"<한국어>" }.
    // 코드 매핑(백엔드 회신 2026-06-02): 401=토큰 없음/만료, 400=사용자 없음 등, 500=Gemini 키 등.
    private fun messageFor(t: Throwable): String {
        val msg = t.message.orEmpty()
        return when {
            "HTTP 401" in msg || "HTTP 403" in msg -> "로그인 후 이용할 수 있어요."
            "HTTP 400" in msg -> "분석할 데이터가 부족해요. 식단·수면·운동을 먼저 기록해주세요."
            "HTTP 500" in msg -> "리포트 생성 중 문제가 발생했어요. 잠시 후 다시 시도해주세요."
            else -> "리포트를 불러오지 못했어요. 잠시 후 다시 시도해주세요."
        }
    }
}
