package dev.gitdroid.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.core.api.Repository
import dev.gitdroid.app.core.api.Workflow
import dev.gitdroid.app.core.api.WorkflowRun
import dev.gitdroid.app.core.repo.GitRepository
import dev.gitdroid.app.ui.components.EmptyState
import dev.gitdroid.app.ui.components.GlassCard
import dev.gitdroid.app.ui.components.MetaPill
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType
import kotlinx.coroutines.launch

/** The status filters github.com offers above a run list. */
private enum class RunFilter(val label: String, val api: String?) {
    ALL("All", null),
    QUEUED("Queued", "queued"),
    RUNNING("In progress", "in_progress"),
    COMPLETED("Completed", "completed"),
}

/**
 * The Actions tab.
 *
 * Two sections, in the order github.com uses them: the workflow files that
 * exist in the repo, then the runs — filterable by status and by workflow.
 * Selecting a workflow narrows the run list to it.
 */
@Composable
fun ActionsTab(
    repository: GitRepository,
    repo: Repository,
    branch: String,
    onOpenRun: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val scope = rememberCoroutineScope()

    var workflows by remember { mutableStateOf<List<Workflow>>(emptyList()) }
    var runs by remember { mutableStateOf<List<WorkflowRun>>(emptyList()) }
    var filter by remember { mutableStateOf(RunFilter.ALL) }
    var selected by remember { mutableStateOf<Workflow?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        error = null
        // Both collections come back in one go; a failure is surfaced rather
        // than swallowed, because an empty Actions tab is otherwise silent.
        runCatching { repository.workflows(repo.ownerLogin, repo.name) }
            .onSuccess { workflows = it }
            .onFailure { error = it.message }
        runs = runCatching {
            repository.workflowRuns(
                repo.ownerLogin,
                repo.name,
                workflowId = selected?.id?.toString(),
                status = filter.api,
            )
        }.getOrElse { emptyList() }
    }

    LaunchedEffect(repo.fullName, filter, selected) {
        loading = true
        load()
        loading = false
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = space.gutter,
            end = space.gutter,
            bottom = space.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(space.rowGap),
    ) {
        // ── Status filter ───────────────────────────────────────────────
        item(key = "filter") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                items(RunFilter.entries) { option ->
                    val active = option == filter
                    MetaPill(
                        text = option.label,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            colors.textTertiary
                        },
                        background = if (active) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            null
                        },
                        modifier = Modifier.clickable { filter = option },
                    )
                }
            }
        }

        if (error != null) {
            item(key = "error") {
                Text(
                    text = error!!,
                    style = type.bodySmall,
                    color = colors.danger,
                )
            }
        }

        // ── The workflow files ──────────────────────────────────────────
        if (workflows.isNotEmpty()) {
            item(key = "workflows-header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Workflows",
                        style = type.labelSmall,
                        color = colors.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected != null) {
                        Text(
                            text = "showing all",
                            style = type.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { selected = null },
                        )
                    }
                }
            }

            items(workflows, key = { "wf-${it.id}" }) { workflow ->
                val active = selected?.id == workflow.id
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    borderColor = if (active) MaterialTheme.colorScheme.primary else null,
                    onClick = { selected = if (active) null else workflow },
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
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = if (workflow.state == "active") {
                                colors.success
                            } else {
                                colors.textDisabled
                            },
                        )
                        Spacer(Modifier.width(space.sm))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = workflow.name,
                                style = type.body,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = workflow.path,
                                style = type.codeSmall,
                                color = colors.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // ── Runs ────────────────────────────────────────────────────────
        item(key = "runs-header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selected?.name ?: "All runs",
                    style = type.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            refreshing = true
                            load()
                            refreshing = false
                        }
                    },
                    enabled = !refreshing,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh runs",
                        modifier = Modifier.size(17.dp),
                        tint = colors.textTertiary,
                    )
                }
            }
        }

        if (loading) {
            item(key = "loading") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(space.sm))
                    Text("Loading runs…", style = type.bodySmall, color = colors.textTertiary)
                }
            }
        }

        if (!loading && runs.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.PlayArrow,
                    title = if (workflows.isEmpty()) "No workflows" else "No runs",
                    message = if (workflows.isEmpty()) {
                        "This repository has no GitHub Actions workflow files."
                    } else {
                        "Nothing matches this filter."
                    },
                )
            }
        }

        items(runs, key = { "run-${it.id}" }) { run ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                onClick = { onOpenRun(run.id) },
            ) {
                Row(
                    modifier = Modifier.padding(space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RunStatusIcon(status = run.status, conclusion = run.conclusion)
                    Spacer(Modifier.width(space.sm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = run.name.ifBlank { "Workflow run" },
                            style = type.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "#${run.runNumber} · ${run.headBranch} · ${run.event}",
                            style = type.labelSmall,
                            color = colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = run.updatedAt?.take(10).orEmpty(),
                        style = type.labelSmall,
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }
}
