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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.core.api.Repository
import dev.gitdroid.app.core.repo.GitRepository
import dev.gitdroid.app.ui.components.EmptyState
import dev.gitdroid.app.ui.components.ErrorBanner
import dev.gitdroid.app.ui.components.GlassCard
import dev.gitdroid.app.ui.components.MetaPill
import dev.gitdroid.app.ui.components.SectionHeader
import dev.gitdroid.app.ui.components.StatusDot
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType
import kotlinx.coroutines.launch

/**
 * The repository list — the app's home.
 *
 * It is deliberately *not* a web page. The list is dense, the pinned repos
 * sit in a rail at the top, and tapping a row opens the repo without losing
 * the scroll position you had.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReposScreen(
    repository: GitRepository,
    onOpenRepo: (Repository) -> Unit,
    onCreateRepo: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMcp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val scope = rememberCoroutineScopeCompat()
    val listState = rememberLazyListState()

    val repos by repository.repos.collectAsStateCompat()
    val loading by repository.reposLoading.collectAsStateCompat()
    val refreshing by repository.reposRefreshing.collectAsStateCompat()
    val error by repository.reposError.collectAsStateCompat()
    val pinned by repository.pinnedRepos.collectAsStateCompat()
    val recent by repository.recentRepos.collectAsStateCompat()

    var query by remember { mutableStateOf("") }
    var switcherOpen by remember { mutableStateOf(false) }

    val filtered = remember(repos, query) {
        if (query.isBlank()) {
            repos
        } else {
            repos.filter {
                it.fullName.contains(query, ignoreCase = true) ||
                    (it.description?.contains(query, ignoreCase = true) == true)
            }
        }
    }

    val pinnedRepos = remember(repos, pinned) { repos.filter { pinned.contains(it.fullName) } }
    val recentRepos = remember(repos, recent, pinned) {
        recent.filterNot { pinned.contains(it.fullName) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(space.rowGap),
        ) {
            item(key = "search") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = space.gutter, vertical = space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = "Filter repositories",
                                style = type.bodySmall,
                                color = colors.textTertiary,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = colors.textTertiary,
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        textStyle = type.body,
                    )
                    Spacer(Modifier.width(space.sm))
                    IconButton(
                        onClick = { switcherOpen = true },
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = "Quick switcher",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (error != null) {
                item(key = "error") {
                    ErrorBanner(
                        message = error!!,
                        onRetry = { scope.launch { repository.refreshRepos() } },
                    )
                }
            }

            if (pinnedRepos.isNotEmpty()) {
                item(key = "pinned-header") { SectionHeader("Pinned") }
                item(key = "pinned") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = space.gutter),
                        horizontalArrangement = Arrangement.spacedBy(space.sm),
                    ) {
                        items(pinnedRepos, key = { it.id }) { repo ->
                            PinnedRepoCard(
                                repo = repo,
                                onClick = { onOpenRepo(repo) },
                                onUnpin = { repository.togglePinned(repo.fullName) },
                            )
                        }
                    }
                }
            }

            if (recentRepos.isNotEmpty() && query.isBlank()) {
                item(key = "recent-header") { SectionHeader("Recent") }
                items(recentRepos, key = { "recent-${it.id}" }) { repo ->
                    dev.gitdroid.app.ui.components.RepoRow(
                        repo = repo,
                        modifier = Modifier.padding(horizontal = space.gutter),
                        onClick = { onOpenRepo(repo) },
                        trailing = {
                            var open by remember(repo.id) { mutableStateOf(false) }
                            ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                                IconButton(
                                    onClick = { open = true },
                                    modifier = Modifier.menuAnchor(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "Repository actions",
                                        modifier = Modifier.size(18.dp),
                                        tint = colors.textTertiary,
                                    )
                                }
                                RepoMenu(
                                    expanded = open,
                                    repo = repo,
                                    pinned = pinned.contains(repo.fullName),
                                    onDismiss = { open = false },
                                    onOpen = { open = false; onOpenRepo(repo) },
                                    onTogglePin = { open = false; repository.togglePinned(repo.fullName) },
                                    onToggleStar = { open = false; scope.launch { repository.toggleStar(repo) } },
                                )
                            }
                        },
                    )
                }
            }

            item(key = "all-header") {
                SectionHeader(
                    title = if (query.isBlank()) "All repositories" else "Results",
                    trailing = {
                        if (refreshing) {
                            MetaPill(text = "Syncing…", color = MaterialTheme.colorScheme.primary)
                        } else {
                            MetaPill(text = "${filtered.size}", color = colors.textTertiary)
                        }
                    },
                )
            }

            if (!loading && filtered.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.Code,
                        title = if (query.isBlank()) "No repositories yet" else "Nothing matched",
                        message = if (query.isBlank()) {
                            "Create your first repository, or pull in one you already have."
                        } else {
                            "Try a different name."
                        },
                    )
                }
            }

            items(filtered, key = { it.id }) { repo ->
                dev.gitdroid.app.ui.components.RepoRow(
                    repo = repo,
                    modifier = Modifier.padding(horizontal = space.gutter),
                    onClick = { onOpenRepo(repo) },
                    trailing = {
                        var open by remember(repo.id) { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = open,
                            onExpandedChange = { open = it },
                        ) {
                            IconButton(
                                onClick = { open = true },
                                modifier = Modifier.menuAnchor(),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Repository actions",
                                    modifier = Modifier.size(18.dp),
                                    tint = colors.textTertiary,
                                )
                            }
                            RepoMenu(
                                expanded = open,
                                repo = repo,
                                pinned = pinned.contains(repo.fullName),
                                onDismiss = { open = false },
                                onOpen = { open = false; onOpenRepo(repo) },
                                onTogglePin = { open = false; repository.togglePinned(repo.fullName) },
                                onToggleStar = { open = false; scope.launch { repository.toggleStar(repo) } },
                            )
                        }
                    },
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = onCreateRepo,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(space.lg),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("New repo", style = type.label) },
        )

        if (switcherOpen) {
            RepoSwitcherSheet(
                repos = repos,
                pinned = pinned,
                onDismiss = { switcherOpen = false },
                onPick = {
                    switcherOpen = false
                    repository.selectRepo(it)
                    onOpenRepo(it)
                },
                onTogglePin = { repository.togglePinned(it.fullName) },
            )
        }
    }
}

/** A pinned repository, rendered as a compact card. */
@Composable
private fun PinnedRepoCard(
    repo: Repository,
    onClick: () -> Unit,
    onUnpin: () -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    GlassCard(
        modifier = Modifier.width(180.dp),
        shape = MaterialTheme.shapes.medium,
        onClick = onClick,
    ) {
        Column(Modifier.padding(space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = repo.name,
                    style = type.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (repo.isPrivate) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Private",
                        modifier = Modifier.size(13.dp),
                        tint = colors.textTertiary,
                    )
                }
            }
            Spacer(Modifier.height(space.xs))
            Text(
                text = repo.description ?: "No description",
                style = type.bodySmall,
                color = colors.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(34.dp),
            )
            Spacer(Modifier.height(space.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (repo.language != null) {
                    StatusDot(color = languageColor(repo.language))
                    Spacer(Modifier.width(space.xs))
                    Text(
                        text = repo.language,
                        style = type.labelSmall,
                        color = colors.textTertiary,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "★ ${repo.stargazersCount}",
                    style = type.labelSmall,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

/** The overflow menu of a repository row, anchored to the row's own button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoMenu(
    expanded: Boolean,
    repo: Repository,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleStar: () -> Unit,
) {
    val type = LocalType.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Open repository", style = type.body) },
            onClick = onOpen,
            leadingIcon = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text(if (pinned) "Unpin from top" else "Pin to top", style = type.body) },
            onClick = onTogglePin,
            leadingIcon = {
                Icon(
                    imageVector = if (pinned) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null,
                )
            },
        )
        DropdownMenuItem(
            text = { Text(if (repo.fork) "Un-star" else "Star on GitHub", style = type.body) },
            onClick = onToggleStar,
            leadingIcon = { Icon(Icons.Rounded.Star, contentDescription = null) },
        )
    }
}

/**
 * The quick switcher.
 *
 * A bottom sheet with a search field and the repositories you actually use.
 * Opening it never clears the list you came from, and picking a repo keeps
 * every screen's scroll position and branch selection intact.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoSwitcherSheet(
    repos: List<Repository>,
    pinned: Set<String>,
    onDismiss: () -> Unit,
    onPick: (Repository) -> Unit,
    onTogglePin: (Repository) -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var query by remember { mutableStateOf("") }

    val filtered = remember(repos, query) {
        if (query.isBlank()) {
            repos
        } else {
            repos.filter { it.fullName.contains(query, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = space.sm)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.rule),
            )
        },
    ) {
        Column(Modifier.padding(horizontal = space.gutter)) {
            Text(
                text = "Switch repository",
                style = type.title,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(space.md))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("Search…", style = type.bodySmall, color = colors.textTertiary)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colors.textTertiary,
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                textStyle = type.body,
            )
            Spacer(Modifier.height(space.md))
            LazyColumn(
                modifier = Modifier.height(420.dp),
                verticalArrangement = Arrangement.spacedBy(space.rowGap),
            ) {
                items(filtered, key = { it.id }) { repo ->
                    SwitcherRow(
                        repo = repo,
                        isPinned = pinned.contains(repo.fullName),
                        onPick = { onPick(repo) },
                        onTogglePin = { onTogglePin(repo) },
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.Search,
                            title = "No match",
                            message = "Try another name.",
                        )
                    }
                }
            }
            Spacer(Modifier.height(space.xxl))
        }
    }
}

/** One row in the switcher sheet. */
@Composable
private fun SwitcherRow(
    repo: Repository,
    isPinned: Boolean,
    onPick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        onClick = onPick,
    ) {
        Row(
            modifier = Modifier.padding(
                start = space.md,
                end = space.xs,
                top = space.sm,
                bottom = space.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = repo.fullName,
                    style = type.body,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (repo.description != null) {
                    Text(
                        text = repo.description,
                        style = type.bodySmall,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = if (isPinned) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = if (isPinned) "Unpin" else "Pin",
                    modifier = Modifier.size(18.dp),
                    tint = if (isPinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        colors.textTertiary
                    },
                )
            }
        }
    }
}

/**
 * The colour GitHub uses for a language.
 *
 * Not the whole catalogue — the twelve that cover most repositories, plus a
 * neutral fallback.
 */
fun languageColor(language: String): Color = when (language.lowercase()) {
    "kotlin" -> Color(0xFFA97BFF)
    "java" -> Color(0xFFB07219)
    "javascript" -> Color(0xFFF1E05A)
    "typescript" -> Color(0xFF3178C6)
    "python" -> Color(0xFF3572A5)
    "go" -> Color(0xFF00ADD8)
    "rust" -> Color(0xFFDEA584)
    "ruby" -> Color(0xFF701516)
    "swift" -> Color(0xFFF05138)
    "c" -> Color(0xFF555555)
    "c++" -> Color(0xFFF34B7D)
    "csharp" -> Color(0xFF178600)
    "php" -> Color(0xFF4F5D95)
    "shell" -> Color(0xFF89E051)
    "html" -> Color(0xFFE34C26)
    "css" -> Color(0xFF563D7C)
    "dart" -> Color(0xFF00B4AB)
    "vue" -> Color(0xFF41B883)
    "scala" -> Color(0xFFC22D40)
    else -> Color(0xFF8B949E)
}
