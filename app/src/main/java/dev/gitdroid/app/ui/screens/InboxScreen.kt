package dev.gitdroid.app.ui.screens

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
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.core.api.Notification
import dev.gitdroid.app.core.repo.Inbox
import dev.gitdroid.app.core.repo.kindLabel
import dev.gitdroid.app.core.repo.repoFullName
import dev.gitdroid.app.ui.components.EmptyState
import dev.gitdroid.app.ui.components.ErrorBanner
import dev.gitdroid.app.ui.components.GlassCard
import dev.gitdroid.app.ui.components.MetaPill
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType

/**
 * The inbox — unread mentions, review requests and CI failures, the way
 * github.com groups them by reason.
 */
@Composable
fun InboxScreen(
    inbox: Inbox,
    onOpenRepo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    val items by inbox.items.collectAsStateCompat()
    val error by inbox.error.collectAsStateCompat()
    val loading by inbox.loading.collectAsStateCompat()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = space.xxxl),
        verticalArrangement = Arrangement.spacedBy(space.rowGap),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = space.gutter, vertical = space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Inbox",
                        style = type.title,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = when {
                            loading -> "Checking for updates…"
                            items.isEmpty() -> "Nothing needs you"
                            else -> "${items.count { it.unread }} unread"
                        },
                        style = type.bodySmall,
                        color = colors.textTertiary,
                    )
                }
                if (items.any { it.unread }) {
                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = { inbox.markAllRead() })
                            .padding(horizontal = space.sm, vertical = space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(space.xs))
                        Text("Mark all read", style = type.label, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        val message = error
        if (message != null) {
            item(key = "error") { ErrorBanner(message = message) }
        }

        if (items.isEmpty() && !loading) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.Inbox,
                    title = "Inbox zero",
                    message = "No unread notifications on this account.",
                )
            }
        }

        // Grouped by reason, the way github.com does it.
        val grouped = items.groupBy { it.reason.ifBlank { "other" } }
        grouped.forEach { (reason, group) ->
            item(key = "reason-$reason") {
                Text(
                    text = reasonLabel(reason).uppercase(),
                    style = type.labelSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(horizontal = space.gutter, vertical = space.xs),
                )
            }
            items(group, key = { it.id }) { notification ->
                NotificationRow(
                    notification = notification,
                    onOpenRepo = { onOpenRepo(notification.repoFullName) },
                )
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: Notification, onOpenRepo: () -> Unit) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = space.gutter),
        shape = MaterialTheme.shapes.medium,
        onClick = onOpenRepo,
    ) {
        Row(
            modifier = Modifier.padding(space.md),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (notification.unread) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            colors.textDisabled
                        },
                    ),
            )
            Spacer(Modifier.width(space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = notification.subject.title.ifBlank { "(no title)" },
                    style = type.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (notification.repository != null) {
                        Text(
                            text = notification.repository.fullName,
                            style = type.labelSmall,
                            color = colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(space.sm))
                    }
                    MetaPill(text = notification.kindLabel, color = colors.textTertiary)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = notification.updatedAt?.take(10).orEmpty(),
                        style = type.labelSmall,
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }
}

/** Turns GitHub's machine reason into a section heading. */
private fun reasonLabel(reason: String): String = when (reason.lowercase()) {
    "assign" -> "Assigned"
    "author" -> "Your threads"
    "comment" -> "Comments"
    "invitation" -> "Invitations"
    "manual" -> "Watching"
    "mention" -> "Mentions"
    "review_requested" -> "Review requests"
    "security_alert" -> "Security alerts"
    "state_change" -> "State changes"
    "subscribed" -> "Subscribed"
    "team_mention" -> "Team mentions"
    else -> "Other"
}
