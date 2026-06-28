plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.toddleai.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.toddleai.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ExecuTorch + QNN ship arm64-v8a only and the target device (Samsung S25 Ultra, SM8750) is
        // arm64. Restrict ABIs so MediaPipe's x86/armeabi-v7a libs don't bloat the APK with code the
        // device can't run.
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

    sourceSets.getByName("main") {
        java.srcDirs("src/main/java", "src/main/kotlin")
        assets.srcDirs("src/main/assets")
        jniLibs.srcDirs("src/main/jniLibs", "build/generated/qnnJniLibs")
    }

    sourceSets.getByName("androidTest") {
        java.srcDirs("src/androidTest/java", "src/androidTest/kotlin")
        assets.srcDirs("src/androidTest/assets")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // MediaPipe tasks-vision and the fbjni/ExecuTorch stack each ship their own libc++_shared.so;
        // keep one to avoid a duplicate-native-library packaging failure.
        jniLibs {
            pickFirsts += "**/libc++_shared.so"
        }
    }
}

dependencies {
    implementation(files("libs/executorch.aar"))
    implementation("com.facebook.soloader:nativeloader:0.10.5")
    implementation("com.facebook.fbjni:fbjni:0.7.0")

    // On-device gait pose estimation: MediaPipe Tasks PoseLandmarker (full 33-landmark BlazePose,
    // incl. feet) driven by app/src/main/assets/pose_landmarker_full.task. Runs locally on CPU
    // (XNNPACK) or GPU; no network. This is the only repo artifact capable of gait-quality landmarks.
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:core-ktx:1.5.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
