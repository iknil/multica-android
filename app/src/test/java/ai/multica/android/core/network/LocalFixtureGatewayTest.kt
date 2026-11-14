package ai.multica.android.core.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFixtureGatewayTest {
    @Test
    fun completesFixtureLoginAndReturnsWorkspacesAndIssues() = runBlocking {
        val gateway = LocalFixtureGateway(delayMillis = 0)
        gateway.sendCode(LocalFixtureGateway.FIXTURE_EMAIL)
        val session = gateway.verifyCode(LocalFixtureGateway.FIXTURE_EMAIL, LocalFixtureGateway.FIXTURE_CODE)
        assertEquals("fixture:standard", session.token)
        val workspaces = gateway.getWorkspaces(session.token)
        assertEquals(2, workspaces.size)
        assertEquals(3, gateway.getIssues(session.token, workspaces.first().slug).size)
    }

    @Test
    fun rejectsIncorrectFixtureCode() {
        val error = assertThrows(GatewayException::class.java) {
            runBlocking { LocalFixtureGateway(delayMillis = 0).verifyCode("demo@multica.local", "000000") }
        }
        assertEquals(GatewayException.Kind.VALIDATION, error.kind)
    }

    @Test
    fun supportsDeterministicEmptyIssueList() = runBlocking {
        val gateway = LocalFixtureGateway(FixtureScenario.EMPTY, 0)
        val session = gateway.verifyCode("demo@multica.local", LocalFixtureGateway.FIXTURE_CODE)
        val workspace = gateway.getWorkspaces(session.token).single()
        assertTrue(gateway.getIssues(session.token, workspace.slug).isEmpty())
    }

    @Test
    fun failsFirstIssueLoadThenSucceeds() = runBlocking {
        val gateway = LocalFixtureGateway(FixtureScenario.FAIL_ONCE, 0)
        val session = gateway.verifyCode("demo@multica.local", LocalFixtureGateway.FIXTURE_CODE)
        assertThrows(GatewayException::class.java) {
            runBlocking { gateway.getIssues(session.token, "fixture") }
        }
        assertEquals(3, gateway.getIssues(session.token, "fixture").size)
    }

    @Test
    fun exposesSessionExpiryAsUnauthorized() = runBlocking {
        val gateway = LocalFixtureGateway(FixtureScenario.EXPIRED, 0)
        val session = gateway.verifyCode("demo@multica.local", LocalFixtureGateway.FIXTURE_CODE)
        val error = assertThrows(GatewayException::class.java) {
            runBlocking { gateway.getIssues(session.token, "fixture") }
        }
        assertEquals(GatewayException.Kind.UNAUTHORIZED, error.kind)
    }
}
