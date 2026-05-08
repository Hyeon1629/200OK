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
- **Samsung Health SDK 직접 연동 금지** — 삼성 개발자 계정 + 앱 심사 필요
- **Android Health Connect 허용** — 삼성 헬스가 자동 동기화됨
- Room DB 등 데이터 영속성 코드 금지
- 의존성 주입 프레임워크(Hilt/Koin) 미사용

## Health Connect 연동 구조

```
HealthDataSource (interface)
├── MockHealthDataSource      ← 기본값 (개발/오프라인)
├── HealthConnectDataSource   ← 실제 삼성 헬스 데이터 (권한 허가 후)
└── SamsungHealthDataSource   ← 미래 SDK 직접 연동용 stub

HealthRepository.switchToHealthConnect() 로 구현체 교체
```

삼성 헬스 동기화 조건: Galaxy 기기 + Samsung Health 설정 → Health Platform → "Health Connect와 동기화" 활성화

## Implementation Log 규칙

**작업 완료 후 반드시 `IMPLEMENTATION_LOG.md`를 업데이트해야 한다.**

- 작업 시작 전: `IMPLEMENTATION_LOG.md`를 읽어 현재까지 구현된 내용을 파악한다
- 작업 완료 후: 날짜 / 작업 내용 / 수정·신규 파일 목록 / 주요 결정 사항을 기록한다
- 기록 형식: `## [YYYY-MM-DD] 작업 제목` 헤더 아래 표 또는 목록으로 정리

## Coding Conventions

- 레이아웃 파일명: `snake_case`, prefix `activity_` / `fragment_` / `item_` / `dialog_`
- Drawable: Vector Drawable 우선
- ViewBinding 항상 사용 (findViewById 금지)
