package dev.gitdroid.app.core.repo

import dev.gitdroid.app.core.api.CodeSearchResult
import dev.gitdroid.app.core.api.CreateFileRequest
import dev.gitdroid.app.core.api.CreateIssueRequest
import dev.gitdroid.app.core.api.CreatePullRequestRequest
import dev.gitdroid.app.core.api.CreateRepoRequest
import dev.gitdroid.app.core.api.GitHubApi
import dev.gitdroid.app.core.api.GitHubException
import dev.gitdroid.app.core.api.Issue
import dev.gitdroid.app.core.api.IssueComment
import dev.gitdroid.app.core.api.PullRequest
import dev.gitdroid.app.core.api.Release
import dev.gitdroid.app.core.api.Repository
import dev.gitdroid.app.core.api.SearchResult
import dev.gitdroid.app.core.api.UpdateIssueRequest
import dev.gitdroid.app.core.api.UpdateRepoRequest
import dev.gitdroid.app.core.api.User
import dev.gitdroid.app.core.api.Workflow
import dev.gitdroid.app.core.api.WorkflowRun
import dev.gitdroid.app.core.api.WorkflowDispatchRequest
import dev.gitdroid.app.core.cache.DiskCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The in-memory view of the user's GitHub workspace.
 *
 * Every screen reads from these flows. The rules that make the app feel
 * "always on":
 *
 * 1. **Cache first.** A screen shows whatever was cached and *then* refreshes.
 * 2. **Never lose state.** A failed refresh keeps the old data on screen and
 *    surfaces the error as a banner, instead of blanking the list.
 * 3. **One source of truth.** The UI and the MCP server read the same
 *    repository, so an agent and a human never disagree about the repo list.
 */
class GitRepository(
    private val api: GitHubApi,
    private val cache: DiskCache,
    private val scope: CoroutineScope,
) {

    // ── Repositories ────────────────────────────────────────────────────────

    private val _repos = MutableStateFlow<List<Repository>>(emptyList())
    val repos: StateFlow<List<Repository>> = _repos.asStateFlow()

    private val _reposLoading = MutableStateFlow(false)
    val reposLoading: StateFlow<Boolean> = _reposLoading.asStateFlow()

    private val _reposRefreshing = MutableStateFlow(false)
    val reposRefreshing: StateFlow<Boolean> = _reposRefreshing.asStateFlow()

    private val _reposError = MutableStateFlow<String?>(null)
    val reposError: StateFlow<String?> = _reposError.asStateFlow()

    /** The repo the user is currently looking at. */
    private val _currentRepo = MutableStateFlow<Repository?>(null)
    val currentRepo: StateFlow<Repository?> = _currentRepo.asStateFlow()

    /** Recently visited repos, newest first — powers the quick switcher. */
    private val _recentRepos = MutableStateFlow<List<Repository>>(emptyList())
    val recentRepos: StateFlow<List<Repository>> = _recentRepos.asStateFlow()

    /** Repos the user starred inside GitDroid. */
    private val _pinnedRepos = MutableStateFlow<Set<String>>(emptySet())
    val pinnedRepos: StateFlow<Set<String>> = _pinnedRepos.asStateFlow()

    /** The branch selected per repo, remembered across sessions. */
    private val _branchSelection = MutableStateFlow<Map<String, String>>(emptyMap())
    val branchSelection: StateFlow<Map<String, String>> = _branchSelection.asStateFlow()

    /** Loads the cached repo list immediately, then refreshes in the background. */
    fun loadRepos() {
        scope.launch {
            val cached = cache.read<List<Repository>>(KEY_REPOS)
            if (cached != null) {
                _repos.value = cached.value
                _reposLoading.value = false
            } else {
                _reposLoading.value = true
            }
            refreshRepos()
        }
    }

    /** Fetches the repo list from GitHub and updates the cache. */
    suspend fun refreshRepos(): Result<List<Repository>> = runCatching {
        _reposRefreshing.value = true
        _reposError.value = null
        val response = api.listOwnRepos(perPage = 100)
        val merged = response.body
        _repos.value = merged
        cache.write(KEY_REPOS, merged)
        merged
    }.onFailure { error ->
        _reposError.value = (error as? GitHubException)?.displayMessage() ?: error.message
    }.also {
        _reposRefreshing.value = false
        _reposLoading.value = false
    }

    /** Loads the current user's profile. */
    suspend fun loadProfile(): User? = runCatching { api.currentUser() }.getOrNull()

    /** Sets the active repo and remembers it for the quick switcher. */
    fun selectRepo(repo: Repository) {
        _currentRepo.value = repo
        scope.launch {
            val recent = (listOf(repo) + _recentRepos.value.filterNot { it.fullName == repo.fullName })
                .take(MAX_RECENT)
            _recentRepos.value = recent
        }
    }

    /** Records a visit without changing the selected repo. */
    fun noteVisit(repo: Repository) {
        scope.launch {
            val recent = (listOf(repo) + _recentRepos.value.filterNot { it.fullName == repo.fullName })
                .take(MAX_RECENT)
            _recentRepos.value = recent
        }
    }

    /** Pins or unpins a repo in the switcher. */
    fun togglePinned(fullName: String) {
        _pinnedRepos.value = _pinnedRepos.value.toMutableSet().apply {
            if (!add(fullName)) remove(fullName)
        }
        scope.launch { cache.write(KEY_PINNED, _pinnedRepos.value) }
    }

    /** Loads the pinned set from disk. */
    fun loadPinned() {
        scope.launch {
            cache.read<Set<String>>(KEY_PINNED)?.let { _pinnedRepos.value = it.value }
        }
    }

    /** Remembers the selected branch for a repo. */
    fun selectBranch(fullName: String, branch: String) {
        _branchSelection.value = _branchSelection.value + (fullName to branch)
        scope.launch { cache.write(KEY_BRANCHES, _branchSelection.value) }
    }

    /** The branch to use for a repo: the remembered one, or its default. */
    fun branchFor(repo: Repository): String =
        _branchSelection.value[repo.fullName] ?: repo.defaultBranch

    /** Loads the remembered branch selection from disk. */
    fun loadBranchSelection() {
        scope.launch {
            cache.read<Map<String, String>>(KEY_BRANCHES)?.let { _branchSelection.value = it.value }
        }
    }

    // ── Repository operations ───────────────────────────────────────────────

    /** Creates a repository. */
    suspend fun createRepo(request: CreateRepoRequest): Result<Repository> = runCatching {
        val repo = api.createRepository(request)
        _repos.value = listOf(repo) + _repos.value.filterNot { it.id == repo.id }
        cache.write(KEY_REPOS, _repos.value)
        repo
    }

    /** Updates a repository's settings. */
    suspend fun updateRepo(
        owner: String,
        repo: String,
        request: UpdateRepoRequest,
    ): Result<Repository> = runCatching {
        val updated = api.updateRepository(owner, repo, request)
        _repos.value = _repos.value.map { if (it.id == updated.id) updated else it }
        if (_currentRepo.value?.fullName == updated.fullName) _currentRepo.value = updated
        cache.write(KEY_REPOS, _repos.value)
        updated
    }

    /** Deletes a repository. */
    suspend fun deleteRepo(owner: String, repo: String): Result<Unit> = runCatching {
        api.deleteRepository(owner, repo)
        _repos.value = _repos.value.filterNot { it.fullName == "$owner/$repo" }
        _recentRepos.value = _recentRepos.value.filterNot { it.fullName == "$owner/$repo" }
        if (_currentRepo.value?.fullName == "$owner/$repo") _currentRepo.value = null
        cache.write(KEY_REPOS, _repos.value)
    }

    /** Toggles the star on a repo. */
    suspend fun toggleStar(repo: Repository): Result<Boolean> = runCatching {
        val starred = repo.stargazersCount > 0 && _starredByMe.contains(repo.fullName)
        if (starred) {
            api.unstarRepository(repo.ownerLogin, repo.name)
            _starredByMe.remove(repo.fullName)
        } else {
            api.starRepository(repo.ownerLogin, repo.name)
            _starredByMe.add(repo.fullName)
        }
        !starred
    }

    private val _starredByMe = mutableSetOf<String>()

    /** Forks a repository. */
    suspend fun forkRepo(owner: String, repo: String): Result<Repository> =
        runCatching { api.forkRepository(owner, repo) }

    // ── Branches / commits ──────────────────────────────────────────────────

    /** Lists branches, falling back to the cache when the network is down. */
    suspend fun branches(owner: String, repo: String): List<BranchInfo> {
        val key = "branches:$owner/$repo"
        return runCatching {
            val branches = api.listBranches(owner, repo).map { BranchInfo(it.name, it.sha, it.isProtected) }
            cache.write(key, branches)
            branches
        }.getOrElse {
            cache.read<List<BranchInfo>>(key)?.value.orEmpty()
        }
    }

    /** Creates a branch from a starting point. */
    suspend fun createBranch(
        owner: String,
        repo: String,
        branch: String,
        fromSha: String,
    ): Result<Unit> = runCatching { api.createBranch(owner, repo, branch, fromSha) }

    /** Lists commits for a branch. */
    suspend fun commits(owner: String, repo: String, sha: String?): List<CommitInfo> {
        val key = "commits:$owner/$repo:$sha"
        return runCatching {
            val commits = api.listCommits(owner, repo, sha).map {
                CommitInfo(
                    sha = it.sha,
                    message = it.commit.message.lines().firstOrNull().orEmpty(),
                    author = it.commit.author?.name ?: it.author?.login.orEmpty(),
                    avatarUrl = it.author?.avatarUrl.orEmpty(),
                    date = it.commit.author?.date,
                    url = it.htmlUrl,
                )
            }
            cache.write(key, commits)
            commits
        }.getOrElse {
            cache.read<List<CommitInfo>>(key)?.value.orEmpty()
        }
    }

    // ── Issues ──────────────────────────────────────────────────────────────

    /** Lists issues for a repo, falling back to the cache. */
    suspend fun issues(owner: String, repo: String, state: String): List<Issue> {
        val key = "issues:$owner/$repo:$state"
        return runCatching {
            val issues = api.listIssues(owner, repo, state).body
            cache.write(key, issues)
            issues
        }.getOrElse {
            cache.read<List<Issue>>(key)?.value.orEmpty()
        }
    }

    /** Loads one issue plus its comments. */
    suspend fun issueDetail(owner: String, repo: String, number: Int): IssueDetail? = runCatching {
        val issue = api.issue(owner, repo, number)
        val comments = api.listIssueComments(owner, repo, number)
        IssueDetail(issue, comments)
    }.getOrNull()

    /** Creates an issue. */
    suspend fun createIssue(
        owner: String,
        repo: String,
        request: CreateIssueRequest,
    ): Result<Issue> = runCatching { api.createIssue(owner, repo, request) }

    /** Updates an issue (state, title, body, labels). */
    suspend fun updateIssue(
        owner: String,
        repo: String,
        number: Int,
        request: UpdateIssueRequest,
    ): Result<Issue> = runCatching { api.updateIssue(owner, repo, number, request) }

    /** Adds a comment to an issue or pull request. */
    suspend fun comment(
        owner: String,
        repo: String,
        number: Int,
        body: String,
    ): Result<IssueComment> = runCatching {
        api.createIssueComment(owner, repo, number, body)
    }

    // ── Pull requests ───────────────────────────────────────────────────────

    /** Lists pull requests for a repo. */
    suspend fun pullRequests(owner: String, repo: String, state: String): List<PullRequest> {
        val key = "pulls:$owner/$repo:$state"
        return runCatching {
            val pulls = api.listPullRequests(owner, repo, state).body
            cache.write(key, pulls)
            pulls
        }.getOrElse {
            cache.read<List<PullRequest>>(key)?.value.orEmpty()
        }
    }

    /** Loads one pull request. */
    suspend fun pullRequest(owner: String, repo: String, number: Int): PullRequest? =
        runCatching { api.pullRequest(owner, repo, number) }.getOrNull()

    /** Creates a pull request. */
    suspend fun createPullRequest(
        owner: String,
        repo: String,
        request: CreatePullRequestRequest,
    ): Result<PullRequest> = runCatching { api.createPullRequest(owner, repo, request) }

    /** Merges a pull request. */
    suspend fun mergePullRequest(
        owner: String,
        repo: String,
        number: Int,
        method: String = "merge",
    ): Result<Unit> = runCatching {
        api.mergePullRequest(owner, repo, number, mergeMethod = method)
        Unit
    }

    // ── Actions ─────────────────────────────────────────────────────────────

    /** Lists the workflows defined in a repo. */
    suspend fun workflows(owner: String, repo: String): List<Workflow> =
        runCatching { api.listWorkflows(owner, repo) }.getOrElse { emptyList() }

    /** Lists workflow runs. */
    suspend fun workflowRuns(
        owner: String,
        repo: String,
        workflowId: String? = null,
    ): List<WorkflowRun> {
        val key = "runs:$owner/$repo:${workflowId ?: "all"}"
        return runCatching {
            val runs = api.listWorkflowRuns(owner, repo, workflowId).body
            cache.write(key, runs)
            runs
        }.getOrElse {
            cache.read<List<WorkflowRun>>(key)?.value.orEmpty()
        }
    }

    /** Loads one workflow run with its jobs. */
    suspend fun workflowRun(owner: String, repo: String, runId: Long): WorkflowRun? =
        runCatching { api.workflowRun(owner, repo, runId) }.getOrNull()

    /** Re-runs a workflow run. */
    suspend fun rerunRun(owner: String, repo: String, runId: Long): Result<Unit> =
        runCatching { api.rerunWorkflow(owner, repo, runId) }

    /** Cancels a workflow run. */
    suspend fun cancelRun(owner: String, repo: String, runId: Long): Result<Unit> =
        runCatching { api.cancelWorkflowRun(owner, repo, runId) }

    /** Dispatches a workflow manually. */
    suspend fun dispatchWorkflow(
        owner: String,
        repo: String,
        workflowId: String,
        branch: String,
        inputs: Map<String, String> = emptyMap(),
    ): Result<Unit> = runCatching {
        api.dispatchWorkflow(owner, repo, workflowId, WorkflowDispatchRequest(branch, inputs))
    }

    // ── Releases / tags ─────────────────────────────────────────────────────

    /** Lists releases. */
    suspend fun releases(owner: String, repo: String): List<Release> =
        runCatching { api.listReleases(owner, repo) }.getOrElse { emptyList() }

    // ── Search ──────────────────────────────────────────────────────────────

    /** Searches public repositories. */
    suspend fun searchRepositories(
        query: String,
        sort: String? = "stars",
    ): SearchResult<Repository> = runCatching {
        api.searchRepositories(query, sort).body
    }.getOrElse { SearchResult() }

    /** Searches code across GitHub. */
    suspend fun searchCode(query: String): SearchResult<CodeSearchResult> = runCatching {
        api.searchCode(query).body
    }.getOrElse { SearchResult() }

    // ── Files ───────────────────────────────────────────────────────────────

    /**
     * Reads a file's text.
     *
     * The Contents API base64-encodes file bodies; this decodes them.
     */
    suspend fun readFile(owner: String, repo: String, path: String, ref: String?): FileContent? =
        runCatching {
            val element = api.contents(owner, repo, path, ref)
            val obj = dev.gitdroid.app.core.api.Json.obj(element) ?: return null
            val type = dev.gitdroid.app.core.api.Json.string(obj["type"])
            if (type == "dir") return null
            val encoding = dev.gitdroid.app.core.api.Json.string(obj["encoding"])
            val content = dev.gitdroid.app.core.api.Json.string(obj["content"]).orEmpty()
            val decoded = if (encoding == "base64") {
                decodeBase64(content)
            } else {
                content
            }
            FileContent(
                path = dev.gitdroid.app.core.api.Json.string(obj["path"]) ?: path,
                name = dev.gitdroid.app.core.api.Json.string(obj["name"]).orEmpty(),
                sha = dev.gitdroid.app.core.api.Json.string(obj["sha"]).orEmpty(),
                size = dev.gitdroid.app.core.api.Json.long(obj["size"]) ?: 0L,
                encoding = encoding.orEmpty(),
                content = decoded,
                downloadUrl = dev.gitdroid.app.core.api.Json.string(obj["download_url"]).orEmpty(),
                htmlUrl = dev.gitdroid.app.core.api.Json.string(obj["html_url"]).orEmpty(),
                isBinary = !isProbablyText(decoded),
            )
        }.getOrNull()

    /** Writes a file, creating it when it does not exist yet. */
    suspend fun writeFile(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        branch: String?,
        sha: String?,
    ): Result<Unit> = runCatching {
        api.createOrUpdateFile(owner, repo, path, CreateFileRequest(message, content, branch, sha))
        Unit
    }

    /** Deletes a file. */
    suspend fun deleteFile(
        owner: String,
        repo: String,
        path: String,
        message: String,
        sha: String,
        branch: String?,
    ): Result<Unit> = runCatching {
        api.deleteFile(owner, repo, path, message, sha, branch)
    }

    /** Lists a directory's contents. */
    suspend fun listDirectory(
        owner: String,
        repo: String,
        path: String,
        ref: String?,
    ): List<DirEntry> {
        val key = "dir:$owner/$repo:$ref:$path"
        return runCatching {
            val element = api.contents(owner, repo, path, ref)
            val array = dev.gitdroid.app.core.api.Json.arr(element) ?: return@runCatching emptyList()
            val entries = array.mapNotNull { item ->
                val obj = dev.gitdroid.app.core.api.Json.obj(item) ?: return@mapNotNull null
                DirEntry(
                    name = dev.gitdroid.app.core.api.Json.string(obj["name"]).orEmpty(),
                    path = dev.gitdroid.app.core.api.Json.string(obj["path"]).orEmpty(),
                    type = dev.gitdroid.app.core.api.Json.string(obj["type"]).orEmpty(),
                    size = dev.gitdroid.app.core.api.Json.long(obj["size"]) ?: 0L,
                    sha = dev.gitdroid.app.core.api.Json.string(obj["sha"]).orEmpty(),
                )
            }.sortedWith(compareBy({ it.type != "dir" }, { it.name.lowercase() }))
            cache.write(key, entries)
            entries
        }.getOrElse {
            cache.read<List<DirEntry>>(key)?.value.orEmpty()
        }
    }

    /** Loads the README of a repo. */
    suspend fun readme(owner: String, repo: String, ref: String?): FileContent? {
        val key = "readme:$owner/$repo:$ref"
        return runCatching {
            val element = api.contents(owner, repo, "README.md", ref)
            val obj = dev.gitdroid.app.core.api.Json.obj(element) ?: return@runCatching null
            val content = dev.gitdroid.app.core.api.Json.string(obj["content"]).orEmpty()
            val encoding = dev.gitdroid.app.core.api.Json.string(obj["encoding"])
            val decoded = if (encoding == "base64") decodeBase64(content) else content
            FileContent(
                path = dev.gitdroid.app.core.api.Json.string(obj["path"]) ?: "README.md",
                name = "README.md",
                sha = dev.gitdroid.app.core.api.Json.string(obj["sha"]).orEmpty(),
                size = dev.gitdroid.app.core.api.Json.long(obj["size"]) ?: 0L,
                encoding = encoding.orEmpty(),
                content = decoded,
                downloadUrl = dev.gitdroid.app.core.api.Json.string(obj["download_url"]).orEmpty(),
                htmlUrl = dev.gitdroid.app.core.api.Json.string(obj["html_url"]).orEmpty(),
                isBinary = false,
            ).also { cache.write(key, it) }
        }.getOrElse { cache.read<FileContent>(key)?.value }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun decodeBase64(value: String): String = runCatching {
        java.util.Base64.getMimeDecoder().decode(value).toString(Charsets.UTF_8)
    }.getOrElse {
        android.util.Base64.decode(value, android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
    }

    private fun isProbablyText(content: String): Boolean {
        if (content.isEmpty()) return true
        val sample = content.take(4096)
        val suspicious = sample.count { it.code == 0 }
        return suspicious == 0 && sample.any { it.code in 32..126 || it.code == 10 || it.code == 9 }
    }

    companion object {
        private const val KEY_REPOS = "repos"
        private const val KEY_PINNED = "pinned"
        private const val KEY_BRANCHES = "branches-selection"
        private const val MAX_RECENT = 12
    }
}

/** A branch, flattened for the UI. */
@kotlinx.serialization.Serializable
data class BranchInfo(
    val name: String,
    val sha: String = "",
    val isProtected: Boolean = false,
)

/** A commit, flattened for the UI. */
@kotlinx.serialization.Serializable
data class CommitInfo(
    val sha: String,
    val message: String = "",
    val author: String = "",
    val avatarUrl: String = "",
    val date: String? = null,
    val url: String = "",
)

/** An entry inside a directory. */
@kotlinx.serialization.Serializable
data class DirEntry(
    val name: String,
    val path: String,
    val type: String,
    val size: Long = 0,
    val sha: String = "",
) {
    val isDirectory: Boolean get() = type == "dir"
}

/** A file's contents. */
@kotlinx.serialization.Serializable
data class FileContent(
    val path: String,
    val name: String,
    val sha: String = "",
    val size: Long = 0,
    val encoding: String = "",
    val content: String = "",
    val downloadUrl: String = "",
    val htmlUrl: String = "",
    val isBinary: Boolean = false,
)

/** An issue plus its comments. */
data class IssueDetail(
    val issue: Issue,
    val comments: List<IssueComment>,
)
