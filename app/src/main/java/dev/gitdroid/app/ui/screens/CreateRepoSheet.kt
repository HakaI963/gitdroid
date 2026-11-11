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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.core.api.CreateRepoRequest
import dev.gitdroid.app.core.repo.GitRepository
import dev.gitdroid.app.ui.components.GlassCard
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType
import kotlinx.coroutines.launch

/**
 * The create-repository sheet.
 *
 * Creating a repo on github.com takes six form fields and a page reload.
 * Here it is three fields and a sheet — the whole point of the app.
 */
@Composable
fun CreateRepoSheet(
    repository: GitRepository,
    onCreated: (dev.gitdroid.app.core.api.Repository) -> Unit,
    onDismiss: () -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var autoInit by remember { mutableStateOf(true) }
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
            text = "New repository",
            style = type.title,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(space.md))

        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name", style = type.bodySmall) },
            placeholder = { Text("my-project", style = type.code) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            textStyle = type.body,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
        )

        Spacer(Modifier.height(space.md))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Description (optional)", style = type.bodySmall) },
            minLines = 2,
            maxLines = 3,
            shape = MaterialTheme.shapes.small,
            textStyle = type.body,
        )

        Spacer(Modifier.height(space.lg))

        GlassCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(space.md)) {
                ToggleRow(
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
                ToggleRow(
                    title = "Initialise with a README",
                    subtitle = "Creates the first commit so the repo is not empty.",
                    icon = Icons.Rounded.Public,
                    checked = autoInit,
                    onCheckedChange = { autoInit = it },
                )
            }
        }

        val errorMessage = error
        if (errorMessage != null) {
            Spacer(Modifier.height(space.md))
            Text(
                text = errorMessage,
                style = type.bodySmall,
                color = colors.danger,
            )
        }

        Spacer(Modifier.height(space.lg))

        Button(
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    repository.createRepo(
                        CreateRepoRequest(
                            name = name.trim(),
                            description = description.trim().ifBlank { null },
                            private = isPrivate,
                            autoInit = autoInit,
                        ),
                    ).onSuccess {
                        onCreated(it)
                    }.onFailure {
                        error = it.message ?: "The repository could not be created"
                    }
                    busy = false
                }
            },
            enabled = name.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Create repository", style = type.label)
            }
        }
    }
}

/** A labelled switch row. */
@Composable
private fun ToggleRow(
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
