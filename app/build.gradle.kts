import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // 4.x needs AGP 9 / Gradle 9 — this project is still on AGP 8.5.2 / Gradle 8.7, so pinned to
    // the last 3.x release instead.
    id("com.github.triplet.play") version "3.13.0"
    // Provides the `record*Roborazzi` task that AimOverlayScreenshotTest needs — plain `test` runs
    // treat captureRoboImage() calls as a no-op (by design, to avoid silently writing files during
    // normal CI test runs).
    id("io.github.takahirom.roborazzi") version "1.24.0"
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
        // CI passes -PversionCode=1000+<github.run_number> so every auto-published build gets a
        // fresh, strictly-increasing code without committing anything back to the repo — offset
        // by 1000 to stay clear of versionCode 1, which the first release used (uploaded manually,
        // before this pipeline existed). Local builds (no property set) fall back to 1.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
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
        // Installs next to the CI-published release instead of replacing it — the release's
        // versionCode (1000+) blocks a local debug build (versionCode 1) from updating it, and
        // uninstalling it would wipe the saved zero profiles.
        debug {
            applicationIdSuffix = ".debug"
        }
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
        buildConfig = true
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

    // Robolectric needs resource merging visible on the unit-test classpath (Material3 pulls
    // system attrs) to render Compose content headlessly — see AimOverlayScreenshotTest, a
    // device-free way to eyeball UI changes without an emulator.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Gradle Play Publisher — uploads a signed release bundle straight to the Internal testing track.
// play-service-account.json is CI-only (written from a GitHub secret, see .github/workflows/); it
// never exists on a dev machine, which is fine — only the `publish*` tasks need it, everything
// else (including plain `assembleRelease`/`bundleRelease`) builds without it.
play {
    serviceAccountCredentials.set(file("play-service-account.json"))
    track.set("internal")
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
    // Headless Compose screenshot rendering for AimOverlayScreenshotTest — no emulator/device
    // needed, runs as a plain JVM unit test. Plain compose-ui-test's captureToImage() times out
    // under Robolectric's paused main Looper (forceRedraw never gets pumped); Roborazzi's
    // captureRoboImage() is built specifically to work around that.
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // Pinned to 1.24.0, not latest — newer releases are compiled with Kotlin 2.x metadata,
    // incompatible with this project's Kotlin 1.9.24.
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.24.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.24.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
