# STEP 11 — Samsung Health Data SDK 연동 설계서

> **상태**: 📐 **설계 + 스켈레톤 단계 (Phase 1)** — Samsung Health Partner Apps Program 승인 + AAR 수령 전까지 호출 경로는 비활성화. 본 문서는 승인 후 즉시 활성화 가능하도록 구조를 미리 마련하는 작업의 명세서.
>
> **작성일**: 2026-05-11
> **선행 조건**: STEP 10 (Billing) 완료, Health Connect 연동 안정화 완료
> **차단 사유**: Samsung Health Partner Apps Program 이 현재 "not accepting any applications at this time" 상태. AAR / Access Code 미수령.

---

## 1. SDK 선택 근거

### 후보 비교

| 항목 | Samsung Health Data SDK | Android Health Connect | Samsung Health SDK (구버전) |
|------|------------------------|------------------------|---------------------------|
| 데이터 범위 | Steps/Exercise/Nutrition/Sleep/Weight + Samsung 고유 데이터 | 공통 14종 (Galaxy↔타사 공유) | 폐기됨 (deprecated) |
| 배포 형태 | 로컬 AAR (`samsung-health-data-api-*.aar`) | Maven (`androidx.health.connect`) | AAR |
| 파트너 가입 | 필수 (Partner Apps Program) | 불필요 | 필수 |
| Galaxy 외 지원 | ❌ (Samsung 단말만) | ✅ | ❌ |
| 권한 다이얼로그 호스트 | Samsung Health 앱 | Health Connect / Permission Controller |
| 데이터 정확도 | 원본에 가까움 | 호환 형식 (단계 등 일부 추정) |

### 결정: **Health Data SDK + Health Connect 병행**

- **Health Connect**: 비-갤럭시 / Samsung Health 미설치 단말 / fallback 경로
- **Health Data SDK**: 갤럭시 + Samsung Health + Partner 승인 시 우선 사용 (Samsung 원본 데이터)
- `HealthRepository` 는 두 소스 중 가용한 것을 자동 선택. 본 단계에선 SDK 측 코드는 **스켈레톤**만 작성.

---

## 2. 의존성 (`app/build.gradle.kts`)

### 현재 (Phase 1 / Partner 미승인)

```kotlin
// === Samsung Health Data SDK (STEP 11) ===
// Partner Apps Program 승인 + AAR 수령 후 아래 블록 활성화:
//
//   1. app/libs/samsung-health-data-api-<version>.aar 배치
//   2. 아래 implementation 라인 주석 해제
//   3. plugins 블록에 `kotlin-parcelize` 추가
//   4. SamsungHealthRepository 의 TODO(samsung-sdk) 마커 부분 활성화
//
// implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
// implementation("com.google.code.gson:gson:2.9.0")
```

### 활성화 후 (Phase 2 / Partner 승인 완료)

```kotlin
plugins {
    // ...
    kotlin("plugin.parcelize")
}

dependencies {
    // ...
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    implementation("com.google.code.gson:gson:2.9.0")
}
```

⚠️ **Maven 원격 좌표는 존재하지 않습니다.** 공식 배포는 Samsung Developer Portal 에서 AAR 직접 다운로드 → `app/libs/` 배치 → `fileTree` 로 포함.

---

## 3. 권한 모델

### 5종 카테고리 (READ-only)

| 우리 enum | SDK 식별자 (확정 시 교체) | 라벨 |
|----------|-------------------------|------|
| `STEPS` | `<TBD-from-aar>` | 걸음 수 |
| `EXERCISE` | `<TBD-from-aar>` | 운동 |
| `NUTRITION` | `<TBD-from-aar>` | 식사 |
| `SLEEP` | `<TBD-from-aar>` | 수면 |
| `WEIGHT` | `<TBD-from-aar>` | 체중 |

권한 다이얼로그는 **Samsung Health 앱이 호스트**. 앱은 `HealthDataStore.requestPermissions(activity, permsSet)` 만 호출.

### 정책
- WRITE 권한 미사용 (의도적 제외, §9)
- 부분 허용 지원: 5개 중 일부만 허가되어도 허가된 카테고리는 정상 표시

---

## 4. 아키텍처

```
[Application]
   └─ SamsungHealthRepository (Application scope, 싱글톤 인스턴스)
        ├─ HealthDataStore 보유 (SDK 클라이언트)
        ├─ ConnectionState StateFlow 노출
        ├─ checkAvailability()  → ConnectionState
        ├─ requestPermissions(activity, perms)
        ├─ checkPermissions(perms)
        ├─ readSteps(date)
        ├─ readExercise(date)
        ├─ readMeal(date)
        ├─ readSleep(date)
        └─ readWeight(date)
                ↓
[SamsungHealthMapper]   (object, 순수 변환 함수)
   ├─ toExerciseSummary(sdkResponse) : ExerciseSummary
   ├─ toMealSummary(sdkResponse)     : MealSummary
   ├─ toSleepSummary(sdkResponse)    : SleepSummary
   └─ 단위 변환 (kcal/min/kg) 정규화
                ↓
[LifestyleViewModel]
   └─ SamsungHealthRepository 주입, StateFlow 4종 노출
                ↓
[LifestyleFragment]
   └─ ConnectionState 분기 UI + 권한 요청 흐름
```

### ConnectionState sealed class

```kotlin
sealed class ConnectionState {
    object Initializing : ConnectionState()
    object Connected : ConnectionState()         // 권한 있음 (전체/부분)
    object PermissionNeeded : ConnectionState()  // SDK는 있으나 권한 없음
    object NotInstalled : ConnectionState()      // Samsung Health 미설치
    object Unsupported : ConnectionState()       // 비-갤럭시 단말
    data class Error(val message: String) : ConnectionState()
}
```

---

## 5. 권한 거부 시 동작

### 전체 거부
- `LifestyleFragment` 가 `PermissionNeeded` 상태 진입
- 카드 영역: EmptyState + "Samsung Health 권한이 필요해요" + "권한 다시 요청" 버튼

### 부분 허용 (예: Steps 만 허가, 나머지 거부)
- 허가된 카테고리는 정상 카드 표시
- 거부된 카테고리는 카드별 EmptyState + "이 데이터는 권한이 없어요" 문구
- 카드 하단에 "권한 다시 요청" 링크 (전체 권한 요청 다이얼로그 재호출)

### Repository 메서드 null 안전
- `readXxx()` 는 권한 없거나 데이터 없으면 **null 반환**
- UI 는 null 일 때 EmptyState 표시

---

## 6. 호스트 앱 미설치 / 미지원 단말

`SamsungHealthRepository.checkAvailability(context)` 가 진입 시 분기:

| 조건 | ConnectionState | UI |
|------|----------------|-----|
| `Build.MANUFACTURER != "samsung"` | `Unsupported` | 다이얼로그: "Samsung Health 는 갤럭시 단말에서만 지원돼요" + Health Connect 안내 |
| Samsung Health 패키지 미설치 (`packageManager.getPackageInfo("com.sec.android.app.shealth")` 실패) | `NotInstalled` | 다이얼로그: "Samsung Health 앱이 필요해요" + Play Store 링크 (`market://details?id=com.sec.android.app.shealth`) |
| SDK 초기화 실패 | `Error(message)` | 토스트 + 재시도 버튼 |

검사 시점: `LifestyleFragment.onViewCreated` (1회). 결과 변화는 권한 변경 시에만 발생하므로 매 진입 검사 불필요.

---

## 7. 데이터 모델 매핑

### SDK Response → 도메인 모델

| SDK 데이터 | 우리 도메인 모델 | 변환 메서드 |
|----------|----------------|------------|
| Step records (일별 집계) | `Int` (totalSteps) | `toStepCount(records)` |
| Exercise sessions | `ExerciseSummary(totalMinutes, goalMinutes, totalCalories, sessions)` | `toExerciseSummary(records)` |
| Nutrition (meal entries) | `MealSummary(totalKcal, goalKcal, carbsG, proteinG, fatG, meals)` | `toMealSummary(records)` |
| Sleep sessions (stages 포함) | `SleepSummary(totalHours, efficiency, deepHours, lightHours, remHours, bedtime, wakeTime)` | `toSleepSummary(records)` |
| Weight measurements | `Float` (kg, 최신값) | `toLatestWeight(records)` |

### 단위 정규화 표

| 데이터 | SDK 단위 | 도메인 단위 | 변환 함수 |
|-------|---------|-----------|---------|
| 에너지 | cal / J | **kcal** (Int) | `kcalFromCalories(value)` |
| 시간 | seconds / ms | **분** (Int) | `minutesFromSeconds(value)` |
| 수면 | seconds | **시간** (Float) | `hoursFromSeconds(value)` |
| 체중 | kg / g | **kg** (Float) | `kgFromGrams(value)` |
| 시각 | epoch millis | `"오전 7:30"` (String) | `formatKoreanTime(epoch)` |

---

## 8. 동기화 정책

### 본 단계 (실시간 풀)
- `LifestyleFragment` 진입 시 → `LifestyleViewModel.sync()` 호출
- 새로고침 버튼 탭 → `sync()` 재호출
- 모든 read 메서드는 `suspend` (`Dispatchers.IO` 에서 실행)

### 보류 (향후 단계)
- WorkManager 기반 백그라운드 주기 동기화 (예: 4시간 간격)
- 데이터 캐싱 (Room 등) — 현재 영속성 코드 금지 정책

---

## 9. 의도적으로 하지 않는 것

| 항목 | 사유 |
|-----|-----|
| WRITE 권한 / 데이터 쓰기 | 본 단계는 READ-only. 쓰기는 별도 검토 필요 |
| CGM (연속혈당측정기) 연동 | Samsung Health Data SDK 범위 외 |
| Galaxy Watch 직접 접근 | Samsung Health 가 워치 데이터를 호스트하므로 SDK 경유 충분 |
| 백그라운드 주기 동기화 | WorkManager 미도입 (별도 단계) |
| 다국어 라벨 | 한국어 고정 (앱 전체 정책) |

---

## 10. 수정 / 생성 파일 목록

### Phase 1 — 현재 (Partner 미승인 / 스켈레톤만)

| 파일 | 상태 | 설명 |
|-----|------|-----|
| `CLAUDE.md` | 수정 | Samsung Health SDK 정책 갱신 (금지 → 조건부 허용) |
| `docs/STEP11_samsung_health.md` | 신규 | 본 설계서 |
| `app/src/main/AndroidManifest.xml` | 수정 | `<queries>` 에 `com.sec.android.app.shealth` 추가 |
| `app/build.gradle.kts` | 수정 | AAR + parcelize 활성화 안내 주석 (실제 활성화는 Phase 2) |
| `app/src/main/java/com/checkdang/app/data/samsunghealth/HealthDataPermission.kt` | 신규 | 5종 권한 enum (SDK 의존성 없음) |
| `app/src/main/java/com/checkdang/app/data/samsunghealth/SamsungHealthRepository.kt` | 신규 | `ConnectionState` + 메서드 시그니처 (모두 null/Stub 반환, `TODO(samsung-sdk)` 마커) |
| `app/src/main/java/com/checkdang/app/data/samsunghealth/SamsungHealthMapper.kt` | 신규 | 단위 변환 헬퍼 + 매핑 시그니처 |
| `IMPLEMENTATION_LOG.md` | 수정 | STEP 11 Phase 1 기록 |

### Phase 2 — Partner 승인 / AAR 수령 후

| 파일 | 변경 |
|-----|-----|
| `app/libs/samsung-health-data-api-<version>.aar` | 신규 (Portal 다운로드) |
| `app/build.gradle.kts` | AAR `fileTree` + `gson` 의존성 활성화, `kotlin-parcelize` 플러그인 추가 |
| `HealthDataPermission.kt` | `sdkConstant` 필드를 SDK 실제 상수로 교체 |
| `SamsungHealthRepository.kt` | `TODO(samsung-sdk)` 마커 부분에 실제 `HealthDataStore` 호출 활성화. Access Code 주입 경로 추가 |
| `SamsungHealthMapper.kt` | SDK Response 타입 import 후 변환 로직 활성화 |
| `LifestyleViewModel.kt` | `SamsungHealthRepository` 주입 + 권한 흐름 통합 |
| `LifestyleFragment.kt` | 4가지 `ConnectionState` 분기 UI |
| `res/layout/dialog_health_permission.xml` | 신규 (권한 안내 다이얼로그) |

---

## 11. 자가 검증 체크리스트

### Phase 1 (현재 단계 — 본 작업 완료 시 통과해야 할 항목)

| # | 검증 항목 | 통과 조건 |
|---|---------|---------|
| 1 | 빌드 통과 | `./gradlew assembleDebug` BUILD SUCCESSFUL |
| 2 | 패키지 가시성 | `AndroidManifest.xml` 의 `<queries>` 에 `com.sec.android.app.shealth` 포함 |
| 3 | 권한 enum 5종 | `HealthDataPermission.kt` 에 STEPS/EXERCISE/NUTRITION/SLEEP/WEIGHT 정의 |
| 4 | ConnectionState 6종 | `SamsungHealthRepository.kt` 에 Initializing/Connected/PermissionNeeded/NotInstalled/Unsupported/Error 정의 |
| 5 | TODO 마커 | `SamsungHealthRepository.kt` 에 `TODO(samsung-sdk)` 마커 ≥ 5개 (요청별) |
| 6 | null 안전 | 모든 read 메서드가 try/catch + null 반환 패턴 |
| 7 | 기존 Health Connect 경로 무손상 | `LifestyleViewModel` / `LifestyleFragment` 미수정 (호출 경로 보존) |
| 8 | 의존성 비활성화 | `app/build.gradle.kts` 의 AAR/gson 라인이 **주석 처리** 상태 |

### Phase 2 (Partner 승인 후 별도 단계)

별도 PR / 별도 검증으로 분리. AAR 도착 시 본 문서 §10 Phase 2 표 + 본 §11 Phase 2 검증을 별도 작성.

---

## 12. 사용자 액션 (Partner 승인 시 진행)

1. Samsung Developer Portal 에서 AAR 다운로드 → `app/libs/` 배치
2. Portal 의 Access Code 전달 (어디에 어떻게 저장할지는 Phase 2 에서 결정 — local.properties 권장)
3. "STEP 11 Phase 2 진행" 메시지로 알림
4. Phase 2 단계에서 본 문서 §10 Phase 2 + §11 Phase 2 따라 활성화

---

## 부록 A — 참고한 공식 출처

- `developer.samsung.com/health/data/overview.html`
- `developer.samsung.com/health/data/process.html`
- `developer.samsung.com/health/data/guide/developer-mode.html` (Client ID + Access Code 설명)
- `developer.samsung.com/health/data/migration-guide/step-app-example.html` (AAR 통합 예시)

## 부록 B — 용어 정리

- **Client ID**: 앱의 package name. 본 앱은 `com.checkdang.app`. 자체 보유, 발급 절차 없음.
- **Access Code**: Partner 승인 후 Samsung 측에서 발급. SDK 초기화 시 주입. Portal 또는 회신 이메일로 전달 (공식 명시 없음).
- **AAR**: Samsung Health Data API 의 Android Archive. 원격 Maven 미공개, 로컬 직접 배포.
