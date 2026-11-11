package dev.gitdroid.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import android.content.ClipData
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.ui.components.CodeWell
import dev.gitdroid.app.ui.components.GlassCard
import dev.gitdroid.app.ui.components.MetaPill
import dev.gitdroid.app.ui.components.SectionHeader
import dev.gitdroid.app.ui.components.StatusDot
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType

/**
 * The MCP dashboard.
 *
 * The screen exists so that connecting an agent is a two-minute job: it shows
 * the URL, the token, and copy-paste configuration for each agent, ready to
 * paste. Everything the agent needs is on one screen.
 */
@Composable
fun McpScreen(
    port: Int,
    running: Boolean,
    bearerToken: String,
    lanAddress: String,
    toolCount: Int,
    onToggleServer: (Boolean) -> Unit,
    onRotateToken: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var showToken by remember { mutableStateOf(false) }
    var selectedAgent by remember { mutableStateOf(AGENTS.first()) }

    val lanUrl = "http://$lanAddress:$port/mcp"
    val usbUrl = "http://127.0.0.1:$port/mcp"

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = space.xxxl),
        verticalArrangement = Arrangement.spacedBy(space.rowGap),
    ) {
        item(key = "status") {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = space.gutter, vertical = space.sm),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.padding(space.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(
                            color = if (running) colors.success else colors.textTertiary,
                            size = 10,
                            pulsing = running,
                        )
                        Spacer(Modifier.width(space.sm))
                        Text(
                            text = if (running) "Server running" else "Server stopped",
                            style = type.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = running,
                            onCheckedChange = onToggleServer,
                        )
                    }
                    Spacer(Modifier.height(space.xs))
                    Text(
                        text = if (running) {
                            "Agents can reach the phone at port $port. $toolCount tools are exposed."
                        } else {
                            "Turn the server on to let an agent drive this GitHub account."
                        },
                        style = type.bodySmall,
                        color = colors.textTertiary,
                    )

                    Spacer(Modifier.height(space.md))

                    Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                        Button(
                            onClick = { onToggleServer(!running) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (running) {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                contentColor = if (running) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                },
                            ),
                        ) {
                            Icon(
                                imageVector = if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(space.sm))
                            Text(
                                text = if (running) "Stop" else "Start",
                                style = type.label,
                            )
                        }
                    }
                }
            }
        }

        item(key = "endpoint-header") { SectionHeader("Endpoint") }

        item(key = "endpoint") {
            Column(Modifier.padding(horizontal = space.gutter)) {
                UrlCard(
                    title = "On the same network",
                    url = lanUrl,
                    onCopy = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("url", lanUrl))) } },
                )
                Spacer(Modifier.height(space.sm))
                UrlCard(
                    title = "Over USB (adb reverse)",
                    url = usbUrl,
                    subtitle = "adb reverse tcp:$port tcp:$port",
                    onCopy = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("url", usbUrl))) } },
                )
            }
        }

        item(key = "token-header") { SectionHeader("Bearer token") }

        item(key = "token") {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = space.gutter),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.padding(space.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.textTertiary,
                        )
                        Spacer(Modifier.width(space.sm))
                        Text(
                            text = if (showToken) bearerToken else "••••••••••••••••••••••••",
                            style = type.code,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(space.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                        OutlinedButton(
                            onClick = { showToken = !showToken },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (showToken) "Hide" else "Show", style = type.label)
                        }
                        OutlinedButton(
                            onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("token", bearerToken))) } },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(space.sm))
                            Text("Copy", style = type.label)
                        }
                        IconButton(onClick = onRotateToken) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Rotate token",
                                modifier = Modifier.size(18.dp),
                                tint = colors.textTertiary,
                            )
                        }
                    }
                }
            }
        }

        item(key = "agents-header") { SectionHeader("Connect an agent") }

        item(key = "agents") {
            Column(Modifier.padding(horizontal = space.gutter)) {
                // Agent picker
                LazyRow(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                    items(AGENTS) { agent ->
                        val selected = agent.name == selectedAgent.name
                        MetaPill(
                            text = agent.name,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                colors.textTertiary
                            },
                            background = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                null
                            },
                            modifier = Modifier.clickable { selectedAgent = agent },
                        )
                    }
                }
                Spacer(Modifier.height(space.md))
                CodeWell(modifier = Modifier.fillMaxWidth()) {
                    SelectionContainerCompat {
                        Text(
                            text = selectedAgent.config(lanUrl, bearerToken),
                            style = type.codeSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(space.md),
                        )
                    }
                }
                Spacer(Modifier.height(space.sm))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(
                                    ClipData.newPlainText(
                                        "config",
                                        selectedAgent.config(lanUrl, bearerToken),
                                    ),
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(space.sm))
                    Text("Copy configuration", style = type.label)
                }
            }
        }

        item(key = "how-header") { SectionHeader("How it works") }

        item(key = "how") {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = space.gutter),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.padding(space.md)) {
                    listOf(
                        "The server speaks MCP over HTTP — the same protocol the official GitHub MCP server uses.",
                        "Tool names match the official server, so an agent's existing instructions keep working.",
                        "The session runs in a foreground service, so the phone can be backgrounded without dropping the connection.",
                        "Every tool call is authenticated with the bearer token above.",
                    ).forEach { line ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text(
                                text = "•",
                                style = type.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(space.sm))
                            Text(
                                text = line,
                                style = type.bodySmall,
                                color = colors.textTertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A URL card with a copy button. */
@Composable
private fun UrlCard(
    title: String,
    url: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onCopy: () -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(space.md)) {
            Text(
                text = title,
                style = type.labelSmall,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(space.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = url,
                    style = type.code,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy URL",
                        modifier = Modifier.size(16.dp),
                        tint = colors.textTertiary,
                    )
                }
            }
            if (subtitle != null) {
                Spacer(Modifier.height(space.xs))
                Text(
                    text = subtitle,
                    style = type.codeSmall,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

/** A text selection container that keeps the code style. */
@Composable
private fun SelectionContainerCompat(content: @Composable () -> Unit) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        content()
    }
}

/** A supported agent and the configuration snippet that connects it. */
data class AgentConfig(
    val name: String,
    val config: (url: String, token: String) -> String,
)

/** The agents GitDroid ships a snippet for. */
val AGENTS: List<AgentConfig> = listOf(
    AgentConfig("Claude Code") { url, token ->
        """
        claude mcp add --transport http gitdroid $url \
          --header "Authorization: Bearer $token"
        """.trimIndent()
    },
    AgentConfig("OpenCode") { url, token ->
        """
        {
          "${'$'}schema": "https://opencode.ai/config.json",
          "mcp": {
            "gitdroid": {
              "type": "remote",
              "url": "$url",
              "enabled": true,
              "headers": { "Authorization": "Bearer $token" }
            }
          }
        }
        """.trimIndent()
    },
    AgentConfig("Codex") { url, token ->
        """
        # ~/.codex/config.toml
        [mcp_servers.gitdroid]
        url = "$url"
        http_headers = { "Authorization" = "Bearer $token" }
        """.trimIndent()
    },
    AgentConfig("Pi") { url, token ->
        """
        {
          "mcpServers": {
            "gitdroid": {
              "type": "http",
              "url": "$url",
              "headers": { "Authorization": "Bearer $token" }
            }
          }
        }
        """.trimIndent()
    },
    AgentConfig("Hermes") { url, token ->
        """
        {
          "mcp": {
            "servers": {
              "gitdroid": {
                "transport": "streamable-http",
                "url": "$url",
                "headers": { "Authorization": "Bearer $token" }
              }
            }
          }
        }
        """.trimIndent()
    },
    AgentConfig("DeepSeek") { url, token ->
        """
        {
          "plugins": {
            "gitdroid": {
              "type": "mcp",
              "transport": "streamable-http",
              "url": "$url",
              "env": { "GITDROID_TOKEN": "$token" }
            }
          }
        }
        """.trimIndent()
    },
    AgentConfig("Generic") { url, token ->
        """
        POST $url
        Authorization: Bearer $token
        Content-Type: application/json
        Accept: application/json, text/event-stream

        {"jsonrpc":"2.0","id":1,"method":"initialize", ...}
        """.trimIndent()
    },
)
