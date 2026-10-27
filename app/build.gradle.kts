plugins {
    // Kotlin itself comes from AGP's built-in Kotlin support (AGP 9+).
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * Secrets live in the git-ignored `.env` at the repo root, so they are never
 * duplicated into a second file. The built-in key is only a development default;
 * it can be overridden at runtime in Settings.
 */
fun loadEnv(): Map<String, String> {
    val file = rootProject.file(".env")
    if (!file.exists()) return emptyMap()
    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
        .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }
}

val env = loadEnv()

android {
    namespace = "com.openagnes.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.openagnes.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField(
            "String",
            "AGNES_BASE_URL",
            "\"${env["AGNES_BASE_URL"] ?: "https://apihub.agnes-ai.com/v1"}\"",
        )
        buildConfigField("String", "AGNES_API_KEY", "\"${env["AGNES_API_KEY"] ?: ""}\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")

    implementation("com.squareup.okhttp3:okhttp:5.5.1")

    testImplementation("junit:junit:4.13.2")
}

// The integration tests talk to the real gateway, so they need the key that is
// otherwise only injected into BuildConfig.
tasks.withType<Test>().configureEach {
    environment("AGNES_API_KEY", env["AGNES_API_KEY"] ?: "")
    environment("AGNES_BASE_URL", env["AGNES_BASE_URL"] ?: "https://apihub.agnes-ai.com/v1")
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
}
