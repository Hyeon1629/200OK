# STEP 10 — Google Play Billing Library v7 구독 결제 설계서

> **상태**: Plan 단계 (사용자 승인 대기)
> **작성일**: 2026-05-11
> **대상 기능**: 프리미엄 구독(월간/연간) 인앱 결제

---

## 0. 배경

현재 `SubscriptionActivity.setupSubscribeButton()` 는 "구독 시작하기" 버튼 클릭 시 `SessionHolder.tier = UserTier.PAID` 로 즉시 변경하는 데모용 코드다. 본 단계에서 이를 **Google Play Billing Library v7** 기반의 실결제 흐름으로 교체한다.

본 단계는 클라이언트 결제 흐름만 다루며, 서버 영수증 검증은 백엔드 도입 후 별도 단계에서 추가한다.

---

## 1. 의존성 추가

`app/build.gradle.kts`

```kotlin
dependencies {
    implementation("com.android.billingclient:billing-ktx:7.1.1")
}
```

> 작성 시점 기준 v7 안정 버전을 사용한다. 구현 시작 시 `https://developer.android.com/google/play/billing/release-notes` 에서 최신 패치 버전을 확인하고 갱신한다.

---

## 2. 상품 ID (Play Console 등록값과 1:1 일치)

| 상수명 | Product ID | 타입 |
|--------|-----------|------|
| `PREMIUM_MONTHLY` | `checkdang_premium_monthly` | 자동 갱신 구독 |
| `PREMIUM_YEARLY`  | `checkdang_premium_yearly`  | 자동 갱신 구독 |

상품 ID는 `ProductIds.kt` 한 곳에서만 선언하고, 모든 사용처는 이 상수만 참조한다 (자가 검증 #4 의 단일 진실 공급원 규칙).

---

## 3. 아키텍처

### 3.1 컴포넌트

```
┌──────────────────────────────────────────────────────────────┐
│ CheckDangApplication                                         │
│  └─ billingRepository: BillingRepository (Application scope) │
└──────────────────────────────────────────────────────────────┘
                          ▲
                          │ inject (Application 캐스팅으로 획득)
                          │
┌──────────────────────────────────────────────────────────────┐
│ SubscriptionActivity                                         │
│  └─ subscriptionViewModel: SubscriptionViewModel             │
│       └─ state: StateFlow<BillingState>                      │
└──────────────────────────────────────────────────────────────┘
```

- **BillingRepository (싱글턴, Application scope)**
  - `BillingClient` 연결/재연결 관리 (`BillingClientStateListener`)
  - `PurchasesUpdatedListener` 구현 — 결제 결과 콜백
  - `queryProductDetailsAsync` — 상품 조회 (가격/문구는 Play Console 값 사용)
  - `launchBillingFlow` — Play 결제 시트 노출
  - `acknowledgePurchase` — 3일 내 미처리 시 자동 환불됨. 필수 호출
  - `queryPurchasesAsync` — 앱 재실행 시 기존 구독 복원
  - `StateFlow<BillingState>` 노출
- **SubscriptionViewModel**
  - `BillingRepository.state` 를 그대로 노출 (또는 화면 표시용으로 매핑)
  - `startPurchase(activity, productId)` 위임
- **BillingState (sealed class)**
  - `Idle | Loading | Ready(products) | Purchasing | Success(productId) | Error(message, code?)`

### 3.2 결제 성공 후 처리

```
PurchasesUpdatedListener.onPurchasesUpdated (OK)
       │
       ▼
handlePurchase(purchase)
       │
       ├─ TODO(backend, billing): 서버에 영수증 검증 요청
       │   POST /api/v1/billing/verify
       │     { purchaseToken, productId, packageName }
       │   응답 OK 시에만 tier 갱신하도록 추후 변경
       │
       ├─ purchase.purchaseState == PURCHASED 인지 확인
       │
       ├─ !purchase.isAcknowledged → acknowledgePurchase 호출
       │       └─ 성공 콜백에서 tier 갱신 + Success state 전환
       │
       └─ 이미 acknowledged → 즉시 tier 갱신 + Success state 전환
```

**중요**: 본 단계에서는 클라이언트 신뢰 — `SessionHolder.tier = UserTier.PAID` 로 직접 갱신. 백엔드 도입 후 서버 검증 응답에 따라 tier 를 갱신하도록 교체한다.

---

## 4. 결제 흐름 시퀀스

```
┌────────────┐
│ User       │ "구독 시작하기" 클릭
└─────┬──────┘
      ▼
SubscriptionViewModel.startPurchase(productId)
      │
      ▼
BillingRepository.launchBillingFlow(activity, productDetails)
      │
      ▼
Play Store 결제 UI 노출 (앱 외부)
      │
      ▼
PurchasesUpdatedListener.onPurchasesUpdated(BillingResult, purchases)
      │
      ▼ (성공)
handlePurchase → acknowledgePurchase  ※ 3일 내 미호출 시 자동 환불
      │
      ▼
SessionHolder.tier = UserTier.PAID
BillingState.Success(productId) 방출 → UI 갱신
```

---

## 5. 엣지 케이스 처리

| 케이스 | 응답 코드 / 조건 | 처리 |
|--------|------------------|------|
| 사용자 결제 취소 | `USER_CANCELED` | 토스트/스낵바 "결제가 취소되었어요", state → `Error("결제가 취소되었어요")` (사용자 의도이므로 비차단) |
| 이미 구독 중인 상품 재구매 | `ITEM_ALREADY_OWNED` 또는 `queryPurchasesAsync` 결과에 존재 | 안내 다이얼로그 + tier 동기화 후 화면 종료 |
| 네트워크 / 일시 오류 | `SERVICE_DISCONNECTED`, `SERVICE_UNAVAILABLE` 등 | state → `Error`, 재시도 버튼 |
| `BillingClient` 연결 끊김 | `onBillingServiceDisconnected` | exponential backoff 재연결 (간단히는 2s/4s/8s, 상한 60s) |
| 결제 보류(`PENDING`) | `purchaseState == PENDING` | acknowledge 하지 않고 안내만, 다음 onResume 에서 다시 확인 |

---

## 6. 수정 / 신규 파일 목록

| 파일 | 상태 | 비고 |
|------|------|------|
| `app/build.gradle.kts` | 수정 | `billing-ktx:7.1.1` 추가 |
| `app/src/main/java/com/checkdang/app/data/billing/ProductIds.kt` | 신규 | 상품 ID 상수 |
| `app/src/main/java/com/checkdang/app/data/billing/BillingState.kt` | 신규 | sealed class |
| `app/src/main/java/com/checkdang/app/data/billing/BillingRepository.kt` | 신규 | 결제 핵심 로직 |
| `app/src/main/java/com/checkdang/app/CheckDangApplication.kt` | 수정 | `billingRepository` 초기화 및 `startConnection()` |
| `app/src/main/java/com/checkdang/app/ui/menu/subscription/SubscriptionViewModel.kt` | 신규 | state 노출 + startPurchase 위임 |
| `app/src/main/java/com/checkdang/app/ui/menu/subscription/SubscriptionActivity.kt` | 수정 | 데모 코드 제거, ViewModel state 구독 |
| `app/src/main/res/layout/activity_subscription.xml` | 수정 | 로딩/에러 상태 UI 추가 |
| `app/src/main/AndroidManifest.xml` | 변경 없음 | `android:name=".CheckDangApplication"` 이미 등록됨 ✅ |

---

## 7. UI 상태 매핑

`SubscriptionActivity` 가 `BillingState` 를 구독하여 다음과 같이 표시한다.

| State | 가격 카드 | 구독 버튼 | 추가 UI |
|-------|-----------|-----------|---------|
| `Idle / Loading` | ProgressBar | 비활성 | — |
| `Ready(products)` | `ProductDetails.subscriptionOfferDetails[0].pricingPhases.pricingPhaseList[0].formattedPrice` 동적 표시 | 활성 | — |
| `Purchasing` | 그대로 | 비활성 | "결제 진행 중..." 표시 |
| `Success(productId)` | 그대로 | — | Snackbar "구독이 시작되었어요" → `finish()` |
| `Error(message)` | 그대로 | 활성 | 에러 메시지 + 재시도 버튼 |

---

## 8. 의도적으로 하지 않는 것

- 서버 영수증 검증 → 백엔드 도입 후 별도 단계
- 구독 변경/취소 UI 안내 → Play Store 외부 작업이라 링크만 제공 예정 (본 단계 범위 외)
- 환불 처리 UI
- 영구 상품(`INAPP`) — 구독(`SUBS`)만 사용
- `MenuFragment` long-press 데모 토글은 보존하되 `// TODO(release): release 빌드에서 제거` 주석 추가

---

## 9. 자가 검증 체크리스트 (구현 후 실행)

| # | 항목 | 통과 조건 |
|---|------|-----------|
| 1 | `./gradlew assembleDebug` | `BUILD SUCCESSFUL` |
| 2 | `billing-ktx` 의존성 | `app/build.gradle.kts` 에 hit 1건 |
| 3 | `applicationId` 일관성 | Play Console 등록값과 일치 (사용자 확인 필요) |
| 4 | 상품 ID 단일 진실 공급원 | `checkdang_premium` grep 결과가 `ProductIds.kt` 외 hit 없음 |
| 5 | `acknowledgePurchase` 호출 존재 | `BillingRepository.kt` 에 hit 1건 이상 ⚠️ 가장 중요 |
| 6 | `USER_CANCELED` 분기 처리 | `onPurchasesUpdated` 에 분기 존재 |
| 7 | `queryPurchasesAsync` 호출 | `BillingRepository.kt` 에 hit 1건 이상 (기존 구독 복원) |
| 8 | `TODO(backend, billing)` 표시 | `app/src/main/java` 하위 1건 이상 |

---

## 10. 사용자 실측 검증 (자가 검증 통과 후)

결제는 자동화 테스트로 검증할 수 없으므로 다음 절차로 실제 단말에서 확인한다.

1. Play Console > 내부 테스트 트랙에 build 업로드
   - `./gradlew bundleRelease` (서명된 AAB 필요)
2. 테스트 라이선스 계정으로 로그인된 Galaxy/Pixel 단말에서 내부 테스트 링크로 설치
3. 앱 내 구독 화면 진입 → 가격이 Play Console 등록값으로 표시되는지 확인
4. "구독 시작하기" → Play 결제 시트 노출 → 테스트 결제 진행
5. 결제 완료 후 메뉴 화면이 PAID 등급으로 표시되는지 확인
6. 앱 강제 종료 → 재실행 후에도 PAID 유지 (queryPurchasesAsync 검증)

**가격 미표시 / "Product not found" 발생 시 우선 점검 3가지**
- Play Console 상품 등록 상태가 "활성"인가
- `applicationId` 가 Play Console 등록값과 정확히 일치하는가
- 테스트 라이선스 계정으로 로그인되어 있는가

검증 통과 시 사용자가 "STEP 10 OK" 회신.

---

## 11. Commit 메시지(예정)

```
feat: STEP 10 Google Play Billing 구독 결제 연동
```
