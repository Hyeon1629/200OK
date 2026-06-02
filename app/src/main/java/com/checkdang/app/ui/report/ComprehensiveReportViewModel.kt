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
                .map { ReportUiState.Loaded(buildMarkdown(it)) }
                .getOrElse { ReportUiState.Error(messageFor(it)) }
        }
    }

    /**
     * 표시용 마크다운 합성.
     * - 데이터가 있는 항목은 백엔드 `report`(Gemini 분석)를 그대로 렌더.
     * - 일부 항목만 0 건이면 상단에 "○○ 기록이 없습니다" 안내를 덧붙인다.
     * - 3 종 모두 0 건이면 백엔드가 고정 안내 메시지(200)를 `report`로 주므로 그대로 노출
     *   (프론트 안내를 또 붙이면 중복 → 백엔드 메시지만 표시). 백엔드 회신 §5 기준.
     */
    private fun buildMarkdown(r: AiReportApiClient.ComprehensiveReport): String {
        val sc = r.sourceCount
        // 전체 0 건: 백엔드 고정 안내(report)만 노출. 혹시 비면 폴백 문구.
        if (sc.diets == 0 && sc.sleeps == 0 && sc.exercises == 0) {
            return r.report.ifEmpty { ALL_EMPTY_FALLBACK }
        }

        val missing = buildList {
            if (sc.diets == 0) add("식단")
            if (sc.sleeps == 0) add("수면")
            if (sc.exercises == 0) add("운동")
        }
        if (missing.isEmpty()) return r.report

        val notice = buildString {
            append("> ⚠️ **기록이 없어 분석에서 제외된 항목**\n>")
            missing.forEach { append("\n> - $it 기록이 없습니다") }
        }
        return "$notice\n\n${r.report}"
    }

    private companion object {
        /** 전체 0 건인데 백엔드 report 마저 비어 온 경우의 안내(정상 응답이면 거의 안 쓰임). */
        const val ALL_EMPTY_FALLBACK =
            "## 종합 리포트\n\n아직 기록된 데이터가 없어요. 식단·수면·운동을 먼저 기록하면 AI가 분석해 드릴게요."
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
