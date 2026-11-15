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

fun parseCanonicalIpv4(host: String): IntArray? {
    val parts = host.split('.')
    if (parts.size != 4) return null
    return parts.map { part ->
        if (part.isEmpty() || (part.length > 1 && part.startsWith('0')) || part.any { !it.isDigit() }) {
            return null
        }
        part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
    }.toIntArray()
}

fun parseIpv6Literal(host: String): ByteArray? {
    if (!host.startsWith('[') || !host.endsWith(']')) return null
    val literal = host.substring(1, host.length - 1)
    if (literal.isEmpty() || '%' in literal) return null
    val doubleColon = literal.indexOf("::")
    if (doubleColon != literal.lastIndexOf("::")) return null

    fun parseSide(value: String): MutableList<Int>? {
        if (value.isEmpty()) return mutableListOf()
        val groups = mutableListOf<Int>()
        for ((index, part) in value.split(':').withIndex()) {
            if (part.contains('.')) {
                if (index != value.split(':').lastIndex) return null
                val ipv4 = parseCanonicalIpv4(part) ?: return null
                groups += (ipv4[0] shl 8) or ipv4[1]
                groups += (ipv4[2] shl 8) or ipv4[3]
            } else {
                if (part.isEmpty() || part.length > 4 || part.any { it.digitToIntOrNull(16) == null }) return null
                groups += part.toInt(16)
            }
        }
        return groups
    }

    val left = parseSide(if (doubleColon >= 0) literal.substring(0, doubleColon) else literal) ?: return null
    val right = parseSide(if (doubleColon >= 0) literal.substring(doubleColon + 2) else "") ?: return null
    val missing = 8 - left.size - right.size
    if ((doubleColon < 0 && missing != 0) || (doubleColon >= 0 && missing < 1)) return null
    val groups = left + List(missing) { 0 } + right
    if (groups.size != 8) return null
    return ByteArray(16).also { bytes ->
        groups.forEachIndexed { index, group ->
            bytes[index * 2] = (group ushr 8).toByte()
            bytes[index * 2 + 1] = group.toByte()
        }
    }
}

fun isLoopbackHost(host: String): Boolean {
    val normalized = host.lowercase()
    if (normalized == "localhost" || normalized == "localhost.") return true
    parseCanonicalIpv4(normalized)?.let { return it[0] == 127 }
    parseIpv6Literal(normalized)?.let { bytes ->
        val ipv6Loopback = bytes.take(15).all { it == 0.toByte() } && bytes[15] == 1.toByte()
        val mappedIpv4 = bytes.take(10).all { it == 0.toByte() } &&
            bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte() &&
            bytes[12].toInt() and 0xff == 127
        val compatibleIpv4 = bytes.take(12).all { it == 0.toByte() } &&
            bytes[12].toInt() and 0xff == 127
        return ipv6Loopback || mappedIpv4 || compatibleIpv4
    }
    return false
}

fun isNonCanonicalNumericHost(host: String): Boolean {
    val normalized = host.lowercase()
    if (normalized.startsWith('[')) return parseIpv6Literal(normalized) == null
    if (normalized.matches(Regex("[0-9.]+"))) return parseCanonicalIpv4(normalized) == null
    return normalized.startsWith("0x") || normalized.contains('%')
}

if (!fixtureModeValue) {
    require(
        apiUri?.scheme == "https" &&
            !apiUri.host.isNullOrBlank() &&
            apiUri.rawUserInfo == null &&
            apiUri.rawQuery == null &&
            apiUri.rawFragment == null,
    ) {
        "Non-fixture builds require an absolute HTTPS multica.apiBaseUrl without userinfo, query, or fragment"
    }
    require(!isNonCanonicalNumericHost(apiUri!!.host)) {
        "Non-fixture builds reject non-canonical IP address forms"
    }
    require(apiUri.host.endsWith(".invalid", ignoreCase = true).not()) {
        "Non-fixture builds cannot use a .invalid API host"
    }
    require(configuredGoogleServerClientId.get().isNotBlank()) {
        "Non-fixture builds require multica.googleServerClientId"
    }
}
if (releaseRequested) {
    require(!fixtureModeValue) { "Release builds must set -Pmultica.fixtureMode=false" }
    require(!isLoopbackHost(apiUri!!.host)) {
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
