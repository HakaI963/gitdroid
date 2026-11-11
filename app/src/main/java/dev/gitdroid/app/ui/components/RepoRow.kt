package dev.gitdroid.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.core.api.Repository
import dev.gitdroid.app.ui.screens.languageColor
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType

/**
 * One repository, rendered as a dense row.
 *
 * The row carries the five things a developer actually scans for: the name,
 * the description, the language, the stars and the open issue count.
 */
@Composable
fun RepoRow(
    repo: Repository,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(
                start = space.md,
                end = space.xs,
                top = space.md,
                bottom = space.md,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = repo.name,
                        style = type.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (repo.isPrivate) {
                        Spacer(Modifier.width(space.xs))
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Private",
                            modifier = Modifier.size(13.dp),
                            tint = colors.textTertiary,
                        )
                    }
                    if (repo.archived) {
                        Spacer(Modifier.width(space.xs))
                        MetaPill(text = "Archived", color = colors.warning)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = repo.description ?: repo.ownerLogin,
                    style = type.bodySmall,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                        Spacer(Modifier.width(space.md))
                    }
                    Text(
                        text = "★ ${repo.stargazersCount}",
                        style = type.labelSmall,
                        color = colors.textTertiary,
                    )
                    Spacer(Modifier.width(space.md))
                    Text(
                        text = "⑂ ${repo.forksCount}",
                        style = type.labelSmall,
                        color = colors.textTertiary,
                    )
                    if (repo.openIssuesCount > 0) {
                        Spacer(Modifier.width(space.md))
                        Text(
                            text = "● ${repo.openIssuesCount}",
                            style = type.labelSmall,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
            trailing?.invoke()
        }
    }
}
