plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ormo.managerdemo"
    // 최신 AndroidX(Compose 1.12 / Lifecycle 2.11)가 compileSdk 37 이상을 요구한다.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ormo.managerdemo"
        minSdk = 26
        // Android 16(API 36) 이상은 태블릿(>=600dp)에서 screenOrientation 고정을 무시하므로,
        // 가로 고정을 유지하려고 targetSdk는 35로 둔다. (README 참고)
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
