plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.checkdang.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.checkdang.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.viewpager2)
    implementation(libs.coil)
    implementation(libs.coroutines.android)
    implementation(libs.mpandroidchart)
    implementation(libs.play.services.auth)
    implementation(libs.kakao.user)
    // Android Health Connect — 삼성 헬스가 One UI 6.0+ 기기에서 자동 동기화됨
    implementation("androidx.health.connect:connect-client:1.1.0-alpha11")
    // Google Play Billing — 인앱 구독 결제 (STEP 10)
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    // === Markwon — Markdown 렌더링 (이용약관/개인정보처리방침 등 정적 문서) ===
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")

    // === Samsung Health Data SDK (STEP 11 — Phase 2 활성화) ===
    // app/libs/samsung-health-data-api-1.1.0.aar — 개발자 모드 가정.
    // SDK 가 Kotlin coroutines suspend API 를 네이티브로 노출하므로 별도 래퍼 불필요.
    // minSdk: AAR=29, 앱=26 → AndroidManifest 의 tools:overrideLibrary 로 merger 통과,
    //        런타임은 Build.VERSION.SDK_INT >= 29 가드(SamsungHealthRepository.connect)로 보호.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    // === AWS Amplify Auth (Cognito) — 백엔드 인증 단일화 (2026-05-24) ===
    // 자체 OAuth+JWT 구조에서 Cognito User Pool + Identity Pool 로 전환.
    // Hosted UI 로 Google·Kakao Federated Sign-In, ID Token / 게스트 Identity ID 직접 발급.
    // core-kotlin: suspend 코루틴 facade. amplifyconfiguration.json 은 res/raw 에 위치.
    implementation("com.amplifyframework:core-kotlin:2.19.1")
    implementation("com.amplifyframework:aws-auth-cognito:2.19.1")
}
