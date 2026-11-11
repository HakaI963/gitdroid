package dev.gitdroid.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import dev.gitdroid.app.AppContainer
import dev.gitdroid.app.core.api.Repository
import dev.gitdroid.app.core.auth.SessionState
import dev.gitdroid.app.core.download.Downloader
import dev.gitdroid.app.ui.components.PageBackground
import dev.gitdroid.app.ui.components.StatusDot
import dev.gitdroid.app.ui.screens.AuthScreen
import dev.gitdroid.app.ui.screens.CreateRepoSheet
import dev.gitdroid.app.ui.screens.FileScreen
import dev.gitdroid.app.ui.screens.IssueScreen
import dev.gitdroid.app.ui.screens.McpScreen
import dev.gitdroid.app.ui.screens.RepoDetailScreen
import dev.gitdroid.app.ui.screens.ReposScreen
import dev.gitdroid.app.ui.screens.RunScreen
import dev.gitdroid.app.ui.screens.SearchScreen
import dev.gitdroid.app.ui.screens.SettingsScreen
import dev.gitdroid.app.ui.screens.collectAsStateCompat
import dev.gitdroid.app.ui.theme.LocalGitDroidColors
import dev.gitdroid.app.ui.theme.LocalType
import dev.gitdroid.app.ui.theme.ThemeMode
import kotlinx.coroutines.launch

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
 * Four tabs, always visible, with a shallow detail stack on top: repository →
 * file, issue or workflow run. Navigation is done by hand rather than with a
 * navigation library because the app has one unusual requirement — **a screen
 * must keep its state when you leave it and come back** — and a remembered
 * state holder gives us that without a framework.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitDroidApp(
    container: AppContainer,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val type = dev.gitdroid.app.ui.theme.LocalType.current
    val context = LocalContext.current

    val session by container.authManager.state.collectAsStateCompat()
    val mcpPort by container.mcpPort.collectAsStateCompat()
    val mcpRunning by container.mcpRunning.collectAsStateCompat()

    var destination by remember { mutableStateOf(TopLevelDestination.REPOS) }
    var repo by remember { mutableStateOf<Repository?>(null) }
    var filePath by remember { mutableStateOf<String?>(null) }
    var issueNumber by remember { mutableStateOf<Int?>(null) }
    var runId by remember { mutableStateOf<Long?>(null) }
    var createRepoOpen by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableStateOf(0L) }

    val downloader = remember { Downloader(context, container.api) }

    // Measure the cache once, off the hot path.
    LaunchedEffect(Unit) {
        cacheSize = container.diskCache.sizeBytes()
    }

    /**
     * The back button pops the detail stack before it pops the app.
     *
     * The stack is shallow — file, issue, run, repository, then the tabs — so
     * it is written out explicitly.
     */
    val inDetail = filePath != null || issueNumber != null || runId != null || repo != null
    BackHandler(enabled = inDetail) {
        when {
            filePath != null -> filePath = null
            issueNumber != null -> issueNumber = null
            runId != null -> runId = null
            else -> repo = null
        }
    }

    when (val current = session) {
        SessionState.Loading -> Box(Modifier.fillMaxSize())

        SessionState.SignedOut -> AuthScreen(
            authManager = container.authManager,
            onSignedIn = { destination = TopLevelDestination.REPOS },
        )

        is SessionState.SignedIn -> Scaffold(
            topBar = {
                // Detail screens carry their own header, so the app bar only
                // belongs to the tabs.
                if (!inDetail) {
                    val refreshing by container.repository.reposRefreshing.collectAsStateCompat()
                    ShellHeader(login = current.login, avatarUrl = current.avatarUrl, syncing = refreshing)
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    TopLevelDestination.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = destination == entry,
                            onClick = {
                                destination = entry
                                repo = null
                                filePath = null
                                issueNumber = null
                                runId = null
                            },
                            icon = {
                                Icon(
                                    imageVector = when (entry) {
                                        TopLevelDestination.REPOS -> Icons.Rounded.Hub
                                        TopLevelDestination.SEARCH -> Icons.Rounded.Search
                                        TopLevelDestination.MCP -> Icons.Rounded.Cable
                                        TopLevelDestination.SETTINGS -> Icons.Rounded.Person
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = { Text(entry.label, style = type.labelSmall) },
                        )
                    }
                }
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            PageBackground(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
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
                            onBack = { repo = null },
                            onOpenFile = { path -> filePath = path },
                            onOpenIssue = { number -> issueNumber = number },
                            onOpenPull = { number -> issueNumber = number },
                            onOpenRun = { id -> runId = id },
                        )

                        destination == TopLevelDestination.REPOS -> ReposScreen(
                            repository = container.repository,
                            onOpenRepo = { repo = it },
                            onCreateRepo = { createRepoOpen = true },
                            onOpenSearch = { destination = TopLevelDestination.SEARCH },
                            onOpenMcp = { destination = TopLevelDestination.MCP },
                        )

                        destination == TopLevelDestination.SEARCH -> SearchScreen(
                            repository = container.repository,
                            onOpenRepo = { repo = it },
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
                                if (enabled) container.enableMcp() else container.disableMcp()
                            },
                            onRotateToken = { container.rotateMcpToken() },
                        )
                    }
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
                        onCreated = { created ->
                            createRepoOpen = false
                            repo = created
                        },
                        onDismiss = { createRepoOpen = false },
                    )
                }
            }
        }
    }
}

/**
 * The app bar for the tab screens.
 *
 * Compact on purpose: the account name and avatar are the only things that
 * earn their space here, plus a sync dot so a background refresh is visible
 * without stealing focus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellHeader(login: String, avatarUrl: String, syncing: Boolean) {
    val type = LocalType.current
    val colors = LocalGitDroidColors.current

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "GitDroid",
                    style = type.title,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (syncing) {
                    Spacer(Modifier.width(8.dp))
                    StatusDot(color = MaterialTheme.colorScheme.primary, pulsing = true)
                }
            }
        },
        actions = {
            Text(
                text = login,
                style = type.labelSmall,
                color = colors.textTertiary,
                maxLines = 1,
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
                Text(
                    text = login.take(1).uppercase(),
                    style = type.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
