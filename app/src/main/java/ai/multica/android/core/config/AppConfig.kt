package ai.multica.android.core.config

import java.net.URI

data class AppConfig(
    val apiBaseUrl: String,
    val clientVersion: String,
    val fixtureMode: Boolean = true,
    val googleServerClientId: String = "",
) {
    init {
        val uri = runCatching { URI(apiBaseUrl) }.getOrNull()
        require(uri != null && uri.scheme == "https" && !uri.host.isNullOrBlank()) {
            "apiBaseUrl must be an absolute HTTPS URL"
        }
        require(clientVersion.isNotBlank()) { "clientVersion must not be blank" }
        if (!fixtureMode) {
            require(!uri.host.endsWith(".invalid")) { "non-fixture builds cannot use an invalid API host" }
            require(googleServerClientId.isNotBlank()) {
                "non-fixture builds require a Google server client ID"
            }
        }
    }
}
