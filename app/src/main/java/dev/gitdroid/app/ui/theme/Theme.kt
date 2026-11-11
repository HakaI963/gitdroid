package dev.gitdroid.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** The spacing ladder, available anywhere in the tree. */
val LocalSpace = staticCompositionLocalOf { GitDroidSpace() }

/** The motion vocabulary, available anywhere in the tree. */
val LocalMotion = staticCompositionLocalOf { Motion.Default }

/** The corner-radius ladder, available anywhere in the tree. */
val LocalShapes2 = staticCompositionLocalOf { GitDroidShapes() }

/** The typography ladder, available anywhere in the tree. */
val LocalType = staticCompositionLocalOf { GitDroidType.Default }

/** Whether the user asked for the dark scheme. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The GitDroid theme.
 *
 * Dark by default — this is a tool that lives in a pocket and gets opened in
 * bad light as often as good. The light scheme exists, and is tuned to feel
 * like the same product rather than an inverted one.
 */
@Composable
fun GitDroidTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colors = if (dark) darkGitDroidColors() else lightGitDroidColors()
    val scheme = if (dark) gitDroidDarkScheme() else gitDroidLightScheme()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !dark
        }
    }

    CompositionLocalProvider(
        LocalGitDroidColors provides colors,
        LocalSpace provides GitDroidSpace(),
        LocalMotion provides Motion.Default,
        LocalShapes2 provides GitDroidShapes(),
        LocalType provides GitDroidType.Default,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = gitDroidTypography,
            shapes = gitDroidShapes,
            content = content,
        )
    }
}

/** Shorthand for the Material shapes. */
val MaterialTheme.gitDroidShapes2: GitDroidShapes
    @Composable get() = LocalShapes2.current
