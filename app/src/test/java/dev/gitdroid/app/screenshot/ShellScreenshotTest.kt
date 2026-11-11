package dev.gitdroid.app.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import dev.gitdroid.app.ui.components.StatusDot
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gitdroid.app.AppContainer
import dev.gitdroid.app.ui.ShellTabBar
import dev.gitdroid.app.ui.components.PageBackground
import dev.gitdroid.app.ui.preview.SampleRepos
import dev.gitdroid.app.ui.screens.ReposScreen
import dev.gitdroid.app.ui.theme.GitDroidTheme
import dev.gitdroid.app.ui.theme.LocalType
import dev.gitdroid.app.ui.theme.ThemeMode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the *whole* shell — page background, tab bar and the repository
 * list — the way the app composes it.
 *
 * This exists because of a real bug: the signed-in screens sat on a
 * transparent Scaffold with no page background, and the tab bar was hidden
 * whenever a repository was open, so the screen read as a flat void with
 * nothing at the bottom. A screenshot of the shell catches that; a screenshot
 * of one row in isolation cannot.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ShellScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shell(name: String, themeMode: ThemeMode) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            val container = remember { AppContainer(context) }
            container.repository.seedForPreview(SampleRepos)
            ShellAt(container, themeMode)
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    fun shellDark() = shell("07-shell-dark", ThemeMode.DARK)

    @Test
    fun shellLight() = shell("08-shell-light", ThemeMode.LIGHT)
}

/** The shell under test uses the app's own destination enum. */
private typealias Tab = dev.gitdroid.app.ui.TopLevelDestination

/** The shell, parameterised by the container so the preview can seed data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellAt(container: AppContainer, themeMode: ThemeMode) {
    val type = LocalType.current
    var tab by remember { mutableStateOf(Tab.INBOX) }

    GitDroidTheme(themeMode = themeMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("GitDroid", style = type.title)
                            Spacer(Modifier.width(8.dp))
                            StatusDot(color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    actions = {
                        Text(
                            text = "HakaI963",
                            style = type.labelSmall,
                            color = LocalGitDroidColors.current.textTertiary,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(28.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("H", style = type.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            bottomBar = {
                ShellTabBar(selected = tab, onSelect = { tab = it })
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            PageBackground(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    ReposScreen(
                        repository = container.repository,
                        onOpenRepo = {},
                        onCreateRepo = {},
                        onOpenSearch = {},
                        onOpenMcp = {},
                    )
                }
            }
        }
    }
}
