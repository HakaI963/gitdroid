package dev.gitdroid.app.core

import android.content.Context
import dev.gitdroid.app.ui.theme.ThemeMode

/**
 * The user's preferences.
 *
 * Small enough to keep in a plain preferences file — there is no reason to
 * pull in DataStore for three values, and a synchronous read means the theme
 * is correct on the very first frame instead of flashing light before the
 * dark scheme arrives.
 */
class Settings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("gitdroid_settings", Context.MODE_PRIVATE)

    /** The colour scheme the user picked. */
    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    /** Whether the MCP server should be running when the app starts. */
    var mcpEnabledOnStart: Boolean
        get() = prefs.getBoolean(KEY_MCP_ON_START, true)
        set(value) = prefs.edit().putBoolean(KEY_MCP_ON_START, value).apply()

    /** The last repository the user looked at, so the app reopens on it. */
    var lastRepo: String?
        get() = prefs.getString(KEY_LAST_REPO, null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_LAST_REPO) else putString(KEY_LAST_REPO, value)
        }.apply()

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_MCP_ON_START = "mcp_on_start"
        const val KEY_LAST_REPO = "last_repo"
    }
}
