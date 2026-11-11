package dev.gitdroid.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.core.api.Contributor
import dev.gitdroid.app.ui.screens.languageColor
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType

/**
 * The language bar — the thin multi-coloured strip github.com puts under a
 * repository's description.
 *
 * Widths are proportional to bytes of code per language, so the bar is an
 * actual chart and not decoration.
 */
@Composable
fun LanguageBar(
    stats: Map<String, Long>,
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    val colors = LocalGitDroidColors.current
    val type = LocalType.current

    val total = stats.values.sum().coerceAtLeast(1)
    val top = stats.entries.sortedByDescending { it.value }.take(6)

    if (top.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.rule),
        ) {
            top.forEach { (language, bytes) ->
                Box(
                    modifier = Modifier
                        .weight((bytes.toFloat() / total).coerceAtLeast(0.01f))
                        .height(8.dp)
                        .background(languageColor(language)),
                )
            }
        }

        Spacer(Modifier.height(space.sm))

        // The legend wraps onto more than one line on a phone.
        val rows = top.chunked(3)
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(space.md),
            ) {
                row.forEach { (language, bytes) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(RoundedCornerShape(50))
                                .background(languageColor(language)),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "$language ${(bytes * 100 / total)}%",
                            style = type.labelSmall,
                            color = colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Keep the weights honest when the last row is short.
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * The contributor strip, the way github.com shows avatars with commit counts.
 */
@Composable
fun ContributorRow(
    contributors: List<Contributor>,
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    if (contributors.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        contributors.take(7).forEach { person ->
            AsyncAvatar(url = person.avatarUrl, fallback = person.login, size = 28.dp)
        }
        Spacer(Modifier.weight(1f))
        val total = contributors.sumOf { it.contributions }
        Text(
            text = "$total commits",
            style = type.labelSmall,
            color = colors.textTertiary,
        )
    }
}

/** A stat block — number over label, the shape github.com uses in its headers. */
@Composable
fun StatBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = value,
            style = type.title,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label.lowercase(),
            style = type.labelSmall,
            color = colors.textTertiary,
        )
    }
}
