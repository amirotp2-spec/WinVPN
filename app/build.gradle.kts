import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The VPN core (Xray, via 2dust/AndroidLibXrayLite) is a native AAR pinned in ../core.lock.
// Get it with scripts/fetch_core.sh (downloads + verifies SHA-256) or the "Core AAR" GitHub workflow.
// The production build always uses the real Xray engine. A missing or invalid core is a build error.
val coreLock = Properties().apply { rootProject.file("core.lock").inputStream().use { load(it) } }
val coreAar = file("libs/libv2ray.aar")
if (!hasXrayCore) {
    throw GradleException("Win VPN requires app/libs/libv2ray.aar. Run scripts/fetch_core.sh before building.")
}

val verifyCoreAar by tasks.registering {
    val aar = coreAar
    val expected = coreLock.getProperty("CORE_AAR_SHA256")
    inputs.files(aar)
    
    doLast {
        val md = MessageDigest.getInstance("SHA-256")
        aar.inputStream().use { ins ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        check(actual == expected) {
            "libv2ray.aar SHA-256 mismatch (expected $expected, got $actual). " +
                "Re-run scripts/fetch_core.sh or update core.lock deliberately."
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(verifyCoreAar) }

android {
    namespace = "com.amir.winvpn"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.amir.winvpn"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "3.0.0"

        // ABIs shipped from the core AAR (it also contains x86, which is dropped: no real devices use it).
        // arm64-v8a: all modern phones; armeabi-v7a: older 32-bit phones; x86_64: emulators.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    sourceSets.getByName("main").java.srcDir("src/xray/java")

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation(files("libs/libv2ray.aar"))
}
