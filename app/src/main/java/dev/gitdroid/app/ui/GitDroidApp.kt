package dev.gitdroid.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import dev.gitdroid.app.AppContainer
import dev.gitdroid.app.core.api.Repository
import dev.gitdroid.app.core.auth.SessionState
import dev.gitdroid.app.core.download.Downloader
import dev.gitdroid.app.ui.screens.AuthScreen
import dev.gitdroid.app.ui.screens.FileScreen
import dev.gitdroid.app.ui.screens.IssueScreen
import dev.gitdroid.app.ui.screens.McpScreen
import dev.gitdroid.app.ui.screens.RepoDetailScreen
import dev.gitdroid.app.ui.screens.ReposScreen
import dev.gitdroid.app.ui.screens.RunScreen
import dev.gitdroid.app.ui.screens.CreateRepoSheet
import dev.gitdroid.app.ui.screens.SearchScreen
import dev.gitdroid.app.ui.screens.SettingsScreen
import dev.gitdroid.app.ui.screens.collectAsStateCompat
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import dev.gitdroid.app.ui.theme.LocalSpace
import dev.gitdroid.app.ui.theme.LocalType

/** The top-level destinations. */
enum class TopLevelDestination(val label: String) {
    REPOS("Repos"),
    SEARCH("Search"),
    MCP("MCP"),
    SETTINGS("You"),
}

/**
 * The navigation shell.
 *
 * The whole app is a small stack on top of three tabs. Navigation is done by
 * hand rather than with a navigation library, because the app's navigation
 * has one unusual requirement: **screens must keep their state when you
 * leave and come back**, which a plain back stack plus a remembered state
 * holder gives us without a framework.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitDroidApp(
    container: AppContainer,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val space = LocalSpace.current
    val type = LocalType.current
    val colors = LocalGitDroidColors.current
    val context = LocalContext.current

    val session by container.authManager.state.collectAsStateCompat()
    val mcpPort by container.mcpPort.collectAsStateCompat()
    val mcpRunning by container.mcpRunning.collectAsStateCompat()
    var destination by remember { mutableStateOf(TopLevelDestination.REPOS) }
    var repo by remember { mutableStateOf<Repository?>(null) }
    var filePath by remember { mutableStateOf<String?>(null) }
    var issueNumber by remember { mutableStateOf<Int?>(null) }
    var runId by remember { mutableStateOf<Long?>(null) }
    var showBack by remember { mutableStateOf(false) }
    var createRepoOpen by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableStateOf(0L) }

    val downloader = remember { Downloader(context, container.api) }

    // Restore the saved theme and measure the cache once, off the hot path.
    LaunchedEffect(Unit) {
        cacheSize = container.diskCache.sizeBytes()
    }

    /**
     * The back button pops the navigation stack.
     *
     * The stack is shallow — file, then issue, then repository, then the
     * tabs — so it is written out explicitly rather than pulled in from a
     * navigation library.
     */
    BackHandler(enabled = showBack || filePath != null || issueNumber != null || runId != null) {
        when {
            runId != null -> runId = null
            filePath != null -> filePath = null
            issueNumber != null -> issueNumber = null
            repo != null -> {
                repo = null
                showBack = false
            }
        }
    }

    when (val current = session) {
        SessionState.Loading -> Box(Modifier.fillMaxSize())

        SessionState.SignedOut -> AuthScreen(
            authManager = container.authManager,
            onSignedIn = { destination = TopLevelDestination.REPOS },
        )

        is SessionState.SignedIn -> Scaffold(
            bottomBar = {
                if (!showBack) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp,
                    ) {
                        TopLevelDestination.entries.forEach { entry ->
                            NavigationBarItem(
                                selected = destination == entry && repo == null,
                                onClick = {
                                    destination = entry
                                    repo = null
                                    filePath = null
                                },
                                icon = {
                                    Icon(
                                        imageVector = when (entry) {
                                            TopLevelDestination.REPOS -> Icons.Rounded.Hub
                                            TopLevelDestination.SEARCH -> Icons.Rounded.Search
                                            TopLevelDestination.MCP -> Icons.Rounded.Info
                                            TopLevelDestination.SETTINGS -> Icons.Rounded.Person
                                        },
                                        contentDescription = null,
                                    )
                                },
                                label = {
                                    Text(entry.label, style = type.labelSmall)
                                },
                            )
                        }
                    }
                }
            },
            containerColor = Color.Transparent,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(it),
            ) {
                when {
                    runId != null && repo != null -> RunScreen(
                        repository = container.repository,
                        owner = repo!!.ownerLogin,
                        repo = repo!!.name,
                        runId = runId!!,
                        onBack = { runId = null },
                    )

                    issueNumber != null && repo != null -> IssueScreen(
                        repository = container.repository,
                        owner = repo!!.ownerLogin,
                        repo = repo!!.name,
                        number = issueNumber!!,
                        onBack = { issueNumber = null },
                    )

                    filePath != null && repo != null -> FileScreen(
                        repository = container.repository,
                        owner = repo!!.ownerLogin,
                        repo = repo!!.name,
                        path = filePath!!,
                        branch = container.repository.branchFor(repo!!),
                        onBack = { filePath = null },
                        onDownload = { content ->
                            // Downloads run in the background; the UI stays put.
                            container.scope.launch {
                                downloader.downloadFile(content, repo!!.ownerLogin, repo!!.name)
                            }
                        },
                    )

                    repo != null -> RepoDetailScreen(
                        repository = container.repository,
                        repo = repo!!,
                        onBack = {
                            repo = null
                            showBack = false
                        },
                        onOpenFile = { path -> filePath = path },
                        onOpenIssue = { number -> issueNumber = number },
                        onOpenPull = { number -> issueNumber = number },
                        onOpenRun = { id -> runId = id },
                    )

                    destination == TopLevelDestination.REPOS -> ReposScreen(
                        repository = container.repository,
                        onOpenRepo = {
                            repo = it
                            showBack = true
                        },
                        onCreateRepo = { createRepoOpen = true },
                        onOpenSearch = { destination = TopLevelDestination.SEARCH },
                        onOpenMcp = { destination = TopLevelDestination.MCP },
                    )

                    destination == TopLevelDestination.SEARCH -> SearchScreen(
                        repository = container.repository,
                        onOpenRepo = {
                            repo = it
                            showBack = true
                        },
                        onBack = { destination = TopLevelDestination.REPOS },
                    )

                    destination == TopLevelDestination.SETTINGS -> SettingsScreen(
                        session = current,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        cacheSizeBytes = cacheSize,
                        onClearCache = {
                            container.scope.launch {
                                container.diskCache.clear()
                                cacheSize = container.diskCache.sizeBytes()
                            }
                        },
                        onSignOut = { container.authManager.signOut() },
                        onOpenMcp = { destination = TopLevelDestination.MCP },
                    )

                    destination == TopLevelDestination.MCP -> McpScreen(
                        port = mcpPort,
                        running = mcpRunning,
                        bearerToken = container.mcpToken(),
                        lanAddress = container.lanAddress(),
                        toolCount = container.mcpToolCount,
                        onToggleServer = { enabled ->
                            if (enabled) {
                                container.enableMcp()
                            } else {
                                container.disableMcp()
                            }
                        },
                        onRotateToken = { container.rotateMcpToken() },
                    )
                }
            }

            if (createRepoOpen) {
                ModalBottomSheet(
                    onDismissRequest = { createRepoOpen = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    CreateRepoSheet(
                        repository = container.repository,
                        onCreated = {
                            createRepoOpen = false
                            repo = it
                            showBack = true
                        },
                        onDismiss = { createRepoOpen = false },
                    )
                }
            }
        }
    }
}
