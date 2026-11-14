package ai.multica.android.model

data class User(
    val id: String,
    val name: String,
    val email: String,
)

data class Workspace(
    val id: String,
    val name: String,
    val slug: String,
    val issuePrefix: String = "",
)

data class IssueSummary(
    val id: String,
    val identifier: String,
    val title: String,
    val status: String,
    val priority: String,
)

data class IssueDetail(
    val id: String,
    val identifier: String,
    val title: String,
    val description: String?,
    val status: String,
    val priority: String,
    val createdAt: String,
    val updatedAt: String,
)

data class TimelineItem(
    val id: String,
    val kind: TimelineKind,
    val actorName: String,
    val createdAt: String,
    val body: String,
    val parentId: String? = null,
    val deleted: Boolean = false,
)

enum class TimelineKind { COMMENT, ACTIVITY }

data class AuthenticatedSession(
    val token: String,
    val user: User,
)
