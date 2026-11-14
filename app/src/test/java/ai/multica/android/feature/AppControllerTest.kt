package ai.multica.android.feature

import ai.multica.android.core.auth.InMemorySessionStore
import ai.multica.android.core.network.FixtureScenario
import ai.multica.android.core.network.GatewayException
import ai.multica.android.core.network.LocalFixtureGateway
import ai.multica.android.core.network.MulticaGateway
import ai.multica.android.model.AuthenticatedSession
import ai.multica.android.model.IssueDetail
import ai.multica.android.model.IssueSummary
import ai.multica.android.model.TimelineItem
import ai.multica.android.model.User
import ai.multica.android.model.Workspace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
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
        signIn(controller)
        val picker = controller.state.value as AppState.WorkspaceSelection
        controller.selectWorkspace(picker.workspaces.first())
        val issues = controller.state.value as AppState.IssueList
        assertEquals(3, issues.issues.size)
        assertEquals("AND-104", issues.issues.first().identifier)
    }

    @Test
    fun opensDetailAndRevealsTimelineInLocalPages() = runBlocking {
        val controller = populatedController()
        val list = controller.state.value as AppState.IssueList
        controller.openIssue(list.issues.first())

        var detail = controller.state.value as AppState.IssueDetailView
        assertEquals("AND-104", detail.detail?.identifier)
        assertEquals(13, detail.timeline.size)
        assertEquals(6, detail.visibleTimeline.size)
        assertTrue(detail.hasMoreTimeline)

        controller.loadMoreTimeline()
        detail = controller.state.value as AppState.IssueDetailView
        assertEquals(12, detail.visibleTimeline.size)
        controller.loadMoreTimeline()
        detail = controller.state.value as AppState.IssueDetailView
        assertEquals(13, detail.visibleTimeline.size)
        assertTrue(!detail.hasMoreTimeline)
    }

    @Test
    fun showsExplicitEmptyTimeline() = runBlocking {
        val controller = populatedController()
        val list = controller.state.value as AppState.IssueList
        controller.openIssue(list.issues[1])
        val detail = controller.state.value as AppState.IssueDetailView
        assertNotNull(detail.detail)
        assertTrue(detail.timeline.isEmpty())
        assertNull(detail.timelineError)
    }

    @Test
    fun retriesTimelineWithoutReloadingDetail() = runBlocking {
        val gateway = LocalFixtureGateway(FixtureScenario.FAIL_ONCE, 0)
        val controller = AppController(gateway, InMemorySessionStore())
        signIn(controller)
        controller.selectWorkspace((controller.state.value as AppState.WorkspaceSelection).workspaces.single())
        controller.retryIssues()
        val list = controller.state.value as AppState.IssueList
        controller.openIssue(list.issues.first())
        val failed = controller.state.value as AppState.IssueDetailView
        assertNotNull(failed.detail)
        assertNotNull(failed.timelineError)

        controller.retryTimeline()
        val retried = controller.state.value as AppState.IssueDetailView
        assertEquals(13, retried.timeline.size)
        assertNull(retried.timelineError)
    }

    @Test
    fun logoutFencesLateTimelineResponse() = runBlocking {
        val gateway = BlockingTimelineGateway()
        val controller = populatedController(gateway)
        val list = controller.state.value as AppState.IssueList
        val request = launch { controller.openIssue(list.issues.first()) }
        gateway.timelineStarted.await()

        controller.logout()
        gateway.releaseTimeline.complete(Unit)
        request.join()

        assertTrue(controller.state.value is AppState.Login)
    }

    @Test
    fun workspaceSwitchFencesLateTimelineResponse() = runBlocking {
        val gateway = BlockingTimelineGateway()
        val controller = populatedController(gateway)
        val list = controller.state.value as AppState.IssueList
        val request = launch { controller.openIssue(list.issues.first()) }
        gateway.timelineStarted.await()

        controller.chooseAnotherWorkspace()
        gateway.releaseTimeline.complete(Unit)
        request.join()

        val picker = controller.state.value as AppState.WorkspaceSelection
        assertEquals(2, picker.workspaces.size)
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

    private suspend fun signIn(controller: AppController) {
        controller.start()
        controller.sendCode(LocalFixtureGateway.FIXTURE_EMAIL)
        controller.verifyCode(LocalFixtureGateway.FIXTURE_CODE)
    }

    private suspend fun populatedController(
        gateway: MulticaGateway = LocalFixtureGateway(delayMillis = 0),
    ): AppController {
        val controller = AppController(gateway, InMemorySessionStore())
        signIn(controller)
        val picker = controller.state.value as AppState.WorkspaceSelection
        controller.selectWorkspace(picker.workspaces.first())
        return controller
    }

    private class BlockingTimelineGateway(
        private val delegate: LocalFixtureGateway = LocalFixtureGateway(delayMillis = 0),
    ) : MulticaGateway by delegate {
        val timelineStarted = CompletableDeferred<Unit>()
        val releaseTimeline = CompletableDeferred<Unit>()

        override suspend fun getTimeline(
            token: String,
            workspaceSlug: String,
            issueId: String,
        ): List<TimelineItem> {
            timelineStarted.complete(Unit)
            releaseTimeline.await()
            return delegate.getTimeline(token, workspaceSlug, issueId)
        }
    }

    private object FailingRestoreGateway : MulticaGateway {
        override suspend fun sendCode(email: String) = Unit
        override suspend fun verifyCode(email: String, code: String) =
            AuthenticatedSession("token", User("id", "name", email))
        override suspend fun getMe(token: String): User =
            throw GatewayException(GatewayException.Kind.NETWORK, "Offline")
        override suspend fun getWorkspaces(token: String): List<Workspace> = emptyList()
        override suspend fun getIssues(token: String, workspaceSlug: String): List<IssueSummary> = emptyList()
        override suspend fun getIssue(token: String, workspaceSlug: String, issueId: String): IssueDetail =
            throw UnsupportedOperationException()
        override suspend fun getTimeline(
            token: String,
            workspaceSlug: String,
            issueId: String,
        ): List<TimelineItem> = emptyList()
    }
}
