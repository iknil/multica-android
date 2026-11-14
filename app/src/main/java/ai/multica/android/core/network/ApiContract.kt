package ai.multica.android.core.network

object ApiRoutes {
    const val SEND_CODE = "/auth/send-code"
    const val VERIFY_CODE = "/auth/verify-code"
    const val REFRESH_SESSION = "/api/auth/refresh"
    const val ME = "/api/me"
    const val WORKSPACES = "/api/workspaces"
    const val INBOX = "/api/inbox"
}

data class ClientMetadata(
    val version: String,
    val platform: String = "mobile",
    val os: String = "android",
)

object ApiContract {
    const val TIMEOUT_SECONDS = 30L

    fun headers(
        metadata: ClientMetadata,
        requestId: String,
        token: String? = null,
        workspaceSlug: String? = null,
    ): Map<String, String> = buildMap {
        require(metadata.version.isNotBlank())
        require(requestId.isNotBlank())
        put("Content-Type", "application/json")
        put("X-Client-Platform", metadata.platform)
        put("X-Client-OS", metadata.os)
        put("X-Client-Version", metadata.version)
        put("X-Request-ID", requestId)
        token?.takeIf(String::isNotBlank)?.let { put("Authorization", "Bearer $it") }
        workspaceSlug?.takeIf(String::isNotBlank)?.let { put("X-Workspace-Slug", it) }
    }
}
