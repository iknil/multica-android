package ai.multica.android.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppConfigTest {
    @Test
    fun acceptsAbsoluteHttpsUrl() {
        val config = AppConfig("https://api.example.com", "1.0.0")
        assertEquals("https://api.example.com", config.apiBaseUrl)
    }

    @Test
    fun rejectsMissingHostAndBlankVersion() {
        assertThrows(IllegalArgumentException::class.java) { AppConfig("/api", "1.0.0") }
        assertThrows(IllegalArgumentException::class.java) { AppConfig("https://api.example.com", "") }
    }

    @Test
    fun fixtureBuildKeepsSafeInvalidDefaultWithoutOAuthConfiguration() {
        val config = AppConfig("https://api.example.invalid", "1.0.0", fixtureMode = true)
        assertEquals(true, config.fixtureMode)
        assertEquals("", config.googleServerClientId)
    }

    @Test
    fun connectedBuildRequiresApprovedHostAndGoogleClientId() {
        assertThrows(IllegalArgumentException::class.java) {
            AppConfig("https://api.example.invalid", "1.0.0", fixtureMode = false, googleServerClientId = "client")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppConfig("https://test.example.com", "1.0.0", fixtureMode = false)
        }
        val config = AppConfig(
            "https://test.example.com",
            "1.0.0",
            fixtureMode = false,
            googleServerClientId = "server-client.apps.googleusercontent.com",
        )
        assertEquals(false, config.fixtureMode)
    }
}
