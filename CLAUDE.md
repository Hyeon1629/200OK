# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**체크당 (Check-Dang)** — 혈당 + 라이프스타일 통합 관리 + AI 2D 바디맵 분석 헬스케어 Android 앱. MVP 단계이며 백엔드 없이 Mock 데이터로만 동작한다.

- **Platform**: Android Native, minSdk 26 (Android 8.0), targetSdk 34
- **Language**: Kotlin only (Java 사용 금지)
- **UI**: XML Layout + ViewBinding (Jetpack Compose 미사용)
- **Build**: Gradle Kotlin DSL (`build.gradle.kts`)
- **Package**: `com.checkdang.app`

## Commands

```bash
# 빌드
./gradlew assembleDebug

# 테스트
./gradlew test

# 단일 테스트 클래스 실행
./gradlew test --tests "com.checkdang.app.ExampleTest"

# Lint
./gradlew lint

# 설치 (연결된 기기/에뮬레이터)
./gradlew installDebug
```

## Architecture

**MVVM** — ViewModel + LiveData/StateFlow. Repository 계층은 Mock으로만 구현한다.

### Data Flow

```
MockDataProvider → Repository(Mock) → ViewModel → Fragment/Activity (ViewBinding)
```

- 모든 목 데이터는 `data/mock/MockDataProvider.kt` 단일 소스에서 제공한다.
- 비동기는 Coroutines + Flow 사용.
- `data/model/` 에는 Data Class만 둔다 (GlucoseRecord, PatientProfile 등).

### Navigation

Jetpack Navigation Component. `res/navigation/nav_graph.xml` 단일 NavGraph.

- **SplashActivity** → **MainActivity** (BottomNavigationView 호스트)
- BottomNav 탭: Home / Glucose / Lifestyle / BodyMap / Menu

### Key Screens

| Package | Screen | 역할 |
|---------|--------|------|
| `ui/splash` | SplashActivity | 앱 진입점 |
| `ui/auth` | Login / Signup / Onboarding | 인증 플로우 (Mock) |
| `ui/main` | MainActivity | BottomNav 호스트 |
| `ui/home` | HomeFragment | 대시보드 |
| `ui/glucose` | GlucoseFragment | 혈당 기록/차트 |
| `ui/lifestyle` | LifestyleFragment | 라이프스타일 관리 |
| `ui/bodymap` | BodyMapFragment | AI 2D 바디맵 |
| `ui/menu` | MenuFragment | 설정/메뉴 |
| `ui/family` | FamilyFragment | 가족 관리 |

## Design Tokens

```xml
<!-- colors.xml 기준 -->
colorPrimary:          #4CAF50  <!-- Material Green 500 -->
colorPrimaryDark:      #388E3C
colorPrimaryLight:     #E8F5E9
textPrimary:           #1A1A1A
textSecondary:         #6E6E73
divider:               #E5E5EA
backgroundSecondary:   #F7F8FA

<!-- 혈당 상태 -->
statusNormal:          #4CAF50
statusWarning:         #FF9800
statusDanger:          #F44336
```

## Glucose Status Thresholds (mg/dL)

| 측정 유형 | 저혈당(위험) | 정상 | 주의 | 고혈당(위험) |
|-----------|------------|------|------|------------|
| 공복 | < 70 | 70 – 99 | 100 – 125 | ≥ 126 |
| 식후 2시간 | < 70 | 70 – 139 | 140 – 199 | ≥ 200 |

## Key Dependencies

- **Chart**: `com.github.PhilJay:MPAndroidChart:v3.1.0`
- **Image**: Coil (Glide 대신 선호)
- **Navigation**: Jetpack Navigation Component
- **Async**: Kotlin Coroutines + Flow

## Constraints (MVP 금지 사항)

- 백엔드 API 호출 코드 작성 금지
- Firebase, Retrofit, Samsung Health SDK 등 외부 서비스 의존성 추가 금지
- 실제 인증/결제 로직 금지
- Room DB 등 데이터 영속성 코드 금지
- 의존성 주입 프레임워크(Hilt/Koin) 미사용 — 필요 시 수동 인스턴스화

## Coding Conventions

- 레이아웃 파일명: `snake_case`, prefix `activity_` / `fragment_` / `item_` / `dialog_`
- Drawable: Vector Drawable 우선 (SVG → VD 변환)
- ViewBinding 항상 사용 (findViewById 금지)
- 파일 생성/수정 시 변경 사유를 한 줄로 설명한다
- 빌드 오류 위험 요소는 작업 전 미리 고지한다
