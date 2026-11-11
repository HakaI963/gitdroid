package dev.gitdroid.app.ui.preview

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
import dev.gitdroid.app.ui.screens.McpScreen
import dev.gitdroid.app.ui.theme.GitDroidTheme
import dev.gitdroid.app.ui.theme.ThemeMode

/** The repository list, as it appears in the app. */
@Preview(name = "Repos · dark", showBackground = true, heightDp = 640)
@Composable
private fun ReposDarkPreview() {
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

/** The repository list in the light scheme. */
@Preview(name = "Repos · light", showBackground = true, heightDp = 640)
@Composable
private fun ReposLightPreview() {
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

/** The MCP dashboard. */
@Preview(name = "MCP · dark", showBackground = true, heightDp = 800)
@Composable
private fun McpDarkPreview() {
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
