package ai.multica.android.feature

import ai.multica.android.core.auth.SessionStore
import ai.multica.android.core.network.FixtureScenario
import ai.multica.android.core.network.GatewayException
import ai.multica.android.core.network.LocalFixtureGateway
import ai.multica.android.core.network.MulticaGateway
import ai.multica.android.model.User
import ai.multica.android.model.Workspace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppController(
    private val gateway: MulticaGateway,
    private val sessionStore: SessionStore,
) {
    private val mutableState = MutableStateFlow<AppState>(AppState.RestoringSession)
    val state: StateFlow<AppState> = mutableState.asStateFlow()

    suspend fun start() {
        mutableState.value = AppState.RestoringSession
        val token = runCatching { sessionStore.readToken() }.getOrElse {
            mutableState.value = AppState.RestoreFailure("Secure session storage could not be read.")
            return
        }
        if (token == null) {
            mutableState.value = AppState.Login()
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
        val cleanEmail = email.trim()
        mutableState.value = current.copy(email = cleanEmail, loading = true, error = null, notice = null)
        try {
            gateway.sendCode(cleanEmail)
            mutableState.value = current.copy(
                email = cleanEmail,
                codeSent = true,
                loading = false,
                notice = "Code ready. The local fixture does not send email.",
            )
        } catch (error: Throwable) {
            mutableState.value = current.copy(email = cleanEmail, loading = false, error = error.userMessage())
        }
    }

    suspend fun verifyCode(code: String) {
        val current = mutableState.value as? AppState.Login ?: return
        mutableState.value = current.copy(loading = true, error = null)
        try {
            val session = gateway.verifyCode(current.email, code.trim())
            sessionStore.writeToken(session.token)
            loadWorkspaces(session.token, session.user)
        } catch (error: Throwable) {
            mutableState.value = current.copy(loading = false, error = error.userMessage())
        }
    }

    suspend fun retryRestore() {
        val current = mutableState.value as? AppState.RestoreFailure ?: return
        mutableState.value = current.copy(loading = true)
        val token = runCatching { sessionStore.readToken() }.getOrNull()
        if (token == null) {
            mutableState.value = AppState.Login(error = "The saved session is unavailable. Sign in again.")
        } else {
            restore(token)
        }
    }

    suspend fun retryWorkspaces() {
        val current = mutableState.value as? AppState.WorkspaceSelection ?: return
        val token = sessionStore.readToken() ?: run {
            expireSession()
            return
        }
        loadWorkspaces(token, current.user)
    }

    suspend fun selectWorkspace(workspace: Workspace) {
        val current = mutableState.value as? AppState.WorkspaceSelection ?: return
        val token = sessionStore.readToken() ?: run {
            expireSession()
            return
        }
        loadIssues(token, current.user, workspace)
    }

    suspend fun retryIssues() {
        val current = mutableState.value as? AppState.IssueList ?: return
        val token = sessionStore.readToken() ?: run {
            expireSession()
            return
        }
        loadIssues(token, current.user, current.workspace)
    }

    suspend fun chooseAnotherWorkspace() {
        val current = mutableState.value as? AppState.IssueList ?: return
        val token = sessionStore.readToken() ?: run {
            expireSession()
            return
        }
        loadWorkspaces(token, current.user)
    }

    suspend fun logout() {
        runCatching { sessionStore.clearToken() }
        mutableState.value = AppState.Login()
    }

    private suspend fun restore(token: String) {
        try {
            val user = gateway.getMe(token)
            loadWorkspaces(token, user)
        } catch (error: Throwable) {
            if (error.isUnauthorized()) {
                expireSession()
            } else {
                // A timeout or 5xx does not destroy a potentially valid credential.
                mutableState.value = AppState.RestoreFailure(error.userMessage())
            }
        }
    }

    private suspend fun loadWorkspaces(token: String, user: User) {
        mutableState.value = AppState.WorkspaceSelection(user = user, loading = true)
        try {
            mutableState.value = AppState.WorkspaceSelection(
                user = user,
                loading = false,
                workspaces = gateway.getWorkspaces(token),
            )
        } catch (error: Throwable) {
            if (error.isUnauthorized()) expireSession() else {
                mutableState.value = AppState.WorkspaceSelection(
                    user = user,
                    loading = false,
                    error = error.userMessage(),
                )
            }
        }
    }

    private suspend fun loadIssues(token: String, user: User, workspace: Workspace) {
        mutableState.value = AppState.IssueList(user, workspace, loading = true)
        try {
            mutableState.value = AppState.IssueList(
                user = user,
                workspace = workspace,
                loading = false,
                issues = gateway.getIssues(token, workspace.slug),
            )
        } catch (error: Throwable) {
            if (error.isUnauthorized()) expireSession() else {
                mutableState.value = AppState.IssueList(
                    user = user,
                    workspace = workspace,
                    loading = false,
                    error = error.userMessage(),
                )
            }
        }
    }

    private suspend fun expireSession() {
        runCatching { sessionStore.clearToken() }
        mutableState.value = AppState.Login(notice = "Your session expired. Sign in again.")
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
