package ai.multica.android.core.network

import ai.multica.android.model.AuthenticatedSession
import ai.multica.android.model.IssueSummary
import ai.multica.android.model.IssueDetail
import ai.multica.android.model.TimelineItem
import ai.multica.android.model.TimelineKind
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
    private val failedTimelineTokens = mutableSetOf<String>()

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
            FixtureScenario.STANDARD -> standardIssues(prefixFor(workspaceSlug))
        }
    }

    override suspend fun getIssue(token: String, workspaceSlug: String, issueId: String): IssueDetail {
        pause()
        fixtureScenario(token)
        if (workspaceSlug.isBlank()) invalid("Workspace slug is required")
        val summary = standardIssues(prefixFor(workspaceSlug)).firstOrNull { it.id == issueId }
            ?: throw GatewayException(GatewayException.Kind.SERVER, "Fixture task was not found")
        return IssueDetail(
            id = summary.id,
            identifier = summary.identifier,
            title = summary.title,
            description = when (summary.id) {
                "issue-1" -> "Confirm that restored sessions, retries, and expired credentials remain distinguishable."
                "issue-2" -> null
                else -> "Read-only fixture detail used to verify native timeline rendering."
            },
            status = summary.status,
            priority = summary.priority,
            createdAt = "2026-11-12T08:00:00Z",
            updatedAt = "2026-11-14T09:30:00Z",
        )
    }

    override suspend fun getTimeline(token: String, workspaceSlug: String, issueId: String): List<TimelineItem> {
        pause()
        if (workspaceSlug.isBlank()) invalid("Workspace slug is required")
        val fixtureScenario = fixtureScenario(token)
        if (fixtureScenario == FixtureScenario.FAIL_ONCE && failedTimelineTokens.add(token)) {
            throw GatewayException(GatewayException.Kind.NETWORK, "Fixture timeline interrupted once")
        }
        if (fixtureScenario == FixtureScenario.EXPIRED) unauthorized()
        if (issueId == "issue-2") return emptyList()
        if (issueId == "issue-3") return fixtureTimeline().take(2)
        return fixtureTimeline()
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

    private fun prefixFor(workspaceSlug: String) = when (workspaceSlug) {
        "android-delivery" -> "AND"
        "product-sandbox" -> "MUL"
        else -> "FIX"
    }

    private fun fixtureTimeline(): List<TimelineItem> = listOf(
        TimelineItem("timeline-01", TimelineKind.ACTIVITY, "Product owner", "2026-11-12T08:00:00Z", "created this task"),
        TimelineItem("timeline-02", TimelineKind.COMMENT, "QA", "2026-11-12T08:30:00Z", "Please keep 401 separate from retryable transport failures."),
        TimelineItem("timeline-03", TimelineKind.ACTIVITY, "Android developer", "2026-11-12T09:00:00Z", "changed status to in progress"),
        TimelineItem("timeline-04", TimelineKind.COMMENT, "Android developer", "2026-11-12T10:00:00Z", "The local fixture now covers deterministic session restore."),
        TimelineItem("timeline-05", TimelineKind.COMMENT, "QA", "2026-11-12T11:00:00Z", "I will verify the device-backed key separately."),
        TimelineItem("timeline-06", TimelineKind.ACTIVITY, "System", "2026-11-12T12:00:00Z", "updated priority to high"),
        TimelineItem("timeline-07", TimelineKind.COMMENT, "Product owner", "2026-11-13T08:00:00Z", "Add read-only task detail before enabling writes."),
        TimelineItem("timeline-08", TimelineKind.COMMENT, "Android developer", "2026-11-13T09:00:00Z", "Detail and timeline use the same workspace scope."),
        TimelineItem("timeline-09", TimelineKind.ACTIVITY, "System", "2026-11-13T10:00:00Z", "linked the Android validation plan"),
        TimelineItem("timeline-10", TimelineKind.COMMENT, "QA", "2026-11-13T11:00:00Z", "Check stale responses after workspace changes."),
        TimelineItem("timeline-11", TimelineKind.COMMENT, "Android developer", "2026-11-14T08:00:00Z", "Old requests are fenced by a session and navigation generation."),
        TimelineItem("timeline-12", TimelineKind.ACTIVITY, "System", "2026-11-14T09:00:00Z", "updated the task"),
        TimelineItem("timeline-13", TimelineKind.COMMENT, "QA", "2026-11-14T09:30:00Z", "Ready for independent fixture verification."),
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
    FAIL_ONCE("retry", "Retry", "First task and timeline loads fail once"),
    EXPIRED("expired", "Expired", "Task load returns HTTP 401 semantics"),
}
