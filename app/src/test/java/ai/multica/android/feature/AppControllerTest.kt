package ai.multica.android.feature

import ai.multica.android.core.auth.InMemorySessionStore
import ai.multica.android.core.network.FixtureScenario
import ai.multica.android.core.network.GatewayException
import ai.multica.android.core.network.LocalFixtureGateway
import ai.multica.android.core.network.MulticaGateway
import ai.multica.android.model.AuthenticatedSession
import ai.multica.android.model.IssueSummary
import ai.multica.android.model.User
import ai.multica.android.model.Workspace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppControllerTest {
    @Test
    fun transitionsLoginWorkspaceAndIssueList() = runBlocking {
        val controller = AppController(LocalFixtureGateway(delayMillis = 0), InMemorySessionStore())
        controller.start()
        controller.sendCode(LocalFixtureGateway.FIXTURE_EMAIL)
        assertTrue((controller.state.value as AppState.Login).codeSent)
        controller.verifyCode(LocalFixtureGateway.FIXTURE_CODE)
        val picker = controller.state.value as AppState.WorkspaceSelection
        controller.selectWorkspace(picker.workspaces.first())
        val issues = controller.state.value as AppState.IssueList
        assertEquals(3, issues.issues.size)
        assertEquals("AND-104", issues.issues.first().identifier)
    }

    @Test
    fun invalidCodeStaysOnLoginAndShowsError() = runBlocking {
        val controller = AppController(LocalFixtureGateway(delayMillis = 0), InMemorySessionStore())
        controller.start()
        controller.sendCode(LocalFixtureGateway.FIXTURE_EMAIL)
        controller.verifyCode("000000")
        val state = controller.state.value as AppState.Login
        assertNotNull(state.error)
        assertTrue(state.codeSent)
    }

    @Test
    fun retriesOneShotIssueFailure() = runBlocking {
        val gateway = LocalFixtureGateway(delayMillis = 0)
        val controller = AppController(gateway, InMemorySessionStore())
        controller.start()
        controller.selectFixtureScenario(FixtureScenario.FAIL_ONCE)
        controller.sendCode(LocalFixtureGateway.FIXTURE_EMAIL)
        controller.verifyCode(LocalFixtureGateway.FIXTURE_CODE)
        controller.selectWorkspace((controller.state.value as AppState.WorkspaceSelection).workspaces.single())
        assertNotNull((controller.state.value as AppState.IssueList).error)
        controller.retryIssues()
        assertEquals(3, (controller.state.value as AppState.IssueList).issues.size)
    }

    @Test
    fun expiredSessionClearsTokenAndReturnsToLogin() = runBlocking {
        val gateway = LocalFixtureGateway(delayMillis = 0)
        val store = InMemorySessionStore()
        val controller = AppController(gateway, store)
        controller.start()
        controller.selectFixtureScenario(FixtureScenario.EXPIRED)
        controller.sendCode(LocalFixtureGateway.FIXTURE_EMAIL)
        controller.verifyCode(LocalFixtureGateway.FIXTURE_CODE)
        controller.selectWorkspace((controller.state.value as AppState.WorkspaceSelection).workspaces.single())
        val login = controller.state.value as AppState.Login
        assertTrue(login.notice.orEmpty().contains("expired"))
        assertNull(store.readToken())
    }

    @Test
    fun restoresValidPersistedSession() = runBlocking {
        val controller = AppController(
            LocalFixtureGateway(delayMillis = 0),
            InMemorySessionStore("fixture:standard"),
        )
        controller.start()
        val state = controller.state.value as AppState.WorkspaceSelection
        assertEquals(2, state.workspaces.size)
    }

    @Test
    fun nonUnauthorizedRestoreFailurePreservesTokenForRetry() = runBlocking {
        val store = InMemorySessionStore("saved-token")
        val controller = AppController(FailingRestoreGateway, store)
        controller.start()
        assertTrue(controller.state.value is AppState.RestoreFailure)
        assertEquals("saved-token", store.readToken())
    }

    @Test
    fun emptyScenarioReachesExplicitEmptyIssueState() = runBlocking {
        val gateway = LocalFixtureGateway(delayMillis = 0)
        val controller = AppController(gateway, InMemorySessionStore())
        controller.start()
        controller.selectFixtureScenario(FixtureScenario.EMPTY)
        controller.sendCode(LocalFixtureGateway.FIXTURE_EMAIL)
        controller.verifyCode(LocalFixtureGateway.FIXTURE_CODE)
        controller.selectWorkspace((controller.state.value as AppState.WorkspaceSelection).workspaces.single())
        val state = controller.state.value as AppState.IssueList
        assertTrue(state.issues.isEmpty())
        assertNull(state.error)
    }

    private object FailingRestoreGateway : MulticaGateway {
        override suspend fun sendCode(email: String) = Unit
        override suspend fun verifyCode(email: String, code: String) =
            AuthenticatedSession("token", User("id", "name", email))
        override suspend fun getMe(token: String): User =
            throw GatewayException(GatewayException.Kind.NETWORK, "Offline")
        override suspend fun getWorkspaces(token: String): List<Workspace> = emptyList()
        override suspend fun getIssues(token: String, workspaceSlug: String): List<IssueSummary> = emptyList()
    }
}
