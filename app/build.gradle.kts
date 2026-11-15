import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val configuredApiBaseUrl = providers.gradleProperty("multica.apiBaseUrl")
    .orElse("https://api.example.invalid")
val configuredFixtureMode = providers.gradleProperty("multica.fixtureMode")
    .orElse("true")
val configuredGoogleServerClientId = providers.gradleProperty("multica.googleServerClientId")
    .orElse("")

val fixtureModeValue = configuredFixtureMode.get().also {
    require(it == "true" || it == "false") { "multica.fixtureMode must be true or false" }
}.toBoolean()
val apiUri = runCatching { URI(configuredApiBaseUrl.get()) }.getOrNull()
val releaseRequested = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
if (!fixtureModeValue) {
    require(apiUri?.scheme in setOf("http", "https") && !apiUri?.host.isNullOrBlank()) {
        "Non-fixture builds require an absolute HTTP(S) multica.apiBaseUrl"
    }
    require(apiUri?.host?.endsWith(".invalid") == false) {
        "Non-fixture builds cannot use a .invalid API host"
    }
    require(configuredGoogleServerClientId.get().isNotBlank()) {
        "Non-fixture builds require multica.googleServerClientId"
    }
}
if (releaseRequested) {
    require(!fixtureModeValue) { "Release builds must set -Pmultica.fixtureMode=false" }
    require(apiUri?.scheme == "https") { "Release builds require an HTTPS multica.apiBaseUrl" }
    require(apiUri?.host !in setOf("localhost", "127.0.0.1", "::1")) {
        "Release builds cannot target a loopback API host"
    }
}

android {
    namespace = "ai.multica.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.multica.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-feasibility"

        buildConfigField("String", "API_BASE_URL", "\"${configuredApiBaseUrl.get()}\"")
        buildConfigField("boolean", "FIXTURE_MODE", fixtureModeValue.toString())
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"${configuredGoogleServerClientId.get()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
