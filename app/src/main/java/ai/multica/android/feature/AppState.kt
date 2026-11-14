package ai.multica.android.feature

import ai.multica.android.core.network.FixtureScenario
import ai.multica.android.model.IssueSummary
import ai.multica.android.model.IssueDetail
import ai.multica.android.model.TimelineItem
import ai.multica.android.model.User
import ai.multica.android.model.Workspace

sealed interface AppState {
    data object RestoringSession : AppState

    data class Login(
        val email: String = "",
        val codeSent: Boolean = false,
        val loading: Boolean = false,
        val error: String? = null,
        val notice: String? = null,
        val fixtureScenario: FixtureScenario = FixtureScenario.STANDARD,
    ) : AppState

    data class RestoreFailure(
        val message: String,
        val loading: Boolean = false,
    ) : AppState

    data class WorkspaceSelection(
        val user: User,
        val loading: Boolean,
        val workspaces: List<Workspace> = emptyList(),
        val error: String? = null,
    ) : AppState

    data class IssueList(
        val user: User,
        val workspace: Workspace,
        val loading: Boolean,
        val issues: List<IssueSummary> = emptyList(),
        val error: String? = null,
    ) : AppState

    data class IssueDetailView(
        val user: User,
        val workspace: Workspace,
        val sourceIssues: List<IssueSummary>,
        val summary: IssueSummary,
        val detail: IssueDetail? = null,
        val detailLoading: Boolean = true,
        val detailError: String? = null,
        val timeline: List<TimelineItem> = emptyList(),
        val visibleTimelineCount: Int = 0,
        val timelineLoading: Boolean = false,
        val timelineError: String? = null,
    ) : AppState {
        val visibleTimeline: List<TimelineItem>
            get() = timeline.take(visibleTimelineCount)
        val hasMoreTimeline: Boolean
            get() = visibleTimelineCount < timeline.size
    }
}
