package dev.gitdroid.app.ui.screens

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
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.core.api.Repository
import dev.gitdroid.app.core.api.UpdateRepoRequest
import dev.gitdroid.app.core.repo.GitRepository
import dev.gitdroid.app.ui.components.GlassCard
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType
import kotlinx.coroutines.launch

/**
 * The repository settings sheet.
 *
 * Rename, change the description, flip the visibility, edit the topics,
 * archive — and delete, which asks you to type the name first.
 */
@Composable
fun RepoSettingsSheet(
    repository: GitRepository,
    repo: Repository,
    onClose: () -> Unit,
    onDeleted: () -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(repo.name) }
    var description by remember { mutableStateOf(repo.description.orEmpty()) }
    var topics by remember { mutableStateOf(repo.topics.joinToString(", ")) }
    var isPrivate by remember { mutableStateOf(repo.isPrivate) }
    var archived by remember { mutableStateOf(repo.archived) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier
            .padding(horizontal = space.gutter)
            .padding(bottom = space.xxl)
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        Text(
            text = "Repository settings",
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
            value = name,
            onValueChange = { name = it; message = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name", style = type.bodySmall) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            textStyle = type.body,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        Spacer(Modifier.height(space.md))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it; message = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Description", style = type.bodySmall) },
            minLines = 2,
            maxLines = 3,
            shape = MaterialTheme.shapes.small,
            textStyle = type.body,
        )

        Spacer(Modifier.height(space.md))

        OutlinedTextField(
            value = topics,
            onValueChange = { topics = it; message = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Topics (comma separated)", style = type.bodySmall) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            textStyle = type.body,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        Spacer(Modifier.height(space.lg))

        GlassCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(space.md)) {
                SettingToggle(
                    title = if (isPrivate) "Private" else "Public",
                    subtitle = if (isPrivate) {
                        "Only you and your collaborators can see it."
                    } else {
                        "Anyone on the internet can see it."
                    },
                    icon = if (isPrivate) Icons.Rounded.Lock else Icons.Rounded.Public,
                    checked = isPrivate,
                    onCheckedChange = { isPrivate = it },
                )
                Spacer(Modifier.height(space.md))
                SettingToggle(
                    title = "Archived",
                    subtitle = "Read-only. The repository is hidden from the list.",
                    icon = Icons.Rounded.Archive,
                    checked = archived,
                    onCheckedChange = { archived = it },
                )
            }
        }

        if (message != null) {
            Spacer(Modifier.height(space.md))
            Text(
                text = message!!,
                style = type.bodySmall,
                color = if (message!!.startsWith("Saved")) colors.success else colors.danger,
            )
        }

        Spacer(Modifier.height(space.lg))

        Button(
            onClick = {
                scope.launch {
                    busy = true
                    message = null
                    repository.updateRepo(
                        repo.ownerLogin,
                        repo.name,
                        UpdateRepoRequest(
                            name = name.trim().ifBlank { null },
                            description = description.trim().ifBlank { null },
                            private = isPrivate,
                            archived = archived,
                            topics = topics.split(',')
                                .map { it.trim() }
                                .filter { it.isNotEmpty() },
                        ),
                    ).onSuccess {
                        message = "Saved"
                    }.onFailure {
                        message = it.message ?: "Could not save the changes"
                    }
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(space.sm))
                Text("Save changes", style = type.label)
            }
        }

        Spacer(Modifier.height(space.md))

        OutlinedButton(
            onClick = { confirmDelete = true },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.danger),
        ) {
            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(space.sm))
            Text("Delete repository", style = type.label)
        }
    }

    if (confirmDelete) {
        DeleteRepoDialog(
            repoName = repo.name,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    busy = true
                    repository.deleteRepo(repo.ownerLogin, repo.name)
                        .onSuccess { onDeleted() }
                        .onFailure { message = it.message ?: "Could not delete the repository" }
                    busy = false
                }
            },
        )
    }
}

/**
 * The delete confirmation.
 *
 * You have to type the repository's name. It is annoying, and it is the
 * difference between a mis-click and a deleted repository.
 */
@Composable
private fun DeleteRepoDialog(
    repoName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    var typed by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Delete ${repoName}?", style = type.title, color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Column {
                Text(
                    text = "This is permanent. The code, the issues and the releases all go with it.",
                    style = type.body,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(space.md))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Type the name to confirm", style = type.bodySmall) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    textStyle = type.body,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = typed.trim() == repoName,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.danger,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                ),
            ) {
                Text("Delete", style = type.label)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = type.label)
            }
        },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/** A labelled switch row inside the settings sheet. */
@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colors.textTertiary,
        )
        Spacer(Modifier.width(space.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = type.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = type.bodySmall,
                color = colors.textTertiary,
            )
        }
        Spacer(Modifier.width(space.sm))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
