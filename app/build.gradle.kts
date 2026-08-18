import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Instrumented tests (and any emulator run) need the emulator's x86_64 native libs, which we
// otherwise exclude to keep the APK small. Opt in per-invocation: -PemulatorAbi=true
val includeEmulatorAbi = (project.findProperty("emulatorAbi") as String?)?.toBoolean() ?: false

// Play Store upload key — never committed (see .gitignore). Missing on a fresh checkout unless
// you generate your own (see the plan doc / README for the keytool command); debug/test tasks
// still work without it, only *Release tasks need it, and fail loudly below if it's absent.
val keystorePropertiesFile = file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}

gradle.taskGraph.whenReady {
    val needsSigning = allTasks.any { it.name.contains("Release") }
    if (needsSigning && !keystorePropertiesFile.exists()) {
        throw GradleException(
            "keystore.properties가 없어 릴리스 빌드에 서명할 수 없습니다 — " +
                "app/app/keystore.properties를 로컬에 생성하세요 (git에 커밋하지 말 것).",
        )
    }
}

android {
    namespace = "dev.sangjun.mobileaim"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.sangjun.mobileaim"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // org.opencv:opencv bundles native libs for all 4 ABIs by default (~134MB unstripped).
        // Real devices are effectively all arm64-v8a today; x86/x86_64 only matter for emulators.
        ndk {
            abiFilters += "arm64-v8a"
            if (includeEmulatorAbi) abiFilters += "x86_64"
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // Extended (not just -core) — the header now needs GpsFixed/Pause/CameraAlt, which aren't in
    // the small -core icon set.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("androidx.camera:camera-video:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.insert-koin:koin-android:3.5.6")
    // Official OpenCV Android AAR on Maven Central (packaging=aar, bundles native .so, no manual NDK setup).
    // Verified against https://repo1.maven.org/maven2/org/opencv/opencv/maven-metadata.xml — re-check for
    // a newer stable release before bumping; 5.0.0 was published very recently and is intentionally skipped
    // here in favor of the more battle-tested 4.x line.
    implementation("org.opencv:opencv:4.11.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
