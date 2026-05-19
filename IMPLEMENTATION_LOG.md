# IMPLEMENTATION LOG

> **규칙**: 작업 완료 후 반드시 이 파일에 날짜·작업 내용·수정 파일 목록을 기록한다.

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
