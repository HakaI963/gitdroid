package dev.gitdroid.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.core.api.Release
import dev.gitdroid.app.core.api.ReleaseAsset
import dev.gitdroid.app.core.api.Repository
import dev.gitdroid.app.core.repo.GitRepository
import dev.gitdroid.app.core.download.Downloader
import dev.gitdroid.app.ui.components.EmptyState
import dev.gitdroid.app.ui.components.GlassCard
import dev.gitdroid.app.ui.components.MarkdownBody
import dev.gitdroid.app.ui.components.MetaPill
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType
import kotlinx.coroutines.launch

/**
 * The releases tab.
 *
 * Each release expands to show its notes rendered as Markdown, its assets as
 * tappable download rows, and the two source archives github.com always
 * offers — `Source code (zip)` and `Source code (tar.gz)`.
 */
@Composable
fun ReleasesTab(
    repository: GitRepository,
    repo: Repository,
    downloader: Downloader,
    onDownloadState: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val scope = rememberCoroutineScope()

    var releases by remember { mutableStateOf<List<Release>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf<Long?>(null) }
    var busyAsset by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(repo.fullName) {
        loading = true
        releases = repository.releases(repo.ownerLogin, repo.name)
        loading = false
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = space.gutter,
            end = space.gutter,
            bottom = space.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        if (loading) {
            item(key = "loading") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(space.sm))
                    Text("Loading releases…", style = type.bodySmall, color = colors.textTertiary)
                }
            }
        }

        if (!loading && releases.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.Tag,
                    title = "No releases",
                    message = "This repository has not published a release yet.",
                )
            }
        }

        items(releases, key = { it.id }) { release ->
            ReleaseCard(
                release = release,
                expanded = expanded == release.id,
                busyAssetId = busyAsset,
                onToggle = { expanded = if (expanded == release.id) null else release.id },
                onDownloadAsset = { asset ->
                    scope.launch {
                        busyAsset = asset.id
                        onDownloadState("Downloading ${asset.name}…")
                        val result = downloader.downloadAsset(
                            url = asset.browserDownloadUrl,
                            name = asset.name,
                        )
                        busyAsset = null
                        onDownloadState(
                            when (result) {
                                is Downloader.Result.Success -> "Saved ${result.displayName}"
                                is Downloader.Result.Failure -> result.message
                            },
                        )
                    }
                },
                onDownloadSource = { format ->
                    scope.launch {
                        val url = sourceUrl(repo, release.tag_name, format)
                        val extension = if (format == "zip") "zip" else "tar.gz"
                        val name = "${repo.name}-${release.tag_name}.$extension"
                        busyAsset = -1L
                        onDownloadState("Downloading $name…")
                        val result = downloader.downloadAsset(url = url, name = name)
                        busyAsset = null
                        onDownloadState(
                            when (result) {
                                is Downloader.Result.Success -> "Saved ${result.displayName}"
                                is Downloader.Result.Failure -> result.message
                            },
                        )
                    }
                },
            )
        }
    }
}

/**
 * The source archive URL for a tag.
 *
 * codeload.github.com serves these directly; the api.github.com form answers
 * with a redirect that needs the token re-sent, which is a needless failure
 * point for a public archive.
 */
private fun sourceUrl(repo: Repository, tag: String, format: String): String {
    val extension = if (format == "zip") "zip" else "tar.gz"
    return "https://codeload.github.com/${repo.ownerLogin}/${repo.name}/$format/$tag?${'$'}extension=$extension"
}

@Composable
private fun ReleaseCard(
    release: Release,
    expanded: Boolean,
    busyAssetId: Long?,
    onToggle: () -> Unit,
    onDownloadAsset: (ReleaseAsset) -> Unit,
    onDownloadSource: (String) -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    val title = release.name?.takeIf { it.isNotBlank() } ?: release.tag_name

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Tag,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(space.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = type.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = release.tag_name +
                            (release.publishedAt?.let { " · ${it.take(10)}" } ?: ""),
                        style = type.labelSmall,
                        color = colors.textTertiary,
                    )
                }
                if (release.prerelease) {
                    MetaPill(text = "Pre-release", color = colors.warning)
                    Spacer(Modifier.width(space.xs))
                }
                if (release.draft) {
                    MetaPill(text = "Draft", color = colors.textTertiary)
                    Spacer(Modifier.width(space.xs))
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onToggle),
                    tint = colors.textTertiary,
                )
            }

            if (expanded) {
                Spacer(Modifier.height(space.md))

                if (!release.body.isNullOrBlank()) {
                    MarkdownBody(
                        content = release.body,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(space.md))
                }

                // ── Assets ──────────────────────────────────────────────
                if (release.assets.isNotEmpty()) {
                    Text(
                        text = "Assets",
                        style = type.labelSmall,
                        color = colors.textTertiary,
                    )
                    Spacer(Modifier.height(space.xs))
                    release.assets.forEach { asset ->
                        DownloadRow(
                            label = asset.name,
                            detail = "${formatBytes(asset.size)} · ${asset.downloadCount} downloads",
                            busy = busyAssetId == asset.id,
                            onClick = { onDownloadAsset(asset) },
                        )
                    }
                    Spacer(Modifier.height(space.sm))
                }

                // ── Source archives, always offered ─────────────────────
                Text(
                    text = "Source code",
                    style = type.labelSmall,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(space.xs))
                DownloadRow(
                    label = "Source code (zip)",
                    detail = "the whole repository at ${release.tag_name}",
                    busy = busyAssetId == -1L,
                    onClick = { onDownloadSource("zip") },
                )
                DownloadRow(
                    label = "Source code (tar.gz)",
                    detail = "the whole repository at ${release.tag_name}",
                    busy = busyAssetId == -1L,
                    onClick = { onDownloadSource("tar") },
                )
            }
        }
    }
}

/** A tappable download row. */
@Composable
private fun DownloadRow(
    label: String,
    detail: String,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onClick)
            .padding(vertical = space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(space.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = type.body,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = type.labelSmall,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
