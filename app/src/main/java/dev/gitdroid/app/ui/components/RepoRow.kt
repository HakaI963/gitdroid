package dev.gitdroid.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space.md),
                ) {
                    if (repo.language != null) {
                        // The dot and its label are one group: the row's
                        // spacing must not separate them.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(color = languageColor(repo.language))
                            Spacer(Modifier.width(space.xs))
                            Text(
                                text = repo.language,
                                style = type.labelSmall,
                                color = colors.textTertiary,
                            )
                        }
                    }
                    MetaStat(
                        icon = Icons.Rounded.Star,
                        count = repo.stargazersCount,
                    )
                    MetaStat(
                        icon = Icons.Rounded.CallSplit,
                        count = repo.forksCount,
                    )
                    if (repo.openIssuesCount > 0) {
                        MetaStat(
                            icon = Icons.Rounded.ErrorOutline,
                            count = repo.openIssuesCount,
                        )
                    }
                }
            }
            trailing?.invoke()
        }
    }
}

/**
 * A count with its glyph.
 *
 * The icon carries the meaning and the number carries the magnitude, so the
 * row stays scannable — an icon alone needs a label, and a number alone
 * needs a legend.
 */
@Composable
private fun MetaStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = colors.textTertiary,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = count.toString(),
            style = type.labelSmall,
            color = colors.textTertiary,
        )
    }
}
