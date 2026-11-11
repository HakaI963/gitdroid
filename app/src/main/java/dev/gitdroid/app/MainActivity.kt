package dev.gitdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.gitdroid.app.ui.GitDroidApp as GitDroidRoot
import dev.gitdroid.app.ui.theme.GitDroidTheme

/**
 * The single activity.
 *
 * The configuration changes are handled in the manifest, so rotating the
 * phone does not recreate the activity — and with it does not lose the
 * scroll position, the selected tab or the branch you were looking at.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as GitDroidApp).container
        var themeMode by mutableStateOf(container.settings.themeMode)

        setContent {
            GitDroidTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GitDroidRoot(
                        container = container,
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            themeMode = mode
                            container.settings.themeMode = mode
                        },
                    )
                }
            }
        }
    }
}
