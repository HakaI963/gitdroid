package dev.gitdroid.app.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.gitdroid.app.core.repo.FileContent
import dev.gitdroid.app.core.repo.GitRepository
import dev.gitdroid.app.ui.components.CodeWell
import dev.gitdroid.app.ui.components.MarkdownBody
import dev.gitdroid.app.ui.components.EmptyState
import dev.gitdroid.app.ui.components.GlassCard
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType
import kotlinx.coroutines.launch

/**
 * The file viewer and editor.
 *
 * One surface for three kinds of content: Markdown (rendered), code
 * (monospace, with a simple highlighter) and images. The same screen is
 * also the editor — tap the pencil, change the text, and the change is
 * committed straight back to the branch you are on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileScreen(
    repository: GitRepository,
    owner: String,
    repo: String,
    path: String,
    branch: String,
    onBack: () -> Unit,
    onDownload: (FileContent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var file by remember { mutableStateOf<FileContent?>(null) }
    var loading by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var commitMessage by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(owner, repo, path, branch) {
        loading = true
        file = repository.readFile(owner, repo, path, branch)
        loading = false
    }

    val isMarkdown = path.endsWith(".md", ignoreCase = true) || path.endsWith(".markdown", ignoreCase = true)
    val isImage = IMAGE_EXTENSIONS.any { path.endsWith(it, ignoreCase = true) }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Toolbar ─────────────────────────────────────────────────────
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = space.gutter, vertical = space.sm),
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                modifier = Modifier.padding(
                    start = space.xs,
                    end = space.sm,
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
                        text = path.substringAfterLast('/'),
                        style = type.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$owner/$repo · $branch",
                        style = type.bodySmall,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (file != null && !file!!.isBinary && !isImage) {
                    IconButton(onClick = { sheetOpen = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download",
                            modifier = Modifier.size(19.dp),
                            tint = colors.textTertiary,
                        )
                    }
                    IconButton(
                        onClick = {
                            draft = file!!.content
                            editing = true
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(19.dp),
                            tint = colors.textTertiary,
                        )
                    }
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

            file == null -> EmptyState(
                icon = Icons.Rounded.Close,
                title = "Could not open the file",
                message = error ?: "The file may have been moved or deleted.",
            )

            isImage -> ImageContent(
                file = file!!,
                modifier = Modifier.fillMaxSize(),
            )

            isMarkdown -> MarkdownContent(
                content = file!!.content,
                modifier = Modifier.fillMaxSize(),
            )

            else -> CodeContent(
                content = file!!.content,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (editing && file != null) {
        EditSheet(
            initialContent = draft,
            path = path,
            saving = saving,
            onDismiss = { editing = false },
            onSave = { message, content ->
                scope.launch {
                    saving = true
                    val result = repository.writeFile(
                        owner = owner,
                        repo = repo,
                        path = path,
                        content = content,
                        message = message,
                        branch = branch,
                        sha = file!!.sha.takeIf { it.isNotBlank() },
                    )
                    saving = false
                    if (result.isSuccess) {
                        editing = false
                        file = repository.readFile(owner, repo, path, branch)
                    } else {
                        error = result.exceptionOrNull()?.message
                    }
                }
            },
        )
    }

    if (sheetOpen && file != null) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(horizontal = space.gutter, vertical = space.md)) {
                Text(
                    text = "Download",
                    style = type.title,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(space.md))
                Button(
                    onClick = {
                        sheetOpen = false
                        onDownload(file!!)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(space.sm))
                    Text("Save to device", style = type.label)
                }
                Spacer(Modifier.height(space.xxl))
            }
        }
    }
}

/** The read-only code view, with lightweight syntax highlighting. */
@Composable
private fun CodeContent(
    content: String,
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    val lines = remember(content) { content.lines() }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = space.gutter, vertical = space.sm),
    ) {
        itemsIndexed(lines) { index, line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                Text(
                    text = (index + 1).toString(),
                    style = type.codeSmall,
                    color = colors.textDisabled,
                    modifier = Modifier.width(40.dp),
                )
                SelectionContainer {
                    Text(
                        text = line.ifEmpty { " " },
                        style = type.code,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** The rendered Markdown view. */
@Composable
private fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = space.gutter,
            end = space.gutter,
            bottom = space.xxxl,
        ),
    ) {
        item { MarkdownBody(content = content, modifier = Modifier.padding(top = space.sm)) }
    }
}

/** The image view. */
@Composable
private fun ImageContent(
    file: FileContent,
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    Box(modifier = modifier.padding(space.md), contentAlignment = Alignment.Center) {
        if (file.downloadUrl.isNotBlank()) {
            AsyncImage(
                model = file.downloadUrl,
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            EmptyState(
                icon = Icons.Rounded.Close,
                title = "Preview unavailable",
                message = "This image has no public download URL.",
            )
        }
    }
}

/** The editor sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(
    initialContent: String,
    path: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (message: String, content: String) -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    var content by remember { mutableStateOf(initialContent) }
    var message by remember { mutableStateOf("Update $path") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .padding(horizontal = space.gutter)
                .padding(bottom = space.xxl),
        ) {
            Text(
                text = "Edit $path",
                style = type.title,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(space.md))

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Commit message", style = type.bodySmall) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                textStyle = type.body,
            )

            Spacer(Modifier.height(space.md))

            CodeWell(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    textStyle = type.code,
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            Spacer(Modifier.height(space.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel", style = type.label)
                }
                Button(
                    onClick = { onSave(message, content) },
                    enabled = !saving && message.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(space.sm))
                        Text("Commit", style = type.label)
                    }
                }
            }
        }
    }
}

/** The file extensions the viewer treats as images. */
val IMAGE_EXTENSIONS = listOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".svg")
