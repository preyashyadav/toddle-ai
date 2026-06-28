plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.npuchat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.npuchat"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // The S25 Ultra is arm64; QNN .so are only shipped for this ABI.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    // Keep the Qualcomm .so verbatim (do not strip/compress; some are signed DSP skels).
    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/*.so"
        }
    }
}

dependencies {
    // QNN-enabled ExecuTorch runtime — user drops executorch.aar into app/libs/.
    // fileTree keeps Gradle sync working even before the AAR is present.
    implementation(fileTree("libs") { include("*.aar") })
    // The AAR's native-loader deps (not auto-resolved for a local .aar):
    implementation("com.facebook.soloader:nativeloader:0.10.5")
    implementation("com.facebook.fbjni:fbjni:0.7.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
