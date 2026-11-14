package ai.multica.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractTest {
    @Test
    fun emitsAndroidHttpIdentityAndScope() {
        val headers = ApiContract.headers(
            metadata = ClientMetadata("0.1.0"),
            requestId = "request-1",
            token = "secret-token",
            workspaceSlug = "demo",
        )
        assertEquals("mobile", headers["X-Client-Platform"])
        assertEquals("android", headers["X-Client-OS"])
        assertEquals("Bearer secret-token", headers["Authorization"])
        assertEquals("demo", headers["X-Workspace-Slug"])
        assertFalse(headers.toString().contains("ios"))
    }

    @Test
    fun buildsReadOnlyIssueRoutesWithoutPaginationParameters() {
        assertEquals("/api/issues/issue-1", ApiRoutes.issue("issue-1"))
        assertEquals("/api/issues/issue-1/timeline", ApiRoutes.timeline("issue-1"))
        assertFalse(ApiRoutes.timeline("issue-1").contains('?'))
    }

    @Test
    fun mapsHttpsToAuthenticatedAndroidWebSocketUrl() {
        val url = RealtimeContract.webSocketUrl("https://api.example.com/v1", "team one", "0.1.0")
        assertEquals(
            "wss://api.example.com/ws?workspace_slug=team%20one&client_platform=mobile&client_os=android&client_version=0.1.0",
            url,
        )
    }

    @Test
    fun mapsHttpToWsAndOmitsOptionalVersion() {
        val url = RealtimeContract.webSocketUrl("http://localhost:8080", "local")
        assertTrue(url.startsWith("ws://localhost:8080/ws?"))
        assertFalse(url.contains("client_version"))
    }

    @Test
    fun createsEscapedAuthFirstFrame() {
        assertEquals(
            "{\"type\":\"auth\",\"payload\":{\"token\":\"a\\\"b\\\\c\"}}",
            RealtimeContract.authFrame("a\"b\\c"),
        )
    }
}
