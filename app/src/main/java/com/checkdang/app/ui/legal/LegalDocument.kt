package com.checkdang.app.ui.legal

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.checkdang.app.R

/**
 * 법적 문서 종류. [LegalDocumentActivity] 가 `EXTRA_DOCUMENT` extra 로 받아 분기한다.
 *
 * 새 문서(예: 위치 기반 서비스 약관, 마케팅 수신 동의 등) 가 추가될 때는 항목만 추가하면 된다.
 *
 * @param titleRes      Toolbar 제목
 * @param rawResId      `res/raw` 의 마크다운 원문 리소스
 * @param metaTextRes   메타 카드 본문 (버전/시행일/수정일 한 줄)
 * @param subtitleRes   메타 카드 부제 (선택, 0 이면 미표시)
 * @param showAgreementFooter Terms 전용 동의 footer 사용 가능 여부 (실제 노출은 `EXTRA_MODE` 도 함께 영향)
 * @param showContactFooter   Privacy 전용 문의 footer 노출 여부
 */
enum class LegalDocument(
    @StringRes val titleRes: Int,
    @RawRes val rawResId: Int,
    @StringRes val metaTextRes: Int,
    @StringRes val subtitleRes: Int,
    val showAgreementFooter: Boolean,
    val showContactFooter: Boolean
) {
    TERMS(
        titleRes            = R.string.legal_terms_title,
        rawResId            = R.raw.terms_of_service,
        metaTextRes         = R.string.legal_terms_meta,
        subtitleRes         = 0,
        showAgreementFooter = true,
        showContactFooter   = false
    ),
    PRIVACY(
        titleRes            = R.string.legal_privacy_title,
        rawResId            = R.raw.privacy_policy,
        metaTextRes         = R.string.legal_privacy_meta,
        subtitleRes         = R.string.legal_privacy_subtitle,
        showAgreementFooter = false,
        showContactFooter   = true
    );

    companion object {
        fun fromName(name: String?): LegalDocument =
            runCatching { valueOf(name ?: "") }.getOrDefault(TERMS)
    }
}
