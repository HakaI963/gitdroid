package dev.gitdroid.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.core.api.CreateIssueRequest
import dev.gitdroid.app.core.api.CreatePullRequestRequest
import dev.gitdroid.app.core.api.Repository
import dev.gitdroid.app.core.repo.GitRepository
import dev.gitdroid.app.ui.components.CodeWell
import dev.gitdroid.app.ui.components.GlassCard
import dev.gitdroid.app.ui.components.MetaPill
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType
import kotlinx.coroutines.launch

/**
 * The new-issue sheet.
 *
 * Title, body, labels, assignees — the four things that matter on a phone,
 * without the full page of controls github.com puts around them.
 */
@Composable
fun CreateIssueSheet(
    repository: GitRepository,
    repo: Repository,
    onCreated: (Int) -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var labels by remember { mutableStateOf("") }
    var assignees by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .padding(horizontal = space.gutter)
            .padding(bottom = space.xxl)
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        Text(
            text = "New issue",
            style = type.title,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = repo.fullName,
            style = type.bodySmall,
            color = colors.textTertiary,
        )

        Spacer(Modifier.height(space.lg))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Title", style = type.bodySmall) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            textStyle = type.body,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Next,
            ),
        )

        Spacer(Modifier.height(space.md))

        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            label = { Text("Comment (Markdown)", style = type.bodySmall) },
            shape = MaterialTheme.shapes.small,
            textStyle = type.body,
        )

        Spacer(Modifier.height(space.md))

        Row(horizontalArrangement = Arrangement.spacedBy(space.md)) {
            OutlinedTextField(
                value = labels,
                onValueChange = { labels = it },
                modifier = Modifier.weight(1f),
                label = { Text("Labels", style = type.bodySmall) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                textStyle = type.body,
            )
            OutlinedTextField(
                value = assignees,
                onValueChange = { assignees = it },
                modifier = Modifier.weight(1f),
                label = { Text("Assignees", style = type.bodySmall) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                textStyle = type.body,
            )
        }

        val message = error
        if (message != null) {
            Spacer(Modifier.height(space.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = colors.danger,
                )
                Spacer(Modifier.width(space.xs))
                Text(message, style = type.bodySmall, color = colors.danger)
            }
        }

        Spacer(Modifier.height(space.lg))

        Button(
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    repository.createIssue(
                        repo.ownerLogin,
                        repo.name,
                        CreateIssueRequest(
                            title = title.trim(),
                            body = body.trim().ifBlank { null },
                            labels = labels.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                            assignees = assignees.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                        ),
                    ).onSuccess { onCreated(it.number) }
                        .onFailure { error = it.message ?: "The issue could not be created" }
                    busy = false
                }
            },
            enabled = title.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Create issue", style = type.label)
            }
        }
    }
}

/**
 * The new-pull-request sheet.
 *
 * The branch pair is the part that needs care: head defaults to the current
 * branch, base to the repository default, and both are shown so a mistake is
 * visible before the request goes out.
 */
@Composable
fun CreatePullSheet(
    repository: GitRepository,
    repo: Repository,
    currentBranch: String,
    onCreated: (Int) -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var head by remember { mutableStateOf(currentBranch) }
    var base by remember { mutableStateOf(repo.defaultBranch) }
    var draft by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .padding(horizontal = space.gutter)
            .padding(bottom = space.xxl)
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        Text(
            text = "New pull request",
            style = type.title,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(space.md))

        // The branch pair, shown the way github.com shows it.
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        ) {
            Row(
                modifier = Modifier.padding(space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CallSplit,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(space.sm))
                Text(head, style = type.code, color = MaterialTheme.colorScheme.onSurface)
                Text("  →  ", style = type.code, color = colors.textTertiary)
                Text(base, style = type.code, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                if (head == base) {
                    MetaPill(text = "same branch", color = colors.warning)
                }
            }
        }

        Spacer(Modifier.height(space.md))

        OutlinedTextField(
            value = head,
            onValueChange = { head = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Head branch", style = type.bodySmall) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            textStyle = type.body,
        )
        Spacer(Modifier.height(space.sm))
        OutlinedTextField(
            value = base,
            onValueChange = { base = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Base branch", style = type.bodySmall) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            textStyle = type.body,
        )

        Spacer(Modifier.height(space.md))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Title", style = type.bodySmall) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            textStyle = type.body,
        )

        Spacer(Modifier.height(space.md))

        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            label = { Text("Description", style = type.bodySmall) },
            shape = MaterialTheme.shapes.small,
            textStyle = type.body,
        )

        Spacer(Modifier.height(space.sm))

        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(checked = draft, onCheckedChange = { draft = it })
            Text("Open as draft", style = type.body, color = colors.textTertiary)
        }

        val message = error
        if (message != null) {
            Spacer(Modifier.height(space.sm))
            Text(message, style = type.bodySmall, color = colors.danger)
        }

        Spacer(Modifier.height(space.lg))

        Button(
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    repository.createPullRequest(
                        repo.ownerLogin,
                        repo.name,
                        CreatePullRequestRequest(
                            title = title.trim(),
                            head = head.trim(),
                            base = base.trim(),
                            body = body.trim().ifBlank { null },
                            draft = draft,
                        ),
                    ).onSuccess { onCreated(it.number) }
                        .onFailure { error = it.message ?: "The pull request could not be created" }
                    busy = false
                }
            },
            enabled = title.isNotBlank() && head.isNotBlank() &&
                base.isNotBlank() && head != base && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Create pull request", style = type.label)
            }
        }
    }
}

/**
 * The clone sheet.
 *
 * GitHub's own app makes you leave for a browser to find the clone URL. Here
 * it is one tap per form, each copyable.
 */
@Composable
fun CloneSheet(repo: Repository) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val clipboard = LocalClipboardManager.current

    Column(Modifier.padding(horizontal = space.gutter, vertical = space.md)) {
        Text(
            text = "Clone",
            style = type.title,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = repo.fullName,
            style = type.bodySmall,
            color = colors.textTertiary,
        )

        Spacer(Modifier.height(space.lg))

        listOf(
            "HTTPS" to repo.cloneUrl,
            "SSH" to repo.sshUrl,
            "GitHub CLI" to "gh repo clone ${repo.fullName}",
        ).forEach { (label, value) ->
            if (value.isNotBlank()) {
                CodeWell(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(space.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = type.labelSmall,
                                color = colors.textTertiary,
                            )
                            Text(
                                text = value,
                                style = type.codeSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                            )
                        }
                        androidx.compose.material3.IconButton(
                            onClick = { clipboard.setText(AnnotatedString(value)) },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = "Copy $label URL",
                                modifier = Modifier.size(17.dp),
                                tint = colors.textTertiary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(space.sm))
            }
        }
    }
}
