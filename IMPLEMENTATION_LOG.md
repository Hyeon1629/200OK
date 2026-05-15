# IMPLEMENTATION LOG

> **규칙**: 작업 완료 후 반드시 이 파일에 날짜·작업 내용·수정 파일 목록을 기록한다.

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
