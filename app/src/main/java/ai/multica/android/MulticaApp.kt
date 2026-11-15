package ai.multica.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ai.multica.android.core.network.FixtureScenario
import ai.multica.android.core.network.LocalFixtureGateway
import ai.multica.android.feature.AppController
import ai.multica.android.feature.AppState
import ai.multica.android.model.IssueSummary
import ai.multica.android.model.TimelineItem
import ai.multica.android.model.TimelineKind
import ai.multica.android.model.Workspace
import kotlinx.coroutines.launch

@Composable
fun MulticaApp(controller: AppController, fixtureMode: Boolean) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    val screenStateHolder = rememberSaveableStateHolder()
    Scaffold(
        topBar = { AppHeader(fixtureMode) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val current = state
            Box(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                screenStateHolder.SaveableStateProvider(screenKey(current)) {
                    when (current) {
                        AppState.RestoringSession -> FullPageLoading("Restoring secure session")
                        is AppState.Login -> LoginScreen(
                            state = current,
                            fixtureMode = fixtureMode,
                            onScenario = controller::selectFixtureScenario,
                            onSendCode = { scope.launch { controller.sendCode(it) } },
                            onVerify = { scope.launch { controller.verifyCode(it) } },
                        )
                        is AppState.RestoreFailure -> RestoreFailureScreen(
                            current,
                            onRetry = { scope.launch { controller.retryRestore() } },
                            onLogout = { scope.launch { controller.logout() } },
                        )
                        is AppState.WorkspaceSelection -> WorkspaceScreen(
                            current,
                            onWorkspace = { scope.launch { controller.selectWorkspace(it) } },
                            onRetry = { scope.launch { controller.retryWorkspaces() } },
                            onLogout = { scope.launch { controller.logout() } },
                        )
                        is AppState.IssueList -> IssueListScreen(
                            current,
                            onIssue = { scope.launch { controller.openIssue(it) } },
                            onRetry = { scope.launch { controller.retryIssues() } },
                            onChangeWorkspace = { scope.launch { controller.chooseAnotherWorkspace() } },
                            onLogout = { scope.launch { controller.logout() } },
                        )
                        is AppState.IssueDetailView -> IssueDetailScreen(
                            current,
                            onBack = controller::backToIssues,
                            onRetryDetail = { scope.launch { controller.retryIssueDetail() } },
                            onRetryTimeline = { scope.launch { controller.retryTimeline() } },
                            onLoadMore = controller::loadMoreTimeline,
                            onChangeWorkspace = { scope.launch { controller.chooseAnotherWorkspace() } },
                            onLogout = { scope.launch { controller.logout() } },
                        )
                    }
                }
            }
        }
    }
}

private fun screenKey(state: AppState): String = when (state) {
    AppState.RestoringSession -> "restoring"
    is AppState.Login -> "login"
    is AppState.RestoreFailure -> "restore-failure"
    is AppState.WorkspaceSelection -> "workspaces"
    is AppState.IssueList -> "issues:${state.workspace.id}"
    is AppState.IssueDetailView -> "issue:${state.workspace.id}:${state.summary.id}"
}

@Composable
private fun AppHeader(fixtureMode: Boolean) {
    Surface(shadowElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Multica", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (fixtureMode) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        "LOCAL FIXTURE",
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(
    state: AppState.Login,
    fixtureMode: Boolean,
    onScenario: (FixtureScenario) -> Unit,
    onSendCode: (String) -> Unit,
    onVerify: (String) -> Unit,
) {
    var email by rememberSaveable(state.email) {
        mutableStateOf(state.email.ifBlank { if (fixtureMode) LocalFixtureGateway.FIXTURE_EMAIL else "" })
    }
    var code by rememberSaveable(state.codeSent) { mutableStateOf("") }
    val listState = rememberLazyListState()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val primaryActionIndex = 1 +
        (if (fixtureMode) 1 else 0) +
        1 +
        (if (state.codeSent) 1 else 0) +
        (if (state.notice != null) 1 else 0) +
        (if (state.error != null) 1 else 0)

    // codeSent inserts controls below the fold. Move the real clickable node fully into the
    // viewport instead of relying on its nominal 48dp size (a clipped node has a smaller hit box).
    LaunchedEffect(state.codeSent, primaryActionIndex, imeBottom) {
        if (state.codeSent) {
            // Wait for restored list state and any IME inset change before resolving clipping.
            withFrameNanos { }
            listState.scrollToItem(primaryActionIndex)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("login_viewport"),
        state = listState,
        contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "login_heading") {
            Text("Sign in", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (fixtureMode) "Safe local flow — no OTP or service request is sent."
                else "Use an authorized Multica test account.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (fixtureMode) {
            item(key = "fixture_scenario") {
                Text("Fixture scenario", style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FixtureScenario.entries.forEach { scenario ->
                        FilterChip(
                            selected = state.fixtureScenario == scenario,
                            onClick = { onScenario(scenario) },
                            label = { Text(scenario.label) },
                            enabled = !state.loading,
                        )
                    }
                }
                Text(
                    state.fixtureScenario.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "login_email") {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                singleLine = true,
                enabled = !state.loading && !state.codeSent,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
        }
        if (state.codeSent) {
            item(key = "login_code") {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("One-time code") },
                    supportingText = if (fixtureMode) ({ Text("Local fixture code: ${LocalFixtureGateway.FIXTURE_CODE}") }) else null,
                    singleLine = true,
                    enabled = !state.loading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
            }
        }
        state.notice?.let { message -> item(key = "login_notice") { MessagePanel(message, isError = false) } }
        state.error?.let { message -> item(key = "login_error") { MessagePanel(message, isError = true) } }
        item(key = "login_primary_action") {
            Button(
                onClick = { if (state.codeSent) onVerify(code) else onSendCode(email) },
                enabled = !state.loading && email.isNotBlank() && (!state.codeSent || code.isNotBlank()),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("login_primary_action"),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        Modifier.height(22.dp).semantics { contentDescription = "Loading" },
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (state.codeSent) "Verify and continue" else "Continue with email")
            }
        }
    }
}

@Composable
private fun RestoreFailureScreen(
    state: AppState.RestoreFailure,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
) {
    CenteredColumn {
        Text("Could not restore session", style = MaterialTheme.typography.headlineSmall)
        Text(
            state.message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Button(onClick = onRetry, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
            Text("Retry")
        }
        TextButton(onClick = onLogout, enabled = !state.loading) { Text("Clear session and sign in") }
    }
}

@Composable
private fun WorkspaceScreen(
    state: AppState.WorkspaceSelection,
    onWorkspace: (Workspace) -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
) {
    when {
        state.loading -> FullPageLoading("Loading workspaces")
        state.error != null -> ErrorState("Workspaces unavailable", state.error, onRetry, onLogout)
        state.workspaces.isEmpty() -> EmptyState(
            title = "No workspaces yet",
            detail = "This account does not have access to a workspace.",
            action = "Refresh",
            onAction = onRetry,
            secondaryAction = "Sign out",
            onSecondary = onLogout,
        )
        else -> LazyColumn(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Choose a workspace", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Signed in as ${state.user.name.ifBlank { state.user.email }}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
            items(state.workspaces, key = Workspace::id) { workspace ->
                Card(
                    onClick = { onWorkspace(workspace) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(workspace.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            workspace.slug,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { TextButton(onClick = onLogout) { Text("Sign out") } }
        }
    }
}

@Composable
private fun IssueListScreen(
    state: AppState.IssueList,
    onIssue: (IssueSummary) -> Unit,
    onRetry: () -> Unit,
    onChangeWorkspace: () -> Unit,
    onLogout: () -> Unit,
) {
    when {
        state.loading -> FullPageLoading("Loading tasks")
        state.error != null -> ErrorState("Tasks unavailable", state.error, onRetry, onChangeWorkspace)
        state.issues.isEmpty() -> EmptyState(
            title = "No tasks in ${state.workspace.name}",
            detail = "There are no tasks in this workspace.",
            action = "Refresh",
            onAction = onRetry,
            secondaryAction = "Change workspace",
            onSecondary = onChangeWorkspace,
        )
        else -> LazyColumn(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                    Text(state.workspace.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${state.issues.size} tasks",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.padding(top = 4.dp)) {
                        TextButton(onClick = onChangeWorkspace) { Text("Change workspace") }
                        TextButton(onClick = onLogout) { Text("Sign out") }
                    }
                }
                HorizontalDivider()
            }
            items(state.issues, key = IssueSummary::id) { issue -> IssueRow(issue, onIssue) }
        }
    }
}

@Composable
private fun IssueRow(issue: IssueSummary, onClick: (IssueSummary) -> Unit) {
    Card(
        onClick = { onClick(issue) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(issue.identifier, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            Text(issue.status.replace('_', ' '), style = MaterialTheme.typography.labelMedium)
        }
        Text(issue.title, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium)
        Text(
            "Priority: ${issue.priority}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    }
}

@Composable
private fun IssueDetailScreen(
    state: AppState.IssueDetailView,
    onBack: () -> Unit,
    onRetryDetail: () -> Unit,
    onRetryTimeline: () -> Unit,
    onLoadMore: () -> Unit,
    onChangeWorkspace: () -> Unit,
    onLogout: () -> Unit,
) {
    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("issue_detail_list"),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Back to tasks") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onChangeWorkspace) { Text("Workspace") }
                TextButton(onClick = onLogout) { Text("Sign out") }
            }
        }
        item {
            Text(
                state.summary.identifier,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                state.detail?.title ?: state.summary.title,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        when {
            state.detailLoading -> item { InlineLoading("Loading task details") }
            state.detailError != null -> item {
                RetryPanel("Task details unavailable", state.detailError, onRetryDetail)
            }
            state.detail != null -> item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${state.detail.status.replace('_', ' ')} · ${state.detail.priority} priority",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text("Description", style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.detail.description?.takeIf { it.isNotBlank() } ?: "No description provided.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Updated ${state.detail.updatedAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (!state.detailLoading && state.detailError == null) {
            item {
                HorizontalDivider()
                Text(
                    "Comments and activity",
                    modifier = Modifier.padding(top = 14.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            when {
                state.timelineLoading -> item { InlineLoading("Loading comments and activity") }
                state.timelineError != null -> item {
                    RetryPanel("Timeline unavailable", state.timelineError, onRetryTimeline)
                }
                state.timeline.isEmpty() -> item {
                    Text(
                        "No comments or activity yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                }
                else -> {
                    items(state.visibleTimeline, key = TimelineItem::id) { item -> TimelineRow(item) }
                    if (state.hasMoreTimeline) {
                        item {
                            OutlinedButton(
                                onClick = onLoadMore,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                            ) {
                                Text("Load more (${state.timeline.size - state.visibleTimelineCount} remaining)")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(item: TimelineItem) {
    val isComment = item.kind == TimelineKind.COMMENT
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isComment) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(12.dp),
        border = if (isComment) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.actorName, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text(
                    if (isComment) "Comment" else "Activity",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                item.body,
                modifier = Modifier.padding(top = 6.dp),
                color = if (item.deleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                item.createdAt,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InlineLoading(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.width(28.dp).semantics { contentDescription = label })
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RetryPanel(title: String, detail: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            OutlinedButton(onClick = onRetry, modifier = Modifier.height(48.dp)) { Text("Retry") }
        }
    }
}

@Composable
private fun ErrorState(
    title: String,
    detail: String,
    onRetry: () -> Unit,
    onSecondary: () -> Unit,
) {
    EmptyState(title, detail, "Retry", onRetry, "Back", onSecondary)
}

@Composable
private fun EmptyState(
    title: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
    secondaryAction: String,
    onSecondary: () -> Unit,
) {
    CenteredColumn {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            detail,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
        OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(secondaryAction) }
    }
}

@Composable
private fun FullPageLoading(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.semantics { contentDescription = label })
            Text(label, modifier = Modifier.padding(top = 14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MessagePanel(message: String, isError: Boolean) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(message, Modifier.fillMaxWidth().padding(14.dp))
    }
}

@Composable
private fun CenteredColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
