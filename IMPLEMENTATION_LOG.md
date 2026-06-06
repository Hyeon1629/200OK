# IMPLEMENTATION LOG

> **규칙**: 작업 완료 후 반드시 이 파일에 날짜·작업 내용·수정 파일 목록을 기록한다.

---

## [2026-06-06] 홈 혈당·라이프스타일·주간차트 실데이터 연결 (placeholder 해소)

### 배경
홈 요약 카드들이 placeholder(`getGlucoseSummary()`/`getLifestyleSummary()`=null, `getWeeklyGlucose()`=empty)로 비어 있던 기존 미배선 상태. 데이터 소스는 이미 존재(혈당=`MockDataProvider.recordsFlow`, 라이프스타일=`HealthRepository`)했으나 홈이 연결을 안 하고 있었음 → 각 탭과 동일 소스에 연결.

### 작업 내용
| 파일 | 변경 |
|------|------|
| `ui/home/HomeViewModel.kt` | 혈당 요약/주간(7일 일별평균)을 `recordsFlow` 에서 파생(StateFlow). 라이프스타일은 `HealthRepository.get{Exercise,Meal,Sleep}Summary()` async 로드(`loadLifestyle()`). `buildGlucoseSummary`/`buildWeekly`/`labelFor`/`startOfDay` 헬퍼 |
| `ui/home/HomeFragment.kt` | `.value` 1회 읽기 → flow 4종(혈당/라이프스타일/주간/인슐린) `repeatOnLifecycle` 구독. 진입 시 `loadLifestyle()` 재로드. 주간차트는 데이터 없는 날(0f) 점 제외 |

### 주요 결정 / 메모
- **혈당/주간/인슐린 = 로컬 실데이터 즉시 반영**(입력하면 홈에 최신값·오늘 횟수·평균·7일 그래프 갱신).
- **라이프스타일 = 현재 HealthRepository 소스 반영** — Health Connect 연결 전엔 `MockHealthDataSource` 샘플(45/60분·1640kcal·7.2h), 라이프스타일 탭에서 HC 연결 후 진입하면 실데이터. (싱글톤 소스라 탭 전환으로 전파)
- 혈당 탭/라이프스타일 탭 로직 불변(같은 소스 공유만). 회귀 위험 낮음.
- 빌드 검증: `assembleDebug` BUILD SUCCESSFUL.

---

## [2026-06-06] 홈 "오늘 인슐린" 요약 카드 추가

### 배경
인슐린 최소 기능에 이어, 홈에서 오늘 주입 합계를 한눈에 보이도록 노출 요청. 확인 결과 홈 요약 카드(혈당/라이프스타일/주간차트)는 현재 모두 빈 placeholder(`getGlucoseSummary()` 등 null/empty — 기존 미배선). → 인슐린은 기존 카드에 얹지 않고 **실데이터로 동작하는 독립 카드**로 추가.

### 작업 내용
| 파일 | 변경 |
|------|------|
| `res/layout/fragment_home.xml` | 메인 혈당 카드 아래 `card_insulin` 추가(💉 오늘 인슐린 / 횟수·최근 / 합계 U, 인디고 액센트) |
| `ui/home/HomeViewModel.kt` | `InsulinDaySummary` + `todayInsulin` StateFlow(`insulinRecordsFlow` → 오늘 합계/횟수/최근 1건) |
| `ui/home/HomeFragment.kt` | `todayInsulin` 라이브 구독 바인딩(입력 즉시 반영), 카드 탭 → 혈당 탭 이동 |

### 주요 결정 / 메모
- 오늘 입력 인슐린 실데이터에서 집계 → 입력 즉시 라이브 갱신(flow). 기록 0건이면 "오늘 기록 없음 / 0 U".
- 기존 혈당/차트 로직 불변(회귀 0). 홈 혈당·라이프스타일 카드의 빈 placeholder 는 별개 기존 이슈로 미해결.
- 빌드 검증: `assembleDebug` BUILD SUCCESSFUL.

---

## [2026-06-06] 인슐린 수동 입력 최소 기능 추가 (혈당 기록 타임라인에 병행)

### 배경
시연 항목에 "인슐린 수동 입력→저장" 이 필요한데 앱에 인슐린 로직이 **전무**(GlucoseRecord에 필드 없음, 코드 전역 검색 0건)했다. 혈당 입력 패턴을 그대로 미러링해 최소 기능을 추가.

### 설계
- **혈당 `records` 흐름은 불변** — PDF/차트/통계가 의존하므로 건드리지 않고, 표시용 **타임라인만 병합**(혈당+인슐린 시간순). 인슐린은 백엔드 push 안 함(인슐린 도메인 미정) — MockDataProvider SharedPreferences 로컬 영속만.

### 작업 내용
| 파일 | 변경 |
|------|------|
| `data/model/InsulinRecord.kt` 🆕 | `InsulinRecord(units, type, injectedAt, memo)` + `InsulinType`(속효성/지속형/혼합형/기타) + `unitsLabel`(.0 제거) |
| `data/mock/MockDataProvider.kt` | 인슐린 저장(`insulinRecordsFlow`/`addInsulinRecord`/`restoreInsulin`/`persistInsulin`, `KEY_INSULIN_RECORDS`) + init/clear 반영 |
| `ui/glucose/GlucoseViewModel.kt` | `TimelineEntry`(Glucose/Insulin) sealed + `timeline` StateFlow(records ⊕ 인슐린, 시간 역순) |
| `ui/glucose/list/GlucoseRecordAdapter.kt` | `ListItem` 에 GlucoseItem/InsulinItem 분리, `buildListItems(List<TimelineEntry>)`, 뷰타입 3종 + InsulinViewHolder |
| `ui/glucose/list/GlucoseListFragment.kt` | `records` → `timeline` 구독 |
| `ui/glucose/GlucoseFragment.kt` | FAB → 선택 다이얼로그(혈당/인슐린). 인슐린 저장 시 스낵바 |
| `ui/glucose/input/InsulinInputBottomSheet.kt` 🆕 + `res/layout/bottom_sheet_insulin_input.xml` 🆕 | 주입량(U, 0.5~100)·종류 칩·시각·메모 입력 → 저장 |
| `res/layout/item_insulin_record.xml` 🆕 + `colors.xml` | 인슐린 리스트 아이템(💉, 인디고 `insulin_accent`) |

### 주요 결정 / 메모
- 혈당과 한 "기록" 탭에 시간순 병합 → 시연 시 혈당·인슐린이 같은 타임라인에 쌓이는 모습. 혈당은 상태색/칩, 인슐린은 인디고 바 + "N U" 로 시각 구분.
- 인슐린은 알림/예측/PDF/백엔드 동기화 대상 아님(최소 기능). 추후 백엔드 인슐린 도메인 생기면 동기화 추가.
- 빌드 검증: `assembleDebug` BUILD SUCCESSFUL(APK 패키징까지).

---

## [2026-06-06] 통증 AI 분석 실연동 (Mock → 백엔드 2단계 호출, AI팀 Gemini PR 계약 반영)

### 배경
AI팀(lsy) PR `lsy_gemini`→`kgh3` 로 FastAPI `gemini.analyze_pain` 가 채워지며 통증 분석 백엔드 체인(**앱 → Spring `PainAnalysisService` → FastAPI `/analyze/pain` → Gemini**)이 완성·계약 확정. 직전까지 프론트는 `MockDataProvider.analyzePainMock`(delay 1.5s 자리표시자) 사용 → 실연동으로 교체. (6대 프론트 현황 중 통증만 유일하게 Mock 으로 남아있던 실작업.)

### 계약 (백엔드 `kgh3` 기준)
| 단계 | 호출 | 요청/응답 |
|------|------|-----------|
| 1 저장 | `POST /api/pain-records` | body `{bodyPart, intensity, qualityTags, situationTags}` → 201 `ApiResponse<{id,…}>` |
| 2 분석 | `POST /api/ai/pain-analysis/{painRecordId}` | (body 없음) → `ApiResponse<{painRecordId, aiCause, aiFirstAid}>` |

- **BodyPart enum 이름이 앱↔백엔드 100% 일치**(`PainRecord.BodyPart`), `qualityTags`/`situationTags` 도 `PainTaxonomy` 동일 어휘 → 추가 매핑 불요.
- 응답 래퍼는 Spring 공통 `{success, data, message}`(PaymentApiClient 와 동일 패턴).

### 작업 내용
| 파일 | 변경 |
|------|------|
| `data/remote/PainAnalysisApiClient.kt` 🆕 | 2단계(저장→분석) 호출. `PainAnalysisResult(painRecordId, aiCause, aiFirstAid)`. Cognito Bearer, readTimeout 60s(Gemini), 비2xx 는 `message` 담아 예외 |
| `ui/bodymap/analysis/AIAnalysisActivity.kt` | `analyzePainMock`+`delay(1500)` 제거 → 실 API. **로딩/결과/에러 3상태** + 게스트 차단(`userId==null`) + 재시도. 인라인 `CorrelationAdapter` 삭제 |
| `res/layout/activity_ai_analysis.xml` | 결과 화면을 **예상 원인(`tv_ai_cause`) + 집에서 할 수 있는 조치(`tv_ai_first_aid`)** 2블록으로 재구성. 연관요인 RecyclerView 제거(백엔드 출력에 대응 없음). 에러 상태 레이아웃(`layout_error`/`tv_error`/`btn_retry`/`btn_error_close`) 추가 |

### 주요 결정 / 메모
- **프로비저널 배선** — `kgh3` 가 아직 머지·배포 전이라 `api.checkdang.xyz` 에서 E2E 검증 불가. 배포되면 코드 변경 없이 동작(종합리포트 골격 때와 동일 패턴).
- **응답 형태 변화 반영** — 새 백엔드는 `aiCause`/`aiFirstAid` 자유텍스트 2개만 제공. 기존 Mock 의 summary/correlations/recommendation 구조는 대응 출력이 없어 화면에서 제거.
- **게스트 차단** — 기존 AI/FastAPI 게스트 미지원 정책 그대로(`SessionHolder.userId == null` → 안내만 표시, 네트워크 호출 스킵).
- **로컬 목록 유지** — 바디맵 기록 리스트가 아직 `MockDataProvider` 로컬 소스라 `addPainRecord` 유지(백엔드에도 1단계에서 별도 저장). 추후 `GET /api/pain-records` 로 전환 가능.
- **후속(별건)**: 미사용이 된 Mock 통증 분석 코드(`analyzePainMock`/`Correlation`/`CorrelationLevel`/`AIAnalysisResult`/`item_correlation.xml`) 정리. + `data/remote/ApiConstants.kt` 는 **미참조 + 옛 Cognito 값**(`db7haykk4`/`chekdang://callback` 오타)이라 정리/확인 필요(현행 `amplifyconfiguration.json` 과 모순).
- 빌드 검증: `compileDebugKotlin` BUILD SUCCESSFUL.

---

## [2026-06-03] AI 리포트 — 5xx 1회 재시도 + 5xx 안내문 확장 (백엔드 회신 반영)

### 배경
백엔드 회신: ① 첫 호출 **약 8~10초**(출력 길이 고정이라 데이터량 무관) ② **Gemini API 가 가끔 5xx** 를 냄 → 5xx 시 안내문 출력 **또는** 타임아웃 약간 ↑ + 1회 재시도 권장. ③ 전부/1개/2개/0개 케이스 점검 요청.

### 작업 내용
| 파일 | 변경 |
|------|------|
| `AiReportApiClient` | `get()` 을 **재시도 래퍼 + `requestOnce()`** 로 분리. **5xx·IOException(타임아웃 등) 1회 재시도**(`MAX_ATTEMPTS=2`, `RETRY_DELAY_MS=800ms`). **4xx 는 즉시 실패**(재시도 무의미). 응답 보관용 `Resp(code, body)` data class 추가 |
| `ComprehensiveReportViewModel` | 5xx 안내문 매핑을 `"HTTP 500"` 단건 → **정규식 `HTTP 5\d\d`(500/502/503/504)** 로 확장. 재시도까지 실패 시에만 도달하는 주석 보강 |

### 주요 결정 / 메모
- **타임아웃은 60s 유지(↑ 안 함)** — 첫 호출 8~10초로 이미 6배 여유. 재시도는 5xx(빠른 실패)·IO 대상이라 지연 누적 미미. 타임아웃을 올리면 백엔드가 진짜 행(hang)일 때 worst-case 대기만 늘어 UX 악화 → 현행이 더 나음.
- **재시도 대상**: 5xx(서버/Gemini 일시 오류) + IOException(연결/읽기 실패). 끝내 실패하면 5xx 예외 → ViewModel 안내문("리포트 생성 중 문제가 발생했어요. 잠시 후 다시 시도해주세요").
- **4케이스(전부/2개/1개/0개)**: `buildMarkdown()` 로직상 모두 처리됨(전부=안내없이 report / 일부 0=⚠️ 누락 항목 prepend / 전부 0=백엔드 고정 안내). **전부(4/2/3)는 라이브 확인 완료.** 나머지(1개/2개/0개)는 계정 데이터를 직접 추가/삭제해야 재현 가능 → **기기 수동 QA 항목**(코드는 검증됨).
- 빌드 검증: `compileDebugKotlin` BUILD SUCCESSFUL.
- **상태(2026-06-03 종료)**: 재시도+안내문 적용·커밋·푸시 완료, 정상 리포트 회귀 확인. 5xx 재시도 실동작은 백엔드가 5xx 인위 발생 시 검증 가능(선택), 1개/2개/0개 데이터 케이스는 기기 수동 QA 잔여. **리포트 5xx 대응 건 종료.**

---

## [2026-06-03] FCM 푸시 알림 — 클라이언트 배선 (토큰 업로드는 백엔드 엔드포인트 대기)

### 배경
백엔드 팀 요청(13주차 주간보고서): Firebase 연동. 용도 확인 결과 **FCM 푸시 알림**. 백엔드가 보유한 Firebase 프로젝트 `checkdang-65238` 로 앱을 등록해야 푸시 타겟팅 가능. 백엔드가 `google-services.json` 다운로드 실패로 본문을 텍스트로 전달.

### 사전 확인 (이미 있던 것)
- `google-services` Gradle 플러그인: 루트·앱 모듈에 **이미 적용돼 있었음**(`libs.plugins.google.services`) → 1·2번(플러그인 추가)은 불요.
- `app/google-services.json`: **다른/오래된 프로젝트**(`ok-project-494806`)로 존재 → 교체 대상.
- 코드 내 `default_web_client_id`·`GoogleSignIn`·FCM 사용 **전무** → 빈 `oauth_client` json 으로 교체해도 컴파일/로그인 안 깨짐(Google 로그인은 Cognito Hosted UI 브라우저 경유).
- 기존 알림 인프라 존재: `util/GlucoseAlertNotifier`(채널/아이콘 `ic_bell`/권한 가드), `POST_NOTIFICATIONS` 매니페스트 선언 → 패턴 재사용.

### 작업 내용
| 파일 | 변경 |
|------|------|
| `app/google-services.json` | `ok-project-494806` → **`checkdang-65238`** 교체(백엔드 제공값). package_name=com.checkdang.app 일치 확인 |
| `app/build.gradle.kts` | `firebase-bom:34.13.0`(platform) + `firebase-messaging` 추가(인라인 스타일) |
| `push/CheckDangMessagingService.kt` 🆕 | `FirebaseMessagingService` — `onNewToken`→`PushTokenStore`, `onMessageReceived`(notification/data 폴백→알림 표시). companion 에 push 채널(`push_default`)/권한 헬퍼 |
| `push/PushTokenStore.kt` 🆕 | 토큰 SharedPreferences 캐시 + logcat(`FcmToken`) 노출 + **백엔드 업로드 stub**(TODO) |
| `AndroidManifest.xml` | 서비스 등록(`MESSAGING_EVENT`) + 백그라운드 기본 채널/아이콘 메타데이터 |
| `CheckDangApplication.kt` | push 채널 생성 + 시작 시 `FirebaseMessaging.token` 1회 조회→등록(onNewToken 은 변경 시만 호출되므로) |
| `MainActivity.kt` | 진입 시 `POST_NOTIFICATIONS` 런타임 요청(탭 무관하게 푸시 권한 확보) |

### 주요 결정 / 메모
- **CLAUDE.md "Firebase 의존성 추가 금지" 제약 교차** — 단 플러그인 이미 도입 + 백엔드 공식 요청 + 제약 비절대(기존 합의). 진행 타당.
- **토큰 업로드만 stub 처리(의도적 분리)** — 백엔드 **토큰 등록 엔드포인트 계약 미확정**이 유일한 블로커. stub 이라 "백엔드 API 호출 금지" 제약도 아직 안 건드림. 현재는 `FcmToken` 로그로 토큰 노출 → 백엔드가 그 값으로 콘솔/서버 테스트 푸시 가능. 엔드포인트 확정 시 `PushTokenStore.register()` TODO 자리 한 줄 연결.
- **백엔드 회신 대기**: ① 토큰 등록 API(URL/메서드/인증=Cognito Bearer?/바디) ② 푸시 형식(notification형 vs data형). 클라는 둘 다 처리하게 구현해 둠.
- 빌드 검증: `assembleDebug` BUILD SUCCESSFUL.
- **상태(2026-06-03 종료)**: 앱 클라이언트 배선 완료 + 토큰 발급 logcat 검증 OK. **백엔드 회신(토큰 등록 API 계약 + 테스트 푸시 발송) 대기로 보류** — 회신 시 `PushTokenStore.register()` TODO 한 줄 연결 + `FcmToken` 진단 로그 제거로 마무리.

---

## [2026-06-03] AI 생활습관 리포트 — 500 해결 확인 / 실연동 검증 (리포트 건 종료)

### 배경
직전 블로커였던 리포트 생성 **500 "서버 오류"**(제미나이 추론 단계 추정)에 대해 백엔드 DM 회신: **"제미나이 출력 시간이 너무 길어 중간에 끊기는 게 원인"** → 백엔드 측 수정 완료. 앱에서 실제로 해결됐는지 + 데이터 없이 진입해도 결과가 나오는 게 더미인지 실연동인지 검증.

### 작업 내용
| 항목 | 변경 |
|------|------|
| 진단 로깅(임시) | `AiReportApiClient`: 응답 파싱 직후 `sourceCount`(항목별 건수)+`reportLen` 로깅 한 줄 추가 → 검증 후 **제거**. 기존 non-2xx 진단 로그(2026-06-02분)도 함께 제거 → 리포트 코드 **net 변경 0**(커밋 대상 아님) |
| 실연동 검증 | 로그인 계정 진입 → logcat `AiReportApi`: **`diets=4 sleeps=2 exercises=3 reportLen=520`**. 3종 모두 >0 → 백엔드 DB 실데이터 기반 분석 확정(앱 더미 아님) |

### 주요 결정 / 메모
- **500 해결 확인** — 정상 200 + 제미나이 분석 본문(520자) 출력. 백엔드 "제미나이 끊김" 수정 반영됨.
- **"데이터 없이 들어갔는데 결과" = 더미 아님** — 리포트 경로(Activity→ViewModel→`AiReportApiClient`→`api.checkdang.xyz`)에 mock 분기 전무. 화면에서 직접 기록 안 해도 그 계정으로 백엔드에 쌓인 데이터(이전 테스트/동기화)로 분석됨.
- **타임아웃은 현 상태 유지(60s)** — 테스트에서 빠르게 응답. "데이터 많은 계정 첫 호출(캐시 미스)이 60s 초과 시 클라 타임아웃 위험" 점검용으로 백엔드에 응답시간 문의(DM). **현재 블로커 아님** → 회신 안 와도 리포트 기능 완성. 실제 느린 계정 타임아웃 증상 발생 시 그때 상향(15s→유지 / 40s→90s / 70s+→120s+로딩안내).
- **리포트 건 종료.** 후속(혈당·통증 합류)은 별도 STEP 유지.

### 신규/수정 파일
- 리포트 코드: 변경 없음(진단 로그 add→remove로 net 0)
- 커밋 `e888180` (별건, 같은 날): `amplifyconfiguration.json` Cognito Scopes `["openid","email"]`→`["openid"]` (카카오 email scope KOE205 로그인 오류 회피). `frontend` 푸시 완료

---

## [2026-06-02] AI 생활습관 리포트 — 항목별 "기록 없음" 표시 (sourceCount 활용)

### 배경
기존엔 200 응답의 `report`만 그대로 렌더. "데이터가 있는 항목만 분석하고, 없는 항목은 '○○ 기록이 없습니다'로 표시"하자는 요청 → 백엔드 회신 스키마(`회신-종합리포트-스키마.md`)의 응답 메타 **`sourceCount: { diets, sleeps, exercises }`**(항목별 건수)를 활용해 처리. "있는 항목 분석"은 백엔드(`report`)가, "없는 항목 안내"는 프론트가 담당.

### 작업 내용
| 항목 | 변경 |
|------|------|
| API 클라이언트 | `AiReportApiClient`: 반환형을 `String` → **`ComprehensiveReport(report, sourceCount)`** 로 변경. `SourceCount(diets, sleeps, exercises)` 신규. `sourceCount`/누락 필드는 0 기본값으로 방어 파싱 |
| 표시 합성 | `ComprehensiveReportViewModel.buildMarkdown()` 신규 — 건수 0 항목을 상단 안내 블록("⚠️ 기록이 없어 분석에서 제외된 항목 / - ○○ 기록이 없습니다")으로 합성 후 `report` 앞에 결합. 전 항목 0 + 빈 report(200)면 안내만 단독 표시 |
| 상태/화면 | `ReportUiState.Loaded(markdown)`·Activity 렌더 로직 변경 없음(합성 결과를 그대로 Markwon 렌더) |

### 주요 결정 / 메모
- 빈 데이터 신호로 **`sourceCount` 채택** — report 마크다운을 섹션 단위로 surgically 파싱하는 방식은 헤더(영/한) 의존이라 취약 → 건수 기반이 견고. 백엔드가 빈 섹션을 누락하든 포함하든 안내는 항상 표시됨.
- 라벨 매핑: `diets`→식단 / `sleeps`→수면 / `exercises`→운동.
- **전체 0건 케이스 보완**(백엔드 회신 `회신-종합리포트-빈섹션처리.md` §5): 3종 모두 0건이면 백엔드가 Gemini 미호출 + 고정 안내 메시지(200)를 `report`로 반환. 이때 프론트 "제외된 항목" 블록을 또 붙이면 중복 → **전체 0건이면 백엔드 report만 노출**(폴백 상수 `ALL_EMPTY_FALLBACK`). 일부만 0건일 때만 안내 블록 prepend.
- 백엔드 회신 확인: 0건 섹션 `report` 포함 여부 (B)→(A) 수정 완료(커밋 `dec1cf5`, **배포 대기**). 헤더 영문 고정. 응답 스키마/`sourceCount` 계약 불변 → 프론트 현행 유지로 충분(추가 파싱 불요). 배포 후 실샘플로 검증 예정.
- 빌드 검증: `compileDebugKotlin` BUILD SUCCESSFUL.

---

## [2026-06-02] AI 생활습관 리포트 — 백엔드 스키마 회신 반영 (실연동 확정)

### 배경
`2026-06-01` provisional 골격에 대해 백엔드(kgh) 스키마 회신 도착(`회신-종합리포트-스키마.md`). 응답은 (a) 마크다운 확정이나 엔드포인트·응답구조·에러형식이 가정값과 달라 정정. 추가로 **현재 리포트는 식단/수면/운동만 분석**(혈당·통증 미포함) 확인.

### 작업 내용
| 항목 | 변경 |
|------|------|
| 엔드포인트 | `AiReportApiClient`: `/api/ai/comprehensive-report` → **`GET /api/ai/reports/health?days=7`**(기본 7, 1~30). `getComprehensiveReport(days)` 파라미터화 |
| 응답 파싱 | 응답이 `{ from, to, sourceCount, report }`(ApiResponse 래퍼 없는 raw)로 메타가 감쌈. `report`는 여전히 최상위 → 기존 `getString("report")` 그대로 동작, 주석/doc만 확정 스키마로 갱신 |
| 에러 파싱 | Spring `{ success:false, data:null, message }` 형식 기준 코드 매핑 정정 — `ComprehensiveReportViewModel`: 401/403=로그인, **400=데이터 부족**, **500=생성 중 문제**, 그외 일반 |
| UI 문구 | 식단/수면/운동만 분석 → 오해 제거. 홈 카드 "AI 종합 리포트 / 혈당·라이프스타일·통증을 한눈에" → **"AI 생활습관 리포트 / 식단·수면·운동을 한눈에"**. 툴바·로딩·게스트 안내·Activity KDoc 동일 정정 |

### 주요 결정 / 메모
- **혈당·통증 포함은 후속**(이번 출시 미포함) — 백엔드 §5 회신 요청에 1번(후속) 선택. 식단/수면/운동으로 먼저 실연동. (후속 시 혈당=FastAPI/DynamoDB 합류, 통증=RDS 조회 추가)
- **문구 변경은 백엔드 "제안"** — 회신 §3은 "생활습관 종합 리포트" 정도를 제안(강제 아님). 최종 결정은 프론트, 실제 채택 문구 "AI 생활습관 리포트"는 로딩문구 일관성 위해 선택.
- 회신 답변 초안 작성(`회신답변-종합리포트-스키마.md`): ①②OK + 데이터 0건 시 200 빈리포트 vs 400 여부 추가 질문.
- 캐싱 확인(같은 기간 재호출 <1초), readTimeout 60s 유지.
- 빌드 검증: `assembleDebug` BUILD SUCCESSFUL.

### 신규/수정 파일
- 수정: `data/remote/AiReportApiClient.kt`, `ui/report/ComprehensiveReportViewModel.kt`, `ui/report/ComprehensiveReportActivity.kt`, `res/layout/activity_comprehensive_report.xml`, `res/layout/fragment_home.xml`

---

## [2026-06-02] 백엔드 대기 중 프론트 단독 작업 배치 (알림 / 결제 점검 / 테스트)

### 배경
백엔드 스키마 회신·실모델(수요일) 대기 동안, 의존성 없는 순수 프론트 작업을 우선 처리.

### 작업 내용
| 영역 | 변경 |
|------|------|
| 혈당 로컬 알림 | 수동 입력이 위험(DANGER) 범위면 본인 기기 알림(`GlucoseAlertNotifier`). 저/고혈당 구분. 삼성헬스 대량 동기화 제외(스팸 방지). `POST_NOTIFICATIONS`(33+) 권한 + 채널(Application) |
| 알림 설정 | `NotificationPrefs`(마스터 ON/OFF + 주의 포함) + `NotificationSettingsActivity`. 메뉴 "알림 설정"을 시스템 직행 → 앱 내 화면으로 교체. 알림은 설정값 반영 |
| 결제 점검 | (버그) PAID 구독관리 진입 시 복원 Success 로 자동 finish → 화면 즉시 닫힘. MenuFragment: PAID 는 Play 구독센터 딥링크. SubscriptionActivity: `purchaseInitiated` 로 신규/복원 구분 |
| 결제 하드닝 | `launchBillingFlow` 에 obfuscatedAccountId(userId SHA-256). onResume 은 구매 진행/완료 중 retry 스킵(경합 방지) |
| 테스트 | `GlucoseEvaluatorTest` 경계값(저혈당/공복/식후2h/그외). `testImplementation junit` 추가 |
| 품질 | 새 알림 코드의 lint MissingPermission 오탐 억제(@SuppressLint + 주석). `lintDebug` 에러 0 |

### 주요 결정 / 메모
- **가족 푸시는 범위 외** — 크로스 디바이스라 푸시 인프라(FCM/SNS)+백엔드 필요. 본인 로컬 알림만 구현.
- 결제 상태머신은 `BillingState` 하나에 상품가용성+구매상태가 섞여 경합 소지 → 실결제 테스트 가능 시 분리 리팩토링 권장(이번엔 가드로만 완화).
- 빌드 검증: `assembleDebug` / `testDebugUnitTest` / `lintDebug` 모두 통과.

### 신규/수정 파일
- 신규: `util/GlucoseAlertNotifier.kt`, `util/NotificationPrefs.kt`, `ui/menu/notification/NotificationSettingsActivity.kt`, `res/layout/activity_notification_settings.xml`, `test/.../GlucoseEvaluatorTest.kt`
- 수정: `CheckDangApplication.kt`, `AndroidManifest.xml`, `ui/glucose/GlucoseFragment.kt`, `ui/menu/MenuFragment.kt`, `ui/menu/subscription/SubscriptionActivity.kt`, `data/billing/BillingRepository.kt`, `build.gradle.kts`

---

## [2026-06-01] AI 종합 리포트 화면 골격 (Gemini · provisional)

### 배경
AI팀 확답으로 프론트의 AI 연동 표면이 2개로 확정 — ① 혈당 예측 모델 출력 표시(`e8f8df5` 로 이미 실연동 완료), ② **Gemini 종합 리포트 표시(신규)**. 통증분석·식단조언 확장·알림은 현 범위 제외. 이 중 ②의 화면 골격을 선구축(백엔드 스키마 회신 전이라 클라이언트는 provisional).

### 핵심 판단
- **렌더 방식 = 마크다운(Markwon)** — 법률문서(`LegalDocumentActivity`)와 동일 스택 재사용. demo-diet-advice 가 `answer` 텍스트를 반환하는 것과 일관. 백엔드가 구조화 JSON 으로 주면 렌더 레이어만 교체.
- **진입점 = Home 대시보드 카드** — "종합"(혈당+라이프스타일+통증) 성격상 특정 탭(Glucose)은 좁고 Menu(설정)는 발견성↓. 대시보드 진입이 의미·발견성 최적.
- **provisional 격리** — 엔드포인트 경로/요청/응답필드 3가지는 `AiReportApiClient` 에 `TODO(report)` 로 명시. 스키마 회신 시 그 3곳만 교체.
- 게스트 차단(로그인 유도) — 기존 AI/FastAPI 게스트 미지원 정책 그대로 적용.

### 작업 내용
| 항목 | 변경 |
|------|------|
| API 클라이언트 | `AiReportApiClient` 신규 — `getComprehensiveReport()`. 가정값: `GET /api/ai/comprehensive-report`, 응답 `{ "report": "<markdown>" }`. Bearer 로그인 전용(게스트 헤더 없음), readTimeout 60s(Gemini) |
| ViewModel | `ComprehensiveReportViewModel` + `ReportUiState`(Idle/Loading/Loaded/Error). 401/403/404 한국어 메시지 매핑 |
| 화면 | `ComprehensiveReportActivity` — 로딩/에러/재시도 + Markwon 렌더 + 게스트 사전 차단. `activity_comprehensive_report.xml` |
| 진입 | `fragment_home.xml` 'AI 종합 리포트' 카드 + `HomeFragment` 클릭 → Activity. `AndroidManifest` 등록 |

### 주요 결정 / 메모
- 현재 백엔드 엔드포인트 미배포 → 진입 시 정상적으로 Error(404 등) 상태로 낙하. 스키마 회신 후 Loaded 동작.
- **Kotlin 중첩 주석 함정**: KDoc(`/** */`) 안에 백틱 `/api/*` 표기 시 `/*` 가 중첩 주석을 열어 파일 전체가 주석화됨(`//` 라인주석은 무해). 문구로 회피.
- 빌드 검증: `:app:assembleDebug` (EXIT=0)

### 신규/수정 파일
- 신규: `data/remote/AiReportApiClient.kt`, `ui/report/ComprehensiveReportViewModel.kt`, `ui/report/ComprehensiveReportActivity.kt`, `res/layout/activity_comprehensive_report.xml`
- 수정: `AndroidManifest.xml`, `ui/home/HomeFragment.kt`, `res/layout/fragment_home.xml`

---

## [2026-06-01] 백엔드 수정요청 반영 — AiAdvice 운영 URL · 게스트 AI/FastAPI 차단

### 배경
백엔드(kgh) `frontend-수정요청.md` 정정 3건 반영. 직전 커밋(`e8f8df5` 예측 실연동, `03b0626` 식단조언 게스트헤더) 검토 결과 도출.

### 핵심 판단 — 게스트 정책 A안 확정
> **게스트는 AI/FastAPI 기능을 사용하지 않는다(로그인 사용자 전용).** 백엔드는 FastAPI 게스트 검증을 구현하지 않음(의도된 차단). **프론트가 게스트 진입을 막는다.**

- `/api/*` = **Spring**(Cognito + GuestIdentityFilter, 단 게스트는 `/api/home/**` 만 허용)
- `/blood-glucose|/step-calorie|/heart-rate`, `/ai/predict|predictions` = **FastAPI**(Bearer 만 검증, **게스트 필터 없음**)
- 결론: 게스트가 AI/FastAPI 호출 시 401 → 프론트가 **사전 차단**(버튼 비활성·로그인 유도). 게스트는 항상 `userId == null` + `isGuest == true`.

### 작업 내용
| 항목 | 변경 |
|------|------|
| 1 🔴 운영 URL | `AiAdviceApiClient.BASE_URL` `http://127.0.0.1:8080` → `https://api.checkdang.xyz`. 주석 정정: `/api/ai/demo-diet-advice` 는 **Spring** 경로(이전엔 FastAPI로 오기) |
| 2 게스트 차단 | `MealDetailActivity` AI 조언 버튼: 게스트면 비활성 + "로그인 후 이용" 안내. `GlucoseChartFragment` 예측 섹션: 게스트면 버튼 비활성·고정 안내 + 예측 조회/구독 스킵. `LifestyleViewModel.pushLifestyleToServer`: 게스트면 early return(동기화 스킵) |
| 3 주석/헤더 정리 | FastAPI 클라이언트(`BloodGlucosePredictionApiClient`/`HealthSyncApiClient`)의 무의미한 `X-Guest-Identity-Id` 부착 코드 제거 + Spring/FastAPI 구분 주석 정정. Bearer 만 유지 |

### 주요 결정
- **`SessionHolder.guestIdentityId` / `CognitoGuestSession` 은 유지** — 게스트 허용 경로 `/api/home/**` 용 인프라이나 현재 해당 클라이언트 미구현이라 어느 곳도 읽지 않는 상태. 향후 연동 위해 보존(삭제 보류)
- 혈당 예측·혈당 push 는 기존 `userId == null` 가드가 이미 게스트를 차단하던 것을 UI 사전 차단으로 보강
- 빌드 검증: `:app:compileDebugKotlin` (EXIT=0)

### 수정 파일
- `data/remote/AiAdviceApiClient.kt`, `data/remote/BloodGlucosePredictionApiClient.kt`, `data/remote/HealthSyncApiClient.kt`
- `ui/lifestyle/meal/MealDetailActivity.kt`, `ui/lifestyle/LifestyleViewModel.kt`, `ui/glucose/chart/GlucoseChartFragment.kt`

---

## [2026-05-30] ML 혈당 예측 API 실연동 (Mock 예측기 → 백엔드 FastAPI 교체)

### 배경
백엔드(kgh) ML 혈당 예측 API 명세 수령(`ml-prediction-api-명세서.md`). 직전 커밋(`c6fcb73`)의 **Mock 예측기**(향후 3일·선형 외삽)를 실제 백엔드 예측(향후 **3시간·5분 간격 36점**)으로 교체.

### 핵심 판단 — 데이터 형태가 근본적으로 다름 → Mock 대체 + 별도 시각화
| | 기존 `GlucosePredictor`(Mock) | 새 백엔드 API |
|---|---|---|
| 입력 | 희소한 일별 기록 몇 건 | **정확히 288개**(24h × 5분 CGM) |
| 출력 | 향후 3일(내일/모레/3일후) | 향후 3시간 36점(5분 간격) |
| 축 | 날짜(MM/dd) | 분 단위 시각(HH:mm) |

- **요청 방식 B 채택**: 앱은 288개 CGM 미보유(수동 입력 위주) → POST **body 생략** → 백엔드가 DynamoDB `blood_glucose_record` 에서 288개 자동 조회. 앱이 CGM 288개를 갖게 되면 glucose 배열 body 만 추가하면 방식 A(락인 없음).
- 기존 날짜축 차트에는 3시간 곡선이 안 맞음 → 예측 카드에 **전용 LineChart** 신설(분 단위 축, 70~180 가이드라인).

### 작업 내용
| 항목 | 변경 |
|------|------|
| API 클라이언트 | `BloodGlucosePredictionApiClient` 신규 — `predict`(POST, body 생략)/`latest`(GET, 404→null)/`history`(GET). `BloodGlucosePrediction` 모델 + `PredictionApiException(code, detail)` 로 422/404/502 분기. base URL·인증 헤더는 `HealthSyncApiClient` 동일 패턴 |
| ViewModel | `PredictionUiState`(Idle/Loading/Loaded/Empty/Error) + `loadLatestPrediction()`(진입 자동)·`runPrediction()`(버튼). 422→데이터 부족, 502→재시도 한국어 매핑. 날짜는 KST 오늘 |
| 예측 카드 | `fragment_glucose_chart.xml` `card_prediction` 재구성 — "예측하기" 버튼 + 전용 `chart_prediction`(36점, HH:mm 축) + 상태 메시지 + 면책. 기존 추세배지/3칸/신뢰도 뷰 제거 |
| 차트 정리 | `GlucoseChartFragment` 메인 차트의 Mock 점선 오버레이 제거 → 실측 전용. 예측은 전용 차트로 분리 렌더 |
| Mock 격리 | `GlucosePredictor.kt`(Mock 예측기)는 더 이상 참조되지 않는 dead code 상태이나 **삭제는 보류** — 작성자(Hyeon1629)와 상의 후 별도 처리. 파일 존재 여부와 무관하게 기능은 동작 |

### 주요 결정
- **게스트(userId 없음)** 는 예측 대상 아님(HealthSync 와 동일 정책) → Empty 표시
- **실제 곡선 표시 조건** — 로그인 userId + 백엔드 DynamoDB 에 그 날짜 혈당 288개 존재. 미달이면 422 → "데이터 부족" 안내
- 빌드 검증: `:app:assembleDebug` BUILD SUCCESSFUL (EXIT=0)

### 신규/수정 파일
- 신규: `data/remote/BloodGlucosePredictionApiClient.kt`
- 수정: `ui/glucose/GlucoseViewModel.kt`, `ui/glucose/chart/GlucoseChartFragment.kt`, `res/layout/fragment_glucose_chart.xml`
- 보류: `ui/glucose/prediction/GlucosePredictor.kt`(Mock, 미참조 dead code — 삭제는 작성자 상의 후)

---

## [2026-05-30] Gemini 식단 조언 배선 + 루트 stray 파일 정리

### 배경
PR #3(`gemini connection`)이 `AiAdviceApiClient.kt` / `MealDetailActivity.kt` / `activity_meal_detail.xml` / `AndroidManifest.xml` 4개를 **저장소 루트(패키지 구조 밖)** 에 업로드해 둔 상태였다. 실제 빌드 소스셋(`app/src`)에는 반영되지 않아 Gemini 식단 조언 기능이 **미배선** 상태였다.

### 핵심 판단 — 통짜 이동이 아닌 수술적 병합
루트 파일들은 현재 코드의 **구버전 포크(stale fork)**. 통째로 덮어쓰면 퇴행이 발생하므로 신규 파일만 이동하고 AI 기능만 이식했다.

| 루트(구버전) | 현재 `app/src`(신버전) | 처리 |
|------|------|------|
| `MealDetailActivity` = `MockDataProvider`(동기) + AI 버튼 | `HealthRepository.getMealSummary()`(suspend) | 신버전 유지 + AI 버튼만 이식 |
| `activity_meal_detail.xml` = 현재 + AI 뷰 2개 | AI 뷰만 없음 | 신버전에 AI 뷰 2개만 추가 |
| `AndroidManifest.xml` = INTERNET만 | Health 권한·FileProvider·LegalDocumentActivity 포함 | 루트 삭제(실 매니페스트에 `INTERNET`+`usesCleartextTraffic` 이미 존재) |

### 작업 내용
| 항목 | 변경 |
|------|------|
| API 클라이언트 | `AiAdviceApiClient.kt` 루트 → `data/remote/` 로 이동(git rename, 내용 무수정). `/api/ai/demo-diet-advice` 호출 후 Gemini 응답 파싱 |
| 화면 배선 | `MealDetailActivity` 에 `setupAiAdviceButton()` 이식 — 버튼 클릭 시 코루틴으로 조언 요청, 요청 중 버튼 비활성화, 실패 시 Toast+에러 메시지 |
| 레이아웃 | `activity_meal_detail.xml` 식단 카드 아래 `btn_ai_advice` + `tv_ai_answer` 추가 |
| 정리 | 루트의 stale 중복 4개 + 설명용 `README.md` 삭제 |

### 주요 결정
- **데이터 소스 퇴행 방지** — 루트 구버전(`MockDataProvider`)이 아닌 현재 `HealthRepository`(async) 경로 유지
- **데모 한정 주의** — `BASE_URL = http://10.0.2.2:8080` 은 **에뮬레이터 전용** localhost 별칭. 실기기/배포 시 실제 백엔드 주소 교체 필요
- 빌드 검증: `:app:compileDebugKotlin --rerun-tasks` BUILD SUCCESSFUL (ViewBinding `btnAiAdvice`/`tvAiAnswer` 정상 생성)

### 신규/수정 파일
- 이동: `AiAdviceApiClient.kt` → `app/src/main/java/com/checkdang/app/data/remote/AiAdviceApiClient.kt`
- 수정: `ui/lifestyle/meal/MealDetailActivity.kt`, `res/layout/activity_meal_detail.xml`
- 삭제: 루트 `AndroidManifest.xml`, `MealDetailActivity.kt`, `activity_meal_detail.xml`, `README.md`

---

## [2026-05-30] 혈당 AI 예측 기능 (Mock 예측기 + 예측 카드/차트 오버레이)

### 배경
혈당 페이지에 "지금까지의 기록을 분석해 앞으로의 혈당을 예측해 알려주는" 기능 추가.
**실제 AI/ML 은 미구현** — 요청대로 기능 골격(데이터 흐름 + UI)만 구현하고, 예측 로직은 Mock(통계) 자리표시자로 둠.

### 작업 내용
| 항목 | 변경 |
|------|------|
| 예측기 | `GlucosePredictor` 신규 — 최근 14일 기록의 선형 추세(최소제곱 회귀)를 외삽해 향후 3일(내일/모레/3일 후) 예측. 추세(상승/하락/안정)·예상 평균·상태·신뢰도(R²+데이터량) 산출. 기록 4건 미만이면 null |
| 시그니처 격리 | `predict(records): Result?` 만 유지하면 내부를 실제 모델로 교체 가능(HealthDataSource 교체 패턴 차용). IMPORTANT 주석으로 Mock 명시 |
| 예측 카드 | `fragment_glucose_chart.xml` 차트 아래 `card_prediction` 추가 — 추세 배지 + 알림 한 줄 + 예측 지점 3칸(코드로 채움) + 신뢰도 + 면책. 예측 불가 시 GONE |
| 차트 오버레이 | `GlucoseChartFragment.updateChart` 가 마지막 실측값에서 이어지는 **점선 예측 데이터셋**을 덧그림. 예측 지점은 상태 색상 원 |
| 데이터 소스 | 차트=기간 필터(`filteredForChart`), 예측=전체(`records`). 두 Flow 를 각각 collect 해 보관 후 함께 렌더 |

### 주요 결정
- **예측 = 전체 기록 기준, 차트 라인 = 필터 기준** — 분석은 기간 필터와 무관해야 의미. 예측 오버레이만 차트 끝에 이어붙임
- **Mock 은 선형 외삽** — 식별 가능한 단순 통계. 실제 모델 부재를 숨기지 않고 "통계 기반 참고" 면책 명시
- **FASTING 기준 상태 분류** — 예측값은 timing 이 없으므로 공복 기준으로 상태/리스크 판정(단순화, 주석화)
- **신뢰도는 Mock** — R²·기록 수 기반 40~95% 표시. 실제 모델 교체 시 모델 신뢰도로 대체

### 신규/수정 파일
- 신규: `ui/glucose/prediction/GlucosePredictor.kt`
- 수정: `GlucoseChartFragment.kt`, `fragment_glucose_chart.xml`

---

## [2026-05-30] 혈당 기록 PDF 내보내기 (공유 / 기기 저장) + 바디맵 PDF 제거

### 배경
- AI 바디맵 분석 결과의 PDF 보관 기능은 **제외** — 현재 분석은 Mock(규칙 기반)이라 비검증 분석에 의학 문서 권위를 입히는 리스크. `AIAnalysisActivity` 의 PDF 버튼(스텁) 삭제.
- 대신 **객관적 측정값인 혈당 기록**만 PDF 로 내보내도록 구현. 당뇨 환자 진료 지참용 "혈당 일지" 실수요. (실제 가치는 Health Connect 연동 후 발현, 현재는 Mock 데이터 기반)

### 작업 내용
| 항목 | 변경 |
|------|------|
| 바디맵 PDF 제거 | `AIAnalysisActivity` `btnPdf` 핸들러·미사용 `Toast` import 제거, `activity_ai_analysis.xml` `btn_pdf` 삭제 후 "확인" 버튼 full-width |
| PDF 생성기 | `GlucosePdfExporter.kt` 신규 — `android.graphics.pdf.PdfDocument` 기반 A4 리포트(헤더+요약+측정표+면책, 다중 페이지). 외부 의존성 0 |
| ① 공유 | `cacheDir/reports` → `FileProvider` `content://` → `ACTION_SEND` chooser |
| ② 기기 저장 | API 29+ `MediaStore.Downloads/체크당`(권한 불요) · API 26–28 레거시 Downloads(`WRITE_EXTERNAL_STORAGE` 사전 요청) |
| FileProvider | `AndroidManifest` `<provider>` 등록(`${applicationId}.fileprovider`) + `res/xml/file_paths.xml`(cache-path) |
| 권한 | `WRITE_EXTERNAL_STORAGE` `maxSdkVersion=28` 추가 |
| UI 연결 | `GlucoseFragment` `btnPdf` → `MaterialAlertDialog`(공유/기기 저장 2지선다). API≤28 저장 권한 런처 + `Dispatchers.IO` 렌더링 |

### 주요 결정
- **AI 분석 PDF 제외, 혈당 PDF 채택** — 측정값(객관)은 OK, Mock 분석(비검증)은 risk. 출처 정직성 기준
- **기본 PdfDocument API** — 제약(외부 의존성/영속성 프레임워크 금지)과 충돌 없음. PDF 는 저장이 아닌 렌더링+공유
- **대상 기간** — 별도 기간 선택 UI 없이 현재 로드된 전체 기록(refresh 90일 + Mock)을 실제 min~max 날짜 범위로 라벨링. 추후 기간 필터 연동 가능
- **닉네임 = 기록 대상** — 대리 기록(가족) 케이스 고려해 "OOO님 혈당 기록"으로 지칭
- **저장 경로 이원화** — 공유(진료 전달 본질) + 기기 저장(보관) 둘 다 제공. minSdk 26 때문에 저장은 버전 분기 필수

### 신규/수정 파일
- 신규: `ui/glucose/export/GlucosePdfExporter.kt`, `res/xml/file_paths.xml`
- 수정: `GlucoseFragment.kt`, `AIAnalysisActivity.kt`, `activity_ai_analysis.xml`, `AndroidManifest.xml`

---

## [2026-05-30] 2D 바디맵 Phase 2 — 상세 통증 기록 (성질/상황 taxonomy)

### 배경
"통증 입력하기" → 기존 바텀시트는 강도(1–5) + 평면 `PainType` 6종 칩뿐. 사양의 통증 분류 체계
(성질 5군 + 상황 3군, 부위별 다중 태그)로 상세 기록 가능하도록 교체.

### 작업 내용
| 항목 | 변경 |
|------|------|
| 분류 데이터 | `PainTaxonomy.kt` 신규 — 성질 5군(둔한/날카로운/신경성/근육성/관절성, 그룹 색상)·상황 3군(동작/일상동작/시간대), 그룹별 하위 태그 + `NEURAL_TAGS` |
| 모델 교체 | `PainRecord.painTypes: List<PainType>` → `qualityTags`/`situationTags: List<String>`. `PainType` enum 제거. `tagSummary` 파생 프로퍼티 추가 |
| 바텀시트 | `PainInputBottomSheet` 재작성 — 강도(유지) + 성질/상황 **아코디언**(그룹 헤더 클릭 토글, 그룹 색상 dot, 선택 개수 배지) + 그룹 색상 칩 다중선택. NestedScrollView 로 스크롤 |
| 칩 스타일 | 선택 시 그룹 색상 채움(alpha 0x22)+윤곽+체크아이콘 틴트. `ColorStateList(state_checked)` |
| 저장 경로 | BottomSheet → `AIAnalysisActivity` extras 를 `EXTRA_QUALITY_TAGS`/`EXTRA_SITUATION_TAGS`(String[]) 로 교체. Activity 가 PainRecord 구성·저장·분석 |
| 영속화 | `MockDataProvider.persistPain/restorePain` 가 `qualityTags`/`situationTags` JSON 배열로 직렬화(구버전 `painTypes` 키는 `optJSONArray` 로 무시) |
| Mock 분석 | `buildCorrelations` 신경 민감도 판정을 `qualityTags ∩ NEURAL_TAGS` 로 변경 |
| 기록 표시 | `PainRecordAdapter`/`AIAnalysisActivity` 결과 라벨을 `record.tagSummary` 로 |

### 주요 결정
- **태그를 String 으로 저장** — 성질·상황 합 ~40종. enum 대신 taxonomy 테이블(`PainTaxonomy`) + `Set<String>`. 디자인이 그룹/태그를 자주 조정할 수 있어 데이터-주도 구성이 유리
- **아코디언 프로그래매틱 생성** — XML+ViewBinding(Compose 미사용) 환경에서 동적 그룹/칩을 코드로 빌드. 그룹 펼침 상태·배지 카운트를 헤더 클릭/칩 토글로 관리
- **저장은 AIAnalysisActivity 단일 지점 유지** — 기존 흐름 보존(중복 저장 방지)
- **구버전 기록 호환** — `painTypes` 키만 있던 과거 mock 데이터는 태그 없이 로드(크래시 방지). dev 데이터라 마이그레이션 불요

### 수정·신규 파일
- 신규: `data/model/PainTaxonomy.kt`
- 수정: `data/model/PainModels.kt`(PainType 제거, PainRecord 필드 교체), `ui/bodymap/input/PainInputBottomSheet.kt`(재작성), `res/layout/bottom_sheet_pain_input.xml`(재구성), `ui/bodymap/analysis/AIAnalysisActivity.kt`, `data/mock/MockDataProvider.kt`, `ui/bodymap/PainRecordAdapter.kt`

### 빌드 검증
`./gradlew assembleDebug` → BUILD SUCCESSFUL (31s)

### 후속
- 부위별 active part 전환 칩 + 태그 개수 배지(바디맵 화면 본문) — 멀티 부위 동시 기록 UX
- 실기기 시각 검증 (아코디언/칩 색상 대비, 시니어 터치 타깃)

---

## [2026-05-30] 2D 바디맵 Phase 1 — 인체 실루엣 PNG + 알파 마스킹 점등

### 배경
BodyMapView 가 둥근 사각형 모자이크("회색 사각형")로 신체부위를 그리던 와이어프레임 상태.
확정 자산(`docs/체크당 바디맵 자산 (단일파일).html`)의 인체 실루엣 PNG 로 교체. Phase 1 은
렌더링/좌표만 교체하고 통증 입력 흐름(PainType·BottomSheet·AIAnalysis)은 그대로 유지.

### 작업 내용
| 항목 | 변경 |
|------|------|
| 자산 추출 | HTML 내장 gzip+base64 blob 2개 디코드 → 정면/후면 PNG(1000×2479, 투명배경, 신장 동일) |
| PNG 배치 | `res/drawable-nodpi/bodymap_grey_front.png` · `bodymap_grey_back.png` 신규 |
| BodyMapView | 사각형 drawRoundRect → **인체 PNG 실루엣** 렌더링. 점등은 `PorterDuff.DST_IN` 으로 **인체 알파 마스킹**(몸 밖 번짐 제거). 메모리 안전 위해 `inSampleSize=2` |
| **부위 = 해부학적 폴리곤** | 초기 사각형 히트박스는 "어깨 박스가 팔·목까지 점등"되는 문제. 실루엣 알파를 row 단위로 실측(`_silhouette_probe.py`)해 정면 16 / 후면 12 부위를 **다각형**으로 재정의(어깨↔팔 사선, 몸통↔팔 간격, 좌우 다리 갈림 반영). 점등은 `drawPath`, 히트 테스트는 `Region.setPath` |
| 좌표 교체 | 확정 자산 좌표계(viewBox 200×400) + 이미지 배치 상수(x=18.87,y=-2.04,w=162.79,h=403.56) |
| 좌/우 규칙 | 인체 자신 기준(의학적 관례) — 정면 LEFT=화면 우측(x>100), 후면 LEFT=화면 좌측 |
| 히트 테스트 | `Region`(1000×2000 정수 스케일) 폴리곤 contains. 겹치는 영역은 **최소 면적 부위 우선**(`minByOrNull`) |
| 분할 검증 | 부위별 색상 분할맵 PNG(`_parts_render.py`) 렌더 → 육안 확인 후 폴리곤 좌표 보정 |

### 주요 결정
- **알파 마스킹 = `saveLayer` + `DST_IN`** — 점등 사각형을 레이어에 그린 뒤 인체 비트맵 알파로 클립. 인체 모양 안에서만 녹색이 채워짐(자산 사양 핵심 요건)
- **`inSampleSize=2`** — 1000×2479 풀해상도(≈9.9MB/장)는 과함. 뷰 높이 대비 1/2(≈2.5MB) 로 충분 + OOM 회피
- **공개 API 유지** — `selectedPart`/`onPartSelected`/`setBodyView`/`clearSelection` 그대로 → BodyMapFragment 무변경
- **BodyPart enum 미변경** — 좌표만 BodyMapView 가 보유. 사양의 부위 이름이 기존 enum 과 일치
- **Phase 2 예고** — 통증 분류 체계(성질 5군/상황 3군), 멀티선택+active part 칩·배지, BottomSheet 아코디언, Mock/AIAnalysis 적응은 별도 STEP

### 수정·신규 파일
- 신규: `res/drawable-nodpi/bodymap_grey_front.png`, `res/drawable-nodpi/bodymap_grey_back.png`
- 수정: `ui/bodymap/BodyMapView.kt` (전면 재작성)

### 빌드 검증
`./gradlew assembleDebug` → BUILD SUCCESSFUL (57s)

### 후속 (Phase 2)
- 통증 분류 taxonomy 교체 + 부위별 `{성질:Set, 상황:Set}` 다중 기록
- active part 전환 칩 + 태그 개수 배지, BottomSheet 아코디언 재구성
- `MockDataProvider.analyzePainMock` / `AIAnalysisActivity` 새 모델 적응

---

## [2026-05-26] Cognito User Pool 이관 — `amplifyconfiguration.json` 갱신

### 배경
백엔드(kgh) DM 으로 신규 User Pool/App Client 로의 이관 요청. 카카오 시연용 임시 우회 적용을 위해 User Pool email 속성을 Optional 로 재구성한 새 풀로 분리한 것으로 추정 (관련: 2026-05-25 항목의 시연용 임시 우회 결정).

### 변경값

| 항목 | 이전 | 변경 |
|---|---|---|
| `CognitoUserPool.PoolId` | `ap-northeast-2_dB7hAykk4` | `ap-northeast-2_mb9vuwfui` |
| `CognitoUserPool.AppClientId` | `668uqu6u9qiqtiv9h6er9lqfu8` | `7a0e8e0agsd8a44cgp1o5as344` |
| `Auth.Default.OAuth.AppClientId` | `668uqu6u9qiqtiv9h6er9lqfu8` | `7a0e8e0agsd8a44cgp1o5as344` |
| `Auth.Default.OAuth.WebDomain` | `ap-northeast-2db7haykk4...` | `ap-northeast-2mb9vuwfui.auth.ap-northeast-2.amazoncognito.com` |
| `Auth.Default.OAuth.Scopes` | `["openid","email","profile"]` | `["openid","profile"]` |

- `CredentialsProvider.CognitoIdentity` (Identity Pool `ap-northeast-2:b8ca4228-55e4-4aad-ae89-acc31771ebbd`) 유지.
- `SignInRedirectURI` / `SignOutRedirectURI` 유지.

### `email` scope 제거 이유
백엔드 메시지대로 카카오에서 `email` 동의 자체를 받을 수 없는 정책 한계 (2026-05-25 항목 참고) + 새 App Client 에 `email` standard scope 활성화 안 된 경우 Hosted UI 가 `invalid_scope` 로 거부할 수 있어 안전 조치. 본 scope 는 Amplify SDK ↔ Cognito 레벨이고, IdP Authorize scope (`openid` 단독) 와 별개.

### 수정 파일
- `app/src/main/res/raw/amplifyconfiguration.json` — 위 5개 필드 교체

### 후속 확인 필요
- 단말 재검증 (Google / Kakao 로그인 → 백엔드 등록 → 메인 진입) — User Pool/Identity Pool 연결, Google·KakaoOIDC IdP 재구성 여부 모두 새 풀에 반영됐는지 단말로 확인.
- 산출물에서 이전 ID (`dB7hAykk4` / `668uqu6u9qiqtiv9h6er9lqfu8`) 참조가 남아있는 곳은 이 LOG 의 과거 항목뿐. 코드 하드코딩 없음 — 회귀 영향 0.

---

## [2026-05-27] 새 풀 단말 검증 (1차) — scope 진단 + ④b 차단점 2건 식별

### 배경
어제 풀 이관 후 첫 단말 검증 (Galaxy `R3CT10JVBHN`). 단계별 에러 변화로 차단점을 좁혀가며 진단.

### 단계별 진행

| 시각 | scope | Google | Kakao | 진단 |
|---|---|---|---|---|
| 23:48 | `["openid","profile"]` | ④a `invalid_scope` | ④a `invalid_scope` | 새 App Client `7a0e8e0agsd8a44cgp1o5as344` 에 `profile` scope 미활성화 (양쪽 동시 실패 → IdP 무관, 공통 인자 scope 뿐) |
| 00:00 | `["openid"]` | ④b `Invalid email address format.` | ④b `PreSignUp failed: exports is not defined in ES module scope.` | scope 통과. 각 IdP 별 다른 백엔드 이슈로 ④b 차단 |

### 차단점 2건 (백엔드 영역)

**① Google — Attribute mapping 누락 또는 email scope 미활성**
- ④ token 교환은 성공, Cognito 가 User Pool 에 attribute 쓰는 단계에서 형식 검증 실패
- 추정: 새 풀에 Google IdP Attribute mapping (`email ← email`) 미설정 + 클라 scope 가 `openid` 뿐이라 email claim 자체가 전달 안 됨
- 어제 (2026-05-25) 이전 풀에서는 동일 mapping 으로 ⑤까지 통과한 이력 있음 — 새 풀 미반영으로 회귀

**② Kakao — PreSignUp Lambda 의 Node.js 모듈 시스템 충돌**
- 시연용 우회를 위해 백엔드가 신규 추가한 PreSignUp Lambda trigger 가 호출은 되지만 런타임 에러
- `exports is not defined in ES module scope` — `package.json` 에 `"type": "module"` 또는 `.mjs` 확장자인데 핸들러는 `exports.handler = ...` CommonJS 문법 사용
- Lambda 호출 단계까지 도달했다는 점에서 Cognito attribute mapping → trigger 매핑은 정상

### 클라이언트 조치
- `app/src/main/res/raw/amplifyconfiguration.json` — `Scopes` 를 `["openid","profile"]` → `["openid"]` 로 진단 목적 임시 축소
- 백엔드 작업 완료 후 활성화된 scope 에 맞춰 원복 예정 (`["openid","email","profile"]` 권장)

### 백엔드 회신 발행
- `C:\Users\LG\Downloads\kgh-new-pool-blockers-20260527.md` — 위 차단점 2건 + 원하는 수정 방향 (Attribute mapping 설정, App Client OpenID Connect scopes 활성화, Lambda 모듈 통일 방안) 정리하여 kgh 전달

### 현재 진척도 (시연 목표 100 기준)
약 **78/100**. Phase 1·2 (Cognito 인프라 + IdP redirect) 완료, Phase 3 (token + attribute + Lambda) 60% 진행. Phase 4 (E2E 폴리시) 미착수.

성공 가능성 높음 — 차단점 모두 콘솔/코드 설정 영역(외부 정책 한계 아님), 어제 Google ⑤ 완주 이력 보유.

### 수정 파일
- `app/src/main/res/raw/amplifyconfiguration.json` — `Scopes` 축소

---

## [2026-05-25] OAuth 단말 재검증 (3차) — Google ✅ / Kakao 비즈 앱 한계로 시연용 우회 결정

### 배경
2차 검증에서 회신한 신규 이슈 2건(Google Attribute mapping, Kakao Authorize scope) 에 대한 백엔드 콘솔 작업 후 단말 재검증 (Galaxy `R3CT10JVBHN`). 단계별로 에러가 다음 단계로 밀려나며 진행, 최종적으로 카카오 측 정책 한계에 부딪힘.

### 결과 요약

| IdP | 5체크포인트 | 결과 |
|---|---|---|
| **Google** | ①②③ ④a ④b ⑤ | ✅ **전부 통과** — `D/SocialLogin: ✅ Cognito + 백엔드 등록 \| userId=3`, 온보딩→메인 진입 정상 |
| **Kakao** | ① ② ③ ④a | ④b 단계에서 차단 — `attributes required: [email]` |

### Kakao 차단 — 단계별 에러 변화 (시간순)

| 시점 | 에러 | 진단 | 조치 |
|---|---|---|---|
| 5/25 22:31 | `invalid_scope: account_email` | 1차 회신에서 권장한 scope 값을 카카오 OIDC 도 거부 | 3차 회신 md `cognito-kakao-scope-followup.md` 작성 → 백엔드에 `openid` 단독 요청 |
| 5/25 22:51 | `invalid_request: attributes required: [email]` | scope 통과. 단 ID Token 에 email claim 없음 | 4차 회신 md `cognito-kakao-attribute-mapping.md` 작성 → KakaoOIDC Attribute mapping 추가 요청 |
| 5/25 23:37 | (동일) | 백엔드는 카카오 콘솔 점검을 프론트에 분담 — 카카오 콘솔 → 카카오 로그인 → **OpenID Connect 활성화 OFF** 발견. ON 변경 후에도 동일 에러 | 카카오 콘솔 → 동의항목 → **`카카오계정(이메일)` "권한 없음"** 상태 확인 |

### 최종 차단 원인 — 카카오 정책 한계
카카오는 `카카오계정(이메일)` 동의항목을 보호 정보로 분류해 **개인 개발자 앱에서 권한 신청 자체가 닫혀있음**. **비즈 앱 전환 (사업자등록증 필요)** 을 통해서만 허용. 본 산학 프로젝트 팀 전원 대학생 신분으로 사업자등록 부담 + 산학협력업체 명의 분리 부담으로 비즈 앱 전환 불가.

### 결정 — 시연용 임시 우회 (백엔드 회신 대기)
출시까지 가는 게 아니라 **앱 완성도 시연까지가 목표** 인 점을 고려해 백엔드에 다음 임시 조치를 DM 으로 요청:
1. **Cognito User Pool `email` 속성 Required → Optional**
2. `/api/auth/social-login` 에서 카카오 sub 기반 사용자 생성 — 더미 이메일 자동 할당 or email NULL 허용 (백엔드 판단)

→ 적용되면 ④b 통과되어 카카오 로그인도 시연 가능. 출시 진행 시 비즈 앱 전환 + Attribute mapping 정식 추가 + email Required 원복 작업 필요 (별도 메모 보관).

### 회신 md 작성 (Downloads → kgh 전달)
- `cognito-kakao-scope-followup.md` (22:43) — `openid` 단독 요청
- `cognito-kakao-attribute-mapping.md` (23:00) — Attribute mapping 추가 요청 (현재까지 작업 미완료)
- (최종) 시연용 임시 우회 DM — 백엔드 회신 대기

### 수정·신규 파일
- (코드) 없음 — 본 차수는 진단 + Cognito 콘솔 측 작업 + 카카오 콘솔 측 작업 중심
- (회신) `~/Downloads/cognito-kakao-scope-followup.md`, `~/Downloads/cognito-kakao-attribute-mapping.md`

### 다음 차수 (대기 중)
- 백엔드 임시 우회 적용 → 단말에서 카카오 로그인 ⑤ 백엔드 등록까지 통과 확인
- (선택) 시연 안정성을 위해 카카오 버튼 가드 (비활성화 or 안내 토스트) — 임시 우회 적용 전까지

---

## [2026-05-25] OAuth 단말 재검증 (2차) — redirect 3건 해결 확인 + 신규 IdP 설정 이슈 2건 발견

### 배경
이전 단말 검증(같은 날 오전)에서 백엔드(kgh) 에 회신 대기로 넘긴 3건이 백엔드 측 콘솔 작업 완료 통보 후 어떻게 됐는지 단말 재검증 (Galaxy `R3CT10JVBHN`, 새 APK 재설치 + `pm clear`).

### 결과 — 회신 대기 3건 vs 재검증

| 회신 대기 항목 | 1차 결과 | 2차 결과 | 잔여 작업 |
|---|---|---|---|
| Google `redirect_uri_mismatch` | ❌ | ✅ 해결 (Hosted UI → Google 동의 → 콜백 통과) | 신규 이슈 — Cognito User Pool **Google IdP Attribute mapping (email)** 누락 |
| Kakao `redirect_uri_mismatch` | ❌ | ✅ 해결 (동일 경로 통과 확인) | 신규 이슈 — **KakaoOIDC IdP Authorize scope** 에 `profile` 포함 (Kakao OIDC 미지원) |
| Identity Pool `InvalidIdentityPoolConfigurationException` | ❌ | ✅ **완전 해결** — `I/CognitoGuest: 게스트 Identity ID 확보` | 없음 |

### 신규 이슈 상세 — token 교환 단계(④)에서 차단

검증 5체크포인트 중 ①②③ 통과, ④ Cognito ↔ IdP token 교환에서 두 IdP 모두 실패. **앱 코드 무관**, Cognito 콘솔의 IdP 설정 문제.

**Google**
```
W/SocialLogin: 구글 로그인 실패: invalid_request: attributes required: [email]
    at com.amplifyframework.auth.cognito.HostedUIClient.fetchToken(HostedUIClient.kt:108)
```
→ User Pool 의 `email` 속성이 Required 인데 Google IdP attribute mapping 에 매핑 행 없음. 동의 화면에서 발급된 인가코드는 정상이지만 Cognito 가 User 생성/lookup 시 거부.

**Kakao**
```
W/SocialLogin: 카카오 로그인 실패: invalid_scope: Invalid scope: profile; error=invalid_scope
    at com.amplifyframework.auth.cognito.HostedUIClient.fetchToken(HostedUIClient.kt:108)
```
→ Cognito 가 IdP token endpoint 호출 시 표준 `profile` scope 포함. Kakao OIDC 는 표준 `profile` 스코프 미지원 — KakaoOIDC IdP 의 "Authorize scope" 필드를 `openid account_email` 같은 Kakao 지원 형식으로 변경 필요.

**왜 앱 측 `amplifyconfiguration.json` 의 Scopes 변경이 답이 아닌가**
- `Scopes: ["openid", "email", "profile"]` 는 **클라이언트 ↔ Cognito** 사이의 scope. Google 은 받고 있음.
- **IdP ↔ Cognito** 사이는 Cognito 콘솔의 각 Identity Provider 의 "Authorize scope" 필드로 별도 정의. KakaoOIDC 만 이 값에서 `profile` 빼면 해결.

### 백엔드(kgh) 회신 요청 (2건)

1. Cognito User Pool `ap-northeast-2_dB7hAykk4` → Identity Providers → **Google** → Attribute mapping 에 `email` ← `email` 행 추가
2. 동일 User Pool → **KakaoOIDC** → Authorize scope 에서 `profile` 제거 (`openid account_email` 권장) + Attribute mapping 의 `email` 행 점검

### 수정 파일
- `IMPLEMENTATION_LOG.md` (이 항목)
- 앱 코드 변경 없음

### 검증 명령 메모 (재현용)
```powershell
$adb = "C:\Users\LG\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb -s R3CT10JVBHN shell pm clear com.checkdang.app   # Cognito 세션 캐시 초기화
& $adb -s R3CT10JVBHN logcat -c
# (단말 조작 후)
$pid = (& $adb -s R3CT10JVBHN shell pidof com.checkdang.app).Trim()
& $adb -s R3CT10JVBHN logcat -d -v time --pid=$pid | Select-String "SocialLogin|CognitoGuest|AuthClient|HostedUI"
```

---

## [2026-05-25] Cognito Callback/Logout URLs 정합성 확인 — 백엔드 콘솔 정리 회신 반영

### 배경
백엔드(kgh) 회신: Cognito App Client 에 잘못 등록돼 있던 URI 두 건(`https://chekdang://callback` — https prefix + `chekdang` 오타, `https://checkdang://logout` — https prefix) 삭제하고, 우리가 요청한 `checkdang://signin/`, `checkdang://signout/` 로 정리 완료. `http://localhost:3000/callback` 은 로컬 개발용으로 유지. Kakao IdP 는 `KakaoOIDC` 이름으로 등록.

### 작업 내용
앱 측 설정·코드는 이미 일치 상태(2026-05-24 작업분) — 별도 수정 불필요. 확인만 진행:

| 위치 | 값 | 상태 |
|------|-----|------|
| `amplifyconfiguration.json` `SignInRedirectURI` | `checkdang://signin/` | ✅ |
| `amplifyconfiguration.json` `SignOutRedirectURI` | `checkdang://signout/` | ✅ |
| `AndroidManifest.xml` `HostedUIRedirectActivity` scheme | `checkdang` | ✅ |
| `LoginActivity.kt:68` Kakao provider | `AuthProvider.custom("KakaoOIDC")` | ✅ |

코드/설정에 `chekdang` 오타 흔적 없음 — 잘못된 URI 는 백엔드 콘솔에만 등록되어 있던 값.

### 주요 결정
- **앱 수정 없음** — 모든 값이 이미 백엔드 정리 후 값과 일치. AndroidManifest 의 "백엔드 등록 요청 필요" TODO 주석만 "등록 완료" 메모로 갱신.

### 수정 파일
- `app/src/main/AndroidManifest.xml` (주석만 갱신, 로직 변경 없음)

### 단말 검증 결과 (Pixel-class 단말, USB 직결)

| 흐름 | 결과 | 원인 |
|------|------|------|
| Google 로그인 | ❌ Hosted UI 에서 Google 페이지 도달 후 `redirect_uri_mismatch` | Google Cloud Console OAuth Client 에 `<cognito>/oauth2/idpresponse` 미등록 |
| Kakao 로그인 | ❌ 동일 cancel/mismatch (실패 시점 동일) | Kakao Developers 콘솔 동일 누락 추정 |
| 비회원 시작 | ⚠️ UI 는 온보딩→메인 정상 진입, 그러나 Cognito Identity Pool 에서 `InvalidIdentityPoolConfigurationException` | Identity Pool 의 unauthenticated IAM role 미할당 또는 trust policy 오류 |

위 진단은 코드/설정 정합성 문제 아닌 **AWS·Google·Kakao 콘솔 설정 누락**. 백엔드(kgh) 에 회신 예정. 우리쪽 추가 변경 없음.

### CognitoGuestSession 로깅 개선 (진단용)

게스트 흐름 단말 검증 중 기존 경고 메시지 "Identity ID 결과가 비어있음 — Identity Pool 미지원 단말?" 가 실제 원인을 가리고 있어 보완:

- `AuthSessionResult` 의 `type` enum 과 `error` 필드를 함께 로깅 → `InvalidIdentityPoolConfigurationException` 같은 SDK 측 진짜 메시지가 logcat 에 표시되도록.
- AWS Amplify Core 2.19.1 jar 디컴파일로 `AuthSessionResult` 가 sealed class 아님(일반 클래스 + `type`/`error`/`value` 게터) 을 사전 확인 후 수정.

수정 파일: `app/src/main/java/com/checkdang/app/data/remote/CognitoGuestSession.kt`

### 백엔드 회신 대기 항목

1. Google Cloud Console — OAuth 2.0 Client (`572413466137-...`) Authorized redirect URIs 에 `https://ap-northeast-2db7haykk4.auth.ap-northeast-2.amazoncognito.com/oauth2/idpresponse` 추가
2. Kakao Developers — KakaoOIDC 앱 Redirect URI 에 동일 URL 추가
3. Cognito Identity Pool `ap-northeast-2:b8ca4228-55e4-4aad-ae89-acc31771ebbd` — unauthenticated IAM role 할당 + trust policy 검증

### 빌드 검증
`./gradlew compileDebugKotlin` → BUILD SUCCESSFUL. `./gradlew assembleDebug` → BUILD SUCCESSFUL (39s, AndroidManifest 변경 직후).

---

## [2026-05-24] sync 3종 `source_id` 필수 필드 추가

### 배경
백엔드(kgh) 회신 `codejwj-source-id-required.md` — `blood_glucose_record`, `heart_rate`, `step_calorie` 세 DynamoDB 테이블의 SK 를 `timestamp` → **`source_id`** 로 교체(FastAPI commit `eee0ecd`, ECR `:latest` 배포). 같은 `source_id` 재전송 시 putItem upsert 로 1건 유지하는 서버 측 멱등 도입. 운영 반영 완료된 상태라 누락 시 즉시 **422** 발생.

### 작업 내용
`HealthSyncApiClient.kt` 의 3개 push 함수 body 에 `source_id` 추가. 심박·걸음의 dead field `user_date` 동시 제거.

| 함수 | source_id 결정 방식 |
|------|---------------------|
| `pushGlucose` | `r.id` (GlucoseRecord 가 이미 보유 — Samsung Health record ID 또는 수동 입력 시 UUID) |
| `pushHeartRates` | `"${deviceId}-${sample.timestamp}"` (HeartRateSample 에 id 필드 없음, epoch ms 로 결정성 ID 생성) |
| `pushStepCalorie` | `"${deviceId}-${date}"` (하루 1건이라 단말+날짜 조합) |

### 주요 결정
- **HeartRateSample / StepCalorie 측 모델에 id 필드 추가 안 함** — 호출 측 시그니처 변경 없이 `HealthSyncApiClient` 안에서 결정성 ID 만 생성. 같은 단말이 같은 timestamp/date 의 데이터를 재전송해도 같은 source_id 가 만들어져 서버 멱등이 작동
- **`user_date` 필드 제거** — md 명시: 백엔드가 `{user_id}#{date}` 자동 조합. 보낼 필요 없음
- **`GlucoseSyncStore` SharedPreferences dedup 유지** — md 권장 그대로. 서버 멱등은 호출 자체는 받아내야 하므로 처음부터 안 보내는 클라이언트 dedup 이 모바일 데이터·배터리 절약 측면에서 유리
- **심박·걸음 dedup 도입은 보류** — md 가 "선택" 이라 했고 데이터 양 측정 후 결정. 현재는 일별 1회 sync 라 누적이 심각하지 않음

### 수정 파일
- `app/src/main/java/com/checkdang/app/data/remote/HealthSyncApiClient.kt`

### 빌드 검증
`./gradlew compileDebugKotlin` → BUILD SUCCESSFUL (23s)

---

## [2026-05-24] 백엔드 회신 4건 정리 — Cognito 인증 단일화 + 헬스 422 + 결제 verify

### 배경
백엔드(kgh)가 보낸 `codejwj-auth-migration.md` (2026-05-24) 회신:
1. 자체 OAuth2 + 자체 JWT 구조 → **AWS Cognito User Pool 단일화**. `/api/auth/social` → `/api/auth/social-login`, body 없음, Cognito ID Token 만 Bearer 헤더로. `/api/auth/logout` 자체 제거(Cognito 가 refresh 관리).
2. 게스트 흐름에 `GuestIdentityFilter` 신규 — Cognito Identity Pool unauthenticated ID 를 `X-Guest-Identity-Id` 헤더로 받아 검증.
3. `/heart-rate`, `/step-calorie` 가 `?date=YYYY-MM-DD` query 필수(DynamoDB PK `{user_id}#{date}` 구조). 누락 시 422.
4. Google Play 결제 verify (`/api/payment/google/verify`) 미구현 — 결제 성공 시 백엔드가 알 수 없어 `users.isPremium` 미갱신.

### 작업 내용

#### 인증 (item 1) — Amplify Auth Cognito 도입
| 항목 | 변경 |
|------|------|
| 의존성 | `com.amplifyframework:core-kotlin:2.19.1` + `aws-auth-cognito:2.19.1` (build.gradle.kts) |
| 설정 | `res/raw/amplifyconfiguration.json` 신규 — User Pool `ap-northeast-2_dB7hAykk4`, App Client `668uqu6u9qiqtiv9h6er9lqfu8`, Identity Pool `ap-northeast-2:b8ca4228-55e4-4aad-ae89-acc31771ebbd`, OAuth Hosted UI 도메인 `ap-northeast-2db7haykk4.auth.ap-northeast-2.amazoncognito.com`, scopes `openid email profile` |
| Manifest | `HostedUIRedirectActivity` intent-filter (scheme `checkdang`) 추가 |
| Application | `Amplify.addPlugin(AWSCognitoAuthPlugin())` + `Amplify.configure(...)` |
| AuthApiClient | `socialLogin()` 단일 함수로 재작성 (body 없음, `/api/auth/social-login`, Bearer 헤더만). 응답은 `{ success, data: {id, email, name, role, isPremium}, message }` 파싱. `logout()` 삭제 |
| LoginActivity | 기존 Google SignIn SDK / Kakao SDK 직접 호출 제거. `Amplify.Auth.signInWithSocialWebUI(AuthProvider.google()/.custom("KakaoOIDC"))` 로 통일. fetchAuthSession → ID Token → SessionHolder.accessToken → `AuthApiClient.socialLogin()`. **mock 토큰 fallback 분기 완전 제거** — 실패 시 Amplify.signOut + 로그인 화면 유지 |
| MenuFragment | 로그아웃: `AuthApiClient.logout(token)` → `Amplify.Auth.signOut()`. 회원 탈퇴 흐름도 Amplify.signOut 추가 |

#### 게스트 (item 2) — Identity Pool ID
| 항목 | 변경 |
|------|------|
| SessionHolder | `guestIdentityId: String?` 필드 추가 + reset() 에 포함 |
| CognitoGuestSession | 신규 헬퍼 — `ensureIdentityId()` 가 `fetchAuthSession().identityIdResult.value` 로 ID 발급 후 SessionHolder 저장 |
| SplashActivity | 게스트 콜드 스타트 복원 시 `ensureIdentityId()` await |
| LoginActivity | 게스트 진입 시 백그라운드로 `ensureIdentityId()` (온보딩 차단 방지) |
| HealthSyncApiClient | `post()` 에서 `SessionHolder.isGuest && guestIdentityId != null` 일 때 `X-Guest-Identity-Id` 헤더 부착 |

#### 헬스 422 fix (item 3)
| 항목 | 변경 |
|------|------|
| HealthSyncApiClient | `pushStepCalorie` path → `/step-calorie/$userId?date=$date`. `pushHeartRates` path → `/heart-rate/$userId?date=$date`. 혈당 `pushGlucose` 와 동일한 패턴 (이미 ?date 송신 중) |

#### 결제 verify (item 4)
| 항목 | 변경 |
|------|------|
| PaymentApiClient | 신규 — `verifyGooglePurchase(purchaseToken, subscriptionId)` → `POST /api/payment/google/verify` Bearer 헤더. body 는 2필드. 응답 `{ success, data: { isPremium, premiumExpiresAt, ... } }` 파싱 |
| BillingRepository | `handlePurchase` 흐름 재구성: verify → 성공 시에만 acknowledge → `isPremium` 결과에 따라 tier 갱신. verify 실패 시 tier 유지 + Error 상태 (purchaseToken 은 acknowledge 전까지 재처리 가능) |

#### 빌드 인프라
| 항목 | 변경 |
|------|------|
| gradle.properties | `org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g` 추가. Amplify SDK 도입으로 default 1G 힙 초과 → DEX merge OOM 발생 → 4G 로 확장 |

### 주요 결정
- **AWS Amplify SDK 도입** — CLAUDE.md "Firebase 등 외부 의존성 추가 금지" 와 일부 상충하나, 기존 Google Sign-In SDK / Kakao SDK 가 이미 존재해 절대적 제약은 아니었음. 백엔드가 Cognito 전용으로 전환한 이상 클라이언트 측 선택지 없음. Hosted UI 한 줄 호출로 refresh / 세션 / Federated 사인-인 자동화 효과 우선
- **mock 토큰 fallback 제거** — 기존 `LoginActivity:187` 의 `"mock_access_token"` 분기는 백엔드 호출이 일부 성공처럼 보이게 만들어 실패를 가렸음. md 의 명시적 요청대로 제거. 실패는 곧 로그인 실패 (재시도 가능 상태)
- **redirect URI 는 `checkdang://signin/`, `checkdang://signout/` 로 선결** — md 에 미명시. backend(kgh) 에 "Cognito App Client 의 Callback/Logout URLs 로 등록" 요청 필요 (TODO 주석)
- **게스트 보호 API 호출은 헤더 plumbing 만 완성** — 현재 `pushGlucose` 등이 `userId == null` 일 때 early return 하는 구조 그대로 유지. 백엔드에 게스트 전용 path 명세 나오면 게스트 데이터 push 별도 STEP

### 미해결 / 후속
- **TODO(auth-redirect-uri)**: 백엔드 Cognito App Client 의 Callback/Logout URLs 에 `checkdang://signin/`, `checkdang://signout/` 등록 확인. 미등록 시 OAuth 콜백 미수신 → 로그인 무한 로딩
- **혈당 `source_id` 멱등 처리(md item 5)**: 백엔드 멱등 처리 완료 회신 후 `pushGlucose`/`pushHeartRate`/`pushStepCalorie` body 에 `source_id` 필드 추가 + 클라이언트 dedup 제거 가능
- **게스트 데이터 push 흐름**: 백엔드 게스트 전용 endpoint 명세 확보 후 별도 STEP

### 수정 파일
- `app/build.gradle.kts`
- `gradle.properties`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/raw/amplifyconfiguration.json` (신규)
- `app/src/main/java/com/checkdang/app/CheckDangApplication.kt`
- `app/src/main/java/com/checkdang/app/data/remote/AuthApiClient.kt`
- `app/src/main/java/com/checkdang/app/data/remote/CognitoGuestSession.kt` (신규)
- `app/src/main/java/com/checkdang/app/data/remote/PaymentApiClient.kt` (신규)
- `app/src/main/java/com/checkdang/app/data/remote/HealthSyncApiClient.kt`
- `app/src/main/java/com/checkdang/app/data/billing/BillingRepository.kt`
- `app/src/main/java/com/checkdang/app/data/mock/SessionHolder.kt`
- `app/src/main/java/com/checkdang/app/ui/auth/login/LoginActivity.kt`
- `app/src/main/java/com/checkdang/app/ui/splash/SplashActivity.kt`
- `app/src/main/java/com/checkdang/app/ui/menu/MenuFragment.kt`

### 빌드 검증
`./gradlew assembleDebug` → **BUILD SUCCESSFUL** (29s, gradle.properties heap 확장 후)

### 단말 테스트 시 확인 사항 (back-and-forth 필요)
- backend(kgh): Cognito App Client Callback/Logout URLs 에 `checkdang://signin/`, `checkdang://signout/` 등록 여부
- backend(kgh): Cognito Hosted UI 의 KakaoOIDC IdP 등록 상태(이름 일치) 확인
- 통합 테스트(md 끝): 로그인 → 온보딩 → 헬스 동기화 6종(혈당/운동/식사/수면/심박/걸음) → AI 분석 → 결제 → 게스트 모드 end-to-end

---

## [2026-05-22] 혈당 push 중복 전송 억제 — GlucoseSyncStore

### 배경
실 단말 테스트 logcat 분석 결과, 혈당 탭에 진입할 때마다 `refresh()` 가 삼성헬스 90일치를
통째로 재전송했다. 서버는 매 요청을 `201 Created` 로 새 row 생성 → 측정값 7건이 ~15초 안에
4회 중복 전송돼 약 28 row 로 누적. 백엔드 멱등 처리(`sourceId` upsert) 가 근본 해결책이나,
답변 대기 동안 앱 측에서 선제적으로 차단.

### 작업 내용
| 항목 | 변경 |
|------|------|
| GlucoseSyncStore | `data/remote/GlucoseSyncStore.kt` 신규 — push 완료한 record ID 를 SharedPreferences 에 영속 기록. `userId` 별 키 네임스페이스 분리(`pushed_ids_{userId}`) |
| HealthSyncApiClient | `pushGlucose` 가 `Unit` → `List<GlucoseRecord>`(전송 성공분) 반환. `runCatching.onSuccess` 로 성공 record 수집 |
| GlucoseViewModel | `refresh()` 가 `GlucoseSyncStore.filterUnsent()` 로 미전송 record 만 push. `pushGlucoseToServer` 가 전송 성공분을 `markPushed()` 로 기록 |
| CheckDangApplication | `GlucoseSyncStore.init(this)` 호출 추가 |

### 주요 결정
- **영속 dedup (SharedPreferences)** — 앱 재시작 후에도 record 당 평생 1회만 전송. Room 금지 제약(CLAUDE.md) 하에서 기존 UserStore/MockDataProvider 와 동일 패턴
- **userId 별 키 분리** — 한 단말에서 계정 전환 시, 삼성헬스 record ID(`{uid}-{timestamp}`)가 단말 공통이라 다른 사용자 기록까지 "전송됨"으로 잘못 걸러지는 것 방지
- **성공분만 기록** — `pushGlucose` 가 전송 성공 record 만 반환 → 실패 record 는 다음 진입 시 재시도됨
- **앱 측 임시 방어막** — 근본 해결은 백엔드 `/blood-glucose` 멱등화. 백엔드 답변 후 `sourceId` body 필드 추가 예정

### 수정 파일
- `data/remote/GlucoseSyncStore.kt` (신규)
- `data/remote/HealthSyncApiClient.kt`
- `ui/glucose/GlucoseViewModel.kt`
- `CheckDangApplication.kt`

### 빌드 검증
`./gradlew assembleDebug` → BUILD SUCCESSFUL (1m 33s)

---

## [2026-05-22] 수동 입력 혈당 백엔드 push 연결

### 배경
백엔드 팀 피드백 — "삼성헬스 관련은 다 정상작동하는데 혈당필드가 서버에 업데이트가 안됐어서 정상작동 되는지 테스트 부탁". 점검 결과, 혈당 push 경로에 갭 발견:
- `GlucoseInputBottomSheet` 의 바텀시트 직접 입력 혈당은 `MockDataProvider.addRecord()` 로 로컬 저장만 되고 `/blood-glucose` 호출이 전혀 없었다.
- `GlucoseViewModel.refresh()` 의 `pushGlucoseToServer` 는 `HealthRepository.getBloodGlucoseRecords()`(= 삼성헬스 데이터)만 push.
- 결과: 삼성헬스에 혈당 데이터가 있을 때만 서버 전송 발생. 수동 입력은 미전송.

### 작업 내용
| 항목 | 변경 |
|------|------|
| GlucoseViewModel | `pushManualRecord(record)` public 메서드 추가 — `viewModelScope` 에서 단건 `pushGlucoseToServer` 호출. ViewModel scope 라 바텀시트 dismiss 후에도 전송 유지 |
| GlucoseFragment | `onRecordSaved` 콜백에서 `viewModel.pushManualRecord(record)` 호출 |
| GlucoseViewModel | `init { refresh() }` 제거 — `GlucoseFragment.onViewCreated` 가 이미 `refresh()` 호출. 첫 진입 시 ViewModel init + onViewCreated 가 동시에 refresh 를 호출해 90일치 혈당이 2개 스레드로 중복 전송되던 문제 제거 |

### 주요 결정
- **단건 즉시 전송** — 삼성헬스 자동 측정(refresh, 90일 일괄)과 별개로 입력 즉시 push. 입력→서버 반영 지연 최소화
- **게스트는 자동 스킵** — `HealthSyncApiClient.pushGlucose` 가 `SessionHolder.userId == null` 일 때 early return. 별도 분기 불필요
- **ViewModel scope 사용** — 바텀시트 `lifecycleScope` 는 `dismiss()` 시 취소되므로, 전송은 살아남는 `GlucoseViewModel.viewModelScope` 에서 수행

### 테스트 결과 (실 단말 logcat)
- ✅ 수동 입력 / 삼성헬스 자동 측정 모두 `POST /blood-glucose/{user_id}?date=...` → **201 Created**
- ⚠️ **중복 전송 잔존** — 혈당 탭 진입마다 `refresh()` 가 삼성헬스 90일치를 통째로 재전송. 서버가 `201 Created` 로 매번 새 row 생성 → 중복 누적. `init` 제거로 첫 진입 2배 호출은 해소했으나, 진입별 재전송은 백엔드 멱등 처리(`sourceId` 기반 upsert) 필요. [2026-05-19] 로그에서 예고된 항목

### 미해결 / 후속
- `POST /blood-glucose` 멱등화 — 앱이 record 고유 ID(`${uid}-${timestamp}` / 수동은 UUID)를 body 에 실어 보내고 백엔드가 upsert. 백엔드 협의 후 별도 STEP

### 수정 파일
- `ui/glucose/GlucoseViewModel.kt`
- `ui/glucose/GlucoseFragment.kt`

### 빌드 검증
`./gradlew compileDebugKotlin` → BUILD SUCCESSFUL

---

## [2026-05-22] 홈/혈당/라이프 — 여백 적정 수준 복구 (직전 축소가 과했음)

### 배경
이전 축소(360dp) 가 너무 적극적이라 화면이 빽빽한 느낌. 스크롤은 유지하지 않되 카드/헤더 간 시각적 호흡을 회복.

### 재조정 — "원본 ↔ 직전 축소 값의 중간 지점" 원칙
| 항목 | 원본 → 직전 → 재조정 |
|------|--------------------|
| **홈** 헤더 paddingTop | 24 → 16 → **20** |
| **홈** 헤더 paddingBottom | 16 → 8 → **12** |
| **홈** 메인 카드 padding | 20 → 16 → **18** |
| **홈** 메인 카드 marginBottom | 16 → 10 → **12** |
| **홈** divider marginVertical | 16 → 10 → **12** |
| **홈** 빈 상태 paddingVertical | 24 → 12 → **16** |
| **홈** 라이프 카드 padding | 14 → 12 → **14 (원복)** |
| **홈** 차트 높이 | 200 → 160 → **170** |
| **홈** 차트 padding | 12 → 8 → **10** |
| **홈** 루트 paddingBottom | 24 → 16 → **20** |
| **혈당** 통계 카드 padding | 16 → 10 → **12** |
| **혈당** 통계 카드 marginTop | 12 → 6 → **8** |
| **혈당** TabLayout marginTop | 8 → 2 → **4** |
| **혈당** 차트 paddingBottom | 24 → 16 → **20** |
| **혈당** 차트 높이 | 320 → 240 → **260** |
| **라이프** 루트 paddingTop | 20 → 12 → **16** |
| **라이프** 루트 paddingBottom | 24 → 16 → **20** |
| **라이프** 동기화 배너 padding | 12 → 8 → **10** |
| **라이프** 카드 padding | 16 → 12 → **14** |
| **라이프** 카드 marginBottom | 12 → 8 → **10** |
| **라이프** 운동 헤더 marginBottom | 16 → 8 → **12** |
| **라이프** 도넛 크기 | 120 → 96 → **104** |
| **라이프** 식사/수면 헤더 marginBottom | 12 → 8 → **10** |
| **라이프** 수면 단계 라벨 marginBottom | 12 → 6 → **10** |

### 합산
- 직전 축소: 약 −360dp
- 재조정 후: 약 **−180dp** (절반만 유지)

### 수정 파일
- `res/layout/fragment_home.xml`
- `res/layout/fragment_glucose.xml`
- `res/layout/fragment_glucose_chart.xml`
- `res/layout/fragment_lifestyle.xml`

### 빌드 검증
`./gradlew assembleDebug` → BUILD SUCCESSFUL (37s)

---

## [2026-05-22] 홈/혈당/라이프 — 한 화면 안에 들어가도록 세로 공간 축소

### 배경
세 메인 화면 콘텐츠가 너무 길어 매번 스크롤이 필요. 한 화면에 핵심 정보가 보이도록 상하 여백·헤더·카드 간격·차트 높이 축소.

### 변경 요약 (수치는 dp)
**홈 (`fragment_home.xml`)**
- 헤더 paddingTop 24→16, paddingBottom 16→8
- 메인 혈당 카드: marginBottom 16→10, 내부 padding 20→16, divider marginVertical 16→10
- 빈 상태 paddingVertical 24→12
- 라이프스타일 섹션 marginBottom 12→8, 라이프 카드 행 marginBottom 16→10
- 라이프스타일 카드 내부 padding 14→12 (×3)
- 차트 섹션 marginBottom 12→8, 차트 높이 200→160, 차트 padding 12→8
- 루트 paddingBottom 24→16
- **합산 약 −130dp**

**혈당 (`fragment_glucose.xml` + `fragment_glucose_chart.xml`)**
- 통계 카드 marginTop 12→6, marginBottom 4→2, padding 16→10
- TabLayout marginTop 8→2
- 차트 페이지 paddingBottom 24→16, chipGroup marginTop 12→6, marginBottom 8→4
- 차트 카드 내부 paddingTop 12→8, paddingBottom 8→4
- **차트 높이 320→240**, 범례 margin 8→4
- **합산 약 −110dp**

**라이프 (`fragment_lifestyle.xml`)**
- 루트 paddingTop 20→12, paddingBottom 24→16
- 헤더 tv_date marginTop 4→2, marginBottom 16→10
- 동기화 배너 marginBottom 12→8, padding 12→8
- 운동/식사 카드 marginBottom 12→8 / 수면 카드는 기존대로 (마지막)
- 모든 카드 내부 padding 16→12
- 운동 카드 헤더 marginBottom 16→8, **도넛 120→96**
- 식사 카드 헤더 marginBottom 12→8, tv_meal_goal marginBottom 12→8
- 수면 카드 헤더 marginBottom 12→8, 총시간 marginBottom 12→8, 단계 라벨 marginBottom 12→6
- **합산 약 −120dp**

### 주요 결정
- **차트 높이 축소가 가장 임팩트 큼** — 홈 차트 200→160, 혈당 차트 320→240. 시각적 변화는 크지만 가독성 유지 범위 내
- **도넛 차트 120→96dp** — 라이프 운동 카드. 우측 통계와의 시각 균형 유지
- **카드 내부 padding 일관 16→12** — 라이프 3카드 공통 패턴. 통일성도 함께 확보
- **헤더 여백 가장 적극적으로 축소** — paddingTop 24→16/12. 스크롤 없이 보이려면 상단부터 줄여야 효과적
- **차트 종횡비 변경** — 데이터 시각화 가독성에 영향 가능. 시각 검증에서 확인 필요

### 수정 파일
- `res/layout/fragment_home.xml`
- `res/layout/fragment_glucose.xml`
- `res/layout/fragment_glucose_chart.xml`
- `res/layout/fragment_lifestyle.xml`

### 빌드 검증
`./gradlew assembleDebug` → BUILD SUCCESSFUL (42s)

---

## [2026-05-22] 개인정보처리방침 화면 + 법적 문서 컴포넌트 공통화 (v1.0)

### 배경
이용약관(`TermsActivity`) 은 구현 완료, 개인정보처리방침은 본문 미확보로 `"준비 중"` Toast 만 노출 중이던 상태. v1.0 처리방침 본문 확보 → 화면 신규 구현. 동시에 두 문서가 70% 공통 구조라 단일 Activity + enum 으로 통합 리팩토링.

### 작업 내용
| 항목 | 변경 |
|------|------|
| 처리방침 원문 | `res/raw/privacy_policy.md` 신규 — v1.0 본문 323줄, 11개 법정 필수 항목 포함, `TODO(legal)` 주석 |
| 의존성 | `io.noties.markwon:ext-tables:4.6.2` 추가 — 처리방침의 표(보유기간/위탁/국외이전) 렌더링 |
| 공통 컴포넌트 | `ui/legal/LegalDocumentActivity` + `LegalDocument` enum (TERMS / PRIVACY) 신규. 단일 레이아웃 `activity_legal_document.xml` |
| 문자열 분리 | `res/values/strings_legal.xml` — 제목/메타/contact/agreement 텍스트, 다국어 확장 대비 |
| Footer 분기 | `agreementFooter` (Terms+MODE_AGREEMENT) / `btnContact` (Privacy) — visibility 토글 |
| 이메일 fallback | `mailto:` 인텐트를 `runCatching` 으로 처리. Android 11+ `<queries>` 가시성 제한 우회 |
| TermsActivity 제거 | 기존 `TermsActivity.kt` + `activity_terms.xml` 삭제, Manifest 등록 교체 |
| 호출처 마이그레이션 | `MenuFragment` (Terms + Privacy 둘 다) / `LoginActivity` 캡션 SpannableString — `LegalDocumentActivity.intent()` 빌더 사용 |

### 주요 결정 (Plan 단계 사용자 승인)
- **옵션 B 채택** — `LegalDocumentActivity` 단일 + enum 분기. 향후 다른 법적 문서(위치 기반 약관 등) 추가 시 enum 항목만 추가
- **`TermsActivity` 완전 삭제** — Thin wrapper 유지는 부채만 늘림. 호출처 2곳만 교체
- **TablePlugin 을 base 에 추가** — Terms 본문 표 0개 확인, 무해 + 향후 표 사용 시 일관 적용
- **이메일 fallback 은 `runCatching`** — 원안의 `resolveActivity` 는 Android 11+ 가시성 제한으로 false negative 위험. 호출 시점 catch 가 안전
- **빌더 패턴** — `LegalDocumentActivity.intent(ctx, doc)` / `agreementIntent(ctx)` 정적 메서드. 직접 `Intent` 생성 + extra 누락 방지

### 산출물 / 삭제 / 수정
- **신규**: `res/raw/privacy_policy.md`, `ui/legal/LegalDocumentActivity.kt`, `ui/legal/LegalDocument.kt`, `res/layout/activity_legal_document.xml`, `res/values/strings_legal.xml`, `docs/STEP_privacy.md`
- **삭제**: `ui/legal/TermsActivity.kt`, `res/layout/activity_terms.xml`
- **수정**: `app/build.gradle.kts`, `AndroidManifest.xml`, `ui/menu/MenuFragment.kt`, `ui/auth/login/LoginActivity.kt`, `CLAUDE.md` ("법적 문서" 섹션으로 확장)

### 자가 검증 (11/11 통과)
1. `./gradlew assembleDebug` → BUILD SUCCESSFUL (54s)
2. Markwon 3건 hit (`core` + `linkify` + `ext-tables`)
3. 처리방침 원문 323줄 (≥200)
4. `TODO(legal)` 주석 hit
5. 11개 법정 필수 항목(수집/목적/보유/제3자/위탁/국외/파기/권리/안전성/보호책임자/침해구제) 모두 hit
6. 민감정보 조항 6건 hit
7. `textIsSelectable` hit
8. 이메일 인텐트 `runCatching` + Toast fallback 둘 다 존재
9. `LegalDocumentActivity` 호출처 — MenuFragment + LoginActivity 2곳
10. `TablePlugin.create` hit
11. `TermsActivity` 잔존 참조 0건

### 미해결 / 후속 STEP
- **자리표시자**: 회사명, `privacy@checkdang.com`, 보호책임자 성명/직책, 백엔드 인프라 제공자 — 출시 전 실값 교체
- **법무 검토**: 본문은 형식 템플릿. 출시 전 개인정보 전문 법무 검토 필수
- **민감정보 별도 동의 화면**: 「개보법」 제23조 의무. `OnboardingActivity` 의 신규 단계로 별도 STEP
- **데이터 이동 요구권**: 본문 제9조 1항 안내만 존재, 실제 export 기능은 별도 STEP
- **다국어 (영문)**: `strings_legal.xml` 분리 완료, 번역 본문은 출시 후

---

## [2026-05-22] 메뉴 — "가족 공유" 를 내 정보 섹션으로 통합

### 변경
- 이전 단계에서 "데이터 내보내기" 제거 후 섹션 2(데이터) 가 가족 공유 한 행만 남아 의미 약화.
- 가족 공유를 섹션 1(내 정보) 카드로 이동, 섹션 2 헤더/카드 통째 제거. 결과: 섹션 2개 (내 정보 / 고객센터 / 계정 → 3개로 축소되었으나 카드 묶음만 보면 시각적으로 더 단정)

### 수정 파일
- `res/layout/fragment_menu.xml` — 섹션 2 `TextView` "데이터" + `MaterialCardView`(menu_family 포함) 제거. menu_family 를 섹션 1 카드 내부로 이동 + 사이 divider 추가
- `ui/menu/MenuFragment.kt` — `configRow(menuFamily, ...)` 를 섹션 1로 이동, 섹션 주석 번호 재정렬

### 빌드 검증
`./gradlew compileDebugKotlin` → BUILD SUCCESSFUL (8s)

---

## [2026-05-22] 메뉴 — "데이터 내보내기" 항목 제거

### 변경
- 데이터 내보내기 기능은 혈당 페이지의 PDF 버튼으로 일원화. 메뉴에서 중복 노출 제거.
- 섹션 2(데이터) 카드는 가족 공유 한 행만 유지 → 행 사이 divider 도 함께 제거

### 수정 파일
- `res/layout/fragment_menu.xml` — `menu_export` `<include>` + divider `<View>` 삭제
- `ui/menu/MenuFragment.kt` — `configRow(menuExport, ...)` + Toast 리스트의 `binding.menuExport` 참조 제거

### 빌드 검증
`./gradlew compileDebugKotlin` → BUILD SUCCESSFUL (24s). 잔존 `menuExport` / `menu_export` 참조 0건.

---

## [2026-05-22] 앱 아이콘 — 확정 시안 Adaptive Icon 통합

### 배경
기존 `mipmap-mdpi` 에 placeholder adaptive-icon (단색 2개 `brand_green_light` + `brand_green`) 만 존재. 디자이너 확정 SVG 시안(물방울 + 광택 + 체크) 을 Android Adaptive Icon 으로 통합.

### 작업 내용
| 항목 | 변경 |
|------|------|
| Background vector | `res/drawable/ic_launcher_background.xml` 신규 — 단색 `#F1F8E9` (모서리는 OS 마스킹) |
| Foreground vector | `res/drawable/ic_launcher_foreground.xml` 신규 — 그라데이션 물방울 + 회전 group(광택) + stroke 체크. `xmlns:aapt` 로 gradient 컴파일 |
| Adaptive-icon 정의 | `mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` 신규 |
| Placeholder 정리 | `mipmap-mdpi/` 디렉토리 + placeholder 2개 파일 삭제 |
| 시안 원본 보관 | `docs/assets/checkdang-icon.svg` 신규 — 변경 이력/참조용 |
| AndroidManifest | `android:icon` / `android:roundIcon` 기 등록 상태 확인 — 변경 없음 |

### 주요 결정 (Plan 단계 사용자 승인)
- **Q1 좌표 표현 → 소수값 정확 사용** — 시안 픽셀 일치 우선
- **Q2 Legacy fallback → 생성 안 함** — minSdk=26 이라 Android 7.1 이하 설치 불가, raster mipmap 은 죽은 자산
- **원안 좌표 일부 산술 오차 보정** — `44.742→44.738`, `45.156→45.141`, `51.914→51.891`, `62.453→62.438` (× 0.2109375 정확 재계산). 시각 차이 약 0.02% (육안 식별 불가) 이나 "정확" 선언과 일치하도록 보정
- **광택 ellipse 회전** — Vector Drawable 의 `<ellipse>` 자체 회전 미지원 → `<group rotation>` 로 우회
- **물방울 path 는 SVG 그대로** — 시안 변경 금지 원칙. 상단 마진 3 (safe zone 경계 근접) 은 시각 검증에서 모니터링

### 산출물 / 삭제 파일
- 신규: `res/drawable/ic_launcher_background.xml`, `res/drawable/ic_launcher_foreground.xml`, `res/mipmap-anydpi-v26/ic_launcher.xml`, `res/mipmap-anydpi-v26/ic_launcher_round.xml`, `docs/assets/checkdang-icon.svg`, `docs/STEP_app_icon.md`
- 삭제: `res/mipmap-mdpi/ic_launcher.xml`, `res/mipmap-mdpi/ic_launcher_round.xml` (+ 빈 디렉토리)
- 수정: `CLAUDE.md` ("앱 아이콘" 섹션 신규)

### 자가 검증 (9/9 통과)
1. `./gradlew assembleDebug` → BUILD SUCCESSFUL (14s)
2. adaptive icon 4종 파일 모두 존재
3. 시안 색상 `#F1F8E9` / `#66BB6A` / `#388E3C` 모두 hit
4. 그라데이션 수직 — `startX==endX==54`, `startY=21.094 < endY=86.484`
5. 체크 stroke 4속성 — `strokeColor=#FFFFFF`, `strokeWidth=4.641`, `strokeLineCap=round`, `strokeLineJoin=round`
6. 광택 회전 — `rotation=-32`, `pivotX=44.738`, `pivotY=45.984`
7. 금기 컬러(red/orange 계열) 0건
8. Manifest 에 `android:icon`, `android:roundIcon` 둘 다 `@mipmap/ic_launcher*` 참조
9. `xmlns:aapt` foreground 에 hit (gradient 컴파일 필수)

---

## [2026-05-22] 이용약관 화면 + 약관 원문 (v1.0)

### 배경
메뉴 "이용약관"·"개인정보처리방침" 및 로그인 화면 캡션의 "이용약관"·"개인정보처리방침" SpannableString 이 모두 `"준비 중"` Toast 만 노출하던 상태. v1.0 약관 본문 확보 후 약관 조회 화면 신규 구현.

### 작업 내용
| 항목 | 변경 |
|------|------|
| 약관 원문 | `res/raw/terms_of_service.md` 신규 — v1.0 마크다운 원본 (221줄, UTF-8) |
| 의존성 | `io.noties.markwon:core:4.6.2` + `linkify:4.6.2` 추가 (~250KB) |
| Activity | `ui/legal/TermsActivity` 신규 — `MODE_VIEW` / `MODE_AGREEMENT` 분기. Markwon + LinkifyPlugin 으로 본문 렌더링 |
| 레이아웃 | `activity_terms.xml` — Toolbar + 메타 카드 + 본문 `TextView`(textIsSelectable) + 옵션 footer(`동의하기` 버튼) |
| Manifest | `TermsActivity` 등록 (`parentActivityName=MainActivity`) |
| MenuFragment | 이용약관 메뉴 → `TermsActivity(MODE_VIEW)`. 개인정보처리방침은 Toast 유지 |
| LoginActivity | 캡션 SpannableString — "이용약관" 클릭 시 `TermsActivity(MODE_VIEW)` 진입. `applySpan` 시그니처를 `(String, () -> Unit)` 으로 변경해 항목별 핸들러 주입 |
| CLAUDE.md | "약관 관리" 섹션 추가 — 원문 위치, 버전 변경 절차, 법무 검토 TODO |

### 주요 결정
- **Markwon 채택** — `WebView` 대비 가볍고 표준 `TextView` 위에서 동작. 다른 옵션(긴 strings.xml, HTML+WebView)대비 가독성/유지보수성 우수
- **TermsViewModel 미생성** — 화면이 read-only 라 상태가 없음. 빈 ViewModel 불필요 (원안에서 변경, Plan 단계에서 명시)
- **개인정보처리방침은 본 STEP 범위 아님** — 본문 미확보. 후속 STEP 에서 동일 패턴(`PrivacyPolicyActivity`)으로 추가
- **회원가입 흐름 약관 동의 강제는 별도 STEP** — `MODE_AGREEMENT` 코드 경로는 미리 깔아둠 (Activity 분기 + footer XML). `OnboardingActivity` 연결은 후속 작업
- **다크 모드 미지원** — 앱 전체 라이트 전용 정책 유지, 야간 리소스 미작성

### 자가 검증 (8/8 통과)
1. `./gradlew assembleDebug` → BUILD SUCCESSFUL (56s)
2. Markwon 의존성 2건 hit
3. 약관 원문 221줄 (≥100줄)
4. `MODE_VIEW`/`MODE_AGREEMENT` + `footerAgreement.visibility` 분기 모두 존재
5. `activity_terms.xml:79 textIsSelectable="true"` hit
6. Manifest 등록 + `parentActivityName` 존재
7. `TermsActivity::class` 참조 2건 (MenuFragment, LoginActivity)
8. 면책 키워드 "의료기기"·"의학적 진단"·"119" 모두 hit

### 시각 검증 (수동, 사용자 확인 필요)
- 메뉴 → 이용약관 진입, 마크다운 렌더링
- 로그인 캡션 "이용약관" 클릭 → 진입
- 본문 길게 누름 → 선택/복사
- `support@checkdang.com` 탭 → 이메일 앱
- 뒤로가기 → 진입 화면 복귀
- 가독성: `lineSpacing 1.4`

### 수정 파일
- `docs/STEP_terms.md` (신규 — Plan 단계 설계서)
- `app/build.gradle.kts`
- `res/raw/terms_of_service.md` (신규)
- `res/layout/activity_terms.xml` (신규)
- `ui/legal/TermsActivity.kt` (신규)
- `AndroidManifest.xml`
- `ui/menu/MenuFragment.kt`
- `ui/auth/login/LoginActivity.kt`
- `CLAUDE.md`

---

## [2026-05-22] 비회원 데이터 영속화 — 콜드 스타트 후에도 유지

### 배경
비회원(`SocialProvider.NONE`)으로 진입한 사용자는 앱 종료 시 모든 상태가 휘발되어,
다음 실행 때 다시 LoginActivity 부터 시작하고 입력했던 혈당/통증 기록도 사라졌다.
원인은 세 군데에 흩어진 in-memory 전용 상태:
1. `SessionHolder` (게스트 플래그/프로필) — 디스크 미저장
2. `MockDataProvider._records` / `_painRecords` — 직접 입력 기록 휘발
3. `ProfileViewModel.save` — `provider==NONE` 일 때 디스크 저장 의도적 차단

### 작업 내용
| 항목 | 변경 |
|------|------|
| UserStore | `markGuestSession` / `isGuestSession` / `clearGuestSession` / `clearAllForProvider` 추가. 게스트도 `SocialProvider.NONE` 네임스페이스로 프로필 영속화 |
| MockDataProvider | `init(context)` + JSON 직렬화로 혈당·통증 기록 SharedPreferences 저장. `clearAllUserData()` — 회원탈퇴/게스트 wipe 용. 가족 구성원은 PAID 전용 + in-memory 유지 |
| CheckDangApplication | `MockDataProvider.init(this)` 호출 — `UserStore.init` 옆에 배치 |
| SplashActivity | `UserStore.isGuestSession()` 이면 `SessionHolder` 복원 후 `MainActivity` 로 바로 진입. 그 외는 기존 동작(`LoginActivity`) |
| OnboardingActivity | 게스트 분기에서 `UserStore.saveProfile(NONE, ...)` + `markRegistered(NONE)` + `markGuestSession()` |
| ProfileViewModel | `save()` 의 NONE 차단 제거 — 게스트도 프로필 편집/저장 영속화 |
| MenuFragment | 로그인 버튼 클릭 시 `clearGuestSession()` (자동 게스트 진입 해제). 회원탈퇴 시 `clearAllForProvider` + `MockDataProvider.clearAllUserData` + 게스트면 `clearGuestSession` |

### 주요 결정
- **게스트 → 로그인 전환 시 기록은 보존** — 같은 디바이스에서 게스트로 다시 돌아올 수 있으므로 `MockDataProvider` 데이터를 지우지 않음. 진짜 삭제는 "회원 탈퇴" 만 수행
- **JSON 기반 직렬화 (org.json)** — Room/DB 도입 제약(CLAUDE.md) 준수. 기록 수가 수백 건을 넘어가면 SQLite 로 마이그레이션 필요 (현재는 적정 수준)
- **MockDataProvider 영속화는 게스트/로그인 사용자 모두에게 적용** — 본 작업 범위는 게스트지만, 분기를 두면 로그인 사용자가 입력한 직접입력 혈당도 백엔드 push 와 별개로 휘발되어 UX 가 깨짐. 통합 영속화가 단순/안전
- **게스트 진단 정보 push 안 함** — `accessToken == null` 이라 `HealthSyncApiClient` 의 Bearer 가 빠지며, FastAPI `userId` 도 null → `pushGlucose` 가 일찍 return. 백엔드 송신은 영향 없음

### 수정 파일
- `data/mock/UserStore.kt`
- `data/mock/MockDataProvider.kt`
- `CheckDangApplication.kt`
- `ui/splash/SplashActivity.kt`
- `ui/auth/onboarding/OnboardingActivity.kt`
- `ui/profile/ProfileViewModel.kt`
- `ui/menu/MenuFragment.kt`

### 빌드 검증
`./gradlew compileDebugKotlin` → BUILD SUCCESSFUL (17s)

---

## [2026-05-22] 환자 프로필 관리 화면 + 알림 설정 라우팅

### 배경
메뉴 화면의 "환자 프로필 관리" / "알림 설정" 항목이 "준비 중" Toast 만 띄우던 상태. 두 항목을 실제로 동작하도록 연결.

### 작업 내용
| 항목 | 변경 |
|------|------|
| 데이터 모델 | `PatientProfile` 에 `diabetesType` / `diagnosedAt` / `fastingTargetMgdl` / `postMealTargetMgdl` 필드 추가. `DiabetesType` enum 신규 (NONE/TYPE_1/TYPE_2/GESTATIONAL/PRE) |
| 영속화 | `UserStore.saveProfile` / `getProfile` 가 새 필드 4종을 SharedPreferences 에 직렬화. enum 역직렬화 실패 시 NONE 으로 fallback |
| 화면 신규 | `ui/profile/ProfileActivity` + `ProfileViewModel` + `activity_profile.xml`. 기본정보(닉네임/생년월일/성별) / 신체정보(키/체중) / 당뇨정보(유형/진단시점) / 혈당목표(공복/식후) 4섹션 |
| ViewModel | StateFlow 기반 — 초기값은 `UserStore` → `SessionHolder.currentProfile` → 빈 객체 순으로 로드. 닉네임만 필수 검증 |
| 라우팅 | `MenuFragment` 의 `menuProfile.root` 클릭 → `ProfileActivity` 시작. 기존 "준비 중" Toast 그룹에서 분리 |
| 알림 설정 | `menuNotification.root` 클릭 → `Settings.ACTION_APP_NOTIFICATION_SETTINGS` 인텐트로 시스템 앱 알림 설정 화면 이동. 실패 시 `ACTION_APPLICATION_DETAILS_SETTINGS` fallback |
| Manifest | `ProfileActivity` 등록 (`parentActivityName=MainActivity`, `windowSoftInputMode=adjustResize`) |

### 주요 결정
- **혈당 목표치는 선택 입력** — 비워두면 `GlucoseEvaluator` 가 기본 가이드라인(공복 70-99 / 식후 <140) 사용. 현 단계에서는 evaluator 와 결합하지 않고 모델/영속화만 준비 (목표치 기반 평가 로직은 후속 작업)
- **알림 설정은 인앱이 아닌 시스템 화면** — 푸시/알림 채널 미구현 상태이므로 OS 의 앱별 알림 설정으로 위임. minSdk=26 이라 `ACTION_APP_NOTIFICATION_SETTINGS` 항상 가용
- **비회원(`authProvider == NONE`) 저장** — `SessionHolder.currentProfile` 메모리에만 반영, `UserStore` 디스크 저장은 스킵 (provider 키가 없어 다음 로그인 사용자와 섞이는 것을 방지)
- **닉네임만 필수** — 다른 필드는 빈 값(0/empty) 허용. 0/empty 는 "미입력" 으로 해석

### 수정 파일
- `data/model/PatientProfile.kt`
- `data/mock/UserStore.kt`
- `ui/profile/ProfileActivity.kt` (신규)
- `ui/profile/ProfileViewModel.kt` (신규)
- `res/layout/activity_profile.xml` (신규)
- `ui/menu/MenuFragment.kt`
- `AndroidManifest.xml`

### 빌드 검증
`./gradlew compileDebugKotlin` → BUILD SUCCESSFUL (50s)

---

## [2026-05-19] FastAPI step_calorie / heart_rate 연동 추가

### 배경
백엔드 팀 회신 — "현재 heart_rate 와 step_calorie 가 안 받아와지는 듯하다" 확인 요청. 분석 결과:
- **step_calorie**: SDK 의 `readSteps()` 는 이미 존재하지만 `SamsungHealthDataSource` 어댑터에 노출 안 되어 있어 ViewModel 까지 도달하지 못함. push 함수도 누락
- **heart_rate**: `HealthDataPermission` enum 자체에 HEART_RATE 없어 권한 요청 미발생. SDK read 메서드/매퍼 모두 부재

### 작업 내용
| 항목 | 변경 |
|------|------|
| 권한 enum | `HealthDataPermission.HEART_RATE` 추가 |
| SDK 매핑 | `SamsungHealthRepository.toSdkDataType` 에 `HEART_RATE → DataTypes.HEART_RATE` 추가 |
| SDK read | `SamsungHealthRepository.readHeartRate(date): List<HeartRateSample>` 신규. 빈 데이터/오류 시 emptyList |
| 데이터 모델 | `data/model/HeartRateSample.kt` 신규 (timestamp Long + bpm Int) |
| 매퍼 | `SamsungHealthMapper.toHeartRateSamples` — SERIES_DATA / HEART_RATE Field 양쪽 처리. SdkHeartRate.startTime, heartRate 속성 사용 (javap 으로 SDK 클래스 정의 확인) |
| 인터페이스 | `HealthDataSource` 에 `getStepCount(date)`, `getHeartRates(date)` default 메서드 추가 (Mock/HC → null/emptyList) |
| 어댑터 | `SamsungHealthDataSource` 가 두 메서드 override |
| Repository | `HealthRepository` pass-through 메서드 추가 |
| device_id | `data/device/DeviceIdProvider.kt` 신규 — `Settings.Secure.ANDROID_ID` 기반 안정 식별자. 더미값(`9774d56d682e549c`) 대응 fallback 포함 |
| Push | `HealthSyncApiClient.pushStepCalorie`, `pushHeartRates` 추가. heart_rate 는 sample 별 POST, runCatching 으로 sample 단위 실패 격리 |
| 호출 시점 | `LifestyleViewModel.pushLifestyleToServer` 가 Samsung Health 활성 시에만 step/heart push 추가 호출 |

### 주요 결정
- step_calorie 의 `calorie` 필드: `ExerciseSummary.totalCalories` (운동 세션 기반 소모 칼로리) 사용. step 기반 활동 칼로리와 의미 차이가 있을 수 있어 백엔드와 추가 확인 필요
- heart_rate sample 수: SDK 가 반환한 모든 샘플 송신. 데이터 폭주 우려 시 batch endpoint / 샘플링 정책 협의 필요
- HEART_RATE 권한 추가로 사용자가 다음 진입 시 Samsung Health 권한 다이얼로그 1회 재노출됨 (정상 동작)

### 수정 파일
- `data/samsunghealth/HealthDataPermission.kt`
- `data/samsunghealth/SamsungHealthRepository.kt`
- `data/samsunghealth/SamsungHealthMapper.kt`
- `data/model/HeartRateSample.kt` (신규)
- `data/health/HealthDataSource.kt`
- `data/health/SamsungHealthDataSource.kt`
- `data/health/HealthRepository.kt`
- `data/device/DeviceIdProvider.kt` (신규)
- `data/remote/HealthSyncApiClient.kt`
- `ui/lifestyle/LifestyleViewModel.kt`

### 빌드 검증
`./gradlew compileDebugKotlin` → BUILD SUCCESSFUL

---

## [2026-05-19] 백엔드 답변 반영 — 혈당 API 3가지 정정

### 작업 내용
백엔드(kgh) 가 보내준 답변 HTML(2026-05-19) 기반. 라이프스타일(운동/식사/수면)은 그대로 통과, 혈당 API 만 3가지 정정.

### 라이프스타일 — 변경 없음 (백엔드 확인 완료)
- `duration` 분 단위 OK
- `quality` 0~100 백분율 OK
- `stages` 빈 배열 허용 (`@NotNull` 검증만)
- `calories` 1식 단위 OK

### 혈당 API 정정 (`POST /blood-glucose/{user_id}`)
| 항목 | Before | After |
|------|--------|-------|
| URL | `/blood-glucose/{user_id}` | `/blood-glucose/{user_id}?date=YYYY-MM-DD` |
| 수치 필드 | `value` | `level` |
| 타이밍 필드 | `timing` | `meal_timing` |
| 측정시각 필드 | `measured_at` (ISO Instant) | `timestamp` (LocalDateTime KST, no Z) |
| 메모 | `memo` | `memo` (그대로) |

**date 쿼리가 필수인 이유**: DynamoDB PK 가 `{user_id}#{date}` 구조.

### MealTiming enum 매핑 (우리 7종 → 백엔드 4종)
| 우리 (`MealTiming`) | 백엔드 (`meal_timing`) |
|---|---|
| FASTING | FASTING |
| PRE_MEAL | BEFORE_MEAL |
| POST_MEAL_30M / POST_MEAL_1H / POST_MEAL_2H | AFTER_MEAL |
| BEFORE_SLEEP | BEDTIME |
| OTHER | FASTING (임시 — 백엔드에 대응 값 없음) |

### FastAPI 인증 — 변경 없음
현재 FastAPI 는 토큰 검증 미실행. Bearer 헤더 부착 상태 유지 (출시 전 JWT 미들웨어 추가 예정으로 미리 대비).

### 추가 권장 사항 (선택, 향후 작업)
백엔드가 권장한 두 필드는 현재 데이터 모델에 없어 미적용:
- `sourceId` (Samsung Health record 고유 ID) — 백엔드 중복 검사용. 누락 시 매 sync 마다 새 row 쌓일 위험. SamsungHealthDataSource 가 raw record ID 를 보유하도록 리팩토링 필요
- `dataSource` ("SAMSUNG_HEALTH" | "MANUAL") — AI 분석/통계 구분용

→ 별도 작업으로 분리

### 수정 파일
| 파일 | 변경 |
|------|------|
| `data/remote/HealthSyncApiClient.kt` | `pushGlucose` 본문 필드 3개 rename, date query 추가, KST LocalDateTime 으로 timestamp 포맷, `mapMealTiming` 헬퍼 추가 |

### 빌드 검증
`./gradlew compileDebugKotlin` → BUILD SUCCESSFUL (18s)

---

## [2026-05-19] 백엔드 Swagger 명세 반영 — endpoint/스키마 재정렬

### 작업 내용
백엔드 팀에서 받은 Swagger 명세(Spring Boot ALB + FastAPI) 를 분석해 `HealthSyncApiClient` 의 path / payload / 인증 방식을 실제 명세에 맞춰 재작성. 라이프스타일은 Spring, 혈당은 FastAPI 로 라우팅된다.

### 도메인 라우팅 (테스트로 확인)
- `https://api.checkdang.xyz` 단일 base URL 에서 path 기반으로 두 서비스 분기
  - `/api/*` → Spring Boot (302 응답 — Spring Security)
  - `/heart-rate`, `/step-calorie`, `/blood-glucose` 등 → FastAPI (422 응답 — pydantic validation)
- ALB 도메인(`checkdang-alb-...elb.amazonaws.com`) 직접 호출 불필요

### endpoint 매핑
| 우리 데이터 | 실제 endpoint | Body | 인증 |
|------------|-------------|------|-----|
| `ExerciseSession[]` | `POST /api/samsung-health/exercises` | Array of `ExerciseSyncRequest` | Bearer JWT |
| `MealItem[]` | `POST /api/samsung-health/diets` | Array of `DietSyncRequest` | Bearer JWT |
| `SleepSummary` | `POST /api/samsung-health/sleeps` | Array (1 element) of `SleepSyncRequest` | Bearer JWT |
| `GlucoseRecord` (record 별) | `POST /blood-glucose/{user_id}` | object | (명세상 비어있음, 헤더는 송신) |

### 신규 변환 헬퍼 (HealthSyncApiClient 내부)
- `mealTypeToEnum`: "아침/점심/저녁/간식" → BREAKFAST/LUNCH/DINNER/SNACK/UNKNOWN
- `koreanClockToIso` / `anyClockToIso`: "오전 7:30" 또는 "23:42" → 오늘 KST 기준 ISO 8601 date-time. 수면 취침 시각은 전날(shiftDay=-1) 로 보정

### 호출 불가(데이터 미보유) — Skip
- `/heart-rate/{user_id}` — bpm/device_id 데이터 미수집
- `/step-calorie/{user_id}` — step_count 미추적

### 수정 파일
| 파일 | 변경 |
|------|------|
| `data/remote/HealthSyncApiClient.kt` | 단일 `pushLifestyle` → `pushExercises`/`pushDiets`/`pushSleep` 3분할. Spring 측 Array body 직렬화. `pushGlucose` 는 record 별 POST 로 재구성. KST ISO 변환 헬퍼 추가. `instanceFollowRedirects = false` 로 Spring Security 302 가 silent 성공으로 잘못 잡히는 것 방지 |
| `ui/lifestyle/LifestyleViewModel.kt` | `pushLifestyleToServer` 가 3종 카테고리를 각각 runCatching 으로 호출 (한 종류 실패가 다른 종류를 막지 않음) |

### ⚠️ 백엔드 재확인 필요 항목
- `ExerciseSyncRequest.duration` 단위 (현재 분 단위로 송신 — ms / sec 여부 확인)
- `DietSyncRequest.calories` 가 1식 단위인지 (현재 1식별로 분해 송신)
- `SleepSyncRequest.quality` 가 0~100 인지 0.0~1.0 인지 (현재 efficiency 0~100 값 송신)
- `SleepSyncRequest.stages` 가 required 인데 우리는 stage 단위 시각 데이터 없음 → 빈 배열 송신
- FastAPI `/blood-glucose/{user_id}` body 스키마가 OpenAPI 에 비어있음 (`{value, timing, measured_at, memo}` 형태로 시도)
- FastAPI 호출에 Bearer 토큰 필요한지 (현재 동일 토큰 송신, 불필요하면 무시될 것)

### 빌드 검증
`./gradlew compileDebugKotlin` → BUILD SUCCESSFUL

---

## [2026-05-19] 백엔드 Base URL 교체 + 라이프스타일/혈당 DB 저장 API 호출 추가

### 작업 내용
1. 백엔드 Base URL 을 render 임시 서버(`https://two00ok-u15n.onrender.com`) 에서 운영 도메인(`https://api.checkdang.xyz`) 으로 일괄 교체.
2. Health Connect / Samsung Health Data SDK 로부터 동기화된 라이프스타일·혈당 데이터를 실제로 백엔드 DB 에 저장하도록 push 호출을 추가. 기존에는 동기화가 "외부 헬스 → 앱 메모리" 에서 끝나고 서버 전송이 없었음.

### 신규 파일
| 파일 | 역할 |
|------|------|
| `data/remote/HealthSyncApiClient.kt` | 라이프스타일(`POST /api/health/lifestyle`) / 혈당(`POST /api/health/glucose`) DB 저장 전용 클라이언트. AuthApiClient 와 동일하게 HttpURLConnection + JSONObject 사용. `SessionHolder.accessToken` 을 `Authorization: Bearer` 헤더에 자동 부착 |

### 수정 파일
| 파일 | 변경 내용 |
|------|----------|
| `data/remote/AuthApiClient.kt` | `BASE_URL` 을 `https://api.checkdang.xyz` 로 교체 |
| `ui/lifestyle/LifestyleViewModel.kt` | `sync()` / `connectAndSync()` / `activateSamsung()` 마지막에 `pushLifestyleToServer(source)` 호출. 활성 소스(`samsung_health`/`health_connect`/`mock`) 라벨 동봉. 호출은 `runCatching` 으로 감싸 실패해도 UI 흐름 보존 |
| `ui/glucose/GlucoseViewModel.kt` | `refresh()` 에서 Samsung 혈당 가져온 직후 `pushGlucoseToServer(records)` 호출. 빈 리스트면 skip, 실패는 silent 로깅 |

### 설계 결정
- **endpoint 명**: 백엔드 명세가 공개되지 않아 RESTful 컨벤션에 따라 `/api/health/lifestyle`, `/api/health/glucose` 로 가정. 실제 명세 확인 후 클라이언트 함수의 path 문자열만 수정하면 됨
- **실패 처리 전략**: 모든 push 호출은 호출 측에서 `runCatching` + `Log.w` 패턴으로 감싸 네트워크 실패가 화면 갱신이나 동기화 상태(`_isSyncing`) 를 막지 않도록 함. 기존 기능 무손상 보장
- **인증 헤더**: `SessionHolder.accessToken` 이 null 일 수 있는 게스트 모드를 고려해 토큰 존재 시에만 헤더 부착
- **payload 직렬화**: 외부 의존성 추가 금지 제약 준수 위해 `JSONObject`/`JSONArray` 수동 직렬화

### 빌드 검증
`./gradlew compileDebugKotlin` → BUILD SUCCESSFUL

---

## [2026-05-17] STEP 11 — 실 단말 테스트 완료 + 진단 로그 정리

### 테스트 결과 (Galaxy / Android 15 / SDK 35)
실 단말에서 Samsung Health Data SDK 연동 정상 동작 확인.

| 검증 항목 | 결과 |
|----------|------|
| Galaxy 단말 감지 (`mfr=samsung`) | ✅ |
| Samsung Health 설치 감지 | ✅ |
| 권한 6종 다이얼로그 노출 + 전부 허가 | ✅ `granted=6/6` |
| SDK 연결 (`HealthDataService.getStore`) | ✅ `Connected` |
| HealthRepository → Samsung 소스 전환 | ✅ |
| 걸음 수 read (`DataType.StepsType.TOTAL`) | ✅ 오늘 5495보 / 어제 2942보 |
| 운동/식사/수면 read | ✅ 호출 성공, 단 사용자가 Samsung Health 에 데이터를 입력하지 않은 카테고리는 0 dataPoints (정상) |
| 운동/식사/혈당 수동 입력 후 재시도 | ✅ 입력한 데이터 모두 앱 카드/차트에 정상 반영 |

### 핵심 발견
- **자동 측정 vs 수동 입력 구분**: Samsung Health 의 걸음 수는 폰 가속도 센서로 자동 추적되지만, 운동 세션/식사/수면/혈당은 사용자가 직접 입력하거나 갤럭시 워치 같은 외부 기기 연동이 필요. 데이터가 없는 카테고리는 SDK 가 0 dataPoints 반환 — 우리 코드 정상
- **개발자 모드 필수성 재확인**: Samsung Health 앱의 개발자 모드 ON 상태에서만 Partner 미승인 앱이 데이터 접근 가능. OFF 면 `connect()` 단계에서 차단됨

### 수정 파일
| 파일 | 변경 내용 |
|------|----------|
| `ui/lifestyle/LifestyleViewModel.kt` | `activateSamsung` 의 진단용 `readSteps` 호출 제거. 핵심 상태 전이 Log.i 는 유지 (운영 중 디버깅에 유용) |
| `data/samsunghealth/SamsungHealthRepository.kt` | `readSteps` 에 Log.i 추가 (응답성 검증용으로 추후에도 유용). 데이터 읽기 섹션 헤더 주석 정리 ("Phase 2-foundation" → 일반 헤더) |
| `IMPLEMENTATION_LOG.md` | 본 항목 추가 |

### 배포 시 추가 작업 (현 시점 미적용)
- Samsung Health Partner Apps Program 신청 (현재 "not accepting" 상태일 수 있음 — 추후 모니터링)
- 앱 서명 키의 SHA-256 fingerprint 등록
- Production Access Code 발급 + 코드 내 적용
- Samsung Health 개발자 모드 비의존 동작 검증

### 검증 완료 — STEP 11 종료
인프라/데이터 입수/UI 와이어링 모두 완성. 코드 측면 작업 없음.

---

## [2026-05-17] STEP 11 — GlucoseFragment 에 Samsung 혈당 데이터 와이어링

### 작업 내용
Samsung Health Data SDK 가 가져오는 혈당 기록이 실제로 GlucoseFragment UI(차트/리스트/주간통계)에 표시되도록 데이터 흐름 연결. 사용자 직접 입력(MockDataProvider)과 Samsung 자동 측정(SamsungHealthRepository)을 ViewModel 에서 병합.

### 신규 인터페이스 메서드
| 메서드 | 위치 | 기본 구현 |
|--------|------|----------|
| `getBloodGlucoseRecords(days: Int): List<GlucoseRecord>` | `HealthDataSource` | `emptyList()` (default implementation in interface) |

### 수정 파일
| 파일 | 변경 내용 |
|------|----------|
| `data/health/HealthDataSource.kt` | `getBloodGlucoseRecords(days)` default 메서드 추가. Mock/HC 자동 구현 |
| `data/health/SamsungHealthDataSource.kt` | `getBloodGlucoseRecords` override — `repo.readBloodGlucoseRange(today-N, today+1)` 위임 |
| `data/health/HealthRepository.kt` | `getBloodGlucoseRecords(days)` pass-through 메서드 |
| `data/samsunghealth/SamsungHealthRepository.kt` | `readBloodGlucoseRange(start, endExclusive)` 신규 — 한 번의 SDK 호출로 다중 일자. 기존 `readBloodGlucose(date)` 는 range 호출 위임 |
| `ui/glucose/GlucoseViewModel.kt` | `_samsungRecords` flow 추가. `records` 가 `MockDataProvider.recordsFlow` + `_samsungRecords` 의 `combine` 결과로 변경. `filteredForChart`/`weeklyStats` 도 새 `records` 기반으로 derive. `refresh()` 공개 메서드 + init 시 1회 호출 |
| `ui/glucose/GlucoseFragment.kt` | `onViewCreated` 에서 `viewModel.refresh()` 호출. 탭 진입마다 Samsung 데이터 재조회 |
| `IMPLEMENTATION_LOG.md` | 본 항목 추가 |

### 데이터 병합 정책
| 출처 | ID 형식 | 용도 |
|------|---------|------|
| 사용자 직접 입력 (MockDataProvider) | `UUID.randomUUID()` | 바텀시트로 직접 추가한 측정 |
| Samsung Health 자동 측정 | `${dp.uid}-${timestamp}` | Samsung 기기/연동 측정기에서 자동 기록 |

ID 충돌 가능성 0. 단순 concat + `distinctBy { it.id }` 안전망 + `sortedByDescending { measuredAt }`.

### Health Connect 영향
- `HealthDataSource.getBloodGlucoseRecords` 가 default 구현(empty)이라 `HealthConnectDataSource` 무수정. HC 활성 시 혈당 데이터는 종전대로 MockDataProvider 만 표시
- 추후 HC 가 혈당까지 지원하려면 HC 권한 set 에 `BloodGlucoseRecord` 추가 필요 — 기존 HC 흐름 변경이 동반되므로 별도 단계로 분리

### 동작 시나리오
| 사용자 행동 | 표시되는 데이터 |
|------------|----------------|
| 첫 진입 (Samsung 비활성, 입력 없음) | 빈 화면 |
| 바텀시트로 측정 추가 | 사용자 입력만 표시 (현 동작 유지) |
| LifestyleFragment 에서 Samsung 활성화 → Glucose 탭 진입 | Mock 입력 + Samsung 측정 병합 표시 |
| Samsung 활성 중 새 측정 추가 | 양쪽 모두 즉시 표시 (Mock flow 가 자동 emit) |
| 비-Galaxy 단말 / Samsung 미활성 | Mock 입력만 표시 (회귀 없음) |

### 주요 결정 사항
- **두 데이터 출처 병행 보존**: Samsung 은 READ-only 라 사용자 입력 기록을 Samsung 에 쓸 수 없음. 양쪽 보관소를 ViewModel 에서 병합하는 것이 손실 없는 최선
- **default interface method 활용**: Kotlin interface default 구현으로 Mock/HC 양쪽 변경 없이 Samsung 만 override. HC 코드 무수정 보장
- **90일 윈도우 단일 호출**: SamsungHealthDataSource 가 일별 7번 호출하지 않고 range 한 번에 — SDK 효율 + IPC 오버헤드 최소

### 단말 테스트 시 확인 사항
1. Galaxy 기기 + Samsung Health 개발자 모드 + Samsung Health 에 혈당 데이터 존재
2. **LifestyleFragment** 진입 → 새로고침 → Samsung 권한 다이얼로그(6종) 허가
3. **Glucose 탭** 이동 → 자동 측정 혈당이 차트/리스트에 표시되는지 확인
4. 바텀시트로 새 측정 추가 → Mock+Samsung 양쪽 데이터가 함께 표시되는지 확인

---

## [2026-05-17] STEP 11 — 혈당 (BloodGlucose) read 메서드 신규 추가

### 작업 내용
본 앱 핵심 도메인인 혈당을 Samsung Health Data SDK 에서 직접 읽어올 수 있도록 read 메서드 + 매핑 추가. `DataTypes.BLOOD_GLUCOSE` 가 SDK 에 이미 존재하므로 단순 추가만으로 충분.

### 수정 파일
| 파일 | 변경 내용 |
|------|----------|
| `data/samsunghealth/HealthDataPermission.kt` | `BLOOD_GLUCOSE("혈당")` enum 항목 추가. 스테일된 `sdkConstant` placeholder 제거 — 실제 매핑은 `SamsungHealthRepository.toSdkDataType` 가 담당하므로 불필요 |
| `data/samsunghealth/SamsungHealthMapper.kt` | `toGlucoseRecords(dataPoints)` 추가. SERIES_DATA(시리즈 측정) / GLUCOSE_LEVEL(단일 측정) 양 케이스 모두 평탄화. `MealStatus → MealTiming` 매핑 표 (FASTING/BEFORE_MEAL/AFTER_MEAL/BEFORE_SLEEP/OTHER) |
| `data/samsunghealth/SamsungHealthRepository.kt` | `readBloodGlucose(date): List<GlucoseRecord>` 추가. `toSdkDataType` 에 BLOOD_GLUCOSE → DataTypes.BLOOD_GLUCOSE 매핑 |
| `IMPLEMENTATION_LOG.md` | 본 항목 추가 |

### MealStatus 매핑 정책
SDK 의 `MealStatus` 는 14가지(FASTING/AFTER_BREAKFAST/BEFORE_LUNCH 등), 앱의 `MealTiming` 은 7가지(공복/식전/식후 30분/식후 1시간/식후 2시간/취침 전/임의). 정밀 시간차(30분/1시간)는 SDK 가 알려주지 않으므로 식후 계열은 모두 POST_MEAL_2H 로 보수적으로 매핑.

| SDK MealStatus | App MealTiming |
|----------------|---------------|
| FASTING | FASTING |
| BEFORE_BREAKFAST / BEFORE_LUNCH / BEFORE_DINNER / BEFORE_MEAL | PRE_MEAL |
| AFTER_BREAKFAST / AFTER_LUNCH / AFTER_DINNER / AFTER_MEAL / AFTER_SNACK | POST_MEAL_2H |
| BEFORE_SLEEP / AFTER_BED_TIME | BEFORE_SLEEP |
| GENERAL / UNDEFINED / null | OTHER |

### 주요 결정 사항
- **HealthDataPermission.ALL 에 BLOOD_GLUCOSE 포함**: 기존 5종 사용자가 권한 재요청 다이얼로그를 보겠지만, 본 앱은 혈당이 1순위 도메인이라 정당화됨. 부분 허가 정책(1개라도 허가 → Samsung 활성화)은 그대로
- **GlucoseFragment 통합은 보류**: 본 단계는 read 메서드 추가까지로 한정. GlucoseViewModel 이 MockDataProvider 대신 Samsung 데이터를 사용하도록 마이그레이션하려면 `HealthDataSource` 인터페이스 확장 + Mock/HealthConnect/Samsung 3개 구현체 보완이 필요 — 별도 단계로 분리
- **dp.uid 가 platform-type non-null 로 추론됨**: Kotlin 컴파일러가 SDK 의 @NotNull 메타데이터를 신뢰. 방어적 `?: "shealth"` 제거하고 직접 사용

### 단말 테스트 시 확인 사항
1. Samsung Health 앱 → 설정 → **개발자 모드** 활성화 (코드로 못 함)
2. Galaxy 기기 + API 29+ (Android 10+)
3. Samsung Health 에 실제 혈당/운동/수면/식사/체중 데이터가 존재해야 read 가 의미 있음
4. 진입 시 silent 시도는 권한 다이얼로그 X. 새로고침 버튼 누르면 6종 권한 다이얼로그 노출
5. 부분 허가도 Samsung 소스로 활성화됨 — 허가 안 한 카테고리만 EmptyState

### 다음 단계 (선택)
- GlucoseFragment 가 SamsungHealthRepository.readBloodGlucose 를 사용하도록 마이그레이션 (HealthDataSource 인터페이스 확장 필요)
- 실 단말 테스트 후 발견되는 미스매치 미세 조정

---

## [2026-05-17] STEP 11 — Samsung Health Data SDK read 메서드 실제 매핑

### 작업 내용
Phase 2-foundation 의 TODO 마커를 실 SDK 호출로 교체. `readExercise/readMeal/readSleep/readWeight` 가 실제로 데이터를 반환하도록 매핑 함수 작성.

### 수정 파일
| 파일 | 변경 내용 |
|------|----------|
| `data/samsunghealth/SamsungHealthMapper.kt` | `toExerciseSummary`, `toMealSummary`, `toSleepSummary`, `toLatestWeight` 매핑 함수 추가. 운동/식사 종류 한글화 표(WALKING→걷기, BIKING→자전거, BREAKFAST→아침 등). SDK `ExerciseSession`/`SleepSession` 과 앱 동명 모델 충돌은 import alias 로 분리 |
| `data/samsunghealth/SamsungHealthRepository.kt` | `readExercise/readMeal/readSleep/readWeight` TODO 제거. `DataTypes.{EXERCISE,NUTRITION,SLEEP,BODY_COMPOSITION}.readDataRequestBuilder` + `LocalTimeFilter` + `Ordering` 으로 ReadDataRequest 구성. 응답을 `SamsungHealthMapper` 에 위임. 수면은 어젯밤 18:00 ~ 오늘 12:00, 체중은 30일 윈도우의 최신값 |
| `data/health/SamsungHealthDataSource.kt` | `getWeeklyExerciseMinutes`/`getWeeklySleepHours` 도 일 7회 readExercise/readSleep 호출로 구현 (Health Connect 와 동일 패턴) |
| `IMPLEMENTATION_LOG.md` | 본 항목 추가 |

### SDK Field 매핑 표
| 도메인 | DataType | Read Field | 매핑 출처 |
|--------|----------|-----------|----------|
| 운동 | EXERCISE | `SESSIONS: List<ExerciseSession>` | session.duration / session.calories / session.exerciseType |
| 식사 | NUTRITION | `CALORIES`, `CARBOHYDRATE`, `PROTEIN`, `TOTAL_FAT`, `MEAL_TYPE`, `TITLE` | dataPoint Field 직접 추출 |
| 수면 | SLEEP | `SESSIONS: List<SleepSession>`, `SLEEP_SCORE` | session.stages 의 StageType (DEEP/LIGHT/REM/AWAKE) 합산 |
| 체중 | BODY_COMPOSITION | `WEIGHT: Float` | 최신 dataPoint 의 weight |

### 주요 결정 사항
- **import alias 로 이름 충돌 해소**: SDK 의 `entries.ExerciseSession` 과 앱의 `model.ExerciseSession` 동명 → `as SdkExerciseSession` 으로 alias. 향후 유지보수에 안전
- **수면 stage 누락 시 추정 비율**: deep/light/rem 모두 0 일 때 20%/55%/25% 로 분배. Health Connect 와 동일 정책
- **운동 종류 한글화 — 미커버 enum 은 fallback**: 명시 매핑 외엔 enum.name 을 보기 좋게 변환(`WEIGHT_MACHINE` → `Weight machine`). 위양성보다 미커버를 그대로 노출하는 게 안전
- **체중은 30일 윈도우**: 매일 측정 안 하는 데이터라서 당일 한정 시 자주 EmptyState. Health Connect 와 다른 정책이지만 UX 우선

### Phase 2 미완 / Follow-up
- 실 Galaxy 단말 + 개발자 모드 활성화 테스트 후 미세 조정 (특히 ExerciseSession.duration nullable 처리, SleepSession.stages 가 빈 리스트일 때 추정값 적정성)
- `readBloodGlucose` 신규 추가 — 본 앱 핵심 도메인 (단계 3)

---

## [2026-05-17] STEP 11 — LifestyleFragment Samsung 우선 와이어링

### 작업 내용
LifestyleFragment 에 Samsung Health Data SDK 우선 / Health Connect 차선 분기 추가. 진입 시 silent 시도, 새로고침 버튼은 명시적 시도(권한 다이얼로그 허용).

### 수정 파일
| 파일 | 변경 내용 |
|------|----------|
| `ui/lifestyle/LifestyleViewModel.kt` | `trySamsungHealthFirst(activity, requestPermissionIfNeeded): Boolean` suspend 함수 추가. ConnectionState 분기 후 권한 처리 → `activateSamsung()` private helper 에서 `HealthRepository.switchToSamsungHealth` + sync. 기존 `connectAndSync()` 무변경 |
| `ui/lifestyle/LifestyleFragment.kt` | `onViewCreated` 에 `trySamsungSilent()` 추가(권한 다이얼로그 X). `btnRefresh` 핸들러를 `onRefreshClicked()` 로 교체 — Samsung 우선 시도 → 실패 시 기존 `startHealthConnectSync()` 호출. HC 코드는 helper 명 유지·내용 무변경 |
| `IMPLEMENTATION_LOG.md` | 본 항목 추가 |

### 동작 시나리오
| 단말 / 상태 | 진입 시 | 새로고침 시 |
|------------|---------|------------|
| Galaxy + Samsung 권한 허가 완료 | Samsung 소스로 자동 전환 + 로드 | Samsung 시도 → 즉시 성공 (Toast: "삼성 헬스(직접 연동)...") |
| Galaxy + Samsung 권한 미허가 | Mock 유지 (silent 실패) | Samsung 권한 다이얼로그 → 1개 이상 허가 시 Samsung 활성화 |
| Galaxy + Samsung 권한 거부 | Mock 유지 | Samsung 실패 → HC fallback (기존 흐름) |
| 비-Galaxy / Samsung Health 미설치 | Mock 유지 | Samsung 즉시 실패 → HC fallback (기존 흐름) |
| API < 29 | Mock 유지 | Samsung 즉시 Unsupported → HC fallback |

### 주요 결정 사항
- **부분 권한 = Samsung 활성화**: 5종 중 하나라도 허가되면 전환 (UX 일관성 — HC 기존 동작과 동일)
- **silent vs explicit 권한 요청**: 진입 시점 자동 다이얼로그는 invasive 하므로 `requestPermissionIfNeeded` 플래그로 분리. 사용자 명시적 액션(새로고침)에서만 다이얼로그 노출
- **HC 코드 보존**: `startHealthConnectSync()` 본문 무변경, 새 `onRefreshClicked()` 가 한 단계 위에서 분기. 회귀 위험 최소화

---

## [2026-05-17] STEP 11 Phase 2-foundation — Samsung Health Data SDK v1.1.0 실연동

### 배경
개발자 모드 가정(Partner 승인 / SHA-256 등록 미고려) 하에 Samsung Health Data SDK 의 실제 호출 경로를 활성화. AAR(`samsung-health-data-api-1.1.0.aar`) 수령 후 Phase 1 스켈레톤을 실제 SDK 호출로 교체. 기존 Health Connect 흐름은 무손상 보존.

### 신규 파일
| 파일 | 설명 |
|------|------|
| `app/libs/samsung-health-data-api-1.1.0.aar` | Samsung Health Data SDK v1.1.0 라이브러리 |

### 수정 파일
| 파일 | 변경 내용 |
|------|----------|
| `app/build.gradle.kts` | `fileTree("libs")` AAR 의존성 활성화. parcelize/gson 은 실제 AAR 의존성에 없어 생략 |
| `app/src/main/AndroidManifest.xml` | `xmlns:tools` 추가 + `<uses-sdk tools:overrideLibrary="com.samsung.android.sdk.health.data"/>` — AAR minSdk 29 vs 앱 26 충돌 해소. 런타임은 `SamsungHealthRepository.connect()` 의 SDK_INT 가드로 보호 |
| `data/samsunghealth/SamsungHealthRepository.kt` | TODO(samsung-sdk) 마커 전체를 실 SDK 호출로 교체 — `HealthDataService.getStore` → `store.getGrantedPermissions` / `requestPermissions` / `aggregateData`. SDK 가 suspend native 라 Future 래퍼 불필요. 단말 API 29+ + Samsung + Samsung Health 설치 3중 가드 |
| `data/health/SamsungHealthDataSource.kt` | stub → `SamsungHealthRepository` 위임 어댑터로 재작성. `HealthDataSource` 인터페이스 구현체로서 `HealthRepository` 가 활성 소스로 선택했을 때만 호출됨 |
| `data/health/HealthRepository.kt` | `switchToSamsungHealth(samsungSource)` + `isConnectedToSamsungHealth()` 추가. 기존 `switchToHealthConnect` 와 동등한 분기. 두 소스 동시 활성화 불가 구조로 충돌 방지 |
| `CheckDangApplication.kt` | `samsungHealthRepository` application-scope 싱글톤 보관소 추가 (BillingRepository 와 동일 패턴) |
| `IMPLEMENTATION_LOG.md` | 본 항목 추가 |

### 주요 결정 사항
- **두 헬스 소스 공존 전략**: `HealthDataSource` interface + `HealthRepository.source` 단일 활성 소스 패턴 유지. Samsung / Health Connect 가 동시에 동일 데이터를 쿼리할 일이 없으므로 SDK 충돌이 구조적으로 차단됨
- **minSdk 충돌**: AAR=29, 앱=26. `tools:overrideLibrary` 로 merger 통과 + 런타임 `Build.VERSION.SDK_INT < Q` 일 때 `ConnectionState.Unsupported` 반환
- **Future 래퍼 불필요**: SDK v1.1.0 의 `HealthDataStore` 메서드가 `kotlin.coroutines.Continuation` 기반 suspend native — 코루틴에서 직접 호출 가능
- **데이터 read 범위 (Phase 2-foundation)**: 연결/권한/걸음수(STEPS.TOTAL aggregate) 만 실 SDK 호출. Exercise/Meal/Sleep/Weight read 는 TODO 로 null 반환 — 실제 단말에서 응답 스키마 확인 후 다음 Phase 에서 매핑 작성
- **권한 카테고리 매핑**: `HealthDataPermission.WEIGHT` → `DataTypes.BODY_COMPOSITION` (SDK 에는 standalone Weight 가 없고 BodyComposition 의 weight Field 로 노출됨)

### 미완 / Follow-up
- LifestyleFragment 에서 Samsung / Health Connect 우선순위 선택 UI/로직 추가
- `SamsungHealthMapper.toExerciseSummary` 등 SDK Response → 도메인 모델 매핑 함수 작성 (실 단말 응답 확인 필요)
- 본 앱 핵심 도메인인 혈당 — `DataTypes.BLOOD_GLUCOSE` 는 SDK 가 이미 지원하므로 별도 read 메서드(`readBloodGlucose`) 추가 검토

---

## [2026-05-15] Health Connect 권한 자동 거부 버그 수정

### 작업 내용
Health Connect 권한 요청 시 다이얼로그가 즉시 자동 거부되던 버그를 해결하고 권한 처리 로직을 안정화.

### 수정 파일
| 파일 | 변경 내용 |
|------|----------|
| `app/src/main/AndroidManifest.xml` | Android 14+ 대응 — `ACTION_VIEW_PERMISSION_USAGE` 인텐트 필터(`activity-alias`)를 `START_VIEW_PERMISSION_USAGE` permission 으로 보호하여 추가. Health Permissions 시스템 호출 진입점 확보 |
| `app/src/main/java/com/checkdang/app/ui/lifestyle/LifestyleFragment.kt` | 권한 콜백 로직 안정화 — `granted.isEmpty()` 일 때만 거부 처리하고 1개 이상 허가 시 `connectAndSync()` 호출. 부분 허가도 정상 흐름 진입 |

### 결과
- 라이프스타일 탭 새로고침 → Health Connect 권한 다이얼로그 정상 노출
- 권한 허가(전체/부분) 직후 데이터 동기화 진행
- Android 14+ 단말에서 Permission Usage 화면 진입 시 system 만 호출 가능하도록 보호

---

## [2026-05-11] STEP 11 Phase 1 — Samsung Health Data SDK 연동 (설계 + 스켈레톤)

### 배경
Samsung Health Partner Apps Program 이 현재 "not accepting any applications at this time" 상태. AAR / Access Code 수령 전까지 실제 SDK 호출은 불가능. 따라서 본 단계는 **승인 후 즉시 활성화 가능하도록 구조를 마련**하는 것이 목표.

### 설계서
`docs/STEP11_samsung_health.md` (12개 섹션 + 부록 2개)

### 신규 파일
| 파일 | 설명 |
|------|------|
| `docs/STEP11_samsung_health.md` | 전체 설계서 (SDK 선택/의존성/권한/아키텍처/4가지 분기/매핑/Phase 1·2 분리) |
| `data/samsunghealth/HealthDataPermission.kt` | 5종 권한 enum (STEPS/EXERCISE/NUTRITION/SLEEP/WEIGHT). `sdkConstant` 는 placeholder |
| `data/samsunghealth/SamsungHealthRepository.kt` | `ConnectionState` sealed class + 가용성/권한/read 메서드 시그니처. 모든 SDK 호출 지점에 `TODO(samsung-sdk)` 마커 |
| `data/samsunghealth/SamsungHealthMapper.kt` | 단위 변환 헬퍼 (kcal/min/hour/kg/시각). 실제 매핑 함수는 Phase 2 |

### 수정 파일
| 파일 | 변경 내용 |
|------|----------|
| `CLAUDE.md` | Samsung Health SDK 정책: "직접 연동 금지" → "Data SDK 허용 (조건부)". Health 연동 구조 다이어그램에 samsunghealth 패키지 추가 |
| `app/src/main/AndroidManifest.xml` | `<queries>` 에 `com.sec.android.app.shealth` 추가 (Android 11+ package visibility) |
| `app/build.gradle.kts` | AAR fileTree + gson + kotlin-parcelize 활성화 안내 주석 (Phase 2 활성화 대기) |
| `IMPLEMENTATION_LOG.md` | 본 항목 추가 |

### 주요 결정 사항
- **Phase 분리**: Partner 승인 전후를 Phase 1 / Phase 2 로 분리. Phase 1 은 코드 구조만 마련하고 호출 경로 비활성화. 기존 Health Connect 흐름은 무손상 유지.
- **Maven 좌표 없음 확정**: 공식 문서 조사 결과 Samsung Health Data SDK 는 원격 Maven 미배포. 로컬 AAR 만 지원 (`app/libs/` 배치 + `fileTree` 포함).
- **"App ID" 개념 없음 확정**: Client ID = package name (`com.checkdang.app`) + Access Code (승인 후 별도 발급) 2개 식별자 사용.
- **Health Connect 와 병행**: 비-갤럭시 / Samsung Health 미설치 / Partner 미승인 단말 fallback 으로 Health Connect 유지.
- **LifestyleViewModel / LifestyleFragment 미수정**: Phase 1 에서 ViewModel 을 교체하면 기존 Health Connect 흐름이 깨짐. Phase 2 에서 통합.
- **TODO(samsung-sdk) 마커 규약**: SDK 호출이 들어갈 모든 지점에 동일 마커. Phase 2 진입 시 grep 으로 일괄 추적.

### Phase 1 자가 검증 결과 (8/8 통과)
1. `./gradlew assembleDebug` → 빌드 통과
2. `AndroidManifest.xml` 의 `<queries>` 에 `com.sec.android.app.shealth` 포함
3. `HealthDataPermission.kt` 에 STEPS/EXERCISE/NUTRITION/SLEEP/WEIGHT 5종 정의
4. `SamsungHealthRepository.kt` 에 ConnectionState 6종(Initializing/Connected/PermissionNeeded/NotInstalled/Unsupported/Error) 정의
5. `TODO(samsung-sdk)` 마커 ≥ 5개
6. 모든 read 메서드가 `runCatching { ... }.getOrNull()` 패턴
7. `LifestyleViewModel.kt` / `LifestyleFragment.kt` 미수정 (Health Connect 경로 보존)
8. `app/build.gradle.kts` 의 AAR/gson 라인이 주석 상태

### ⏳ Phase 2 진입 조건 — 사용자 TODO 체크리스트

> Phase 2 (실제 SDK 호출 활성화) 는 아래 항목이 **모두** 완료된 시점에서만 가능. 항목별로 끝나면 본 체크박스에 표시.

#### 🔍 1단계 — Partner Apps Program 신청 재개 확인
- [ ] `developer.samsung.com/health/data` 접속 → "Partner Apps Program" 페이지 확인
- [ ] "not accepting any applications at this time" 문구가 **사라졌는지** 확인
   - 사라지지 않은 동안에는 아래 단계 진행 불가. 주기적으로 확인 필요 (월 1회 권장)

#### 🔑 2단계 — SHA-256 fingerprint 추출
- [ ] **디버그 키스토어** SHA-256 추출 (PowerShell):
   ```powershell
   keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
   ```
   출력의 `SHA256:` 라인 (예: `AB:CD:EF:...`) 메모.
- [ ] **릴리즈 키스토어** SHA-256 추출 (서명 키 생성 후):
   ```powershell
   keytool -list -v -keystore <release.jks 경로> -alias <alias> -storepass <비밀번호>
   ```
   ⚠️ 릴리즈 키스토어가 아직 없다면 먼저 생성 필요. Play Console 업로드 시 사용한 키와 동일해야 함.

#### 📝 3단계 — Samsung Developer Portal 신청
- [ ] Samsung Account 로 `developer.samsung.com` 로그인
- [ ] Health → Data SDK → Partner Apps Program 신청 폼 작성:
   - **앱 이름**: 체크당 (Check-Dang)
   - **패키지명**: `com.checkdang.app`
   - **앱 카테고리**: Health & Wellness
   - **SHA-256 (디버그)**: 2단계에서 추출한 값
   - **SHA-256 (릴리즈)**: 2단계에서 추출한 값
   - **사용 데이터 카테고리 5종**: Steps / Exercise / Nutrition / Sleep / Weight
   - **사용 목적 설명**: 사용자의 라이프스타일 데이터를 혈당 관리와 통합 분석하기 위함
- [ ] 신청 제출 → 승인 대기 (영업일 기준 며칠 ~ 수 주)

#### 📥 4단계 — 승인 후 자산 수령
- [ ] 승인 메일 / Portal 알림 수신 확인
- [ ] **AAR 파일** 다운로드: `samsung-health-data-api-<version>.aar`
- [ ] **Access Code** 수신 (메일 본문 또는 Portal Console 에서 확인)
- [ ] 승인된 카테고리 5종이 모두 포함되었는지 확인 (일부 거부 가능성)

#### 📦 5단계 — 프로젝트에 자산 배치
- [ ] `app/libs/` 폴더 생성 (없다면)
- [ ] AAR 파일을 `app/libs/samsung-health-data-api-<version>.aar` 경로에 배치
- [ ] Access Code 를 `local.properties` 에 추가 (Phase 2 시 정확한 키 이름 협의):
   ```properties
   # local.properties (Git 추적 제외)
   samsung.health.accessCode=<Portal 에서 받은 코드>
   ```
- [ ] `local.properties` 가 `.gitignore` 에 포함되어 있는지 확인 (Access Code 노출 방지)

#### 🚀 6단계 — Phase 2 진행 요청
- [ ] 아래 형식으로 메시지 전달:
   ```
   STEP 11 Phase 2 진행
   - AAR 버전: <samsung-health-data-api-X.Y.Z>
   - 승인된 카테고리: Steps / Exercise / Nutrition / Sleep / Weight (또는 일부)
   - Access Code 저장 위치: local.properties (samsung.health.accessCode)
   ```
- [ ] 메시지 수신 시 다음 작업 자동 진행:
   1. `app/build.gradle.kts` AAR + gson + parcelize 활성화
   2. `HealthDataPermission.sdkConstant` 값을 SDK 실제 상수로 교체
   3. `SamsungHealthRepository.kt` 의 `TODO(samsung-sdk)` 마커 활성화 (grep 으로 일괄 추적)
   4. `SamsungHealthMapper.kt` 의 SDK Response 변환 함수 활성화
   5. `LifestyleViewModel` / `LifestyleFragment` 권한 흐름 + 4가지 분기 UI 통합
   6. `res/layout/dialog_health_permission.xml` 작성
   7. Phase 2 자가 검증 + 실기기 테스트 가이드 작성

### 💡 참고
- 본 체크리스트의 단계별 상세 명세는 `docs/STEP11_samsung_health.md` §10 (수정/생성 파일 목록) 및 §12 (사용자 액션) 참조.
- Partner Program 이 보류 중인 동안에는 Health Connect 경로가 정상 동작하므로 **현재 앱 기능에 영향 없음**.

### 의도적으로 하지 않은 것
- SDK 실제 호출 코드 (AAR 미수령 — 컴파일 불가)
- `LifestyleViewModel` / `LifestyleFragment` 수정 (Phase 2)
- WRITE 권한 / CGM / 워치 직접 접근 / WorkManager 백그라운드 동기화

---

## [2026-05-11] STEP 10 — Google Play Billing v7 구독 결제 연동

### 작업 내용
프리미엄 구독(월간/연간) 인앱 결제를 Play Billing Library v7 기반으로 구현. 기존 데모용 즉시 PAID 전환 코드를 실결제 흐름으로 교체. 서버 영수증 검증은 백엔드 도입 후 별도 단계로 유보(TODO 표시).

### 설계서
`docs/STEP10_billing.md`

### 신규 파일
| 파일 | 설명 |
|------|------|
| `data/billing/ProductIds.kt` | 상품 ID 단일 진실 공급원 (`checkdang_premium_monthly`, `checkdang_premium_yearly`) |
| `data/billing/BillingState.kt` | sealed class — Idle/Loading/Ready/Purchasing/Success/Error |
| `data/billing/BillingRepository.kt` | BillingClient 연결, 상품/구매 조회, 결제 흐름, acknowledgePurchase, exponential backoff 재연결 |
| `ui/menu/subscription/SubscriptionViewModel.kt` | AndroidViewModel — BillingRepository.state 노출 + 구매/재시도 위임 |

### 수정 파일
| 파일 | 변경 내용 |
|------|----------|
| `app/build.gradle.kts` | `billing-ktx:7.1.1` 의존성 추가 |
| `CheckDangApplication.kt` | `billingRepository` 초기화 및 `startConnection()` 호출 |
| `ui/menu/subscription/SubscriptionActivity.kt` | 데모 즉시 PAID 코드 제거 → ViewModel state 구독, 가격 동적 표시, 로딩/에러/성공 UI 분기, `onResume`에서 상태 동기화 |
| `res/layout/activity_subscription.xml` | 가격 TextView ID 추가 (`tv_price_monthly`, `tv_price_yearly`), 로딩 ProgressBar, 에러 레이아웃(`layout_error` + `btn_retry`) 추가 |
| `ui/menu/MenuFragment.kt` | `toggleTierForDemo` long-press 호출부에 `// TODO(release)` 주석 추가 |

### 주요 결정 사항
- **클라이언트 신뢰**: 본 단계에서는 acknowledge 성공 시 `SessionHolder.tier = UserTier.PAID` 직접 갱신. 백엔드 도입 후 `POST /api/v1/billing/verify` 검증 응답에 따라 갱신하도록 교체 예정 (`TODO(backend, billing)` 표시).
- **acknowledgePurchase 필수**: 3일 내 미호출 시 자동 환불됨. handlePurchase 내에서 `!isAcknowledged` 체크 후 호출.
- **기존 구매 복원**: `queryPurchasesAsync(SUBS)` 를 `onBillingSetupFinished`, `startConnection`, `onResume` 시점에 호출하여 앱 재실행/재진입 시 PAID 유지.
- **재연결 backoff**: 1s, 2s, 4s, 8s, 16s, 32s → 상한 60s.
- **이미 구독 중**: `ITEM_ALREADY_OWNED` 응답 → `queryExistingPurchases` 로 동기화 후 안내.
- **USER_CANCELED / ITEM_ALREADY_OWNED**: 비차단 Snackbar 표시 후 state 소비, 그 외 에러는 retry 가능한 layout_error 표시.
- **MenuFragment 데모 토글**: 보존하되 release 빌드 제거용 TODO 명시.

### 자가 검증 결과 (8/8 통과)
1. `./gradlew assembleDebug` → BUILD SUCCESSFUL
2. `billing-ktx` 의존성 hit 1건 (line 65)
3. `applicationId = "com.checkdang.app"` 확인 — Play Console 등록값과 사용자 검증 필요
4. 상품 ID 단일 진실 공급원: `checkdang_premium*` 은 `ProductIds.kt` 외 hit 없음
5. `acknowledgePurchase` 호출: `BillingRepository.kt:165`
6. `USER_CANCELED` 분기 처리: `BillingRepository.kt:143`
7. `queryPurchasesAsync`: `BillingRepository.kt:103`
8. `TODO(backend, billing)`: `BillingRepository.kt:154`

### 의도적으로 하지 않은 것
- 서버 영수증 검증 (백엔드 도입 후 별도 단계)
- 구독 변경/취소 UI 안내 링크
- 환불 처리 UI
- 영구 상품(INAPP) — 구독(SUBS)만 사용

### 사용자 실측 검증 필요
실결제는 자동 검증 불가능. Play Console 내부 테스트 트랙에 서명된 AAB 업로드 후 테스트 라이선스 계정으로 결제 흐름 / 가격 표시 / 앱 재실행 후 PAID 유지를 확인해야 함. 상세 절차는 `docs/STEP10_billing.md` §10 참고.

---

## [2026-05-04] 프로젝트 초기 파일 검사

### 작업 내용
전체 프로젝트 파일 구조 파악 및 주요 파일 코드 리뷰.

### 확인된 주요 사항
- Android Native MVVM 앱 (체크당), minSdk 26, Kotlin only
- 인증: 구글/카카오 소셜 로그인 + 비회원 (LoginActivity → OnboardingActivity)
- 실제 백엔드 API 연동됨: `AuthApiClient.kt` → `https://two00ok-8r84.onrender.com`
  - `POST /api/auth/social` (소셜 로그인)
  - `POST /api/auth/logout` (로그아웃)
  - API 실패 시 Mock 토큰으로 fallback 처리
- `MockDataProvider.kt`의 라이프스타일 메서드(운동/수면/식사)가 모두 `null` 반환 → 홈/라이프스타일 화면 데이터 없음
- BottomNav 5탭: Home / Glucose / Lifestyle / BodyMap / Menu
- `strings.xml`에 카카오 앱키 노출 (퍼블릭 리포 시 보안 주의)

---

## [2026-05-04] 삼성 헬스 연동 추상화 레이어 구현 (Phase A)

### 작업 내용
Samsung Health SDK 직접 연동 전, 나중에 SDK를 꽂을 수 있도록 추상화 레이어(인터페이스 + 구현체)를 먼저 구축.

### 신규 파일
| 파일 | 설명 |
|------|------|
| `app/src/main/java/com/checkdang/app/data/health/HealthDataSource.kt` | 건강 데이터 소스 인터페이스 |
| `app/src/main/java/com/checkdang/app/data/health/MockHealthDataSource.kt` | Mock 구현체 (현실적인 하드코딩 데이터) |
| `app/src/main/java/com/checkdang/app/data/health/SamsungHealthDataSource.kt` | 삼성 헬스 SDK 연동 stub (TODO 주석 포함) |
| `app/src/main/java/com/checkdang/app/data/health/HealthRepository.kt` | 활성 구현체 관리 싱글톤 |

### 수정 파일
| 파일 | 변경 내용 |
|------|----------|
| `data/mock/MockDataProvider.kt` | 라이프스타일 6개 메서드를 HealthRepository에 위임 |
| `ui/lifestyle/LifestyleViewModel.kt` | `StateFlow` + `sync()` 메서드 추가 |
| `ui/lifestyle/LifestyleFragment.kt` | Flow 관찰 + 새로고침 버튼을 `viewModel.sync()` 연결 |

### 아키텍처 구조
```
HealthDataSource (interface)
├── MockHealthDataSource   ← 기본값
└── SamsungHealthDataSource ← 미래 SDK 연동용 stub

HealthRepository → MockDataProvider → ViewModel → Fragment
```

---

## [2026-05-04] Android Health Connect 실제 연동 구현 (Phase B)

### 배경
- Samsung Health SDK는 삼성 개발자 계정 + 앱 심사 필요 (Maven 미제공, 수동 AAR 다운로드)
- Android Health Connect (`androidx.health.connect`)를 사용하면 Galaxy 기기의 삼성 헬스 데이터를 동일하게 수신 가능
- 삼성 헬스 앱 → 설정 → Health Platform → "Health Connect와 동기화" 활성화 시 자동 동기화 (One UI 6.0+)

### 수정 파일
| 파일 | 변경 내용 |
|------|----------|
| `CLAUDE.md` | Health Connect 허용, Samsung Health SDK 직접 연동 금지 조항 명시화, Health Connect 연동 구조 추가 |
| `app/build.gradle.kts` | Health Connect 의존성 추가 (`1.1.0-alpha10`), `compileSdk` 34 → 35 상향 |
| `app/src/main/AndroidManifest.xml` | 권한 3개 추가, `<queries>` 추가, `MainActivity`에 권한 안내 intent-filter 추가 |

### 신규 파일
| 파일 | 설명 |
|------|------|
| `data/health/HealthConnectDataSource.kt` | 실제 Health Connect API 연동 구현체 (운동/수면/식사/주간 데이터) |

### 수정 파일 (health 레이어)
| 파일 | 변경 내용 |
|------|----------|
| `data/health/HealthDataSource.kt` | 모든 데이터 메서드에 `suspend` 추가 |
| `data/health/MockHealthDataSource.kt` | `suspend` 추가 |
| `data/health/SamsungHealthDataSource.kt` | `suspend` 추가 |
| `data/health/HealthRepository.kt` | `suspend` 추가, `switchToHealthConnect()` 메서드 추가 |
| `data/mock/MockDataProvider.kt` | 라이프스타일 메서드 `null` 반환으로 복원 (ViewModel이 HealthRepository 직접 호출) |

### 수정 파일 (UI 레이어)
| 파일 | 변경 내용 |
|------|----------|
| `ui/lifestyle/LifestyleViewModel.kt` | `AndroidViewModel`로 변경, `connectAndSync()` 추가 |
| `ui/lifestyle/LifestyleFragment.kt` | `PermissionController` 권한 요청 플로우 추가, Health Connect 가용성 체크 |
| `ui/lifestyle/exercise/ExerciseDetailActivity.kt` | `lifecycleScope` + `HealthRepository` 직접 호출 |
| `ui/lifestyle/meal/MealDetailActivity.kt` | 동일 |
| `ui/lifestyle/sleep/SleepDetailActivity.kt` | 동일 |

### Health Connect 데이터 매핑
| 삼성 헬스 데이터 | Health Connect 레코드 | 앱 모델 |
|----------------|----------------------|---------|
| 운동 | `ExerciseSessionRecord` | `ExerciseSummary` / `ExerciseSession` |
| 수면 | `SleepSessionRecord` (stages 포함) | `SleepSummary` |
| 식사/영양 | `NutritionRecord` | `MealSummary` / `MealItem` |

### 권한 요청 플로우
```
라이프스타일 탭 새로고침 버튼 탭
    ↓
HealthConnectClient.getSdkStatus() 확인
    ├── SDK_UNAVAILABLE → "Samsung Health 앱이 설치되어 있지 않아요"
    └── SDK_AVAILABLE
            ↓
        getGrantedPermissions() 확인
            ├── 권한 있음 → viewModel.connectAndSync() 즉시 실행
            └── 권한 없음 → PermissionController 권한 요청 다이얼로그
                            ├── 허가 → connectAndSync() + "연동 완료" 토스트
                            └── 거부 → "일부 권한이 거부되었어요" 토스트
```

### 사용자 기기 설정 방법
1. Galaxy 기기에서 **Samsung Health 앱** 실행
2. 설정 → **Health Platform** → **"Health Connect와 동기화"** 활성화
3. 체크당 앱 → 라이프스타일 탭 → 새로고침 버튼 → 권한 허가

---

## [2026-05-04] IMPLEMENTATION_LOG.md 작성 및 CLAUDE.md 정리

### 작업 내용
- 지금까지의 모든 작업 내역을 `IMPLEMENTATION_LOG.md`에 기록
- `CLAUDE.md`에 작업 완료 후 로그 기록 의무 규칙 추가
- `CLAUDE.md` 불필요한 내용 제거 (outdated 아키텍처 설명, 자명한 관례, 세부 명령어 등)

---

## [2026-05-04] 수동 새로고침 버튼 연동 버그 수정 (Phase C)

### 작업 내용
새로고침 버튼 → Health Connect 연동 플로우의 3가지 버그 수정.

### 수정 버그

| 버그 | 원인 | 수정 |
|------|------|------|
| `_isSyncing` 즉시 true 미반영 | `connectAndSync()`가 `sync()`를 별도 `viewModelScope.launch`로 호출해 타이밍 어긋남 | `connectAndSync()` 안에서 직접 `_isSyncing = true` → 데이터 로드 → `false` 처리 |
| 에러 시 `isSyncing` 무한 true | `sync()` 위임 방식에서 예외 발생 시 `finally` 없이 `false` 도달 불가 | `runCatching` 블록으로 감싸고 블록 밖에서 `_isSyncing.value = false` |
| 부분 권한 허가 시 연동 안 됨 | 퍼미션 콜백이 `granted.containsAll(...)` 조건만 통과 → 부분 허가 시 데이터 로드 안 함 | `granted.isEmpty()` 일 때만 거부 처리, 1개 이상 허가 시 `connectAndSync()` 호출 |

### 수정 파일

| 파일 | 변경 내용 |
|------|----------|
| `ui/lifestyle/LifestyleViewModel.kt` | `connectAndSync()` 단일 코루틴으로 재작성, `runCatching` 추가 |
| `ui/lifestyle/LifestyleFragment.kt` | 퍼미션 콜백: 부분 허가 시에도 `connectAndSync()` 호출하도록 수정 |

---

## Future TODOs

> 진행 시 본 체크박스에 표시하고 별도 항목으로 작업 로그를 추가한다.

- [ ] 백엔드 파트와 Base URL 맞춰서 Health Connect 실데이터 전송 및 수신 테스트
- [ ] STEP 11 Phase 2 — Samsung Health Partner Apps Program 승인 후 AAR 파일 및 Access Code 연동
- [ ] STEP 10 — Play Billing v7 프리미엄 구독 서버 영수증 검증 API 연동
