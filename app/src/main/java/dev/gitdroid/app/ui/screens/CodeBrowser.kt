package dev.gitdroid.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.core.api.Repository
import dev.gitdroid.app.core.repo.DirEntry
import dev.gitdroid.app.core.repo.GitRepository
import dev.gitdroid.app.ui.components.EmptyState
import dev.gitdroid.app.ui.components.GlassCard
import dev.gitdroid.app.ui.components.MarkdownBody
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType

/**
 * The code browser.
 *
 * A real directory browser, not a flat list: tapping a folder descends into
 * it, the breadcrumb walks back up, and the README renders under the listing
 * the way github.com shows it.
 */
@Composable
fun CodeBrowser(
    repository: GitRepository,
    repo: Repository,
    branch: String,
    onOpenFile: (String) -> Unit,
    onRequestBranchSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    // The path segments from the root down to what is on screen.
    var trail by remember(repo.fullName, branch) { mutableStateOf<List<String>>(emptyList()) }
    var entries by remember { mutableStateOf<List<DirEntry>>(emptyList()) }
    var readme by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    val path = trail.joinToString("/")

    LaunchedEffect(repo.fullName, branch, path) {
        loading = true
        entries = repository.listDirectory(repo.ownerLogin, repo.name, path, branch)
        // Only the repository root shows a README, like github.com does.
        readme = if (path.isEmpty()) {
            repository.readme(repo.ownerLogin, repo.name, branch)?.content
        } else {
            null
        }
        loading = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = space.gutter, vertical = space.sm),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(space.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.CallSplit,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(space.xs))
                    Text(
                        text = branch,
                        style = type.label,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onRequestBranchSheet),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${entries.size} items",
                        style = type.labelSmall,
                        color = colors.textTertiary,
                    )
                }

                if (trail.isNotEmpty()) {
                    Spacer(Modifier.height(space.sm))
                    Breadcrumb(trail = trail, root = repo.name) { index ->
                        trail = if (index < 0) emptyList() else trail.take(index + 1)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = space.gutter,
                end = space.gutter,
                bottom = space.xxxl,
            ),
            verticalArrangement = Arrangement.spacedBy(space.rowGap),
        ) {
            if (loading && entries.isEmpty()) {
                item(key = "loading") {
                    Text(
                        text = "Reading this folder…",
                        style = type.bodySmall,
                        color = colors.textTertiary,
                    )
                }
            }

            if (!loading && entries.isEmpty() && readme == null) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.Code,
                        title = "Nothing here",
                        message = "This folder is empty on the $branch branch.",
                    )
                }
            }

            items(entries, key = { "$path/" + it.path }) { entry ->
                val isDir = entry.isDirectory
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    // A folder descends, a file opens. Routing both to the
                    // file screen is what made every folder say "could not
                    // open the file".
                    onClick = {
                        if (isDir) trail = trail + entry.name else onOpenFile(entry.path)
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
                        Icon(
                            imageVector = if (isDir) Icons.Rounded.Folder else Icons.Rounded.Description,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = if (isDir) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                colors.textTertiary
                            },
                        )
                        Spacer(Modifier.width(space.sm))
                        Text(
                            text = entry.name,
                            style = if (isDir) type.titleSmall else type.body,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (!isDir && entry.size > 0) {
                            Text(
                                text = formatBytes(entry.size),
                                style = type.labelSmall,
                                color = colors.textTertiary,
                            )
                        }
                    }
                }
            }

            if (readme != null) {
                item(key = "readme-header") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = colors.textTertiary,
                        )
                        Spacer(Modifier.width(space.xs))
                        Text(
                            text = "README.md",
                            style = type.label,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                item(key = "readme") {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        MarkdownBody(
                            content = readme!!,
                            modifier = Modifier.padding(space.md),
                        )
                    }
                }
            }
        }
    }
}

/** The path trail. Tapping a segment walks back to it; -1 is the root. */
@Composable
fun Breadcrumb(trail: List<String>, root: String, onJump: (Int) -> Unit) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = root,
            style = type.label,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onJump(-1) },
        )
        trail.forEachIndexed { index, segment ->
            Text(
                text = "  /  ",
                style = type.label,
                color = colors.textDisabled,
            )
            val last = index == trail.lastIndex
            Text(
                text = segment,
                style = type.label,
                color = if (last) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    colors.textTertiary
                },
                modifier = Modifier.clickable(enabled = !last) { onJump(index) },
            )
        }
    }
}
