package dev.gitdroid.app.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gitdroid.app.ui.components.RepoRow
import dev.gitdroid.app.ui.preview.SampleRepos
import dev.gitdroid.app.ui.screens.McpScreen
import dev.gitdroid.app.ui.theme.GitDroidTheme
import dev.gitdroid.app.ui.theme.ThemeMode

/**
 * The screens the design review renders.
 *
 * Every preview here is rendered on the JVM by
 * `./gradlew :app:updateDebugScreenshotTest` and lands in
 * `app/build/outputs/roborazzi/`.
 */

@Preview(name = "01-repos-dark", showBackground = true, heightDp = 700)
@Composable
fun ReposDark() {
    GitDroidTheme(themeMode = ThemeMode.DARK) {
        Surface(modifier = Modifier.fillMaxSize()) {
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
}

@Preview(name = "02-repos-light", showBackground = true, heightDp = 700)
@Composable
fun ReposLight() {
    GitDroidTheme(themeMode = ThemeMode.LIGHT) {
        Surface(modifier = Modifier.fillMaxSize()) {
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
}

@Preview(name = "03-mcp-dark", showBackground = true, heightDp = 900)
@Composable
fun McpDark() {
    GitDroidTheme(themeMode = ThemeMode.DARK) {
        Surface(modifier = Modifier.fillMaxSize()) {
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
}

@Preview(name = "04-mcp-light", showBackground = true, heightDp = 900)
@Composable
fun McpLight() {
    GitDroidTheme(themeMode = ThemeMode.LIGHT) {
        Surface(modifier = Modifier.fillMaxSize()) {
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
