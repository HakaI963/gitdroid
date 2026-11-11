package dev.gitdroid.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.core.api.Repository
import dev.gitdroid.app.core.repo.GitRepository
import dev.gitdroid.app.ui.components.EmptyState
import dev.gitdroid.app.ui.components.GlassCard
import dev.gitdroid.app.ui.components.MetaPill
import dev.gitdroid.app.ui.components.StatusDot
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalMotion
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType

/**
 * The search screen.
 *
 * Searches **public** GitHub — repositories and code — which is the one
 * capability the official mobile app genuinely lacks. An agent hitting the
 * MCP server gets the same access.
 */
@Composable
fun SearchScreen(
    repository: GitRepository,
    onOpenRepo: (Repository) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<Repository>>(emptyList()) }
    var codeHits by remember { mutableStateOf<List<dev.gitdroid.app.core.api.CodeSearchResult>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    LaunchedEffect(query, mode) {
        if (query.isBlank()) {
            results = emptyList()
            codeHits = emptyList()
            total = 0
            searched = false
            return@LaunchedEffect
        }
        searching = true
        if (mode == 0) {
            val result = repository.searchRepositories(query)
            results = result.items
            total = result.totalCount
        } else {
            val result = repository.searchCode(query)
            codeHits = result.items
            total = result.totalCount
        }
        searching = false
        searched = true
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
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = if (mode == 0) "Search repositories" else "Search code",
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
            }
        }

        // ── Mode switch ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = space.gutter, vertical = space.xs),
            horizontalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            SearchModeTab(
                label = "Repositories",
                selected = mode == 0,
                modifier = Modifier.weight(1f),
                onClick = { mode = 0 },
            )
            SearchModeTab(
                label = "Code",
                selected = mode == 1,
                modifier = Modifier.weight(1f),
                onClick = { mode = 1 },
            )
        }

        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn(motion.quick()) togetherWith fadeOut(motion.quick()) },
            label = "search-mode",
            modifier = Modifier.weight(1f),
        ) { current ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = space.gutter,
                    end = space.gutter,
                    bottom = space.xxxl,
                ),
                verticalArrangement = Arrangement.spacedBy(space.rowGap),
            ) {
                if (searched && total > 0) {
                    item(key = "count") {
                        Text(
                            text = "$total result${if (total == 1) "" else "s"}",
                            style = type.labelSmall,
                            color = colors.textTertiary,
                        )
                    }
                }

                if (searching) {
                    item(key = "loading") {
                        Text(
                            text = "Searching…",
                            style = type.bodySmall,
                            color = colors.textTertiary,
                        )
                    }
                }

                if (!searching && searched && total == 0) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Rounded.Search,
                            title = "No results",
                            message = "Try a different query, or add a qualifier like `language:kotlin`.",
                        )
                    }
                }

                if (current == 0) {
                    items(results, key = { it.id }) { repo ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            onClick = { onOpenRepo(repo) },
                        ) {
                            Column(Modifier.padding(space.md)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = repo.fullName,
                                        style = type.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = colors.textTertiary,
                                    )
                                    Spacer(Modifier.width(space.xs))
                                    Text(
                                        text = repo.stargazersCount.toString(),
                                        style = type.labelSmall,
                                        color = colors.textTertiary,
                                    )
                                }
                                if (repo.description != null) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = repo.description,
                                        style = type.bodySmall,
                                        color = colors.textTertiary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
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
                                        Spacer(Modifier.width(space.md))
                                    }
                                    MetaPill(text = repo.defaultBranch, color = colors.textTertiary)
                                }
                            }
                        }
                    }
                } else {
                    items(codeHits, key = { it.sha + it.path }) { hit ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Column(Modifier.padding(space.md)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Code,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(space.sm))
                                    Text(
                                        text = hit.path,
                                        style = type.codeSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = hit.repository?.fullName.orEmpty(),
                                    style = type.labelSmall,
                                    color = colors.textTertiary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A search mode tab. */
@Composable
private fun SearchModeTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val motion = LocalMotion.current

    val background by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        animationSpec = motion.quick(),
        label = "search-mode-bg",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = space.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = type.label,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                colors.textTertiary
            },
        )
    }
}
