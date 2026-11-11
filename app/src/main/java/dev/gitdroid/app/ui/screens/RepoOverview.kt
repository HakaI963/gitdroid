package dev.gitdroid.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.core.api.Commit
import dev.gitdroid.app.core.api.Contributor
import dev.gitdroid.app.core.api.Repository
import dev.gitdroid.app.core.repo.GitRepository
import dev.gitdroid.app.ui.components.AsyncAvatar
import dev.gitdroid.app.ui.components.ContributorRow
import dev.gitdroid.app.ui.components.GlassCard
import dev.gitdroid.app.ui.components.LanguageBar
import dev.gitdroid.app.ui.components.MetaPill
import dev.gitdroid.app.ui.components.StatBlock
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType
import kotlinx.coroutines.launch

/**
 * The repository header, built the way github.com builds it: what the project
 * is, who last touched it, how big it is, and what it is written in.
 *
 * The commit, contributor and language data arrive after the first frame —
 * the header paints immediately from the repository object and fills in the
 * rest, so opening a repo never waits on three extra round trips.
 */
@Composable
fun RepoOverview(
    repository: GitRepository,
    repo: Repository,
    branch: String,
    onRequestBranchSheet: () -> Unit,
    onBack: () -> Unit = {},
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val scope = rememberCoroutineScope()

    var latest by remember { mutableStateOf<Commit?>(null) }
    var contributors by remember { mutableStateOf<List<Contributor>>(emptyList()) }
    var languages by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var starred by remember(repo.fullName) { mutableStateOf(repository.isStarred(repo.fullName)) }

    LaunchedEffect(repo.fullName, branch) {
        languages = runCatching { repository.languageStats(repo.ownerLogin, repo.name) }
            .getOrDefault(emptyMap())
        contributors = runCatching { repository.contributors(repo.ownerLogin, repo.name) }
            .getOrDefault(emptyList())
        latest = runCatching { repository.latestCommit(repo.ownerLogin, repo.name, branch) }
            .getOrNull()
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = space.gutter, vertical = space.sm),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = repo.ownerLogin,
                            style = type.titleSmall,
                            color = colors.textTertiary,
                        )
                        Text(
                            text = " / ",
                            style = type.titleSmall,
                            color = colors.textDisabled,
                        )
                        Text(
                            text = repo.name,
                            style = type.title,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (repo.isPrivate) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Private",
                        modifier = Modifier.size(14.dp),
                        tint = colors.textTertiary,
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            repository.toggleStar(repo)
                            starred = repository.isStarred(repo.fullName)
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (starred) "Unstar" else "Star",
                        modifier = Modifier.size(19.dp),
                        tint = if (starred) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            colors.textTertiary
                        },
                    )
                }
            }

            if (repo.description != null) {
                Spacer(Modifier.height(space.xs))
                Text(
                    text = repo.description,
                    style = type.bodySmall,
                    color = colors.textTertiary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (repo.topics.isNotEmpty()) {
                Spacer(Modifier.height(space.sm))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(space.xs),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    repo.topics.take(6).forEach { topic ->
                        MetaPill(
                            text = topic,
                            color = MaterialTheme.colorScheme.secondary,
                            background = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(space.md))

            // ── The counts github.com puts under the description ──────────
            Row(horizontalArrangement = Arrangement.spacedBy(space.xxl)) {
                StatBlock(label = "Stars", value = repo.stargazersCount.toString())
                StatBlock(label = "Forks", value = repo.forksCount.toString())
                StatBlock(label = "Watchers", value = repo.watchersCount.toString())
            }

            Spacer(Modifier.height(space.md))

            if (languages.isNotEmpty()) {
                LanguageBar(stats = languages)
                Spacer(Modifier.height(space.md))
            }

            if (contributors.isNotEmpty()) {
                ContributorRow(contributors = contributors)
                Spacer(Modifier.height(space.md))
            }

            // ── Branch + the latest commit, the row github.com shows ──────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.CallSplit,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
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
                latest?.let { commit ->
                    Text(
                        text = commit.sha.take(7),
                        style = type.codeSmall,
                        color = colors.textTertiary,
                    )
                }
            }

            latest?.let { commit ->
                Spacer(Modifier.height(space.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncAvatar(
                        url = commit.author?.avatarUrl.orEmpty(),
                        fallback = commit.author?.login ?: "?",
                        size = 22.dp,
                    )
                    Spacer(Modifier.width(space.sm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = commit.commit.message.lines().firstOrNull().orEmpty(),
                            style = type.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = buildString {
                                append(commit.author?.login ?: commit.commit.author?.name ?: "unknown")
                                commit.commit.author?.date?.let { append(" · ${it.take(10)}") }
                            },
                            style = type.labelSmall,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
        }
    }
}
