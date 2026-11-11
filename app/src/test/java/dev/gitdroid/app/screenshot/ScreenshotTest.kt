package dev.gitdroid.app.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gitdroid.app.ui.components.RepoRow
import dev.gitdroid.app.ui.preview.SampleRepos
import dev.gitdroid.app.ui.screens.McpScreen
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
            McpScreen(
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
            McpScreen(
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
}
