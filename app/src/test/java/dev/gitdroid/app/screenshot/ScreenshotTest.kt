package dev.gitdroid.app.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gitdroid.app.AppContainer
import dev.gitdroid.app.core.download.Downloader
import dev.gitdroid.app.ui.components.RepoRow
import dev.gitdroid.app.ui.preview.SampleRepos
import dev.gitdroid.app.ui.screens.ActionsTab
import dev.gitdroid.app.ui.screens.IssueScreen
import dev.gitdroid.app.ui.screens.McpScreen
import dev.gitdroid.app.ui.screens.RepoDetailScreen
import dev.gitdroid.app.ui.screens.RepoOverview
import dev.gitdroid.app.ui.theme.GitDroidTheme
import dev.gitdroid.app.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the app's screens to PNGs so the design can be reviewed without a
 * device.
 *
 * These capture the **real** composables, not stand-ins: a screenshot of a
 * hand-built mock-up only proves the mock-up, so a regression in the shipped
 * component would sail through. Where a component needs data it cannot fetch
 * in Robolectric, the empty and error states are exactly what the picture
 * should show.
 *
 * The images land in `app/build/screenshots/`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, themeMode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
        compose.setContent {
            GitDroidTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun container(): AppContainer {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return AppContainer(context)
    }

    @Test
    fun reposDark() {
        capture("01-repos-dark") {
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
    fun mcpRunning() {
        val c = container()
        capture("03-mcp-dark") {
            McpScreen(
                port = 8787,
                running = true,
                bearerToken = "3f9a1c7e42b8d0567a1e9f0c4b28d13",
                lanAddress = "192.168.1.42",
                toolCount = c.mcpToolCount,
                onToggleServer = {},
                onRotateToken = {},
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }

    @Test
    fun mcpStoppedLight() {
        val c = container()
        capture("04-mcp-light", ThemeMode.LIGHT) {
            McpScreen(
                port = 8787,
                running = false,
                bearerToken = "3f9a1c7e42b8d0567a1e9f0c4b28d13",
                lanAddress = "192.168.1.42",
                toolCount = c.mcpToolCount,
                onToggleServer = {},
                onRotateToken = {},
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }

    /** The issue list, rendered through the repository detail screen's tab. */
    @Test
    fun issues() {
        val c = container()
        capture("05-issues-dark") {
            IssueScreen(
                repository = c.repository,
                owner = "HakaI963",
                repo = "gitdroid",
                number = 1,
                onBack = {},
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }

    /** The real Actions tab — workflows, filters and runs. */
    @Test
    fun actions() {
        val c = container()
        capture("06-actions-dark") {
            ActionsTab(
                repository = c.repository,
                repo = SampleRepos.first(),
                branch = "main",
                onOpenRun = {},
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }

    /** The repository detail screen: header, action bar, tabs. */
    @Test
    fun repoDetail() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val c = container()
        capture("10-repo-detail") {
            RepoDetailScreen(
                repository = c.repository,
                repo = SampleRepos.first(),
                downloader = Downloader(context, c.api),
                onBack = {},
                onOpenFile = {},
                onOpenIssue = {},
                onOpenPull = {},
                onOpenRun = {},
            )
        }
    }

    /** The repository header with its GitHub-style content. */
    @Test
    fun repoOverview() {
        val c = container()
        capture("09-repo-overview") {
            Column(Modifier.padding(vertical = 12.dp)) {
                RepoOverview(
                    repository = c.repository,
                    repo = SampleRepos.first(),
                    branch = "main",
                    onRequestBranchSheet = {},
                )
            }
        }
    }
}
