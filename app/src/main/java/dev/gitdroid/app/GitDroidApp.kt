package dev.gitdroid.app

import android.app.Application
import dev.gitdroid.app.mcp.McpService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The GitDroid application.
 *
 * Holds the [AppContainer] and kicks off the session restore plus the MCP
 * server as early as possible, so the app is usable the moment it appears.
 */
class GitDroidApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        container.scope.launch {
            runCatching {
                container.authManager.restore()
                container.repository.loadPinned()
                container.repository.loadBranchSelection()
                container.repository.loadRepos()
            }
        }

        // The MCP endpoint is useful even when the UI is not on screen.
        McpService.start(this)
    }

    override fun onTerminate() {
        container.stopMcpServer()
        super.onTerminate()
    }
}
