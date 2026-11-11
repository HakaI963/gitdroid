package dev.gitdroid.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.core.api.WorkflowJob
import dev.gitdroid.app.core.api.WorkflowRun
import dev.gitdroid.app.core.repo.GitRepository
import dev.gitdroid.app.ui.components.EmptyState
import dev.gitdroid.app.ui.components.GlassCard
import dev.gitdroid.app.ui.components.MetaPill
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType
import kotlinx.coroutines.launch

/**
 * A single workflow run: its jobs, their steps, and the two actions that
 * matter — re-run and cancel.
 */
@Composable
fun RunScreen(
    repository: GitRepository,
    owner: String,
    repo: String,
    runId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val scope = rememberCoroutineScope()

    var run by remember { mutableStateOf<WorkflowRun?>(null) }
    var jobs by remember { mutableStateOf<List<WorkflowJob>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(runId) {
        loading = true
        run = repository.workflowRun(owner, repo, runId)
        jobs = repository.workflowRunJobs(owner, repo, runId)
        loading = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = space.gutter, vertical = space.sm),
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                modifier = Modifier.padding(
                    start = space.xs,
                    end = space.md,
                    top = space.sm,
                    bottom = space.sm,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = run?.name?.ifBlank { "Workflow run" } ?: "Workflow run",
                        style = type.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = run?.let { "#${it.runNumber} · ${it.headBranch}" } ?: "",
                        style = type.bodySmall,
                        color = colors.textTertiary,
                    )
                }
                run?.let { current ->
                    RunAction(
                        running = current.status != "completed",
                        busy = busy,
                        onRerun = {
                            scope.launch {
                                busy = true
                                repository.rerunRun(owner, repo, current.id)
                                run = repository.workflowRun(owner, repo, runId)
                                jobs = repository.workflowRunJobs(owner, repo, runId)
                                busy = false
                            }
                        },
                        onCancel = {
                            scope.launch {
                                busy = true
                                repository.cancelRun(owner, repo, current.id)
                                run = repository.workflowRun(owner, repo, runId)
                                busy = false
                            }
                        },
                    )
                }
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            run == null -> EmptyState(
                icon = Icons.Rounded.Bolt,
                title = "Could not load the run",
                message = "It may have been removed.",
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = space.gutter,
                    end = space.gutter,
                    bottom = space.xxxl,
                ),
                verticalArrangement = Arrangement.spacedBy(space.rowGap),
            ) {
                item(key = "summary") {
                    Row(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
                        MetaPill(text = run!!.status, color = MaterialTheme.colorScheme.secondary)
                        run!!.conclusion?.let { conclusion ->
                            MetaPill(
                                text = conclusion,
                                color = when (conclusion) {
                                    "success" -> colors.success
                                    "failure" -> colors.danger
                                    else -> colors.textTertiary
                                },
                            )
                        }
                        MetaPill(text = run!!.event, color = colors.textTertiary)
                    }
                }

                if (jobs.isEmpty()) {
                    item(key = "no-jobs") {
                        EmptyState(
                            icon = Icons.Rounded.Refresh,
                            title = "No jobs reported",
                            message = "The run may still be queuing.",
                        )
                    }
                }

                items(jobs, key = { it.id }) { job ->
                    JobCard(job)
                }
            }
        }
    }
}

/** The re-run / cancel button, chosen by whether the run is still going. */
@Composable
private fun RunAction(
    running: Boolean,
    busy: Boolean,
    onRerun: () -> Unit,
    onCancel: () -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current

    OutlinedButton(
        onClick = if (running) onCancel else onRerun,
        enabled = !busy,
    ) {
        Icon(
            imageVector = if (running) Icons.Rounded.Cancel else Icons.Rounded.Refresh,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(space.xs))
        Text(if (running) "Cancel" else "Re-run", style = type.label)
    }
}

/** One job of a workflow run, with its steps. */
@Composable
private fun JobCard(job: WorkflowJob) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RunStatusIcon(status = job.status, conclusion = job.conclusion)
                Spacer(Modifier.width(space.sm))
                Text(
                    text = job.name,
                    style = type.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (job.steps.isNotEmpty()) {
                Spacer(Modifier.height(space.sm))
                job.steps.forEach { step ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    when {
                                        step.status == "completed" && step.conclusion == "success" ->
                                            colors.success

                                        step.conclusion == "failure" -> colors.danger
                                        step.status == "in_progress" -> MaterialTheme.colorScheme.primary
                                        else -> colors.textDisabled
                                    },
                                ),
                        )
                        Spacer(Modifier.width(space.sm))
                        Text(
                            text = step.name,
                            style = type.bodySmall,
                            color = colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
