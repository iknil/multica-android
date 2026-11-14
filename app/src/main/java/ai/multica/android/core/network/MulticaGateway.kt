package ai.multica.android.core.network

import ai.multica.android.model.AuthenticatedSession
import ai.multica.android.model.IssueSummary
import ai.multica.android.model.IssueDetail
import ai.multica.android.model.TimelineItem
import ai.multica.android.model.User
import ai.multica.android.model.Workspace

interface MulticaGateway {
    suspend fun sendCode(email: String)
    suspend fun verifyCode(email: String, code: String): AuthenticatedSession
    suspend fun getMe(token: String): User
    suspend fun getWorkspaces(token: String): List<Workspace>
    suspend fun getIssues(token: String, workspaceSlug: String): List<IssueSummary>
    suspend fun getIssue(token: String, workspaceSlug: String, issueId: String): IssueDetail
    suspend fun getTimeline(token: String, workspaceSlug: String, issueId: String): List<TimelineItem>
}

class GatewayException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind { VALIDATION, UNAUTHORIZED, NETWORK, SERVER, PROTOCOL }
}
