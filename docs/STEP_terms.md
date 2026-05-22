# STEP — 이용약관 페이지 설계서

> **상태**: Plan 단계 (사용자 승인 대기)
> **작성일**: 2026-05-22
> **대상 기능**: 약관 원문 조회 화면 + 회원가입 흐름 약관 동의 (옵션)

---

## 0. 배경

현재 메뉴의 "이용약관"·"개인정보처리방침" 항목은 클릭 시 `"준비 중"` Toast 만 노출한다. 로그인 화면 하단의 약관 안내 캡션의 "이용약관"·"개인정보처리방침" SpannableString 도 동일하게 Toast 만 띄운다. v1.0 약관 본문이 확보되었으므로 본 STEP 에서 약관 조회 화면을 추가한다.

본 STEP 의 범위는 **약관 화면 구현**까지이며, 회원가입 흐름에서 약관 동의를 강제로 받는 부분은 별도 STEP 으로 분리할 수 있다.

---

## 1. 약관 데이터 저장 방식

### 검토한 옵션

| 옵션 | 위치 | 렌더링 | 장점 | 단점 |
|------|------|--------|------|------|
| A | `res/raw/terms_of_service.md` | Markwon | 마크다운 원문 보존, 가독성 ↑, 외부 링크 클릭 가능 | 의존성 1개 추가 (Markwon ~300KB) |
| B | `res/values/strings_terms.xml` | 단일 `TextView` | 의존성 0개 | 마크다운 문법이 그대로 노출됨, 가독성 ↓ |
| C | `assets/terms_of_service.html` | `WebView` | HTML 자유도 ↑ | `WebView` 무거움, JS/네트워크 보안 표면 ↑ |

### 결정 → **옵션 A** 채택

- 약관 본문은 마크다운 원본이므로 그대로 사용 가능
- Markwon 은 안드로이드 표준 `TextView` 위에서 동작 — 추가 `WebView` 없이도 풍부한 렌더링
- 이메일·URL 자동 링크화(`linkify` 플러그인) 가능 → 부가 요구사항 4-(2) 충족

---

## 2. 화면 구조

```
┌────────────────────────────────────┐
│  [←] 이용약관                       │  ← MaterialToolbar (parentActivityName)
├────────────────────────────────────┤
│  ┌──────────────────────────────┐  │
│  │ v1.0 · 시행일 2026.07.01      │  │  ← 메타 카드 (Caption)
│  │ 최종 수정 2026.06.15           │  │
│  └──────────────────────────────┘  │
│                                    │
│  # 체크당 서비스 이용약관           │  ← Markwon 렌더링 본문
│  ## 제1조 (목적)                    │     (NestedScrollView)
│  본 약관은 …                        │     setTextIsSelectable=true
│  ## 제2조 (용어의 정의)              │
│  …                                  │
│                                    │
└────────────────────────────────────┤
│  ☑ 위 약관에 동의합니다  [동의하기] │  ← footerAgreement (MODE_AGREEMENT 만)
└────────────────────────────────────┘
```

- 루트: `LinearLayout`(vertical) — 기존 `FamilyActivity` / `ProfileActivity` 와 동일 패턴
- 본문: `NestedScrollView` → `LinearLayout` → 메타 카드 + 본문 `TextView`
- 하단 sticky footer: `MODE_AGREEMENT` 일 때만 표시 (기본 `visibility=gone`)
- 다크 모드 미지원 — 앱 전체 라이트 전용 정책 유지

---

## 3. 진입 경로 2가지

| 모드 | 진입처 | 동작 |
|------|--------|------|
| `MODE_VIEW` | 메뉴 → 이용약관 | 본문 조회만, footer 숨김 |
| `MODE_VIEW` | 로그인 화면 캡션의 "이용약관" 클릭 | 본문 조회만, footer 숨김 |
| `MODE_AGREEMENT` (옵션) | 회원가입/온보딩 흐름 | footer 표시, "동의하기" 클릭 시 `RESULT_OK` 반환 |

두 경로에서 **같은 `TermsActivity`** 를 재사용하며, `EXTRA_MODE` 로 분기.

```kotlin
companion object {
    const val EXTRA_MODE      = "extra_mode"
    const val MODE_VIEW       = "view"
    const val MODE_AGREEMENT  = "agreement"
}
```

---

## 4. 부가 요구사항

| 요구사항 | 구현 방안 |
|----------|-----------|
| 본문 텍스트 선택/복사 | `binding.tvTerms.setTextIsSelectable(true)` |
| 외부 링크 (URL/이메일) 클릭 | Markwon `LinkifyPlugin.create()` — 자동 인텐트 처리 |
| 다크 모드 미지원 | 별도 야간 리소스 미작성, 라이트 컬러 고정 |
| 백 키 동작 | `parentActivityName=MainActivity` + `setNavigationOnClickListener` |
| 버튼 잘림 방지 | `insetTop/Bottom=0dp`, `paddingHorizontal=24dp`, `singleLine=true`, `minWidth=160dp` |

---

## 5. 수정/생성 파일

| 파일 | 종류 | 비고 |
|------|------|------|
| `app/build.gradle.kts` | 수정 | Markwon `core` + `linkify` 4.6.2 추가 |
| `res/raw/terms_of_service.md` | 신규 | v1.0 약관 원문 (UTF-8) |
| `res/layout/activity_terms.xml` | 신규 | Toolbar + 메타 카드 + 본문 + footer |
| `ui/legal/TermsActivity.kt` | 신규 | 모드 분기 + Markwon 로드 |
| `AndroidManifest.xml` | 수정 | `TermsActivity` 등록 (`parentActivityName=MainActivity`) |
| `ui/menu/MenuFragment.kt` | 수정 | 이용약관 메뉴 → `TermsActivity` (MODE_VIEW). 개인정보처리방침은 Toast 유지 (별도 STEP) |
| `ui/auth/login/LoginActivity.kt` | 수정 | 캡션 "이용약관" 클릭 → `TermsActivity` (MODE_VIEW). "개인정보처리방침" Toast 유지 |
| `CLAUDE.md` | 수정 | "약관 관리" 섹션 추가 (위치·갱신 절차·법무 검토 TODO) |

**제외 (별도 STEP)**:
- `TermsViewModel.kt` — 본 화면은 상태 없음 (read-only). 굳이 ViewModel 도입하지 않음
- `PrivacyPolicyActivity` — 동일 패턴이지만 본문 미확보, 후속 STEP
- `OnboardingActivity` 약관 동의 강제 — 화면 구현 이후 별도 STEP

---

## 6. 의존성 영향

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")
}
```

- Markwon 4.6.2 — 마지막 안정 버전. 라이선스 Apache 2.0
- APK 크기 영향: 약 +250KB (core ~150KB + linkify ~100KB)
- 기존 의존성과 충돌 없음 (MPAndroidChart / Coil / Health Connect 와 독립)

---

## 7. 자가 검증 체크리스트

| # | 항목 | 통과 조건 |
|---|------|-----------|
| 1 | 빌드 | `./gradlew assembleDebug` BUILD SUCCESSFUL |
| 2 | Markwon 의존성 | `app/build.gradle.kts` 에 `markwon` 2건 hit |
| 3 | 약관 원문 파일 | `res/raw/terms_of_service.md` 존재 + 100줄 이상 |
| 4 | 두 가지 모드 분기 | `MODE_VIEW`/`MODE_AGREEMENT` + footer visibility 분기 |
| 5 | 텍스트 선택 가능 | `setTextIsSelectable` 또는 `textIsSelectable` 1건 이상 |
| 6 | Manifest 등록 | `TermsActivity` + `parentActivityName` 존재 |
| 7 | 진입 경로 2곳 | `TermsActivity::class` 참조 2건 이상 (MenuFragment + LoginActivity) |
| 8 | 면책 조항 누락 | 약관 본문에 "의료기기"·"의학적 진단"·"119" 3개 키워드 모두 hit |

---

## 8. 시각 검증 (수동)

1. 메뉴 → 이용약관 → 약관 화면 진입, 마크다운 렌더링 정상
2. 로그인 화면 하단 "이용약관" 텍스트 클릭 → 약관 화면 진입
3. 약관 본문 길게 누름 → 선택/복사 가능
4. `support@checkdang.com` 탭 시 이메일 앱 실행
5. 뒤로가기 → 진입 화면으로 정상 복귀
6. 가독성: `lineSpacing` 충분, 조항 번호와 본문 구분 명확

---

## 9. 향후 작업 (Out-of-Scope)

- **PrivacyPolicyActivity** — 본 STEP 과 동일 패턴, 본문 확보 후
- **회원가입 흐름 약관 동의 강제** — `OnboardingActivity` 첫 단계에 `ActivityResultLauncher` 로 `MODE_AGREEMENT` 호출
- **버전 변경 시 재동의 플로우** — `UserStore` 에 "agreed_terms_version" 저장 후 갱신 비교, 버전 차이 시 강제 동의 화면. 출시 전 법무 검토 필요
- **버전 변경 이력 페이지** — 약관 본문 부칙을 별도 화면으로 분리

---
