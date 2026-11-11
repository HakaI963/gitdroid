package dev.gitdroid.app.core.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.serializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin, allocation-light wrapper over the GitHub REST API.
 *
 * The client is deliberately shared and long-lived: connection reuse is what
 * makes the "no reload" promise feel instant, because a warm TLS session
 * answers in a few milliseconds instead of a full handshake.
 */
class GitHubApi(
    private val tokenProvider: suspend () -> String?,
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: HttpUrl = "https://api.github.com/".toHttpUrl(),
) {

    /** Raw response wrapper carrying the headers we care about. */
    data class Response<T>(
        val body: T,
        val etag: String? = null,
        val link: String? = null,
        val status: Int = 200,
    )

    // ── Auth / user ─────────────────────────────────────────────────────────

    suspend fun currentUser(): User = get("user")

    suspend fun user(login: String): User = get("users/$login")

    suspend fun rateLimit(): RateLimit = get("rate_limit")

    // ── Repositories ────────────────────────────────────────────────────────

    suspend fun listOwnRepos(
        page: Int = 1,
        perPage: Int = 100,
        sort: String = "updated",
    ): Response<List<Repository>> = getPaged(
        "user/repos?per_page=$perPage&page=$page&sort=$sort&affiliation=owner,collaborator,organization_member",
    )

    suspend fun listUserRepos(
        login: String,
        page: Int = 1,
        perPage: Int = 100,
        sort: String = "updated",
    ): Response<List<Repository>> = getPaged(
        "users/$login/repos?per_page=$perPage&page=$page&sort=$sort",
    )

    suspend fun listStarredRepos(
        login: String,
        page: Int = 1,
        perPage: Int = 100,
    ): Response<List<Repository>> = getPaged("users/$login/starred?per_page=$perPage&page=$page")

    suspend fun repository(owner: String, repo: String): Repository = get("repos/$owner/$repo")

    suspend fun createRepository(request: CreateRepoRequest): Repository =
        post("user/repos", request)

    suspend fun updateRepository(
        owner: String,
        repo: String,
        request: UpdateRepoRequest,
    ): Repository = patch("repos/$owner/$repo", request)

    suspend fun deleteRepository(owner: String, repo: String) {
        delete<Unit>("repos/$owner/$repo")
    }

    suspend fun forkRepository(owner: String, repo: String): Repository =
        post("repos/$owner/$repo/forks", EmptyBody)

    suspend fun starRepository(owner: String, repo: String) {
        put<Unit>("user/starred/$owner/$repo", EmptyBody)
    }

    suspend fun unstarRepository(owner: String, repo: String) {
        delete<Unit>("user/starred/$owner/$repo")
    }

    suspend fun setTopics(owner: String, repo: String, topics: List<String>) {
        putElement("repos/$owner/$repo/topics", mapOf("names" to topics))
    }

    suspend fun transferRepository(owner: String, repo: String, newOwner: String): Repository =
        post("repos/$owner/$repo/transfer", mapOf("new_owner" to newOwner))

    suspend fun listCollaborators(owner: String, repo: String): List<User> =
        get("repos/$owner/$repo/collaborators")

    // ── Branches / commits ──────────────────────────────────────────────────

    suspend fun listBranches(owner: String, repo: String, perPage: Int = 100): List<Branch> =
        get("repos/$owner/$repo/branches?per_page=$perPage")

    suspend fun createBranch(owner: String, repo: String, branch: String, fromSha: String) {
        post<Unit>(
            "repos/$owner/$repo/git/refs",
            CreateBranchRequest("refs/heads/$branch", fromSha),
        )
    }

    suspend fun listCommits(
        owner: String,
        repo: String,
        sha: String? = null,
        perPage: Int = 50,
    ): List<Commit> {
        val query = buildString {
            append("repos/$owner/$repo/commits?per_page=$perPage")
            if (sha != null) append("&sha=$sha")
        }
        return get(query)
    }

    suspend fun commit(owner: String, repo: String, sha: String): Commit =
        get("repos/$owner/$repo/commits/$sha")

    // ── Files ───────────────────────────────────────────────────────────────

    /**
     * A directory listing, a file's metadata, or a file's encoded text.
     *
     * The Contents API returns three different shapes depending on the target,
     * so the raw [JsonElement] is handed back and interpreted by the caller.
     */
    suspend fun contents(
        owner: String,
        repo: String,
        path: String,
        ref: String? = null,
    ): JsonElement {
        val encoded = path.trim('/').split('/').filter { it.isNotEmpty() }
            .joinToString("/") { encodePathSegment(it) }
        val query = if (ref != null) "?ref=${encodeQuery(ref)}" else ""
        return getElement("repos/$owner/$repo/contents/$encoded$query")
    }

    suspend fun tree(owner: String, repo: String, sha: String, recursive: Boolean = true): GitTree =
        get("repos/$owner/$repo/git/trees/$sha?recursive=$recursive")

    suspend fun createOrUpdateFile(
        owner: String,
        repo: String,
        path: String,
        request: CreateFileRequest,
    ): JsonElement = postElement("repos/$owner/$repo/contents/$path", request)

    suspend fun deleteFile(
        owner: String,
        repo: String,
        path: String,
        message: String,
        sha: String,
        branch: String? = null,
    ) {
        delete<Unit>(
            "repos/$owner/$repo/contents/$path",
            EmptyRequest(commitMessage = message, branch = branch.orEmpty(), sha = sha),
        )
    }

    // ── Issues ──────────────────────────────────────────────────────────────

    suspend fun listIssues(
        owner: String,
        repo: String,
        state: String = "open",
        page: Int = 1,
        perPage: Int = 50,
    ): Response<List<Issue>> = getPaged(
        "repos/$owner/$repo/issues?state=$state&per_page=$perPage&page=$page&sort=updated",
    )

    suspend fun issue(owner: String, repo: String, number: Int): Issue =
        get("repos/$owner/$repo/issues/$number")

    suspend fun createIssue(owner: String, repo: String, request: CreateIssueRequest): Issue =
        post("repos/$owner/$repo/issues", request)

    suspend fun updateIssue(
        owner: String,
        repo: String,
        number: Int,
        request: UpdateIssueRequest,
    ): Issue = patch("repos/$owner/$repo/issues/$number", request)

    suspend fun listIssueComments(owner: String, repo: String, number: Int): List<IssueComment> =
        get("repos/$owner/$repo/issues/$number/comments?per_page=100")

    suspend fun createIssueComment(owner: String, repo: String, number: Int, body: String): IssueComment =
        post("repos/$owner/$repo/issues/$number/comments", CreateCommentRequest(body))

    // ── Pull requests ───────────────────────────────────────────────────────

    suspend fun listPullRequests(
        owner: String,
        repo: String,
        state: String = "open",
        page: Int = 1,
        perPage: Int = 50,
    ): Response<List<PullRequest>> = getPaged(
        "repos/$owner/$repo/pulls?state=$state&per_page=$perPage&page=$page&sort=updated",
    )

    suspend fun pullRequest(owner: String, repo: String, number: Int): PullRequest =
        get("repos/$owner/$repo/pulls/$number")

    suspend fun createPullRequest(
        owner: String,
        repo: String,
        request: CreatePullRequestRequest,
    ): PullRequest = post("repos/$owner/$repo/pulls", request)

    suspend fun mergePullRequest(
        owner: String,
        repo: String,
        number: Int,
        commitMessage: String? = null,
        mergeMethod: String = "merge",
    ): JsonElement = putElement(
        "repos/$owner/$repo/pulls/$number/merge",
        mapOf(
            "commit_message" to (commitMessage ?: ""),
            "merge_method" to mergeMethod,
        ),
    )

    suspend fun listPullRequestFiles(owner: String, repo: String, number: Int): List<JsonObject> =
        get("repos/$owner/$repo/pulls/$number/files?per_page=100")

    // ── Actions ─────────────────────────────────────────────────────────────

    /**
     * GitHub wraps the Actions collections in an object
     * (`{"total_count":n,"workflows":[…]}`) instead of returning a bare
     * array, so they need unwrapping before decoding.
     */
    suspend fun listWorkflows(owner: String, repo: String): List<Workflow> =
        getWrapped("repos/$owner/$repo/actions/workflows?per_page=100", "workflows")

    suspend fun listWorkflowRuns(
        owner: String,
        repo: String,
        workflowId: String? = null,
        status: String? = null,
        page: Int = 1,
        perPage: Int = 30,
    ): List<WorkflowRun> {
        val base = if (workflowId != null) {
            "repos/$owner/$repo/actions/workflows/$workflowId/runs"
        } else {
            "repos/$owner/$repo/actions/runs"
        }
        val query = buildString {
            append("$base?per_page=$perPage&page=$page")
            if (status != null) append("&status=$status")
        }
        return getWrapped(query, "workflow_runs")
    }

    suspend fun workflowRun(owner: String, repo: String, runId: Long): WorkflowRun =
        get("repos/$owner/$repo/actions/runs/$runId")

    suspend fun workflowRunJobs(owner: String, repo: String, runId: Long): List<WorkflowJob> =
        getWrapped("repos/$owner/$repo/actions/runs/$runId/jobs?per_page=100", "jobs")

    /** The artefacts attached to a run — what "download stuff" means for CI. */
    suspend fun workflowRunArtifacts(owner: String, repo: String, runId: Long): List<Artifact> =
        getWrapped("repos/$owner/$repo/actions/runs/$runId/artifacts?per_page=100", "artifacts")

    suspend fun rerunWorkflow(owner: String, repo: String, runId: Long) {
        post<Unit>("repos/$owner/$repo/actions/runs/$runId/rerun", EmptyBody)
    }

    suspend fun rerunFailedJobs(owner: String, repo: String, runId: Long) {
        post<Unit>("repos/$owner/$repo/actions/runs/$runId/rerun-failed-jobs", EmptyBody)
    }

    suspend fun cancelWorkflowRun(owner: String, repo: String, runId: Long) {
        post<Unit>("repos/$owner/$repo/actions/runs/$runId/cancel", EmptyBody)
    }

    suspend fun dispatchWorkflow(
        owner: String,
        repo: String,
        workflowId: String,
        request: WorkflowDispatchRequest,
    ) {
        post<Unit>("repos/$owner/$repo/actions/workflows/$workflowId/dispatches", request)
    }

    // ── Releases / tags ─────────────────────────────────────────────────────

    suspend fun listReleases(owner: String, repo: String, perPage: Int = 30): List<Release> =
        get("repos/$owner/$repo/releases?per_page=$perPage")

    suspend fun latestRelease(owner: String, repo: String): Release =
        get("repos/$owner/$repo/releases/latest")

    suspend fun listTags(owner: String, repo: String, perPage: Int = 30): List<Tag> =
        get("repos/$owner/$repo/tags?per_page=$perPage")

    // ── Search ──────────────────────────────────────────────────────────────

    suspend fun searchRepositories(
        query: String,
        sort: String? = "stars",
        order: String = "desc",
        page: Int = 1,
        perPage: Int = 30,
    ): Response<SearchResult<Repository>> {
        val q = "q=${encodeQuery(query)}&per_page=$perPage&page=$page"
        val suffix = if (sort != null) "&sort=$sort&order=$order" else ""
        return get("search/repositories?$q$suffix")
    }

    suspend fun searchCode(
        query: String,
        page: Int = 1,
        perPage: Int = 30,
    ): Response<SearchResult<CodeSearchResult>> {
        val q = "q=${encodeQuery(query)}&per_page=$perPage&page=$page"
        return get("search/code?$q")
    }

    suspend fun searchIssues(
        query: String,
        page: Int = 1,
        perPage: Int = 30,
    ): Response<SearchResult<Issue>> {
        val q = "q=${encodeQuery(query)}&per_page=$perPage&page=$page"
        return get("search/issues?$q")
    }

    // ── Activity, people, languages ─────────────────────────────────────────

    /** Contributors ranked by commit count, the way github.com lists them. */
    suspend fun listContributors(owner: String, repo: String, perPage: Int = 10): List<Contributor> =
        get("repos/$owner/$repo/contributors?per_page=$perPage")

    /** Bytes of code per language, which is what the language bar renders. */
    suspend fun languageStats(owner: String, repo: String): Map<String, Long> =
        get("repos/$owner/$repo/languages")

    /** The latest commit on a ref — the row header github.com shows per folder. */
    suspend fun latestCommit(owner: String, repo: String, ref: String? = null): Commit {
        val query = if (ref != null) "repos/$owner/$repo/commits?sha=${encodeQuery(ref)}&per_page=1"
        else "repos/$owner/$repo/commits?per_page=1"
        val list: List<Commit> = get(query)
        return list.first()
    }

    /** Open pull requests and issues, counted the way the repo header counts them. */
    suspend fun openCounts(owner: String, repo: String): Pair<Int, Int> {
        val issues: List<Issue> = get("repos/$owner/$repo/issues?state=open&per_page=1")
        val pulls: List<PullRequest> = get("repos/$owner/$repo/pulls?state=open&per_page=100")
        return issues.size to pulls.size
    }

    // ── Notifications ───────────────────────────────────────────────────────

    suspend fun listNotifications(
        all: Boolean = false,
        participating: Boolean = false,
    ): List<Notification> =
        get("notifications?all=$all&participating=$participating&per_page=50")

    /**
     * The HTTP client, so callers that need raw bytes (the downloader) can
     * reuse the same warm connection pool instead of opening a new one.
     */
    fun httpClient(): OkHttpClient = client

    // ── Plumbing ────────────────────────────────────────────────────────────

    suspend fun getElement(path: String): JsonElement = execute(request(path).get().build(), path).body

    suspend inline fun <reified T> get(path: String): T =
        decodeElement(getElement(path))

    /**
     * Decodes a list that GitHub nests inside an object under [key] — the
     * Actions endpoints and a few others do this instead of returning a bare
     * array.
     */
    suspend inline fun <reified T> getWrapped(path: String, key: String): List<T> {
        val element = getElement(path)
        val array = (element as? JsonObject)?.get(key) as? JsonArray
            ?: throw ParseException("Expected a \"$key\" array from $path")
        return GitHubJson.decodeFromJsonElement(
            kotlinx.serialization.builtins.ListSerializer(serializer<T>()),
            array,
        )
    }

    suspend inline fun <reified T> getPaged(path: String): Response<List<T>> {
        val raw = execute(request(path).get().build(), path)
        val listSerializer = kotlinx.serialization.builtins.ListSerializer(serializer<T>())
        val body = GitHubJson.decodeFromJsonElement(listSerializer, raw.body)
        return Response(body = body, etag = raw.etag, link = raw.link, status = raw.status)
    }

    suspend inline fun <reified T> post(path: String, body: Any): T =
        decodeElement(postElement(path, body))

    suspend fun postElement(path: String, body: Any): JsonElement =
        execute(request(path).post(jsonBody(body)).build(), path).body

    suspend inline fun <reified T> put(path: String, body: Any): T =
        decodeElement(putElement(path, body))

    suspend fun putElement(path: String, body: Any): JsonElement =
        execute(request(path).put(jsonBody(body)).build(), path).body

    suspend inline fun <reified T> patch(path: String, body: Any): T =
        decodeElement(execute(request(path).patch(jsonBody(body)).build(), path).body)

    suspend inline fun <reified T> delete(path: String, payload: Any? = null): T {
        val raw = if (payload != null) {
            execute(request(path).delete(jsonBody(payload)).build(), path).body
        } else {
            execute(request(path).delete().build(), path).body
        }
        @Suppress("UNCHECKED_CAST")
        return if (raw is JsonNull) Unit as T else decodeElement(raw)
    }

    inline fun <reified T> decodeElement(element: JsonElement): T =
        GitHubJson.decodeFromJsonElement(serializer<T>(), element)

    /** Serializes [body] into the JSON payload GitHub expects. */
    fun jsonBody(body: Any): RequestBody {
        val json: String = when (body) {
            is String -> body
            is Map<*, *> -> buildJsonObject {
                body.forEach { (k, v) ->
                    when (v) {
                        null -> Unit
                        is String -> put(k.toString(), JsonPrimitive(v))
                        is Boolean -> put(k.toString(), JsonPrimitive(v))
                        is Int -> put(k.toString(), JsonPrimitive(v))
                        is Long -> put(k.toString(), JsonPrimitive(v))
                        is List<*> -> put(
                            k.toString(),
                            buildJsonArray {
                                v.forEach { item ->
                                    when (item) {
                                        is String -> add(JsonPrimitive(item))
                                        is Boolean -> add(JsonPrimitive(item))
                                        is Int -> add(JsonPrimitive(item))
                                        is Long -> add(JsonPrimitive(item))
                                        else -> Unit
                                    }
                                }
                            },
                        )

                        else -> put(k.toString(), JsonPrimitive(v.toString()))
                    }
                }
            }.toString()

            else -> GitHubJson.encodeToString(serializer(body::class.java), body)
        }
        return json.toRequestBody(JSON_MEDIA_TYPE)
    }

    suspend fun execute(request: Request, path: String): Response<JsonElement> =
        withContext(Dispatchers.IO) {
            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw GitHubException(0, e.message ?: "Network error")
            }
            response.use {
                val bodyText = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    val message = extractMessage(bodyText) ?: "HTTP ${it.code}"
                    throw GitHubException(it.code, message, extractDocUrl(bodyText), bodyText)
                }
                val element = if (bodyText.isBlank()) {
                    JsonNull
                } else {
                    try {
                        GitHubJson.parseToJsonElement(bodyText)
                    } catch (e: Exception) {
                        throw ParseException("Malformed response from $path: ${e.message}")
                    }
                }
                Response(
                    body = element,
                    etag = it.header("ETag"),
                    link = it.header("Link"),
                    status = it.code,
                )
            }
        }

    private fun extractMessage(body: String): String? = runCatching {
        Json.string(Json.obj(GitHubJson.parseToJsonElement(body))?.get("message"))
    }.getOrNull()

    private fun extractDocUrl(body: String): String? = runCatching {
        Json.string(Json.obj(GitHubJson.parseToJsonElement(body))?.get("documentation_url"))
    }.getOrNull()

    suspend fun request(path: String): Request.Builder {
        val rawPath = path.substringBefore('?')
        val rawQuery = path.substringAfter('?', "")
        val url = baseUrl.newBuilder().apply {
            rawPath.trim('/').split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) }
            rawQuery.split('&').filter { it.isNotBlank() }.forEach { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0) {
                    addQueryParameter(pair.substring(0, idx), pair.substring(idx + 1))
                } else {
                    addQueryParameter(pair, "")
                }
            }
        }.build()

        return Request.Builder().url(url).apply {
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            header("User-Agent", "GitDroid/1.0 (Android)")
            tokenProvider()?.takeIf { it.isNotBlank() }?.let {
                header("Authorization", "Bearer $it")
            }
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val EmptyBody = emptyMap<String, String>()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        fun encodePathSegment(value: String): String = value

        fun encodeQuery(value: String): String = value
    }
}

/** One hit from the code search endpoint. */
@kotlinx.serialization.Serializable
data class CodeSearchResult(
    val name: String = "",
    val path: String = "",
    @kotlinx.serialization.SerialName("sha") val sha: String = "",
    @kotlinx.serialization.SerialName("html_url") val htmlUrl: String = "",
    @kotlinx.serialization.SerialName("git_url") val gitUrl: String = "",
    @kotlinx.serialization.SerialName("repository") val repository: Repository? = null,
)
