# STEP — 앱 아이콘 통합 (확정 시안)

> **상태**: Plan 단계 (사용자 승인 대기)
> **작성일**: 2026-05-22
> **대상 기능**: 체크당 런처 아이콘 — 확정 SVG 시안 → Android Adaptive Icon

---

## 0. 배경

현재 `res/mipmap-mdpi/` 의 placeholder adaptive-icon 은 단순히 `@color/brand_green_light` + `@color/brand_green` 두 색을 background/foreground 로 지정한 임시 상태 (단색 사각형만 표시됨). 다른 해상도/`mipmap-anydpi-v26` 디렉토리는 부재.

본 STEP 에서 확정된 SVG 시안(물방울 + 광택 + 체크)을 Android Vector Drawable + Adaptive Icon 으로 통합한다.

---

## 1. 확정 시안 명세 (절대 변경 금지)

| 레이어 | 속성 | 값 |
|--------|------|-----|
| 캔버스 | viewBox | 0 0 512 512 |
| 배경 | shape | rounded rectangle, radius 96 |
| 배경 | fill | `#F1F8E9` |
| 물방울 | path | `M 256 100 C 320 180, 376 230, 376 290 A 120 120 0 1 1 136 290 C 136 230, 192 180, 256 100 Z` |
| 물방울 | gradient | vertical (0,0 → 0,1) |
| 물방울 | gradient 0% / 100% | `#66BB6A` / `#388E3C` |
| 광택 | ellipse | cx=212 cy=218 rx=26 ry=14 |
| 광택 | rotation | -32° (자기 중심) |
| 광택 | fill / opacity | `#FFFFFF` / 0.35 |
| 체크 | path | `M 214 296 L 246 330 L 304 268` |
| 체크 | stroke | `#FFFFFF`, width 22, linecap round, linejoin round |

---

## 2. 좌표계 변환 (512 → 108)

Adaptive Icon viewport 는 108×108. 변환 비율: **× 0.2109375** (= 108/512).

| 항목 | 원본 (512) | 변환 (108) |
|------|-----------|-----------|
| 물방울 시작점 | (256, 100) | (54, 21.094) |
| 물방울 끝점 (Z) | (256, 100) | (54, 21.094) |
| 물방울 arc 정점 | y ≈ 410 | y ≈ 86.484 |
| 광택 중심 | (212, 218) | (44.742, 45.984) |
| 광택 반경 | (26, 14) | (5.484, 2.953) |
| 체크 P1 | (214, 296) | (45.156, 62.453) |
| 체크 P2 | (246, 330) | (51.914, 69.609) |
| 체크 P3 | (304, 268) | (64.125, 56.531) |
| stroke-width | 22 | 4.641 |
| 광택 회전 | -32° | -32° (각도는 불변) |

### ⚠️ 좌표 표현 결정 사항 (사용자 확인 필요)

| 방식 | 장점 | 단점 |
|------|------|------|
| **A. 소수값 정확 사용** (예: 21.094, 45.156, 4.641) | 시안과 픽셀 단위까지 일치 | XML 가독성 ↓ |
| **B. 정수 근사값** (예: 21, 45, 5) | 가독성 ↑ | 시각 차이 발생 가능 (특히 작은 광택 타원에서 ~1px 오차) |

**권장**: **A. 소수값** — 시각 일치가 시안 통합의 본질이며, Android Vector Drawable 은 소수 좌표 완벽 지원.

---

## 3. 레이어 분리 전략 (Adaptive Icon Foreground/Background)

| 레이어 | 내용 | 비고 |
|--------|------|------|
| Background | `#F1F8E9` 단색 | 둥근 모서리는 **OS 마스킹**이 자동 적용 — vector 에서 corner radius 표현하지 않음 |
| Foreground | 물방울(gradient) + 광택(ellipse, 회전) + 체크(stroke) | 단일 vector drawable 에 3 path |

### Safe Zone 검증 (108 viewport 중앙 66%)
- Safe zone 범위: **18 ~ 90** (viewport 좌표)
- 물방울이 차지하는 영역 (변환 후):
  - 상단: y ≈ 21.094 ❌ (safe zone 상단 18 보다는 안쪽, 하지만 마진 3 만)
  - 하단: y ≈ 86.484 ✅ (90 안)
  - 좌우: x ≈ 28.69 ~ 79.31 ✅
- **상단 마진이 3 (약 3%) 로 작지만 safe zone 내에 포함** — Pixel 런처 원형 마스크 기준 물방울 꼭지가 살짝 잘릴 가능성 모니터링 필요. 시각 검증에서 확인.

> 시안의 물방울이 상하로 길쭉한 형태라 viewport 상단을 거의 채운다. 더 안전하게 가려면 전체를 0.9 스케일로 축소할 수 있으나, 본 STEP 은 **"시안 그대로 재현"** 이 목표이므로 원본 크기 유지.

---

## 4. 산출물 파일 목록

### 신규 생성
| 파일 | 목적 |
|------|------|
| `res/drawable/ic_launcher_background.xml` | 단색 vector (#F1F8E9) |
| `res/drawable/ic_launcher_foreground.xml` | 물방울 + 광택 + 체크 vector (aapt namespace 필수) |
| `res/mipmap-anydpi-v26/ic_launcher.xml` | adaptive-icon 정의 |
| `res/mipmap-anydpi-v26/ic_launcher_round.xml` | 동일 내용 (원형 마스크는 OS) |
| `docs/assets/checkdang-icon.svg` | 시안 원본 SVG (변경 이력/참조용 보관) |

### 수정
| 파일 | 변경 |
|------|------|
| `AndroidManifest.xml` | `android:icon` / `android:roundIcon` 속성 확인 (이미 `@mipmap/ic_launcher*` 참조 중 — 변경 불필요 예상) |
| `res/mipmap-mdpi/ic_launcher.xml` | 기존 placeholder adaptive-icon — 정리 결정 필요 |
| `res/mipmap-mdpi/ic_launcher_round.xml` | 동상 |

---

## 5. Legacy Fallback (Android 7.1 이하)

`mipmap-anydpi-v26` 의 adaptive icon 은 **Android 8.0 (API 26)+** 에서 적용됨.

본 앱의 `minSdk=26` 이므로 **Android 7.1 이하 사용자는 정의상 존재하지 않음** → **legacy fallback PNG/WebP 불필요**.

### 결정 사항 (사용자 확인 필요)

| 방식 | 내용 |
|------|------|
| **A. Image Asset Studio 안내** | 원안 — 사용자가 Android Studio 에서 mipmap 5종 자동 생성 |
| **B. Legacy 생성 안 함** (권장) | `minSdk=26` 이므로 불필요. 기존 `mipmap-mdpi` placeholder 정리만 |

**권장**: **B** — minSdk 가 26이라 7.1 이하는 설치 자체가 불가능. legacy raster 는 죽은 자산. `mipmap-mdpi` 의 placeholder 두 파일은 **삭제**해서 혼란 제거.

---

## 6. 의도적으로 하지 않는 것

- Android 13+ Themed Icon (모노크롬 `monochrome` 레이어) — 별도 STEP
- Play Store 등록용 512×512 PNG / Feature Graphic — 출시 단계
- 다크 모드 전용 아이콘 변형 — 앱 전체 라이트 전용 정책
- Notification small icon (24×24 단색) — 알림 기능 자체 미구현, 후속 STEP

---

## 7. 자가 검증 체크리스트

| # | 항목 | 통과 조건 |
|---|------|-----------|
| 1 | 빌드 | `./gradlew assembleDebug` BUILD SUCCESSFUL |
| 2 | adaptive icon 4종 파일 | foreground/background + mipmap-anydpi-v26 의 launcher/launcher_round 모두 존재 |
| 3 | 시안 색상 정확성 | `#F1F8E9` / `#66BB6A` / `#388E3C` 모두 hit |
| 4 | 그라데이션 수직 | `startX==endX==54`, `startY<endY` |
| 5 | 체크 stroke 4속성 | strokeColor=`#FFFFFF`, strokeLineCap=round, strokeLineJoin=round, strokeWidth≈4.641 |
| 6 | 광택 회전 | `<group>` rotation=-32, pivot=(44.742, 45.984) |
| 7 | 금기 컬러 부재 | `#FF0000` / `#F44336` / `#FF9800` / red / orange 없음 |
| 8 | Manifest 등록 | `android:icon` + `android:roundIcon` 둘 다 `@mipmap/ic_launcher*` |
| 9 | aapt 네임스페이스 | foreground 에 `xmlns:aapt` hit (gradient 컴파일 필수) |

---

## 8. 시각 검증 (수동)

1. `./gradlew installDebug` 후 홈 화면 아이콘 확인
2. 시안 일치도: 민트 배경 / 그라데이션 물방울 / 좌상단 광택 (기울어진 타원) / 흰색 체크 (둥근 끝)
3. 런처별 마스킹 (원형/스퀴클/사각형) 에서 물방울 꼭지 잘림 확인
4. 설정 > 앱 정보의 작은 아이콘 식별 가능 여부
5. 알림 영역 (24×24) 표시는 본 STEP 범위 아님 (알림 기능 미구현)

---

## 9. 사용자 결정 요청 사항

승인 시 다음 2가지를 확정해 주세요:

| # | 결정 항목 | 옵션 A | 옵션 B | 권장 |
|---|----------|--------|--------|------|
| Q1 | 좌표 표현 | 정확한 소수값 (예: 4.641) | 정수 근사값 (예: 5) | A |
| Q2 | Legacy fallback | Image Asset Studio 가이드 | 생성 안 함 + 기존 placeholder 삭제 | B (minSdk=26) |
