package dev.gitdroid.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.core.api.Repository
import dev.gitdroid.app.core.api.WorkflowRun
import dev.gitdroid.app.core.repo.BranchInfo
import dev.gitdroid.app.core.repo.GitRepository
import dev.gitdroid.app.ui.components.EmptyState
import dev.gitdroid.app.ui.components.GlassCard
import dev.gitdroid.app.ui.components.MetaPill
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalMotion
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType
import kotlinx.coroutines.launch

/** The tabs of a repository. */
enum class RepoTab(val label: String) {
    CODE("Code"),
    ISSUES("Issues"),
    PULLS("Pulls"),
    ACTIONS("Actions"),
    RELEASES("Releases"),
    SETTINGS("Settings"),
}

/**
 * The repository screen.
 *
 * Four surfaces in one scroll: the code browser, the issue list, the pull
 * request list and the Actions runs. The tab strip stays put and the content
 * cross-fades, so switching tabs never feels like loading a page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    repository: GitRepository,
    repo: Repository,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenIssue: (Int) -> Unit,
    onOpenPull: (Int) -> Unit,
    onOpenRun: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    var branches by remember { mutableStateOf<List<BranchInfo>>(emptyList()) }
    var branchSheet by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }

    val currentBranch = repository.branchFor(repo)

    LaunchedEffect(repo.fullName) {
        repository.selectRepo(repo)
        branches = repository.branches(repo.ownerLogin, repo.name)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Header ──────────────────────────────────────────────────────
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = space.gutter, vertical = space.sm),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(space.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(space.xs))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = repo.name,
                            style = type.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = repo.ownerLogin,
                            style = type.bodySmall,
                            color = colors.textTertiary,
                        )
                    }
                    if (repo.isPrivate) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Private",
                            modifier = Modifier.size(15.dp),
                            tint = colors.textTertiary,
                        )
                    }
                    Spacer(Modifier.width(space.sm))
                    IconButton(
                        onClick = { scope.launch { repository.toggleStar(repo) } },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = "Star",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (repo.description != null) {
                    Spacer(Modifier.height(space.xs))
                    Text(
                        text = repo.description,
                        style = type.bodySmall,
                        color = colors.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(space.md))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space.sm),
                ) {
                    MetaPill(
                        text = currentBranch,
                        color = MaterialTheme.colorScheme.secondary,
                        background = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.clickable { branchSheet = true },
                    )
                    if (repo.language != null) {
                        MetaPill(
                            text = repo.language,
                            color = colors.textTertiary,
                        )
                    }
                    MetaPill(text = "★ ${repo.stargazersCount}", color = colors.textTertiary)
                    MetaPill(text = "⑂ ${repo.forksCount}", color = colors.textTertiary)
                }
            }
        }

        // ── Tabs ────────────────────────────────────────────────────────
        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {},
            edgePadding = space.gutter,
        ) {
            RepoTab.entries.forEachIndexed { index, entry ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = {
                        Text(
                            text = entry.label,
                            style = type.label,
                        )
                    },
                )
            }
        }

        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(motion.quick()) togetherWith fadeOut(motion.quick()) },
            label = "repo-tab",
            modifier = Modifier.weight(1f),
        ) { index ->
            when (RepoTab.entries[index]) {
                RepoTab.CODE -> CodeTab(
                    repository = repository,
                    repo = repo,
                    branch = currentBranch,
                    onOpenFile = onOpenFile,
                    onRequestBranchSheet = { branchSheet = true },
                )

                RepoTab.ISSUES -> IssuesTab(
                    repository = repository,
                    repo = repo,
                    onOpenIssue = onOpenIssue,
                )

                RepoTab.PULLS -> PullsTab(
                    repository = repository,
                    repo = repo,
                    onOpenPull = onOpenPull,
                )

                RepoTab.ACTIONS -> ActionsTab(
                    repository = repository,
                    repo = repo,
                    onOpenRun = onOpenRun,
                )

                RepoTab.RELEASES -> ReleasesTab(
                    repository = repository,
                    repo = repo,
                )

                RepoTab.SETTINGS -> RepoSettingsTab(
                    repository = repository,
                    repo = repo,
                    onOpenSettings = { settingsOpen = true },
                )
            }
        }
    }

    if (branchSheet) {
        BranchPickerSheet(
            repository = repository,
            owner = repo.ownerLogin,
            repo = repo.name,
            currentBranch = currentBranch,
            onPick = { newBranch ->
                repository.selectBranch(repo.fullName, newBranch)
                branchSheet = false
            },
            onDismiss = { branchSheet = false },
        )
    }

    if (settingsOpen) {
        ModalBottomSheet(
            onDismissRequest = { settingsOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            RepoSettingsSheet(
                repository = repository,
                repo = repo,
                onClose = { settingsOpen = false },
                onDeleted = {
                    settingsOpen = false
                    onBack()
                },
            )
        }
    }
}

/** The settings tab — a summary plus the full editor. */
@Composable
private fun RepoSettingsTab(
    repository: GitRepository,
    repo: Repository,
    onOpenSettings: () -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = space.gutter,
            end = space.gutter,
            bottom = space.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(space.rowGap),
    ) {
        item(key = "summary") {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.padding(space.md)) {
                    InfoRow("Default branch", repo.defaultBranch)
                    InfoRow("Visibility", if (repo.isPrivate) "Private" else "Public")
                    InfoRow("Language", repo.language ?: "—")
                    InfoRow("Stars", repo.stargazersCount.toString())
                    InfoRow("Forks", repo.forksCount.toString())
                    InfoRow("Open issues", repo.openIssuesCount.toString())
                    InfoRow("Updated", repo.updatedAt?.take(10) ?: "—")
                }
            }
        }

        item(key = "topics") {
            if (repo.topics.isNotEmpty()) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(Modifier.padding(space.md)) {
                        Text(
                            text = "Topics",
                            style = type.labelSmall,
                            color = colors.textTertiary,
                        )
                        Spacer(Modifier.height(space.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
                            repo.topics.take(4).forEach { topic ->
                                MetaPill(text = topic, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }

        item(key = "edit") {
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(space.sm))
                Text("Edit repository settings", style = type.label)
            }
        }
    }
}

/** A label and a value, in a row. */
@Composable
private fun InfoRow(label: String, value: String) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = type.bodySmall,
            color = colors.textTertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = type.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Code tab ────────────────────────────────────────────────────────────────

@Composable
private fun CodeTab(
    repository: GitRepository,
    repo: Repository,
    branch: String,
    onOpenFile: (String) -> Unit,
    onRequestBranchSheet: () -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<List<dev.gitdroid.app.core.repo.DirEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(repo.fullName, branch) {
        loading = true
        entries = repository.listDirectory(repo.ownerLogin, repo.name, "", branch)
        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = space.gutter,
            end = space.gutter,
            bottom = space.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(space.rowGap),
    ) {
        item(key = "branch") {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                onClick = onRequestBranchSheet,
            ) {
                Row(
                    modifier = Modifier.padding(space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Branch",
                            style = type.labelSmall,
                            color = colors.textTertiary,
                        )
                        Text(
                            text = branch,
                            style = type.titleSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Text(
                        text = "Change",
                        style = type.label,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (!loading && entries.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.Code,
                    title = "Nothing here",
                    message = "This branch has no files at the root.",
                )
            }
        }

        items(entries, key = { it.path }) { entry ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                onClick = { onOpenFile(entry.path) },
            ) {
                Row(
                    modifier = Modifier.padding(
                        start = space.md,
                        end = space.md,
                        top = space.sm,
                        bottom = space.sm,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (entry.isDirectory) {
                            Icons.Rounded.Code
                        } else {
                            Icons.Rounded.Description
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (entry.isDirectory) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            colors.textTertiary
                        },
                    )
                    Spacer(Modifier.width(space.sm))
                    Text(
                        text = entry.name,
                        style = type.body,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!entry.isDirectory && entry.size > 0) {
                        Text(
                            text = formatBytes(entry.size),
                            style = type.labelSmall,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

// ── Issues tab ──────────────────────────────────────────────────────────────

@Composable
private fun IssuesTab(
    repository: GitRepository,
    repo: Repository,
    onOpenIssue: (Int) -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    var issues by remember { mutableStateOf<List<dev.gitdroid.app.core.api.Issue>>(emptyList()) }
    var showClosed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(repo.fullName, showClosed) {
        loading = true
        issues = repository.issues(repo.ownerLogin, repo.name, if (showClosed) "closed" else "open")
        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = space.gutter,
            end = space.gutter,
            bottom = space.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(space.rowGap),
    ) {
        item(key = "filter") {
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                FilterTab(
                    label = "Open",
                    selected = !showClosed,
                    modifier = Modifier.weight(1f),
                    onClick = { showClosed = false },
                )
                FilterTab(
                    label = "Closed",
                    selected = showClosed,
                    modifier = Modifier.weight(1f),
                    onClick = { showClosed = true },
                )
            }
        }

        if (!loading && issues.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.CheckCircle,
                    title = if (showClosed) "No closed issues" else "No open issues",
                    message = "Nice — nothing to triage.",
                )
            }
        }

        items(issues, key = { it.number }) { issue ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                onClick = { onOpenIssue(issue.number) },
            ) {
                Row(
                    modifier = Modifier.padding(space.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = if (issue.state == "open") {
                            Icons.Rounded.ErrorOutline
                        } else {
                            Icons.Rounded.CheckCircle
                        },
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = if (issue.state == "open") colors.success else colors.textTertiary,
                    )
                    Spacer(Modifier.width(space.sm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = issue.title,
                            style = type.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "#${issue.number} · ${issue.user?.login ?: "unknown"}",
                            style = type.bodySmall,
                            color = colors.textTertiary,
                        )
                        if (issue.labels.isNotEmpty()) {
                            Spacer(Modifier.height(space.xs))
                            Row(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
                                issue.labels.take(3).forEach { label ->
                                    MetaPill(
                                        text = label.name,
                                        color = labelColor(label.color),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Pull requests tab ───────────────────────────────────────────────────────

@Composable
private fun PullsTab(
    repository: GitRepository,
    repo: Repository,
    onOpenPull: (Int) -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    var pulls by remember { mutableStateOf<List<dev.gitdroid.app.core.api.PullRequest>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(repo.fullName) {
        loading = true
        pulls = repository.pullRequests(repo.ownerLogin, repo.name, "open")
        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = space.gutter,
            end = space.gutter,
            bottom = space.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(space.rowGap),
    ) {
        if (!loading && pulls.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.CallSplit,
                    title = "No open pull requests",
                    message = "Everything is merged.",
                )
            }
        }

        items(pulls, key = { it.number }) { pull ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                onClick = { onOpenPull(pull.number) },
            ) {
                Column(Modifier.padding(space.md)) {
                    Text(
                        text = pull.title,
                        style = type.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "#${pull.number} · ${pull.head.ref} → ${pull.base.ref}",
                        style = type.bodySmall,
                        color = colors.textTertiary,
                    )
                    Spacer(Modifier.height(space.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
                        if (pull.draft) {
                            MetaPill(text = "Draft", color = colors.textTertiary)
                        }
                        MetaPill(text = "+${pull.additions}", color = colors.success)
                        MetaPill(text = "−${pull.deletions}", color = colors.danger)
                    }
                }
            }
        }
    }
}

// ── Actions tab ─────────────────────────────────────────────────────────────

@Composable
private fun ActionsTab(
    repository: GitRepository,
    repo: Repository,
    onOpenRun: (Long) -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val scope = rememberCoroutineScope()

    var runs by remember { mutableStateOf<List<WorkflowRun>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(repo.fullName) {
        loading = true
        runs = repository.workflowRuns(repo.ownerLogin, repo.name)
        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = space.gutter,
            end = space.gutter,
            bottom = space.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(space.rowGap),
    ) {
        item(key = "actions-header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Workflow runs",
                    style = type.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            runs = repository.workflowRuns(repo.ownerLogin, repo.name)
                            busy = false
                        }
                    },
                    enabled = !busy,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh runs",
                        modifier = Modifier.size(18.dp),
                        tint = colors.textTertiary,
                    )
                }
            }
        }

        if (!loading && runs.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.BugReport,
                    title = "No runs yet",
                    message = "This repository has no Actions workflow runs.",
                )
            }
        }

        items(runs, key = { it.id }) { run ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                onClick = { onOpenRun(run.id) },
            ) {
                Column(Modifier.padding(space.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RunStatusIcon(
                            status = run.status,
                            conclusion = run.conclusion,
                        )
                        Spacer(Modifier.width(space.sm))
                        Text(
                            text = run.name.ifBlank { "Workflow run" },
                            style = type.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "#${run.runNumber}",
                            style = type.labelSmall,
                            color = colors.textTertiary,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${run.headBranch} · ${run.event}",
                        style = type.bodySmall,
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }
}

/** The status glyph of a workflow run. */
@Composable
fun RunStatusIcon(
    status: String,
    conclusion: String?,
    modifier: Modifier = Modifier,
    size: Int = 17,
) {
    val colors = LocalGitDroidColors.current
    val (icon, tint) = when {
        status == "completed" && conclusion == "success" ->
            Icons.Rounded.CheckCircle to colors.success

        status == "completed" && conclusion == "failure" ->
            Icons.Rounded.Cancel to colors.danger

        status == "completed" && conclusion == "cancelled" ->
            Icons.Rounded.Cancel to colors.textTertiary

        status == "in_progress" || status == "queued" ->
            Icons.Rounded.Schedule to MaterialTheme.colorScheme.primary

        else -> Icons.Rounded.RadioButtonUnchecked to colors.textTertiary
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier.size(size.dp),
        tint = tint,
    )
}

// ── Releases tab ────────────────────────────────────────────────────────────

@Composable
private fun ReleasesTab(
    repository: GitRepository,
    repo: Repository,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    var releases by remember { mutableStateOf<List<dev.gitdroid.app.core.api.Release>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(repo.fullName) {
        loading = true
        releases = repository.releases(repo.ownerLogin, repo.name)
        loading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = space.gutter,
            end = space.gutter,
            bottom = space.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(space.rowGap),
    ) {
        if (!loading && releases.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.Star,
                    title = "No releases",
                    message = "This repository has not published a release.",
                )
            }
        }

        items(releases, key = { it.id }) { release ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.padding(space.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = release.name?.ifBlank { release.tag_name } ?: release.tag_name,
                            style = type.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (release.prerelease) {
                            MetaPill(text = "Pre-release", color = colors.warning)
                        }
                        if (release.draft) {
                            MetaPill(text = "Draft", color = colors.textTertiary)
                        }
                    }
                    Text(
                        text = release.tag_name,
                        style = type.bodySmall,
                        color = colors.textTertiary,
                    )
                    if (release.assets.isNotEmpty()) {
                        Spacer(Modifier.height(space.sm))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
                            items(release.assets, key = { it.id }) { asset ->
                                MetaPill(
                                    text = "${asset.name} · ${formatBytes(asset.size)}",
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Shared small pieces ─────────────────────────────────────────────────────

/** A two-way filter tab. */
@Composable
private fun FilterTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val motion = LocalMotion.current

    val background by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            Color.Transparent
        },
        animationSpec = motion.quick(),
        label = "filter-bg",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = space.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = type.label,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                colors.textTertiary
            },
        )
    }
}

/** Turns a GitHub label colour into a readable tint. */
fun labelColor(hex: String): Color = runCatching {
    Color(("ff$hex".takeIf { it.length == 8 } ?: hex).toLong(16))
}.getOrElse { Color(0xFF8B949E) }

/** Formats a byte count the way GitHub does. */
fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
