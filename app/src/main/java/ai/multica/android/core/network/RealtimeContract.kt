package ai.multica.android.core.network

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object RealtimeContract {
    const val HEARTBEAT_INTERVAL_SECONDS = 25L
    const val HEARTBEAT_TIMEOUT_SECONDS = 10L
    const val RECONNECT_BASE_SECONDS = 1L
    const val RECONNECT_CAP_SECONDS = 30L

    fun webSocketUrl(
        apiBaseUrl: String,
        workspaceSlug: String,
        clientVersion: String? = null,
    ): String {
        require(workspaceSlug.isNotBlank())
        val base = URI(apiBaseUrl)
        val scheme = when (base.scheme) {
            "http" -> "ws"
            "https" -> "wss"
            else -> error("API URL must use HTTP(S)")
        }
        val query = buildList {
            add("workspace_slug=${encode(workspaceSlug)}")
            add("client_platform=mobile")
            add("client_os=android")
            clientVersion?.takeIf(String::isNotBlank)?.let { add("client_version=${encode(it)}") }
        }.joinToString("&")
        val socketBase = URI(scheme, base.userInfo, base.host, base.port, "/ws", null, null).toASCIIString()
        return "$socketBase?$query"
    }

    fun authFrame(token: String): String {
        require(token.isNotBlank())
        return "{\"type\":\"auth\",\"payload\":{\"token\":\"${escapeJson(token)}\"}}"
    }

    const val AUTH_ACK_TYPE = "auth_ack"
    const val PING_FRAME = "{\"type\":\"ping\"}"
    const val PONG_TYPE = "pong"

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")

    private fun escapeJson(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}
