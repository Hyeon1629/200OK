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

    // === Samsung Health Data SDK (STEP 11 — Phase 2 활성화 대기) ===
    // Samsung Health Partner Apps Program 승인 + AAR 수령 후 아래 블록 활성화:
    //   1. app/libs/samsung-health-data-api-<version>.aar 배치
    //   2. 아래 두 implementation 라인 주석 해제
    //   3. 파일 상단 plugins 블록에 `kotlin("plugin.parcelize")` 추가
    //   4. SamsungHealthRepository.kt 의 TODO(samsung-sdk) 마커 활성화
    // implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    // implementation("com.google.code.gson:gson:2.9.0")
}
