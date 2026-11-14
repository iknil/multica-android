package ai.multica.android.core.config

import java.net.URI

data class AppConfig(
    val apiBaseUrl: String,
    val clientVersion: String,
) {
    init {
        val uri = runCatching { URI(apiBaseUrl) }.getOrNull()
        require(uri != null && uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) {
            "apiBaseUrl must be an absolute HTTP(S) URL"
        }
        require(clientVersion.isNotBlank()) { "clientVersion must not be blank" }
    }
}
