# CLAUDE.md

## Project Overview

**체크당 (Check-Dang)** — 혈당 + 라이프스타일 통합 관리 + AI 2D 바디맵 분석 헬스케어 Android 앱.

- **Platform**: Android Native, minSdk 26, compileSdk 35, targetSdk 34
- **Language**: Kotlin only (Java 사용 금지)
- **UI**: XML Layout + ViewBinding (Jetpack Compose 미사용)
- **Build**: Gradle Kotlin DSL (`build.gradle.kts`)
- **Package**: `com.checkdang.app`

## Commands

```bash
./gradlew assembleDebug   # 빌드
./gradlew test            # 테스트
./gradlew lint            # Lint
./gradlew installDebug    # 기기/에뮬레이터 설치
```

## Architecture

**MVVM** — ViewModel + StateFlow. 라이프스타일 데이터는 `HealthRepository`를 통해 조회하고, 혈당 등 나머지 Mock 데이터는 `MockDataProvider`에서 제공한다.

### Navigation

Jetpack Navigation Component. `res/navigation/nav_graph.xml` 단일 NavGraph.

- **SplashActivity** → **MainActivity** (BottomNavigationView 호스트)
- BottomNav 탭: Home / Glucose / Lifestyle / BodyMap / Menu

### Key Screens

| Package | Screen | 역할 |
|---------|--------|------|
| `ui/splash` | SplashActivity | 앱 진입점 |
| `ui/auth` | Login / Onboarding | 인증 플로우 |
| `ui/main` | MainActivity | BottomNav 호스트 |
| `ui/home` | HomeFragment | 대시보드 |
| `ui/glucose` | GlucoseFragment | 혈당 기록/차트 |
| `ui/lifestyle` | LifestyleFragment | 라이프스타일 관리 |
| `ui/bodymap` | BodyMapFragment | AI 2D 바디맵 |
| `ui/menu` | MenuFragment | 설정/메뉴 |
| `ui/family` | FamilyFragment | 가족 관리 |

## Design Tokens

```xml
colorPrimary:        #4CAF50
colorPrimaryDark:    #388E3C
colorPrimaryLight:   #E8F5E9
textPrimary:         #1A1A1A
textSecondary:       #6E6E73
divider:             #E5E5EA
backgroundSecondary: #F7F8FA
statusNormal:        #4CAF50
statusWarning:       #FF9800
statusDanger:        #F44336
```

## Glucose Status Thresholds (mg/dL)

| 측정 유형 | 저혈당 | 정상 | 주의 | 고혈당 |
|-----------|--------|------|------|--------|
| 공복 | < 70 | 70–99 | 100–125 | ≥ 126 |
| 식후 2시간 | < 70 | 70–139 | 140–199 | ≥ 200 |

## Key Dependencies

- **Chart**: `com.github.PhilJay:MPAndroidChart:v3.1.0`
- **Image**: Coil
- **Navigation**: Jetpack Navigation Component
- **Health**: `androidx.health.connect:connect-client:1.1.0-alpha10`

## Constraints

- 백엔드 API 호출 금지 (소셜 로그인/로그아웃 API 제외)
- Firebase, Retrofit 등 외부 서비스 의존성 추가 금지
- **Samsung Health Data SDK 허용 (조건부)** — Samsung Health Partner Apps Program 승인 + AAR 수령 완료 후 활성화. 미승인 상태에서는 스켈레톤만 유지하고 호출 경로 비활성화.
- **Android Health Connect 허용** — 삼성 헬스가 자동 동기화됨. Samsung Health Data SDK 와 병행 사용 가능 (Repository 가 활성 소스 관리).
- Room DB 등 데이터 영속성 코드 금지
- 의존성 주입 프레임워크(Hilt/Koin) 미사용

## Health 연동 구조

```
HealthDataSource (interface)
├── MockHealthDataSource      ← 기본값 (개발/오프라인)
├── HealthConnectDataSource   ← 실제 삼성 헬스 데이터 (Health Connect 권한 허가 후)
└── SamsungHealthDataSource   ← 미래 SDK 직접 연동용 stub (현 미사용)

HealthRepository.switchToHealthConnect() 로 구현체 교체

Samsung Health Data SDK (STEP 11 — Partner 승인 후 활성화):
data/samsunghealth/
├── HealthDataPermission       ← 5종 권한 enum
├── SamsungHealthRepository    ← Application scope, ConnectionState 관리
└── SamsungHealthMapper        ← SDK Response → 도메인 모델 변환
```

삼성 헬스 동기화 조건:
- (현재) Galaxy 기기 + Samsung Health 설정 → Health Platform → "Health Connect와 동기화" 활성화
- (Partner 승인 후) Samsung Health Data SDK 로 직접 조회 (Steps/Exercise/Nutrition/Sleep/Weight)

## 앱 아이콘

- **확정 시안**: 물방울 + 좌상단 광택 + 흰색 체크 (참조: `docs/assets/checkdang-icon.svg`)
- **구조**: Adaptive Icon (Background 단색 + Foreground vector). 원형/스퀴클/사각형 마스킹은 OS 가 처리
- **파일**:
  - `res/drawable/ic_launcher_background.xml` — 단색 `#F1F8E9`
  - `res/drawable/ic_launcher_foreground.xml` — gradient 물방울 + ellipse 광택(group rotation -32°) + stroke 체크
  - `res/mipmap-anydpi-v26/ic_launcher{,_round}.xml` — adaptive-icon 정의
- **색상 변경 금지**: `#F1F8E9` / `#66BB6A` / `#388E3C` / `#FFFFFF`
- **IMPORTANT: 체크 path 의 `strokeLineCap=round` 는 시각 일관성의 핵심. 변경 금지.**
- **IMPORTANT: 광택의 회전 각도 -32° 는 디자이너 의도. 변경 시 시안 재승인 필요.**
- 좌표는 viewBox 512 → 108 으로 변환 (× 0.2109375). foreground 의 그라데이션 컴파일에 `xmlns:aapt` 필수
- Legacy mipmap raster 미생성 — minSdk=26 이라 Android 8.0+ 만 지원 (adaptive icon 항상 적용)
- TODO(icon): Android 13+ Themed Icon (monochrome 레이어) 별도 STEP

## 약관 관리

- **약관 원문 위치**: `app/src/main/res/raw/terms_of_service.md` (UTF-8 마크다운)
- **렌더링**: `TermsActivity` 가 Markwon 으로 표시 (`io.noties.markwon:core` + `linkify`)
- **버전 변경 시**:
  1. 약관 본문 상단의 `시행일` / `최종 수정일` / `버전` 갱신
  2. 부칙 섹션에 변경 이력 한 줄 추가
  3. **사용자 재동의가 필요할 수 있음** — 변경 범위에 따라 법무 검토 후 진행
- TODO(legal): 현 v1.0 약관은 형식 템플릿이며, 출시 전 법무 검토 필요
- TODO(legal): 개인정보처리방침은 본문 확보 후 동일 패턴(`PrivacyPolicyActivity`)으로 구현 예정

## Implementation Log 규칙

**작업 완료 후 반드시 `IMPLEMENTATION_LOG.md`를 업데이트해야 한다.**

- 작업 시작 전: `IMPLEMENTATION_LOG.md`를 읽어 현재까지 구현된 내용을 파악한다
- 작업 완료 후: 날짜 / 작업 내용 / 수정·신규 파일 목록 / 주요 결정 사항을 기록한다
- 기록 형식: `## [YYYY-MM-DD] 작업 제목` 헤더 아래 표 또는 목록으로 정리

## Coding Conventions

- 레이아웃 파일명: `snake_case`, prefix `activity_` / `fragment_` / `item_` / `dialog_`
- Drawable: Vector Drawable 우선
- ViewBinding 항상 사용 (findViewById 금지)
