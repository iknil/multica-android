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

data class AuthenticatedSession(
    val token: String,
    val user: User,
)
