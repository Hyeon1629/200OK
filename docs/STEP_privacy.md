# STEP — 개인정보처리방침 페이지 설계서

> **상태**: Plan 단계 (사용자 승인 대기)
> **작성일**: 2026-05-22
> **대상 기능**: 개인정보처리방침 조회 화면 + 보호책임자 문의 동선

---

## 0. 배경

이용약관(`TermsActivity`) 화면이 `res/raw/terms_of_service.md` + Markwon 으로 구현 완료된 상태. 메뉴/로그인 화면의 "개인정보처리방침" 항목은 `"준비 중"` Toast 만 노출 중. v1.0 처리방침 본문 확보로 화면 신규 구현.

---

## 1. 약관 vs 처리방침의 본질적 차이 (코드 영향)

| 항목 | 이용약관 | 개인정보처리방침 |
|------|---------|------------------|
| 법적 성격 | 계약 (동의 필요) | 법정 고지 (동의 불요) |
| 변경 시 | 재동의 가능 | 고지만 |
| 회원가입 흐름 | 동의 체크박스 필요 | 별도 동의 불요 (단 민감정보는 별도) |
| 화면 footer | "동의하기" 버튼 (`MODE_AGREEMENT`) | **footer 없음 + 문의 버튼만** |

→ **PrivacyPolicy 는 `MODE_AGREEMENT` 미사용**. 그 자리에 "개인정보 관련 문의하기" 버튼.

---

## 2. 공통 컴포넌트 추출 결정

### 검토한 옵션
| 옵션 | 내용 | 작업량 |
|------|------|--------|
| A. 단독 작성 | `PrivacyPolicyActivity` 신규, `TermsActivity` 그대로 | 적음 |
| B. 공통 base | `LegalDocumentActivity` + `enum LegalDocument` 분기, `TermsActivity` 제거 | 중간 |

### 차이 분석 (Terms vs Privacy)

| 항목 | 공통 / 차이 |
|------|------------|
| Toolbar (뒤로가기 + 제목) | 공통 (제목만 다름) |
| 메타 정보 카드 (버전/시행일) | 공통 (수치만 다름) |
| Markwon 본문 렌더링 | 공통 |
| `textIsSelectable` | 공통 |
| Footer | **차이** — Terms: 동의 버튼 / Privacy: 문의 버튼 |
| TablePlugin 사용 | **차이** — Terms 본문 표 0개, Privacy 본문 표 다수 → base 에 추가해도 Terms 무해 |

공통 ≈ 70%, 차이는 footer 한 곳. **footer 부분만 분기**하면 깔끔하게 공통화 가능.

### 권장: **옵션 B**

근거:
- footer 가 유일한 분기점이라 코드 복잡도 ↓
- 향후 "위치 기반 서비스 약관", "오픈소스 라이선스 고지" 등 추가 시 enum 항목만 추가
- Markwon 빌더 / TablePlugin 도 base 한 곳에서 관리
- `TermsActivity` 호출처 2곳(MenuFragment + LoginActivity) 마이그레이션 비용 작음

### 클래스 설계 (옵션 B)

```kotlin
enum class LegalDocument(
    val titleRes: Int,
    val rawResId: Int,
    val metaTextRes: Int,
    val showAgreementFooter: Boolean,   // Terms 에서만 true 가능
    val showContactFooter: Boolean      // Privacy 에서만 true
) {
    TERMS(   R.string.legal_terms_title,   R.raw.terms_of_service, R.string.legal_terms_meta,   true,  false),
    PRIVACY( R.string.legal_privacy_title, R.raw.privacy_policy,    R.string.legal_privacy_meta, false, true)
}

class LegalDocumentActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_DOCUMENT = "extra_document"          // "TERMS" / "PRIVACY"
        const val EXTRA_MODE     = "extra_mode"              // Terms 전용. PRIVACY 일 때는 무시
        const val MODE_VIEW      = "view"
        const val MODE_AGREEMENT = "agreement"
    }
    ...
}
```

`TermsActivity` 는 **삭제**하고 호출처를 `LegalDocumentActivity` 로 교체. (Thin wrapper 유지는 부채만 늘리므로 비채택)

---

## 3. 화면 구조

```
┌─────────────────────────────────────┐
│ [←] 개인정보처리방침                  │  ← Toolbar
├─────────────────────────────────────┤
│ ┌──────────────────────────────┐    │
│ │ v1.0 · 시행 2026.07.01        │    │  ← 메타 카드
│ │ 회사는 「개인정보 보호법」…     │    │
│ └──────────────────────────────┘    │
│                                     │
│ ## 제1조 (수집하는 개인정보 항목) │
│ ...                                 │  ← Markwon 본문
│ ## 제4조 (보유 및 이용 기간)        │
│ ┌────────────────────────┐          │
│ │ 항목 │ 보유기간 │ 사유 │          │  ← TablePlugin 으로 표 렌더링
│ └────────────────────────┘          │
│ ...                                 │
│                                     │
│      [📧 개인정보 관련 문의하기]    │  ← contact 버튼 (Privacy 전용)
└─────────────────────────────────────┘
```

- 루트: `LinearLayout(vertical)` (TermsActivity 와 동일 구조)
- 본문: `NestedScrollView` → 메타 카드 + 본문 + footer 영역
- Footer 영역: 한 컨테이너 안에 `agreementFooter` + `contactFooter` 두 그룹 두고, document/mode 에 따라 visibility 토글

---

## 4. 의존성 추가

```kotlin
// app/build.gradle.kts
implementation("io.noties.markwon:ext-tables:4.6.2")   // 신규 — 처리방침 표 렌더링
```

(`core`, `linkify` 는 이미 추가됨)

---

## 5. 진입 경로

| 진입처 | 인텐트 |
|--------|--------|
| 메뉴 > 이용약관 | `LegalDocumentActivity(TERMS, MODE_VIEW)` |
| 메뉴 > 개인정보처리방침 | `LegalDocumentActivity(PRIVACY)` (mode 무시) |
| 로그인 화면 캡션 "이용약관" | `LegalDocumentActivity(TERMS, MODE_VIEW)` |
| 로그인 화면 캡션 "개인정보처리방침" | `LegalDocumentActivity(PRIVACY)` |

---

## 6. 이메일 인텐트 처리 (Privacy 전용)

```kotlin
private fun openPrivacyContact() {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:privacy@checkdang.com")
        putExtra(Intent.EXTRA_SUBJECT, "[체크당] 개인정보 관련 문의")
    }
    runCatching { startActivity(intent) }.onFailure {
        Toast.makeText(
            this,
            "이메일 앱을 찾을 수 없어요. privacy@checkdang.com 으로 직접 보내주세요.",
            Toast.LENGTH_LONG
        ).show()
    }
}
```

- `resolveActivity` 대신 `runCatching` 사용 — `ACTION_SENDTO` 는 Android 11+ `<queries>` 가시성 제한이 있어 `resolveActivity` 가 false negative 를 낼 수 있음. 실제 호출 시점에 catch 가 더 안정적

---

## 7. 산출물 / 수정 파일

### 신규
| 파일 | 목적 |
|------|------|
| `res/raw/privacy_policy.md` | v1.0 처리방침 원문 (TODO(legal) 주석 포함) |
| `ui/legal/LegalDocumentActivity.kt` | 공통 base Activity |
| `ui/legal/LegalDocument.kt` | enum |
| `res/layout/activity_legal_document.xml` | 새 통합 레이아웃 (Terms layout 흡수) |
| `res/values/strings_legal.xml` | 제목/메타 문자열 분리 (다국어 확장 대비) |

### 삭제
| 파일 | 사유 |
|------|------|
| `ui/legal/TermsActivity.kt` | LegalDocumentActivity 로 통합 |
| `res/layout/activity_terms.xml` | activity_legal_document.xml 로 통합 |

### 수정
| 파일 | 변경 |
|------|------|
| `app/build.gradle.kts` | `ext-tables:4.6.2` 추가 |
| `AndroidManifest.xml` | `TermsActivity` 등록 제거 + `LegalDocumentActivity` 등록 |
| `ui/menu/MenuFragment.kt` | `menuTerms`/`menuPrivacy` 모두 `LegalDocumentActivity` 호출 |
| `ui/auth/login/LoginActivity.kt` | SpannableString 두 링크 모두 `LegalDocumentActivity` 호출 |
| `CLAUDE.md` | "약관 관리" 섹션 → "법적 문서" 로 확장. 두 문서 모두 다룸 |

---

## 8. 처리방침 본문 — 자리표시자 / TODO

원문 최상단에 다음 주석 삽입:

```markdown
<!--
  ⚠️ TODO(legal): 본 처리방침은 형식 템플릿입니다. 출시 전 법무 검토 필수.

  자리표시자 항목 (출시 전 실값 교체 필요):
  - 회사명 "(주)체크당"
  - 이메일 "privacy@checkdang.com"
  - 보호책임자 성명/직책 (제12조)
  - 백엔드 인프라 제공자 (제6조 위탁 표)
-->
```

---

## 9. 의도적으로 하지 않는 것

- **민감정보 별도 동의 화면** — 「개보법」 제23조 별도 동의 의무는 존재하지만, 본 STEP 은 *문서 조회 화면* 구현. `OnboardingActivity` 의 신규 동의 단계는 별도 STEP
- **데이터 이동 요구권 (제9조 1항 데이터 다운로드)** — 화면 메뉴 안내만 본문에 존재, 실제 export 기능은 별도 STEP
- **다국어 (영문 처리방침)** — strings 분리 단계까지만, 실제 번역 본문은 출시 후

---

## 10. 자가 검증 체크리스트

| # | 항목 | 통과 조건 |
|---|------|-----------|
| 1 | 빌드 | `./gradlew assembleDebug` BUILD SUCCESSFUL |
| 2 | Markwon 의존성 | `app/build.gradle.kts` 에 `markwon` 3건 (`core`/`linkify`/`ext-tables`) |
| 3 | 원문 파일 + 분량 | `privacy_policy.md` 존재, 200줄 이상 |
| 4 | 법적 주의 주석 | 원문에 `TODO(legal)` hit |
| 5 | 11개 필수 항목 | "수집 항목"·"이용 목적"·"보유 기간"·"제3자 제공"·"위탁"·"국외 이전"·"파기"·"권리"·"안전성"·"보호책임자"·"침해 구제" 각각 hit (각 키워드 1건 이상) |
| 6 | 민감정보 조항 | "민감정보" 또는 "건강정보" hit (제3조) |
| 7 | 텍스트 선택 가능 | `textIsSelectable` 또는 `setTextIsSelectable` hit |
| 8 | 이메일 fallback | `runCatching` + Toast 안내 둘 다 존재 |
| 9 | 진입 경로 4곳 | `LegalDocumentActivity::class` 참조 — MenuFragment 2건 + LoginActivity 2건 = 최소 4건 (또는 인텐트 헬퍼로 추출 시 2건+) |
| 10 | TablePlugin 활성화 | `TablePlugin.create` 호출 1건 |
| 11 | 기존 TermsActivity 마이그레이션 완료 | `TermsActivity::class` 잔존 참조 0건 |

---

## 11. 시각 검증 (수동)

1. 메뉴 > 이용약관 → 화면 진입, 마크다운 렌더링 정상 (회귀 검증)
2. 메뉴 > 개인정보처리방침 → 화면 진입
3. 로그인 화면 캡션 "이용약관"/"개인정보처리방침" 각각 클릭 → 해당 화면 진입
4. **본문 표 (제4조 보유 기간, 제6조 위탁, 제7조 국외 이전 등) 정상 렌더링** — 깨지면 TablePlugin 재확인
5. 본문 길게 누름 → 선택/복사
6. 본문 내 `privacy@checkdang.com` 탭 → 이메일 앱 (LinkifyPlugin)
7. 하단 "개인정보 관련 문의하기" 버튼 → 이메일 앱 (수동 인텐트)
8. 이메일 앱 없는 환경에서 → Toast 안내

---

## 12. 사용자 결정 요청

| # | 결정 항목 | 옵션 | 권장 |
|---|----------|------|------|
| Q1 | 공통 컴포넌트 추출 | A. 단독 작성 / B. `LegalDocumentActivity` 통합 + Terms 마이그레이션 | **B** |
