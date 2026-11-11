package dev.gitdroid.app.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gitdroid.app.core.api.Issue
import dev.gitdroid.app.core.api.Label
import dev.gitdroid.app.core.api.User
import dev.gitdroid.app.core.api.WorkflowRun
import dev.gitdroid.app.ui.components.MetaPill
import dev.gitdroid.app.ui.components.RepoRow
import dev.gitdroid.app.ui.preview.SampleRepos
import dev.gitdroid.app.ui.components.StatusDot
import dev.gitdroid.app.ui.screens.RunStatusIcon
import dev.gitdroid.app.ui.theme.GitDroidTheme
import dev.gitdroid.app.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the app's screens to PNGs so the design can be reviewed without a
 * device.
 *
 * These are the actual pixels the app produces — Roborazzi drives the real
 * Compose UI on the JVM through Robolectric.
 *
 * The images land in `app/build/screenshots/`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, themeMode: ThemeMode, content: @Composable () -> Unit) {
        compose.setContent {
            GitDroidTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    fun reposDark() {
        capture("01-repos-dark", ThemeMode.DARK) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SampleRepos.forEach { repo ->
                    RepoRow(repo = repo, onClick = {})
                }
            }
        }
    }

    @Test
    fun reposLight() {
        capture("02-repos-light", ThemeMode.LIGHT) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SampleRepos.forEach { repo ->
                    RepoRow(repo = repo, onClick = {})
                }
            }
        }
    }

    @Test
    fun mcpDark() {
        capture("03-mcp-dark", ThemeMode.DARK) {
            dev.gitdroid.app.ui.screens.McpScreen(
                port = 8787,
                running = true,
                bearerToken = "3f9a1c7e42b8d0567a1e9f0c4b28d13",
                lanAddress = "192.168.1.42",
                toolCount = 28,
                onToggleServer = {},
                onRotateToken = {},
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }

    @Test
    fun mcpLight() {
        capture("04-mcp-light", ThemeMode.LIGHT) {
            dev.gitdroid.app.ui.screens.McpScreen(
                port = 8787,
                running = false,
                bearerToken = "3f9a1c7e42b8d0567a1e9f0c4b28d13",
                lanAddress = "192.168.1.42",
                toolCount = 28,
                onToggleServer = {},
                onRotateToken = {},
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }

    /** The issue list, which is the densest list in the app. */
    @Test
    fun issuesDark() {
        capture("05-issues-dark", ThemeMode.DARK) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SampleIssues.forEach { issue ->
                    dev.gitdroid.app.ui.components.GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Box(Modifier.width(8.dp))
                            Column {
                                androidx.compose.material3.Text(
                                    text = issue.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                androidx.compose.material3.Text(
                                    text = "#${issue.number} · ${issue.user?.login}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (issue.labels.isNotEmpty()) {
                                    androidx.compose.foundation.layout.Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        issue.labels.forEach { label ->
                                            MetaPill(text = label.name, color = MaterialTheme.colorScheme.secondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** The workflow run list, with the status colours that matter most. */
    @Test
    fun actionsDark() {
        capture("06-actions-dark", ThemeMode.DARK) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SampleRuns.forEach { run ->
                    dev.gitdroid.app.ui.components.GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RunStatusIcon(status = run.status, conclusion = run.conclusion)
                            Box(Modifier.width(8.dp))
                            Column {
                                androidx.compose.material3.Text(
                                    text = run.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                androidx.compose.material3.Text(
                                    text = "${run.headBranch} · ${run.event}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
