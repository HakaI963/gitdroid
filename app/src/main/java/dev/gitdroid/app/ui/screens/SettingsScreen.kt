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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.core.auth.SessionState
import dev.gitdroid.app.ui.components.GlassCard
import dev.gitdroid.app.ui.components.MetaPill
import dev.gitdroid.app.ui.components.SectionHeader
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType
import dev.gitdroid.app.ui.theme.ThemeMode

/**
 * The account and preferences screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    session: SessionState.SignedIn,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    cacheSizeBytes: Long,
    onClearCache: () -> Unit,
    onSignOut: () -> Unit,
    onOpenMcp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val scope = rememberCoroutineScope()

    var confirmSignOut by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = space.xxxl),
        verticalArrangement = Arrangement.spacedBy(space.rowGap),
    ) {
        item(key = "account") {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = space.gutter, vertical = space.sm),
                shape = MaterialTheme.shapes.large,
            ) {
                Row(
                    modifier = Modifier.padding(space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = session.login.take(2).uppercase(),
                            style = type.title,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(space.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = session.login,
                            style = type.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = session.name ?: "Signed in",
                            style = type.bodySmall,
                            color = colors.textTertiary,
                        )
                    }
                    MetaPill(
                        text = if (session.method.name == "DEVICE_FLOW") "OAuth" else "Token",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        item(key = "appearance-header") { SectionHeader("Appearance") }

        item(key = "appearance") {
            Column(Modifier.padding(horizontal = space.gutter)) {
                Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                    ThemeMode.entries.forEach { mode ->
                        val selected = mode == themeMode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.medium)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .clickable { onThemeModeChange(mode) }
                                .padding(vertical = space.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.Palette,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        colors.textTertiary
                                    },
                                )
                                Spacer(Modifier.height(space.xs))
                                Text(
                                    text = when (mode) {
                                        ThemeMode.SYSTEM -> "System"
                                        ThemeMode.LIGHT -> "Light"
                                        ThemeMode.DARK -> "Dark"
                                    },
                                    style = type.label,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        colors.textTertiary
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "storage-header") { SectionHeader("Storage") }

        item(key = "storage") {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = space.gutter),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.padding(space.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = colors.textTertiary,
                        )
                        Spacer(Modifier.width(space.sm))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Offline cache",
                                style = type.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Cached lists so a screen never opens empty — ${formatBytes(cacheSizeBytes)}",
                                style = type.bodySmall,
                                color = colors.textTertiary,
                            )
                        }
                    }
                    Spacer(Modifier.height(space.md))
                    OutlinedButton(
                        onClick = onClearCache,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Clear cache", style = type.label)
                    }
                }
            }
        }

        item(key = "mcp-header") { SectionHeader("Agents") }

        item(key = "mcp") {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = space.gutter),
                shape = MaterialTheme.shapes.medium,
                onClick = onOpenMcp,
            ) {
                Row(
                    modifier = Modifier.padding(space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "MCP server",
                            style = type.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Connect Claude Code, OpenCode, Codex and the rest.",
                            style = type.bodySmall,
                            color = colors.textTertiary,
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.textTertiary,
                    )
                }
            }
        }

        item(key = "signout") {
            OutlinedButton(
                onClick = { confirmSignOut = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = space.gutter),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colors.danger,
                )
                Spacer(Modifier.width(space.sm))
                Text("Sign out", style = type.label, color = colors.danger)
            }
        }
    }

    if (confirmSignOut) {
        ModalBottomSheet(
            onDismissRequest = { confirmSignOut = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier
                    .padding(horizontal = space.gutter)
                    .padding(bottom = space.xxl),
            ) {
                Text(
                    text = "Sign out of GitDroid?",
                    style = type.title,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(space.xs))
                Text(
                    text = "The token is deleted from this device. The MCP server stops accepting " +
                        "requests from your agents.",
                    style = type.bodySmall,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(space.lg))
                OutlinedButton(
                    onClick = {
                        confirmSignOut = false
                        onSignOut()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.danger,
                    )
                    Spacer(Modifier.width(space.sm))
                    Text("Sign out", style = type.label, color = colors.danger)
                }
                Spacer(Modifier.height(space.sm))
                OutlinedButton(
                    onClick = { confirmSignOut = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel", style = type.label)
                }
            }
        }
    }
}
