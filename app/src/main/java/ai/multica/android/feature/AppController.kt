package ai.multica.android.feature

import ai.multica.android.core.auth.SessionStore
import ai.multica.android.core.network.FixtureScenario
import ai.multica.android.core.network.GatewayException
import ai.multica.android.core.network.LocalFixtureGateway
import ai.multica.android.core.network.MulticaGateway
import ai.multica.android.model.IssueSummary
import ai.multica.android.model.User
import ai.multica.android.model.Workspace
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppController(
    private val gateway: MulticaGateway,
    private val sessionStore: SessionStore,
) {
    private val mutableState = MutableStateFlow<AppState>(AppState.RestoringSession)
    private val generation = AtomicLong(0)
    val state: StateFlow<AppState> = mutableState.asStateFlow()

    suspend fun start() {
        val request = beginRequest()
        mutableState.value = AppState.RestoringSession
        val token = runCatching { sessionStore.readToken() }.getOrElse {
            ifActive(request) { mutableState.value = AppState.RestoreFailure("Secure session storage could not be read.") }
            return
        }
        if (!isActive(request)) return
        if (token == null) {
            mutableState.value = loginState()
            return
        }
        restore(token)
    }

    fun selectFixtureScenario(scenario: FixtureScenario) {
        (gateway as? LocalFixtureGateway)?.selectScenario(scenario) ?: return
        val current = mutableState.value as? AppState.Login ?: return
        mutableState.value = current.copy(fixtureScenario = scenario, error = null, notice = null)
    }

    suspend fun sendCode(email: String) {
        val current = mutableState.value as? AppState.Login ?: return
        val request = beginRequest()
        val cleanEmail = email.trim()
        mutableState.value = current.copy(email = cleanEmail, loading = true, error = null, notice = null)
        try {
            gateway.sendCode(cleanEmail)
            ifActive(request) {
                mutableState.value = current.copy(
                    email = cleanEmail,
                    codeSent = true,
                    loading = false,
                    notice = if (gateway is LocalFixtureGateway) {
                        "Code ready. The local fixture does not send email."
                    } else {
                        "Code sent. Check your email and enter the one-time code."
                    },
                )
            }
        } catch (error: Throwable) {
            ifActive(request) {
                mutableState.value = current.copy(email = cleanEmail, loading = false, error = error.userMessage())
            }
        }
    }

    suspend fun verifyCode(code: String) {
        val current = mutableState.value as? AppState.Login ?: return
        val request = beginRequest()
        mutableState.value = current.copy(loading = true, error = null)
        try {
            val session = gateway.verifyCode(current.email, code.trim())
            if (!isActive(request)) return
            sessionStore.writeToken(session.token)
            if (!isActive(request)) return
            loadWorkspaces(session.token, session.user)
        } catch (error: Throwable) {
            ifActive(request) { mutableState.value = current.copy(loading = false, error = error.userMessage()) }
        }
    }

    suspend fun retryRestore() {
        val current = mutableState.value as? AppState.RestoreFailure ?: return
        val request = beginRequest()
        mutableState.value = current.copy(loading = true)
        val token = runCatching { sessionStore.readToken() }.getOrNull()
        if (!isActive(request)) return
        if (token == null) {
            mutableState.value = loginState(error = "The saved session is unavailable. Sign in again.")
        } else {
            restore(token)
        }
    }

    suspend fun retryWorkspaces() {
        val current = mutableState.value as? AppState.WorkspaceSelection ?: return
        sessionTokenOrExpire(beginRequest())?.let { loadWorkspaces(it, current.user) }
    }

    suspend fun selectWorkspace(workspace: Workspace) {
        val current = mutableState.value as? AppState.WorkspaceSelection ?: return
        sessionTokenOrExpire(beginRequest())?.let { loadIssues(it, current.user, workspace) }
    }

    suspend fun retryIssues() {
        val current = mutableState.value as? AppState.IssueList ?: return
        sessionTokenOrExpire(beginRequest())?.let { loadIssues(it, current.user, current.workspace) }
    }

    suspend fun openIssue(issue: IssueSummary) {
        val current = mutableState.value as? AppState.IssueList ?: return
        sessionTokenOrExpire(beginRequest())?.let {
            loadIssueDetail(it, current.user, current.workspace, current.issues, issue)
        }
    }

    suspend fun retryIssueDetail() {
        val current = mutableState.value as? AppState.IssueDetailView ?: return
        sessionTokenOrExpire(beginRequest())?.let {
            loadIssueDetail(it, current.user, current.workspace, current.sourceIssues, current.summary)
        }
    }

    suspend fun retryTimeline() {
        val current = mutableState.value as? AppState.IssueDetailView ?: return
        val request = beginRequest()
        val token = sessionTokenOrExpire(request) ?: return
        if (!isActive(request)) return
        mutableState.value = current.copy(timelineLoading = true, timelineError = null)
        try {
            val timeline = gateway.getTimeline(token, current.workspace.slug, current.summary.id)
            ifActive(request) {
                mutableState.value = current.copy(
                    timeline = timeline,
                    visibleTimelineCount = minOf(TIMELINE_PAGE_SIZE, timeline.size),
                    timelineLoading = false,
                    timelineError = null,
                )
            }
        } catch (error: Throwable) {
            handleTimelineFailure(request, current, error)
        }
    }

    fun loadMoreTimeline() {
        val current = mutableState.value as? AppState.IssueDetailView ?: return
        mutableState.value = current.copy(
            visibleTimelineCount = minOf(current.timeline.size, current.visibleTimelineCount + TIMELINE_PAGE_SIZE),
        )
    }

    fun backToIssues() {
        val current = mutableState.value as? AppState.IssueDetailView ?: return
        beginRequest() // Fence any detail/timeline response still in flight.
        mutableState.value = AppState.IssueList(
            user = current.user,
            workspace = current.workspace,
            loading = false,
            issues = current.sourceIssues,
        )
    }

    suspend fun chooseAnotherWorkspace() {
        val current = mutableState.value
        val user = when (current) {
            is AppState.IssueList -> current.user
            is AppState.IssueDetailView -> current.user
            else -> return
        }
        sessionTokenOrExpire(beginRequest())?.let { loadWorkspaces(it, user) }
    }

    suspend fun logout() {
        beginRequest() // Invalidate before the serialized secure-store clear suspends.
        mutableState.value = loginState()
        runCatching { sessionStore.clearToken() }
    }

    private suspend fun restore(token: String) {
        val request = beginRequest()
        try {
            val user = gateway.getMe(token)
            if (isActive(request)) loadWorkspaces(token, user)
        } catch (error: Throwable) {
            if (!isActive(request)) return
            if (error.isUnauthorized()) expireSession(request) else {
                mutableState.value = AppState.RestoreFailure(error.userMessage())
            }
        }
    }

    private suspend fun loadWorkspaces(token: String, user: User) {
        val request = beginRequest()
        mutableState.value = AppState.WorkspaceSelection(user = user, loading = true)
        try {
            val workspaces = gateway.getWorkspaces(token)
            ifActive(request) {
                mutableState.value = AppState.WorkspaceSelection(user, loading = false, workspaces = workspaces)
            }
        } catch (error: Throwable) {
            if (!isActive(request)) return
            if (error.isUnauthorized()) expireSession(request) else {
                mutableState.value = AppState.WorkspaceSelection(user, loading = false, error = error.userMessage())
            }
        }
    }

    private suspend fun loadIssues(token: String, user: User, workspace: Workspace) {
        val request = beginRequest()
        mutableState.value = AppState.IssueList(user, workspace, loading = true)
        try {
            val issues = gateway.getIssues(token, workspace.slug)
            ifActive(request) {
                mutableState.value = AppState.IssueList(user, workspace, loading = false, issues = issues)
            }
        } catch (error: Throwable) {
            if (!isActive(request)) return
            if (error.isUnauthorized()) expireSession(request) else {
                mutableState.value = AppState.IssueList(user, workspace, loading = false, error = error.userMessage())
            }
        }
    }

    private suspend fun loadIssueDetail(
        token: String,
        user: User,
        workspace: Workspace,
        sourceIssues: List<IssueSummary>,
        summary: IssueSummary,
    ) {
        val request = beginRequest()
        val initial = AppState.IssueDetailView(user, workspace, sourceIssues, summary)
        mutableState.value = initial
        val detail = try {
            gateway.getIssue(token, workspace.slug, summary.id)
        } catch (error: Throwable) {
            if (!isActive(request)) return
            if (error.isUnauthorized()) {
                expireSession(request)
            } else {
                mutableState.value = initial.copy(detailLoading = false, detailError = error.userMessage())
            }
            return
        }
        if (!isActive(request)) return
        val withDetail = initial.copy(detail = detail, detailLoading = false, timelineLoading = true)
        mutableState.value = withDetail
        try {
            val timeline = gateway.getTimeline(token, workspace.slug, summary.id)
            ifActive(request) {
                mutableState.value = withDetail.copy(
                    timeline = timeline,
                    visibleTimelineCount = minOf(TIMELINE_PAGE_SIZE, timeline.size),
                    timelineLoading = false,
                )
            }
        } catch (error: Throwable) {
            handleTimelineFailure(request, withDetail, error)
        }
    }

    private suspend fun handleTimelineFailure(
        request: Long,
        state: AppState.IssueDetailView,
        error: Throwable,
    ) {
        if (!isActive(request)) return
        if (error.isUnauthorized()) expireSession(request) else {
            mutableState.value = state.copy(timelineLoading = false, timelineError = error.userMessage())
        }
    }

    private suspend fun sessionTokenOrExpire(request: Long): String? {
        val token = runCatching { sessionStore.readToken() }.getOrNull()
        if (!isActive(request)) return null
        if (token == null) expireSession(request)
        return token
    }

    private suspend fun expireSession(request: Long) {
        if (!isActive(request)) return
        generation.incrementAndGet()
        mutableState.value = loginState(notice = "Your session expired. Sign in again.")
        runCatching { sessionStore.clearToken() }
    }

    private fun loginState(error: String? = null, notice: String? = null): AppState.Login =
        AppState.Login(
            fixtureScenario = (gateway as? LocalFixtureGateway)?.scenario ?: FixtureScenario.STANDARD,
            error = error,
            notice = notice,
        )

    private fun beginRequest(): Long = generation.incrementAndGet()
    private fun isActive(request: Long): Boolean = generation.get() == request
    private inline fun ifActive(request: Long, action: () -> Unit) {
        if (isActive(request)) action()
    }

    companion object {
        const val TIMELINE_PAGE_SIZE = 6
    }
}

private fun Throwable.isUnauthorized(): Boolean =
    this is GatewayException && kind == GatewayException.Kind.UNAUTHORIZED

private fun Throwable.userMessage(): String = when (this) {
    is GatewayException -> message ?: when (kind) {
        GatewayException.Kind.VALIDATION -> "Check the entered value."
        GatewayException.Kind.UNAUTHORIZED -> "The request was not authorized."
        GatewayException.Kind.NETWORK -> "Check your connection and retry."
        GatewayException.Kind.SERVER -> "The service is temporarily unavailable."
        GatewayException.Kind.PROTOCOL -> "The service response could not be read."
    }
    else -> "Something went wrong. Retry without changing your session."
}
