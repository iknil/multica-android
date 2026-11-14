package ai.multica.android.core.network

import ai.multica.android.model.AuthenticatedSession
import ai.multica.android.model.IssueSummary
import ai.multica.android.model.User
import ai.multica.android.model.Workspace
import kotlinx.coroutines.delay

/** Deterministic, local-only contract fixture. It never opens a network connection. */
class LocalFixtureGateway(
    initialScenario: FixtureScenario = FixtureScenario.STANDARD,
    private val delayMillis: Long = 350,
) : MulticaGateway {
    var scenario: FixtureScenario = initialScenario
        private set
    private val failedIssueTokens = mutableSetOf<String>()

    fun selectScenario(value: FixtureScenario) {
        scenario = value
    }

    override suspend fun sendCode(email: String) {
        pause()
        if (!email.contains('@')) invalid("Enter a valid email address")
    }

    override suspend fun verifyCode(email: String, code: String): AuthenticatedSession {
        pause()
        if (!email.contains('@')) invalid("Enter a valid email address")
        if (code != FIXTURE_CODE) invalid("Use the local fixture code $FIXTURE_CODE")
        val token = "fixture:${scenario.key}"
        return AuthenticatedSession(token, fixtureUser(email))
    }

    override suspend fun getMe(token: String): User {
        pause()
        fixtureScenario(token)
        return fixtureUser(FIXTURE_EMAIL)
    }

    override suspend fun getWorkspaces(token: String): List<Workspace> {
        pause()
        fixtureScenario(token)
        return when (fixtureScenario(token)) {
            FixtureScenario.STANDARD -> listOf(
                Workspace("ws-android", "Android delivery", "android-delivery", "AND"),
                Workspace("ws-sandbox", "Product sandbox", "product-sandbox", "MUL"),
            )
            else -> listOf(Workspace("ws-fixture", "Fixture workspace", "fixture", "FIX"))
        }
    }

    override suspend fun getIssues(token: String, workspaceSlug: String): List<IssueSummary> {
        pause()
        if (workspaceSlug.isBlank()) invalid("Workspace slug is required")
        return when (fixtureScenario(token)) {
            FixtureScenario.EMPTY -> emptyList()
            FixtureScenario.FAIL_ONCE -> {
                if (failedIssueTokens.add(token)) {
                    throw GatewayException(GatewayException.Kind.NETWORK, "Fixture connection interrupted once")
                }
                standardIssues("FIX")
            }
            FixtureScenario.EXPIRED -> throw GatewayException(
                GatewayException.Kind.UNAUTHORIZED,
                "Fixture session expired",
            )
            FixtureScenario.STANDARD -> standardIssues(if (workspaceSlug == "android-delivery") "AND" else "MUL")
        }
    }

    private suspend fun pause() {
        if (delayMillis > 0) delay(delayMillis)
    }

    private fun fixtureScenario(token: String): FixtureScenario {
        if (!token.startsWith("fixture:")) unauthorized()
        return FixtureScenario.entries.firstOrNull { it.key == token.substringAfter(':') } ?: unauthorized()
    }

    private fun fixtureUser(email: String) = User("fixture-user", "Fixture developer", email)

    private fun standardIssues(prefix: String) = listOf(
        IssueSummary("issue-1", "$prefix-104", "Verify Android session recovery", "in_progress", "high"),
        IssueSummary("issue-2", "$prefix-98", "Review workspace empty state", "todo", "medium"),
        IssueSummary("issue-3", "$prefix-77", "Close completed accessibility audit", "done", "low"),
    )

    private fun invalid(message: String): Nothing =
        throw GatewayException(GatewayException.Kind.VALIDATION, message)

    private fun unauthorized(): Nothing =
        throw GatewayException(GatewayException.Kind.UNAUTHORIZED, "Fixture token is not valid")

    companion object {
        const val FIXTURE_EMAIL = "demo@multica.local"
        const val FIXTURE_CODE = "123456"
    }
}

enum class FixtureScenario(val key: String, val label: String, val detail: String) {
    STANDARD("standard", "Populated", "Two workspaces and three tasks"),
    EMPTY("empty", "Empty", "Workspace with no tasks"),
    FAIL_ONCE("retry", "Retry", "First task load fails once"),
    EXPIRED("expired", "Expired", "Task load returns HTTP 401 semantics"),
}
