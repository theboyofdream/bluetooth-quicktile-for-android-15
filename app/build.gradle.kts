plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

base {
    archivesName.set("BluetoothQuickTile")
}

android {
    namespace = "com.bluetoothquicktile.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bluetoothquicktile.app"
        minSdk = 24
        // targetSdk is set to 32 to utilize the Android backward compatibility layer
        // (the exact mechanism discovered from MacroDroid Connectivity Helper)
        // which allows programmatic Bluetooth enable/disable on Android 13, 14, and 15
        // without encountering the API 33+ system restriction or popup prompts.
        targetSdk = 32
        versionCode = 1
        // Single source of truth for the version. The release workflow filters on this file and
        // tags the release v<contents>, so bumping it is the whole release action.
        versionName = rootProject.file("VERSION").readText().trim()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("int", "TARGET_SDK", "32")
        buildConfigField("int", "COMPILE_SDK", "35")
    }

    // Exposes TARGET_SDK and COMPILE_SDK to the app so user-facing text that mentions them
    // cannot drift out of sync with these values.
    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("release.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("KEY_ALIAS") ?: "release"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        release {
            // R8 strips the unused Material Design glyphs. Without this the APK carries roughly
            // two thousand vector drawables that the app never draws.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null && releaseSigning.storeFile!!.exists()) {
                signingConfig = releaseSigning
            } else {
                // Falls back to the debug key so a local build still produces an installable APK.
                // CI sets KEYSTORE_BASE64 and fails the job when it is missing, so this path
                // cannot silently publish a debug-signed artifact.
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
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

    lint {
        // The release build must fail on a real lint error rather than publishing a broken APK.
        // The unused-resource and export warnings are addressed directly in the source.
        checkReleaseBuilds = true
        abortOnError = true
        warningsAsErrors = false
        // targetSdk is deliberately held at 32 for the Bluetooth compatibility layer. This is a
        // conscious product decision, so the expiry warning is suppressed rather than fixed.
        disable.add("ExpiredTargetSdkVersion")
    }
}

// Pin the APK output names so the release workflow and the documented download link refer to the
// same file rather than AGP's default app-release.apk.
//
// VariantOutput exposes no public outputFileName in AGP 8.7 (only versionName), so the rename goes
// through applicationVariants, which is deprecated but still the supported route in 8.x.
@Suppress("DEPRECATION")
android.applicationVariants.all {
    outputs.all {
        (this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl)?.outputFileName =
            "BluetoothQuickTile-$name.apk"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    // Deliberately material-icons-core, not -extended. The extended artifact is ~34 MB unpacked
    // and ships ~2,000 glyphs; this app draws eleven. See ui/AppIcons.kt for the glyph list.
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}