package dev.gitdroid.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import dev.gitdroid.app.core.repo.GitRepository
import dev.gitdroid.app.ui.components.EmptyState
import dev.gitdroid.app.ui.components.GlassCard
import dev.gitdroid.app.ui.components.MetaPill
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType
import kotlinx.coroutines.launch

/**
 * The issue and pull-request detail screen.
 *
 * Both live in the same shape because GitHub's API does: an issue is a pull
 * request with `pull_request` set, so one renderer covers both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueScreen(
    repository: GitRepository,
    owner: String,
    repo: String,
    number: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val scope = rememberCoroutineScope()

    var detail by remember { mutableStateOf<dev.gitdroid.app.core.repo.IssueDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var commentText by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    var commentOpen by remember { mutableStateOf(false) }

    LaunchedEffect(owner, repo, number) {
        loading = true
        detail = repository.issueDetail(owner, repo, number)
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
                        text = "#$number",
                        style = type.labelSmall,
                        color = colors.textTertiary,
                    )
                    Text(
                        text = detail?.issue?.title ?: "",
                        style = type.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
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

            detail == null -> EmptyState(
                icon = Icons.Rounded.ErrorOutline,
                title = "Could not load the issue",
                message = "It may have been deleted, or the token lacks access.",
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
                item(key = "meta") {
                    Row(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
                        MetaPill(
                            text = detail!!.issue.state,
                            color = if (detail!!.issue.state == "open") {
                                colors.success
                            } else {
                                colors.textTertiary
                            },
                        )
                        if (detail!!.issue.isPullRequest) {
                            MetaPill(text = "Pull request", color = MaterialTheme.colorScheme.secondary)
                        }
                        detail!!.issue.labels.forEach { label ->
                            MetaPill(text = label.name, color = labelColor(label.color))
                        }
                    }
                }

                item(key = "body") {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(Modifier.padding(space.md)) {
                            Text(
                                text = detail!!.issue.user?.login ?: "unknown",
                                style = type.labelSmall,
                                color = colors.textTertiary,
                            )
                            Spacer(Modifier.height(space.xs))
                            Text(
                                text = detail!!.issue.body?.takeIf { it.isNotBlank() }
                                    ?: "No description.",
                                style = type.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (detail!!.comments.isNotEmpty()) {
                    item(key = "comments-header") {
                        Text(
                            text = "${detail!!.comments.size} comment" +
                                if (detail!!.comments.size == 1) "" else "s",
                            style = type.labelSmall,
                            color = colors.textTertiary,
                        )
                    }
                }

                items(detail!!.comments, key = { it.id }) { comment ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Column(Modifier.padding(space.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = colors.textTertiary,
                                )
                                Spacer(Modifier.width(space.xs))
                                Text(
                                    text = comment.user?.login ?: "unknown",
                                    style = type.labelSmall,
                                    color = colors.textTertiary,
                                )
                            }
                            Spacer(Modifier.height(space.xs))
                            Text(
                                text = comment.body,
                                style = type.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item(key = "comment-action") {
                    Button(
                        onClick = { commentOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(space.sm))
                        Text("Add a comment", style = type.label)
                    }
                }
            }
        }
    }

    if (commentOpen) {
        ModalBottomSheet(
            onDismissRequest = { commentOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier
                    .padding(horizontal = space.gutter)
                    .padding(bottom = space.xxl)
                    .imePadding(),
            ) {
                Text(
                    text = "Comment",
                    style = type.title,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(space.md))
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    placeholder = { Text("Write a comment…", style = type.bodySmall) },
                    shape = MaterialTheme.shapes.small,
                    textStyle = type.body,
                )
                Spacer(Modifier.height(space.md))
                Button(
                    onClick = {
                        scope.launch {
                            posting = true
                            repository.comment(owner, repo, number, commentText)
                            detail = repository.issueDetail(owner, repo, number)
                            commentText = ""
                            posting = false
                            commentOpen = false
                        }
                    },
                    enabled = commentText.isNotBlank() && !posting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (posting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Post comment", style = type.label)
                    }
                }
            }
        }
    }
}
