package ai.multica.android.feature

import ai.multica.android.core.auth.GoogleCredentialProvider
import ai.multica.android.core.auth.InMemorySessionStore
import ai.multica.android.core.auth.SessionStore
import ai.multica.android.core.network.FixtureScenario
import ai.multica.android.core.network.GatewayException
import ai.multica.android.core.network.LocalFixtureGateway
import ai.multica.android.core.network.MulticaGateway
import ai.multica.android.model.AuthenticatedSession
import ai.multica.android.model.GoogleAuthChallenge
import ai.multica.android.model.IssueDetail
import ai.multica.android.model.IssueSummary
import ai.multica.android.model.TimelineItem
import ai.multica.android.model.User
import ai.multica.android.model.Workspace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppControllerTest {
    @Test
    fun googleLoginUsesServerNoncePersistsOnlyMulticaTokenAndLoadsWorkspaces() = runBlocking {
        val gateway = RecordingGoogleGateway()
        val provider = RecordingGoogleCredentialProvider()
        val store = InMemorySessionStore()
        val controller = AppController(gateway, store, provider, "server-client")
        controller.start()

        controller.signInWithGoogle()

        assertEquals("server-client", provider.clientId)
        assertEquals("nonce-1", provider.nonce)
        assertEquals("attempt-1", gateway.attemptId)
        assertEquals("google-id-token", gateway.idToken)
        assertEquals("multica-session", store.readToken())
        assertTrue(controller.state.value is AppState.WorkspaceSelection)
    }

    @Test
    fun fixtureBuildCannotUseGoogleLogin() = runBlocking {
        val provider = RecordingGoogleCredentialProvider()
        val controller = AppController(LocalFixtureGateway(delayMillis = 0), InMemorySessionStore(), provider, "client")
        controller.start()

        controller.signInWithGoogle()

        assertEquals(0, provider.requestCount)
        assertNotNull((controller.state.value as AppState.Login).error)
    }

    @Test
    fun logoutFencesLateGoogleCredentialAndClearsProviderState() = runBlocking {
        val gateway = RecordingGoogleGateway()
        val provider = BlockingGoogleCredentialProvider()
        val store = InMemorySessionStore()
        val controller = AppController(gateway, store, provider, "server-client")
        controller.start()
        val login = launch { controller.signInWithGoogle() }
        provider.started.await()

        controller.logout()
        provider.release.complete(Unit)
        login.join()

        assertTrue(controller.state.value is AppState.Login)
        assertNull(store.readToken())
        assertEquals(0, gateway.exchangeCount)
        assertEquals(1, provider.clearCount)
    }

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
    fun logoutFencesLateIssueListResponse() = runBlocking {
        val gateway = BlockingIssuesGateway()
        val controller = AppController(gateway, InMemorySessionStore())
        signIn(controller)
        val workspace = (controller.state.value as AppState.WorkspaceSelection).workspaces.first()
        val request = launch { controller.selectWorkspace(workspace) }
        gateway.issuesStarted.await()

        controller.logout()
        gateway.releaseIssues.complete(Unit)
        request.join()

        assertTrue(controller.state.value is AppState.Login)
    }

    @Test
    fun logoutWinsWhenVerifiedLoginWriteAlreadyOwnsSessionLock() = runBlocking {
        val store = BlockingWriteSessionStore()
        val controller = AppController(LocalFixtureGateway(delayMillis = 0), store)
        controller.start()
        controller.sendCode(LocalFixtureGateway.FIXTURE_EMAIL)

        val verification = launch { controller.verifyCode(LocalFixtureGateway.FIXTURE_CODE) }
        store.writeStarted.await()
        val logout = launch { controller.logout() }
        yield() // logout invalidates the generation before waiting for the session lock.
        assertTrue(controller.state.value is AppState.Login)

        store.releaseWrite.complete(Unit)
        verification.join()
        logout.join()

        assertNull(store.readToken())
        assertEquals(1, store.writeCount)
        assertTrue(store.clearCount >= 1)
    }

    @Test
    fun unauthorizedClearWinsWhenSessionLockPrecedesStaleLoginWrite() = runBlocking {
        val gateway = FirstVerificationBlocksThenExpiresGateway()
        val store = BlockingClearSessionStore()
        val controller = AppController(gateway, store)
        controller.start()
        controller.sendCode(LocalFixtureGateway.FIXTURE_EMAIL)

        val staleVerification = launch { controller.verifyCode(LocalFixtureGateway.FIXTURE_CODE) }
        gateway.firstVerificationStarted.await()
        val expiringVerification = launch { controller.verifyCode(LocalFixtureGateway.FIXTURE_CODE) }
        store.clearStarted.await() // second login reached a 401 and owns the session lock while clearing.
        gateway.releaseFirstVerification.complete(Unit)
        yield() // stale login is queued behind the clear and must re-check generation.

        store.releaseClear.complete(Unit)
        expiringVerification.join()
        staleVerification.join()

        assertTrue(controller.state.value is AppState.Login)
        assertNull(store.readToken())
        assertEquals(1, store.writeCount) // only the active login wrote before its 401; stale login did not.
        assertEquals(1, store.clearCount)
    }

    @Test
    fun workspaceSwitchFencesLateIssueListResponse() = runBlocking {
        val gateway = BlockingIssuesGateway()
        val controller = AppController(gateway, InMemorySessionStore())
        signIn(controller)
        val workspace = (controller.state.value as AppState.WorkspaceSelection).workspaces.first()
        val request = launch { controller.selectWorkspace(workspace) }
        gateway.issuesStarted.await()

        controller.chooseAnotherWorkspace()
        gateway.releaseIssues.complete(Unit)
        request.join()

        assertTrue(controller.state.value is AppState.WorkspaceSelection)
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
    fun fixtureScenarioRemainsSelectedAfterLogout() = runBlocking {
        val gateway = LocalFixtureGateway(delayMillis = 0)
        val controller = AppController(gateway, InMemorySessionStore())
        controller.start()
        controller.selectFixtureScenario(FixtureScenario.FAIL_ONCE)
        controller.sendCode(LocalFixtureGateway.FIXTURE_EMAIL)
        controller.verifyCode(LocalFixtureGateway.FIXTURE_CODE)
        controller.logout()

        val login = controller.state.value as AppState.Login
        assertEquals(FixtureScenario.FAIL_ONCE, login.fixtureScenario)
    }

    @Test
    fun expiredScenarioRemainsSelectedAfterSessionExpiry() = runBlocking {
        val gateway = LocalFixtureGateway(delayMillis = 0)
        val controller = AppController(gateway, InMemorySessionStore())
        controller.start()
        controller.selectFixtureScenario(FixtureScenario.EXPIRED)
        controller.sendCode(LocalFixtureGateway.FIXTURE_EMAIL)
        controller.verifyCode(LocalFixtureGateway.FIXTURE_CODE)
        controller.selectWorkspace((controller.state.value as AppState.WorkspaceSelection).workspaces.single())

        val login = controller.state.value as AppState.Login
        assertEquals(FixtureScenario.EXPIRED, login.fixtureScenario)
        assertTrue(login.notice.orEmpty().contains("expired"))
    }

    @Test
    fun connectedGatewayUsesEmailDeliveryPrompt() = runBlocking {
        val controller = AppController(ConnectedPromptGateway, InMemorySessionStore())
        controller.start()
        controller.sendCode("tester@example.com")

        val login = controller.state.value as AppState.Login
        assertTrue(login.notice.orEmpty().contains("Check your email"))
        assertTrue(!login.notice.orEmpty().contains("fixture"))
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


    private class BlockingWriteSessionStore : SessionStore {
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        var writeCount = 0
        var clearCount = 0
        private var token: String? = null

        override suspend fun readToken(): String? = token

        override suspend fun writeToken(token: String) {
            writeCount += 1
            writeStarted.complete(Unit)
            releaseWrite.await()
            this.token = token
        }

        override suspend fun clearToken() {
            clearCount += 1
            token = null
        }
    }

    private class BlockingClearSessionStore : SessionStore {
        val clearStarted = CompletableDeferred<Unit>()
        val releaseClear = CompletableDeferred<Unit>()
        var writeCount = 0
        var clearCount = 0
        private var token: String? = null

        override suspend fun readToken(): String? = token

        override suspend fun writeToken(token: String) {
            writeCount += 1
            this.token = token
        }

        override suspend fun clearToken() {
            clearCount += 1
            clearStarted.complete(Unit)
            releaseClear.await()
            token = null
        }
    }

    private class FirstVerificationBlocksThenExpiresGateway(
        private val delegate: LocalFixtureGateway = LocalFixtureGateway(delayMillis = 0),
    ) : MulticaGateway by delegate {
        val firstVerificationStarted = CompletableDeferred<Unit>()
        val releaseFirstVerification = CompletableDeferred<Unit>()
        private var verificationCount = 0

        override suspend fun verifyCode(email: String, code: String): AuthenticatedSession {
            verificationCount += 1
            if (verificationCount == 1) {
                firstVerificationStarted.complete(Unit)
                releaseFirstVerification.await()
            }
            return delegate.verifyCode(email, code)
        }

        override suspend fun getWorkspaces(token: String): List<Workspace> =
            throw GatewayException(GatewayException.Kind.UNAUTHORIZED, "Session expired")
    }

    private class BlockingIssuesGateway(
        private val delegate: LocalFixtureGateway = LocalFixtureGateway(delayMillis = 0),
    ) : MulticaGateway by delegate {
        val issuesStarted = CompletableDeferred<Unit>()
        val releaseIssues = CompletableDeferred<Unit>()

        override suspend fun getIssues(token: String, workspaceSlug: String): List<IssueSummary> {
            issuesStarted.complete(Unit)
            releaseIssues.await()
            return delegate.getIssues(token, workspaceSlug)
        }
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


    private class RecordingGoogleCredentialProvider : GoogleCredentialProvider {
        var clientId: String? = null
        var nonce: String? = null
        var requestCount = 0
        var clearCount = 0

        override suspend fun getIdToken(serverClientId: String, nonce: String): String {
            requestCount += 1
            clientId = serverClientId
            this.nonce = nonce
            return "google-id-token"
        }

        override suspend fun clearCredentialState() {
            clearCount += 1
        }
    }

    private class BlockingGoogleCredentialProvider : GoogleCredentialProvider {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var clearCount = 0

        override suspend fun getIdToken(serverClientId: String, nonce: String): String {
            started.complete(Unit)
            release.await()
            return "late-google-id-token"
        }

        override suspend fun clearCredentialState() {
            clearCount += 1
        }
    }

    private class RecordingGoogleGateway(
        private val delegate: LocalFixtureGateway = LocalFixtureGateway(delayMillis = 0),
    ) : MulticaGateway by delegate {
        var attemptId: String? = null
        var idToken: String? = null
        var exchangeCount = 0

        override suspend fun createGoogleAuthChallenge() = GoogleAuthChallenge("attempt-1", "nonce-1")

        override suspend fun exchangeGoogleIdToken(attemptId: String, idToken: String): AuthenticatedSession {
            exchangeCount += 1
            this.attemptId = attemptId
            this.idToken = idToken
            return AuthenticatedSession(
                "multica-session",
                User("google-user", "Google User", "google@example.com"),
            )
        }
    }

    private object ConnectedPromptGateway : MulticaGateway {
        override suspend fun sendCode(email: String) = Unit
        override suspend fun createGoogleAuthChallenge(): GoogleAuthChallenge = throw UnsupportedOperationException()
        override suspend fun exchangeGoogleIdToken(attemptId: String, idToken: String): AuthenticatedSession =
            throw UnsupportedOperationException()
        override suspend fun verifyCode(email: String, code: String) =
            AuthenticatedSession("token", User("id", "name", email))
        override suspend fun getMe(token: String) = User("id", "name", "tester@example.com")
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

    private object FailingRestoreGateway : MulticaGateway {
        override suspend fun sendCode(email: String) = Unit
        override suspend fun createGoogleAuthChallenge(): GoogleAuthChallenge = throw UnsupportedOperationException()
        override suspend fun exchangeGoogleIdToken(attemptId: String, idToken: String): AuthenticatedSession =
            throw UnsupportedOperationException()
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
