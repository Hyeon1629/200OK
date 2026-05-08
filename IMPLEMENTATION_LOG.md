# IMPLEMENTATION LOG

> **규칙**: 작업 완료 후 반드시 이 파일에 날짜·작업 내용·수정 파일 목록을 기록한다.

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
