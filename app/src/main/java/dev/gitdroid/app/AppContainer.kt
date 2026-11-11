package dev.gitdroid.app

import android.content.Context
import dev.gitdroid.app.core.api.GitHubApi
import dev.gitdroid.app.core.auth.AuthManager
import dev.gitdroid.app.core.auth.SecureStore
import dev.gitdroid.app.core.cache.DiskCache
import dev.gitdroid.app.core.repo.GitRepository
import dev.gitdroid.app.mcp.McpServer
import dev.gitdroid.app.mcp.McpService
import dev.gitdroid.app.mcp.McpTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The application's object graph.
 *
 * Manual dependency injection: the graph is small, and a plain container keeps
 * the build free of annotation processors, which keeps CI fast.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** A scope that lives as long as the process. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val secureStore = SecureStore(appContext)

    val diskCache = DiskCache(appContext)

    val settings = dev.gitdroid.app.core.Settings(appContext)

    val api = GitHubApi(tokenProvider = { secureStore.githubToken })

    val authManager = AuthManager(
        context = appContext,
        store = secureStore,
        apiProvider = { api },
    )

    val repository = GitRepository(
        api = api,
        cache = diskCache,
        scope = scope,
    )

    private val mcpTools = McpTools(api)

    private var mcpServer: McpServer? = null

    private val _mcpPort = MutableStateFlow(McpServer.DEFAULT_PORT)
    val mcpPort: StateFlow<Int> = _mcpPort.asStateFlow()

    private val _mcpRunning = MutableStateFlow(false)
    val mcpRunning: StateFlow<Boolean> = _mcpRunning.asStateFlow()

    /** How many tools the MCP server exposes. */
    val mcpToolCount: Int get() = mcpTools.tools.size

    /**
     * Turns the MCP endpoint on.
     *
     * The foreground service is what keeps the process alive, so starting the
     * server and starting the service are the same operation from the UI's
     * point of view.
     */
    fun enableMcp() {
        settings.mcpEnabledOnStart = true
        if (mcpServer?.isRunning != true) {
            val server = McpServer(
                tools = mcpTools,
                scope = scope,
                bearerToken = { secureStore.mcpToken },
            )
            val port = runCatching { server.start() }
                .recoverCatching { server.start(port = 0) }
                .getOrNull()
            if (port != null) {
                mcpServer = server
                _mcpPort.value = port
                _mcpRunning.value = true
            }
        }
        McpService.start(appContext)
    }

    /** Turns the MCP endpoint off and stops the foreground service. */
    fun disableMcp() {
        settings.mcpEnabledOnStart = false
        McpService.stop(appContext)
        stopMcpServer()
    }

    /**
     * Starts the MCP server without touching the foreground service.
     *
     * If the preferred port is taken, an ephemeral one is used instead — the
     * app always shows the port it actually ended up on.
     */
    fun startMcpServer() {
        if (mcpServer?.isRunning == true) return
        val server = McpServer(
            tools = mcpTools,
            scope = scope,
            bearerToken = { secureStore.mcpToken },
        )
        val port = runCatching { server.start() }
            .recoverCatching {
                server.start(port = 0)
            }
            .getOrElse {
                _mcpRunning.value = false
                return
            }
        mcpServer = server
        _mcpPort.value = port
        _mcpRunning.value = true
    }

    /** Stops the MCP server. */
    fun stopMcpServer() {
        mcpServer?.stop()
        mcpServer = null
        _mcpRunning.value = false
    }

    /**
     * The bearer token an agent must send, generating one on first use.
     *
     * It is read once and kept in memory: the MCP screen asks for it on every
     * recomposition, and a keystore round trip does not belong there.
     */
    fun mcpToken(): String {
        cachedToken?.let { return it }
        val token = secureStore.mcpToken?.takeIf { it.isNotBlank() }
            ?: SecureStore.randomToken().also { secureStore.mcpToken = it }
        cachedToken = token
        return token
    }

    /** Replaces the bearer token, invalidating every client that had the old one. */
    fun rotateMcpToken(): String {
        val token = SecureStore.randomToken()
        secureStore.mcpToken = token
        cachedToken = token
        return token
    }

    private var cachedToken: String? = null

    /** The LAN address an agent on the same network can reach. */
    fun lanAddress(): String {
        val addresses = runCatching {
            java.net.NetworkInterface.getNetworkInterfaces().toList()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList().asSequence() }
                .map { it.hostAddress }
                .filterNot { it.contains(':') }
                .toList()
        }.getOrDefault(emptyList())
        return addresses.firstOrNull() ?: "127.0.0.1"
    }
}

/** The [AppContainer] of the running process. */
val Context.container: AppContainer
    get() = (applicationContext as GitDroidApp).container
