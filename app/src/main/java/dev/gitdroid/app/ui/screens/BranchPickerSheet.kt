package dev.gitdroid.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import dev.gitdroid.app.core.repo.BranchInfo
import dev.gitdroid.app.core.repo.GitRepository
import dev.gitdroid.app.ui.components.EmptyState
import dev.gitdroid.app.ui.components.GlassCard
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType
import kotlinx.coroutines.launch

/**
 * The branch picker.
 *
 * Switching branches is the operation a developer does most often after
 * opening a repository, so it gets a sheet of its own rather than a nested
 * dropdown. Protected branches are marked, because trying to commit to one
 * is a common and confusing mistake.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchPickerSheet(
    repository: GitRepository,
    owner: String,
    repo: String,
    currentBranch: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val scope = rememberCoroutineScope()

    var branches by remember { mutableStateOf<List<BranchInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var newBranch by remember { mutableStateOf("") }

    // Load the branches in the background: the sheet opens instantly and the
    // list fills in when it arrives.
    remember {
        scope.launch {
            branches = repository.branches(owner, repo)
        }
        true
    }

    val filtered = remember(branches, query) {
        if (query.isBlank()) {
            branches
        } else {
            branches.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                text = "Branch",
                style = type.title,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(space.md))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search branches", style = type.bodySmall) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                textStyle = type.body,
            )

            if (creating) {
                Spacer(Modifier.height(space.md))
                OutlinedTextField(
                    value = newBranch,
                    onValueChange = { newBranch = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("New branch name", style = type.bodySmall) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    textStyle = type.body,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val name = newBranch.trim()
                                if (name.isEmpty()) return@IconButton
                                scope.launch {
                                    repository.createBranch(owner, repo, name, currentBranch)
                                    branches = repository.branches(owner, repo)
                                    creating = false
                                    newBranch = ""
                                    onPick(name)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Create branch",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
            } else {
                Spacer(Modifier.height(space.sm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { creating = true }
                        .padding(vertical = space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(space.sm))
                    Text(
                        text = "Create a branch from $currentBranch",
                        style = type.label,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(space.sm))

            LazyColumn(
                modifier = Modifier.height(400.dp),
                verticalArrangement = Arrangement.spacedBy(space.rowGap),
            ) {
                items(filtered, key = { it.name }) { branch ->
                    val selected = branch.name == currentBranch
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        onClick = {
                            if (!selected) onPick(branch.name)
                        },
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
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = branch.name,
                                    style = type.body,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (branch.sha.isNotEmpty()) {
                                    Text(
                                        text = branch.sha.take(8),
                                        style = type.codeSmall,
                                        color = colors.textTertiary,
                                    )
                                }
                            }
                            if (branch.isProtected) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "Protected",
                                    modifier = Modifier.size(13.dp),
                                    tint = colors.warning,
                                )
                                Spacer(Modifier.width(space.xs))
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Current branch",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.Add,
                            title = "No branch matched",
                            message = "Try another name, or create one.",
                        )
                    }
                }
            }
        }
    }
}
