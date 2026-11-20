import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ============================================================
// Win VPN V3 - Real Xray Core
// AndroidLibXrayLite / libv2ray AAR
// ============================================================

val coreLock = Properties().apply {
    rootProject.file("core.lock").inputStream().use {
        load(it)
    }
}

// Real Xray core AAR expected here.
val coreAar = file("libs/libv2ray.aar")

// IMPORTANT:
// This variable MUST be declared before it is used.
val hasXrayCore = coreAar.exists()

// Never allow a production APK without the real Xray core.
if (!hasXrayCore) {
    throw GradleException(
        "Win VPN requires app/libs/libv2ray.aar. " +
        "Run scripts/fetch_core.sh before building."
    )
}

// ============================================================
// Verify Xray Core SHA-256
// ============================================================

val verifyCoreAar by tasks.registering {

    val aar = coreAar
    val expected = coreLock.getProperty("CORE_AAR_SHA256")

    if (expected.isNullOrBlank()) {
        throw GradleException(
            "CORE_AAR_SHA256 is missing from core.lock."
        )
    }

    inputs.file(aar)

    doLast {
        if (!aar.exists()) {
            throw GradleException(
                "Missing Xray core AAR: ${aar.absolutePath}"
            )
        }

        val md = MessageDigest.getInstance("SHA-256")

        aar.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)

            while (true) {
                val count = input.read(buffer)

                if (count < 0) {
                    break
                }

                md.update(buffer, 0, count)
            }
        }

        val actual = md.digest()
            .joinToString("") {
                "%02x".format(it)
            }

        check(actual.equals(expected, ignoreCase = true)) {
            "libv2ray.aar SHA-256 mismatch. " +
            "Expected: $expected, " +
            "Actual: $actual. " +
            "Re-run scripts/fetch_core.sh or deliberately update core.lock."
        }
    }
}

// Make sure core verification happens before preBuild.
tasks.matching {
    it.name == "preBuild"
}.configureEach {
    dependsOn(verifyCoreAar)
}

// ============================================================
// Android Configuration
// ============================================================

android {
    namespace = "com.amir.winvpn"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.amir.winvpn"

        minSdk = 26
        targetSdk = 36

        versionCode = 3
        versionName = "3.0.0"

        // ABIs supported by the real Xray core AAR.
        ndk {
            abiFilters += listOf(
                "arm64-v8a",
                "armeabi-v7a",
                "x86_64"
            )
        }
    }

    // V3 uses the real Xray implementation.
    sourceSets
        .getByName("main")
        .java
        .srcDir("src/xray/java")

    buildTypes {

        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
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
}

// ============================================================
// Dependencies
// ============================================================

dependencies {

    // AndroidX
    implementation(
        "androidx.core:core-ktx:1.15.0"
    )

    implementation(
        "androidx.activity:activity-compose:1.10.1"
    )

    implementation(
        "androidx.lifecycle:lifecycle-runtime-ktx:2.8.7"
    )

    implementation(
        "androidx.lifecycle:lifecycle-runtime-compose:2.8.7"
    )

    implementation(
        "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7"
    )

    // Coroutines
    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0"
    )

    // ========================================================
    // Jetpack Compose
    // ========================================================

    implementation(
        platform(
            "androidx.compose:compose-bom:2025.04.01"
        )
    )

    implementation(
        "androidx.compose.ui:ui"
    )

    implementation(
        "androidx.compose.ui:ui-tooling-preview"
    )

    implementation(
        "androidx.compose.material3:material3"
    )

    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )

    // ========================================================
    // REAL XRAY CORE
    // ========================================================

    implementation(
        files("libs/libv2ray.aar")
    )
}
