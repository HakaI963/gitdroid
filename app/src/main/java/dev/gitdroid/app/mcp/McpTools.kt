package dev.gitdroid.app.mcp

import dev.gitdroid.app.core.api.CreateFileRequest
import dev.gitdroid.app.core.api.CreateIssueRequest
import dev.gitdroid.app.core.api.CreatePullRequestRequest
import dev.gitdroid.app.core.api.CreateRepoRequest
import dev.gitdroid.app.core.api.GitHubApi
import dev.gitdroid.app.core.api.GitHubException
import dev.gitdroid.app.core.api.GitHubJson
import dev.gitdroid.app.core.api.UpdateIssueRequest
import dev.gitdroid.app.core.api.UpdateRepoRequest
import dev.gitdroid.app.core.api.WorkflowDispatchRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * The GitHub tools GitDroid exposes over MCP.
 *
 * The tool names deliberately mirror the official GitHub MCP server, so an
 * agent that already knows how to drive GitHub needs no new instructions —
 * it can point at GitDroid and keep working.
 */
class McpTools(
    private val api: GitHubApi,
) {

    /**
     * The registry of every tool this server exposes.
     *
     * Built lazily because the individual definitions are declared further
     * down; the list is assembled once the object is fully constructed.
     */
    val tools: List<ToolDefinition> by lazy {
        listOf(
            searchRepositories,
            searchCode,
            getFileContents,
            createOrUpdateFile,
            deleteFile,
            listRepositories,
            getRepository,
            createRepository,
            updateRepository,
            deleteRepository,
            forkRepository,
            listBranches,
            createBranch,
            listCommits,
            listIssues,
            createIssue,
            updateIssue,
            listPullRequests,
            createPullRequest,
            mergePullRequest,
            listWorkflows,
            listWorkflowRuns,
            rerunWorkflow,
            cancelWorkflowRun,
            dispatchWorkflow,
            listReleases,
            listTags,
            getRepositoryTree,
        )
    }

    private fun tool(
        name: String,
        description: String,
        properties: Map<String, ToolProperty>,
        required: List<String> = emptyList(),
    ) = ToolDefinition(
        name = name,
        description = description,
        inputSchema = ToolSchema(
            type = "object",
            properties = properties,
            required = required,
            additionalProperties = false,
        ),
    )

    private fun str(description: String, enum: List<String>? = null) =
        ToolProperty(type = "string", description = description, enum = enum)

    private fun int(description: String) = ToolProperty(type = "integer", description = description)

    private fun bool(description: String) = ToolProperty(type = "boolean", description = description)

    // ── Tool definitions ────────────────────────────────────────────────────

    private val searchRepositories = tool(
        name = "search_repositories",
        description = "Search GitHub repositories. Supports the same qualifiers as the GitHub web " +
            "search, e.g. `language:kotlin stars:>100 topic:android`.",
        properties = mapOf(
            "query" to str("The search query."),
            "sort" to str("Sort key.", enum = listOf("stars", "forks", "help-wanted-issues", "updated")),
            "page" to int("1-based page number."),
            "perPage" to int("Results per page (max 100)."),
        ),
        required = listOf("query"),
    )

    private val searchCode = tool(
        name = "search_code",
        description = "Search code across GitHub. Use `repo:owner/name` to scope it to one repository.",
        properties = mapOf(
            "query" to str("The code search query."),
            "page" to int("1-based page number."),
            "perPage" to int("Results per page (max 100)."),
        ),
        required = listOf("query"),
    )

    private val getFileContents = tool(
        name = "get_file_contents",
        description = "Get the contents of a file or a directory in a repository. Directories return " +
            "a listing; files return their decoded text.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "path" to str("Path inside the repository. Use an empty string for the root."),
            "ref" to str("Branch, tag or commit SHA. Defaults to the default branch."),
        ),
        required = listOf("owner", "repo"),
    )

    private val createOrUpdateFile = tool(
        name = "create_or_update_file",
        description = "Create a new file or update an existing one, committing the change to a branch. " +
            "To update an existing file, pass its current `sha`.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "path" to str("Path of the file inside the repository."),
            "content" to str("The full new contents of the file."),
            "message" to str("The commit message."),
            "branch" to str("Branch to commit to. Defaults to the default branch."),
            "sha" to str("The blob SHA of the file being replaced. Required when updating."),
        ),
        required = listOf("owner", "repo", "path", "content", "message"),
    )

    private val deleteFile = tool(
        name = "delete_file",
        description = "Delete a file from a repository.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "path" to str("Path of the file to delete."),
            "message" to str("The commit message."),
            "branch" to str("Branch to commit to."),
            "sha" to str("The blob SHA of the file being deleted."),
        ),
        required = listOf("owner", "repo", "path", "message", "sha"),
    )

    private val listRepositories = tool(
        name = "list_repositories",
        description = "List the repositories the signed-in user has access to, newest activity first.",
        properties = mapOf(
            "page" to int("1-based page number."),
            "perPage" to int("Results per page (max 100)."),
            "sort" to str("Sort key.", enum = listOf("created", "updated", "pushed", "full_name")),
        ),
    )

    private val getRepository = tool(
        name = "get_repository",
        description = "Get metadata about a single repository.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
        ),
        required = listOf("owner", "repo"),
    )

    private val createRepository = tool(
        name = "create_repository",
        description = "Create a new repository for the signed-in user.",
        properties = mapOf(
            "name" to str("The repository name."),
            "description" to str("A short description."),
            "private" to bool("Create it as a private repository."),
            "autoInit" to bool("Initialise it with a README."),
            "hasIssues" to bool("Enable the issue tracker."),
            "hasWiki" to bool("Enable the wiki."),
        ),
        required = listOf("name"),
    )

    private val updateRepository = tool(
        name = "update_repository",
        description = "Change a repository's settings: name, description, visibility, topics, default " +
            "branch, or archive it.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "name" to str("A new name for the repository."),
            "description" to str("A new description."),
            "private" to bool("Set the visibility."),
            "archived" to bool("Archive or unarchive the repository."),
            "defaultBranch" to str("The new default branch."),
            "topics" to ToolProperty(type = "array", description = "The topic list."),
            "hasIssues" to bool("Enable or disable the issue tracker."),
            "hasWiki" to bool("Enable or disable the wiki."),
        ),
        required = listOf("owner", "repo"),
    )

    private val deleteRepository = tool(
        name = "delete_repository",
        description = "Delete a repository. This is irreversible.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
        ),
        required = listOf("owner", "repo"),
    )

    private val forkRepository = tool(
        name = "fork_repository",
        description = "Fork a repository into the signed-in user's account.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "organization" to str("Fork into this organization instead of the personal account."),
        ),
        required = listOf("owner", "repo"),
    )

    private val listBranches = tool(
        name = "list_branches",
        description = "List the branches of a repository.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "perPage" to int("Results per page."),
        ),
        required = listOf("owner", "repo"),
    )

    private val createBranch = tool(
        name = "create_branch",
        description = "Create a branch in a repository from a given commit SHA.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "branch" to str("The name of the new branch."),
            "fromSha" to str("The commit SHA to branch from. Defaults to the default branch head."),
        ),
        required = listOf("owner", "repo", "branch"),
    )

    private val listCommits = tool(
        name = "list_commits",
        description = "List commits on a branch or ref.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "sha" to str("Branch, tag or commit SHA. Defaults to the default branch."),
            "perPage" to int("Results per page."),
        ),
        required = listOf("owner", "repo"),
    )

    private val listIssues = tool(
        name = "list_issues",
        description = "List the issues of a repository.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "state" to str("Which issues to return.", enum = listOf("open", "closed", "all")),
            "page" to int("1-based page number."),
            "perPage" to int("Results per page."),
        ),
        required = listOf("owner", "repo"),
    )

    private val createIssue = tool(
        name = "create_issue",
        description = "Create an issue in a repository.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "title" to str("The issue title."),
            "body" to str("The issue body, in Markdown."),
            "labels" to ToolProperty(type = "array", description = "Labels to apply."),
            "assignees" to ToolProperty(type = "array", description = "Logins to assign."),
        ),
        required = listOf("owner", "repo", "title"),
    )

    private val updateIssue = tool(
        name = "update_issue",
        description = "Update an issue: change its title, body, state, labels or assignees.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "issueNumber" to int("The issue number."),
            "title" to str("A new title."),
            "body" to str("A new body."),
            "state" to str("The new state.", enum = listOf("open", "closed")),
            "stateReason" to str("Why the issue was closed.", enum = listOf("completed", "not_planned", "reopened")),
            "labels" to ToolProperty(type = "array", description = "The new label list."),
            "assignees" to ToolProperty(type = "array", description = "The new assignee list."),
        ),
        required = listOf("owner", "repo", "issueNumber"),
    )

    private val listPullRequests = tool(
        name = "list_pull_requests",
        description = "List the pull requests of a repository.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "state" to str("Which pull requests to return.", enum = listOf("open", "closed", "all")),
            "page" to int("1-based page number."),
            "perPage" to int("Results per page."),
        ),
        required = listOf("owner", "repo"),
    )

    private val createPullRequest = tool(
        name = "create_pull_request",
        description = "Open a pull request.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "title" to str("The pull request title."),
            "body" to str("The pull request body, in Markdown."),
            "head" to str("The branch containing the changes."),
            "base" to str("The branch to merge into."),
            "draft" to bool("Open it as a draft."),
            "maintainerCanModify" to bool("Allow maintainers to edit the branch."),
        ),
        required = listOf("owner", "repo", "title", "head", "base"),
    )

    private val mergePullRequest = tool(
        name = "merge_pull_request",
        description = "Merge a pull request.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "pullNumber" to int("The pull request number."),
            "commitMessage" to str("The merge commit message."),
            "mergeMethod" to str("How to merge.", enum = listOf("merge", "squash", "rebase")),
        ),
        required = listOf("owner", "repo", "pullNumber"),
    )

    private val listWorkflows = tool(
        name = "list_workflows",
        description = "List the GitHub Actions workflows of a repository.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
        ),
        required = listOf("owner", "repo"),
    )

    private val listWorkflowRuns = tool(
        name = "list_workflow_runs",
        description = "List workflow runs, optionally scoped to one workflow.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "workflowId" to str("The workflow file name or numeric id."),
            "status" to str("Filter by status.", enum = listOf("queued", "in_progress", "completed", "waiting")),
            "page" to int("1-based page number."),
            "perPage" to int("Results per page."),
        ),
        required = listOf("owner", "repo"),
    )

    private val rerunWorkflow = tool(
        name = "rerun_workflow",
        description = "Re-run a workflow run, optionally only the failed jobs.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "runId" to int("The workflow run id."),
            "failedOnly" to bool("Re-run only the jobs that failed."),
        ),
        required = listOf("owner", "repo", "runId"),
    )

    private val cancelWorkflowRun = tool(
        name = "cancel_workflow_run",
        description = "Cancel an in-progress workflow run.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "runId" to int("The workflow run id."),
        ),
        required = listOf("owner", "repo", "runId"),
    )

    private val dispatchWorkflow = tool(
        name = "dispatch_workflow",
        description = "Trigger a workflow_dispatch workflow manually.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "workflowId" to str("The workflow file name or numeric id."),
            "ref" to str("The branch or tag to run on."),
            "inputs" to ToolProperty(type = "object", description = "Workflow inputs, as key/value pairs."),
        ),
        required = listOf("owner", "repo", "workflowId", "ref"),
    )

    private val listReleases = tool(
        name = "list_releases",
        description = "List the releases of a repository.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "perPage" to int("Results per page."),
        ),
        required = listOf("owner", "repo"),
    )

    private val listTags = tool(
        name = "list_tags",
        description = "List the tags of a repository.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "perPage" to int("Results per page."),
        ),
        required = listOf("owner", "repo"),
    )

    private val getRepositoryTree = tool(
        name = "get_repository_tree",
        description = "Get the full file tree of a repository in one call.",
        properties = mapOf(
            "owner" to str("Repository owner."),
            "repo" to str("Repository name."),
            "recursive" to bool("Include the whole tree, not just the top level."),
            "sha" to str("The tree SHA or ref. Defaults to the default branch."),
        ),
        required = listOf("owner", "repo"),
    )

    // ── Dispatch ────────────────────────────────────────────────────────────

    /**
     * Runs the tool named [name] with the given [arguments].
     *
     * Errors are returned as a normal result with `isError = true` rather
     * than as a protocol error, which is what the MCP specification asks
     * for: the model should see the failure and be able to correct itself.
     */
    suspend fun call(name: String, arguments: JsonObject): CallToolResult = try {
        val definition = tools.firstOrNull { it.name == name }
            ?: return errorResult("Unknown tool: $name")

        // Report every missing argument at once. Reporting only the first
        // one would make the agent fix its arguments one round trip at a
        // time, which is exactly the kind of friction an MCP server should
        // not have.
        val missing = definition.inputSchema.required.filter { arguments.string(it).isNullOrBlank() }
        if (missing.isNotEmpty()) {
            val list = missing.joinToString(", ") { "`$it`" }
            errorResult(
                if (missing.size == 1) {
                    "`${missing.first()}` is required"
                } else {
                    "Missing required arguments: $list"
                },
            )
        } else {
            dispatch(name, arguments)
        }
    } catch (e: GitHubException) {
        errorResult(e.displayMessage())
    } catch (e: Exception) {
        errorResult(e.message ?: "The tool failed")
    }

    /**
     * The tools this server can actually run, keyed by name.
     *
     * A map rather than a `when` so the registry and the implementation are
     * checked against each other by construction — a tool that is advertised
     * but not wired up is the worst failure an MCP server can have, because
     * every call to it looks like a typo in the agent's prompt.
     */
    private val handlers: Map<String, suspend (JsonObject) -> CallToolResult> by lazy {
        mapOf(
            "search_repositories" to ::searchRepositories,
            "search_code" to ::searchCode,
            "get_file_contents" to ::getFileContents,
            "create_or_update_file" to ::createOrUpdateFile,
            "delete_file" to ::deleteFile,
            "list_repositories" to ::listRepositories,
            "get_repository" to ::getRepository,
            "create_repository" to ::createRepository,
            "update_repository" to ::updateRepository,
            "delete_repository" to ::deleteRepository,
            "fork_repository" to ::forkRepository,
            "list_branches" to ::listBranches,
            "create_branch" to ::createBranch,
            "list_commits" to ::listCommits,
            "list_issues" to ::listIssues,
            "create_issue" to ::createIssue,
            "update_issue" to ::updateIssue,
            "list_pull_requests" to ::listPullRequests,
            "create_pull_request" to ::createPullRequest,
            "merge_pull_request" to ::mergePullRequest,
            "list_workflows" to ::listWorkflows,
            "list_workflow_runs" to ::listWorkflowRuns,
            "rerun_workflow" to ::rerunWorkflow,
            "cancel_workflow_run" to ::cancelWorkflowRun,
            "dispatch_workflow" to ::dispatchWorkflow,
            "list_releases" to ::listReleases,
            "list_tags" to ::listTags,
            "get_repository_tree" to ::getRepositoryTree,
        )
    }

    /** The names this server can dispatch — used by the drift test. */
    fun dispatchableNames(): Set<String> = handlers.keys

    private suspend fun dispatch(name: String, arguments: JsonObject): CallToolResult =
        handlers[name]?.invoke(arguments) ?: errorResult("Unknown tool: $name")

    // ── Implementations ─────────────────────────────────────────────────────

    private suspend fun searchRepositories(args: JsonObject): CallToolResult {
        val query = args.string("query") ?: return errorResult("`query` is required")
        val sort = args.string("sort")
        val result = api.searchRepositories(
            query = query,
            sort = sort,
            page = args.int("page") ?: 1,
            perPage = args.int("perPage") ?: 30,
        ).body

        val payload = buildJsonObject {
            put("total_count", result.totalCount)
            putJsonArray("items") {
                result.items.forEach { repo ->
                    add(
                        buildJsonObject {
                            put("full_name", repo.fullName)
                            put("description", repo.description ?: "")
                            put("private", repo.private)
                            put("stars", repo.stargazersCount)
                            put("forks", repo.forksCount)
                            put("language", repo.language ?: "")
                            put("default_branch", repo.defaultBranch)
                            put("html_url", repo.htmlUrl)
                            put("updated_at", repo.updatedAt ?: "")
                        },
                    )
                }
            }
        }
        return jsonResult(payload)
    }

    private suspend fun searchCode(args: JsonObject): CallToolResult {
        val query = args.string("query") ?: return errorResult("`query` is required")
        val result = api.searchCode(
            query = query,
            page = args.int("page") ?: 1,
            perPage = args.int("perPage") ?: 30,
        ).body

        val payload = buildJsonObject {
            put("total_count", result.totalCount)
            putJsonArray("items") {
                result.items.forEach { hit ->
                    add(
                        buildJsonObject {
                            put("name", hit.name)
                            put("path", hit.path)
                            put("repository", hit.repository?.fullName ?: "")
                            put("html_url", hit.htmlUrl)
                        },
                    )
                }
            }
        }
        return jsonResult(payload)
    }

    private suspend fun getFileContents(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val path = args.string("path").orEmpty()
        val ref = args.string("ref")

        val element = api.contents(owner, repo, path, ref)
        val array = dev.gitdroid.app.core.api.Json.arr(element)
        if (array != null) {
            val payload = buildJsonObject {
                put("type", "directory")
                putJsonArray("items") {
                    array.forEach { item ->
                        val obj = dev.gitdroid.app.core.api.Json.obj(item) ?: return@forEach
                        add(
                            buildJsonObject {
                                put("name", dev.gitdroid.app.core.api.Json.string(obj["name"]).orEmpty())
                                put("path", dev.gitdroid.app.core.api.Json.string(obj["path"]).orEmpty())
                                put("type", dev.gitdroid.app.core.api.Json.string(obj["type"]).orEmpty())
                                put("size", dev.gitdroid.app.core.api.Json.long(obj["size"]) ?: 0L)
                            },
                        )
                    }
                }
            }
            return jsonResult(payload)
        }

        val obj = dev.gitdroid.app.core.api.Json.obj(element)
            ?: return errorResult("The path could not be read")
        val encoding = dev.gitdroid.app.core.api.Json.string(obj["encoding"])
        val raw = dev.gitdroid.app.core.api.Json.string(obj["content"]).orEmpty()
        val decoded = if (encoding == "base64") {
            runCatching {
                java.util.Base64.getMimeDecoder().decode(raw).toString(Charsets.UTF_8)
            }.getOrElse { raw }
        } else {
            raw
        }

        val payload = buildJsonObject {
            put("type", "file")
            put("path", dev.gitdroid.app.core.api.Json.string(obj["path"]).orEmpty())
            put("sha", dev.gitdroid.app.core.api.Json.string(obj["sha"]).orEmpty())
            put("size", dev.gitdroid.app.core.api.Json.long(obj["size"]) ?: 0L)
            put("content", decoded)
        }
        return jsonResult(payload)
    }

    private suspend fun createOrUpdateFile(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val path = args.string("path") ?: return errorResult("`path` is required")
        val content = args.string("content") ?: return errorResult("`content` is required")
        val message = args.string("message") ?: "Update $path"

        val result = api.createOrUpdateFile(
            owner,
            repo,
            path,
            CreateFileRequest(
                message = message,
                content = content,
                branch = args.string("branch"),
                sha = args.string("sha"),
            ),
        )
        val obj = dev.gitdroid.app.core.api.Json.obj(result)
        val payload = buildJsonObject {
            put("path", dev.gitdroid.app.core.api.Json.string(obj?.get("path")).orEmpty())
            put("sha", dev.gitdroid.app.core.api.Json.string(obj?.get("sha")).orEmpty())
            put("commit_url", dev.gitdroid.app.core.api.Json.string(obj?.get("commit")).orEmpty())
        }
        return jsonResult(payload)
    }

    private suspend fun deleteFile(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val path = args.string("path") ?: return errorResult("`path` is required")
        val sha = args.string("sha") ?: return errorResult("`sha` is required")
        api.deleteFile(
            owner,
            repo,
            path,
            args.string("message") ?: "Delete $path",
            sha,
            args.string("branch"),
        )
        return jsonResult(buildJsonObject { put("deleted", true); put("path", path) })
    }

    private suspend fun listRepositories(args: JsonObject): CallToolResult {
        val response = api.listOwnRepos(
            page = args.int("page") ?: 1,
            perPage = args.int("perPage") ?: 100,
            sort = args.string("sort") ?: "updated",
        )
        val payload = buildJsonObject {
            putJsonArray("repositories") {
                response.body.forEach { repo ->
                    add(
                        buildJsonObject {
                            put("full_name", repo.fullName)
                            put("description", repo.description ?: "")
                            put("private", repo.private)
                            put("stars", repo.stargazersCount)
                            put("language", repo.language ?: "")
                            put("default_branch", repo.defaultBranch)
                            put("updated_at", repo.updatedAt ?: "")
                        },
                    )
                }
            }
        }
        return jsonResult(payload)
    }

    private suspend fun getRepository(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val r = api.repository(owner, repo)
        val payload = buildJsonObject {
            put("full_name", r.fullName)
            put("description", r.description ?: "")
            put("private", r.private)
            put("archived", r.archived)
            put("stars", r.stargazersCount)
            put("forks", r.forksCount)
            put("open_issues", r.openIssuesCount)
            put("language", r.language ?: "")
            put("default_branch", r.defaultBranch)
            put("size_kb", r.size)
            put("topics", r.topics.joinToString(","))
            put("html_url", r.htmlUrl)
            put("updated_at", r.updatedAt ?: "")
        }
        return jsonResult(payload)
    }

    private suspend fun createRepository(args: JsonObject): CallToolResult {
        val name = args.string("name") ?: return errorResult("`name` is required")
        val repo = api.createRepository(
            CreateRepoRequest(
                name = name,
                description = args.string("description"),
                private = args.bool("private") ?: false,
                autoInit = args.bool("autoInit") ?: true,
                hasIssues = args.bool("hasIssues") ?: true,
                hasWiki = args.bool("hasWiki") ?: true,
            ),
        )
        return jsonResult(
            buildJsonObject {
                put("full_name", repo.fullName)
                put("html_url", repo.htmlUrl)
                put("private", repo.private)
                put("default_branch", repo.defaultBranch)
            },
        )
    }

    private suspend fun updateRepository(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val topics = args.stringList("topics")
        val updated = api.updateRepository(
            owner,
            repo,
            UpdateRepoRequest(
                name = args.string("name"),
                description = args.string("description"),
                private = args.bool("private"),
                archived = args.bool("archived"),
                defaultBranch = args.string("defaultBranch"),
                topics = topics,
                hasIssues = args.bool("hasIssues"),
                hasWiki = args.bool("hasWiki"),
            ),
        )
        return jsonResult(
            buildJsonObject {
                put("full_name", updated.fullName)
                put("description", updated.description ?: "")
                put("private", updated.private)
                put("archived", updated.archived)
                put("default_branch", updated.defaultBranch)
                put("topics", updated.topics.joinToString(","))
            },
        )
    }

    private suspend fun deleteRepository(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        api.deleteRepository(owner, repo)
        return jsonResult(buildJsonObject { put("deleted", true); put("full_name", "$owner/$repo") })
    }

    private suspend fun forkRepository(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val forked = api.forkRepository(owner, repo)
        return jsonResult(
            buildJsonObject {
                put("full_name", forked.fullName)
                put("html_url", forked.htmlUrl)
            },
        )
    }

    private suspend fun listBranches(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val branches = api.listBranches(owner, repo)
        val payload = buildJsonObject {
            putJsonArray("branches") {
                branches.forEach { branch ->
                    add(
                        buildJsonObject {
                            put("name", branch.name)
                            put("sha", branch.sha)
                            put("protected", branch.isProtected)
                        },
                    )
                }
            }
        }
        return jsonResult(payload)
    }

    private suspend fun createBranch(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val branch = args.string("branch") ?: return errorResult("`branch` is required")
        val fromSha = args.string("fromSha")
            ?: api.listBranches(owner, repo).firstOrNull()?.sha
            ?: return errorResult("Could not determine the starting commit")
        api.createBranch(owner, repo, branch, fromSha)
        return jsonResult(buildJsonObject { put("branch", branch); put("sha", fromSha) })
    }

    private suspend fun listCommits(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val commits = api.listCommits(
            owner,
            repo,
            args.string("sha"),
            args.int("perPage") ?: 30,
        )
        val payload = buildJsonObject {
            putJsonArray("commits") {
                commits.forEach { commit ->
                    add(
                        buildJsonObject {
                            put("sha", commit.sha)
                            put("message", commit.commit.message)
                            put("author", commit.commit.author?.name ?: commit.author?.login ?: "")
                            put("date", commit.commit.author?.date ?: "")
                            put("html_url", commit.htmlUrl)
                        },
                    )
                }
            }
        }
        return jsonResult(payload)
    }

    private suspend fun listIssues(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val issues = api.listIssues(
            owner,
            repo,
            args.string("state") ?: "open",
            args.int("page") ?: 1,
            args.int("perPage") ?: 50,
        ).body
        val payload = buildJsonObject {
            putJsonArray("issues") {
                issues.forEach { issue ->
                    add(
                        buildJsonObject {
                            put("number", issue.number)
                            put("title", issue.title)
                            put("state", issue.state)
                            put("comments", issue.comments)
                            put("author", issue.user?.login ?: "")
                            put("created_at", issue.createdAt ?: "")
                            put("labels", issue.labels.joinToString(",") { it.name })
                            put("html_url", issue.htmlUrl)
                        },
                    )
                }
            }
        }
        return jsonResult(payload)
    }

    private suspend fun createIssue(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val title = args.string("title") ?: return errorResult("`title` is required")
        val issue = api.createIssue(
            owner,
            repo,
            CreateIssueRequest(
                title = title,
                body = args.string("body"),
                labels = args.stringList("labels"),
                assignees = args.stringList("assignees"),
            ),
        )
        return jsonResult(
            buildJsonObject {
                put("number", issue.number)
                put("title", issue.title)
                put("html_url", issue.htmlUrl)
            },
        )
    }

    private suspend fun updateIssue(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val number = args.int("issueNumber") ?: return errorResult("`issueNumber` is required")
        val issue = api.updateIssue(
            owner,
            repo,
            number,
            UpdateIssueRequest(
                title = args.string("title"),
                body = args.string("body"),
                state = args.string("state"),
                stateReason = args.string("stateReason"),
                labels = args.stringList("labels"),
                assignees = args.stringList("assignees"),
            ),
        )
        return jsonResult(
            buildJsonObject {
                put("number", issue.number)
                put("state", issue.state)
                put("title", issue.title)
                put("html_url", issue.htmlUrl)
            },
        )
    }

    private suspend fun listPullRequests(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val pulls = api.listPullRequests(
            owner,
            repo,
            args.string("state") ?: "open",
            args.int("page") ?: 1,
            args.int("perPage") ?: 50,
        ).body
        val payload = buildJsonObject {
            putJsonArray("pull_requests") {
                pulls.forEach { pull ->
                    add(
                        buildJsonObject {
                            put("number", pull.number)
                            put("title", pull.title)
                            put("state", pull.state)
                            put("draft", pull.draft)
                            put("head", pull.head.ref)
                            put("base", pull.base.ref)
                            put("author", pull.user?.login ?: "")
                            put("created_at", pull.createdAt ?: "")
                            put("html_url", pull.htmlUrl)
                        },
                    )
                }
            }
        }
        return jsonResult(payload)
    }

    private suspend fun createPullRequest(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val title = args.string("title") ?: return errorResult("`title` is required")
        val head = args.string("head") ?: return errorResult("`head` is required")
        val base = args.string("base") ?: return errorResult("`base` is required")
        val pull = api.createPullRequest(
            owner,
            repo,
            CreatePullRequestRequest(
                title = title,
                head = head,
                base = base,
                body = args.string("body"),
                draft = args.bool("draft") ?: false,
                maintainerCanModify = args.bool("maintainerCanModify") ?: true,
            ),
        )
        return jsonResult(
            buildJsonObject {
                put("number", pull.number)
                put("title", pull.title)
                put("html_url", pull.htmlUrl)
            },
        )
    }

    private suspend fun mergePullRequest(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val number = args.int("pullNumber") ?: return errorResult("`pullNumber` is required")
        api.mergePullRequest(
            owner,
            repo,
            number,
            args.string("commitMessage"),
            args.string("mergeMethod") ?: "merge",
        )
        return jsonResult(buildJsonObject { put("merged", true); put("number", number) })
    }

    private suspend fun listWorkflows(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val workflows = api.listWorkflows(owner, repo)
        val payload = buildJsonObject {
            putJsonArray("workflows") {
                workflows.forEach { workflow ->
                    add(
                        buildJsonObject {
                            put("id", workflow.id)
                            put("name", workflow.name)
                            put("path", workflow.path)
                            put("state", workflow.state)
                            put("html_url", workflow.htmlUrl)
                        },
                    )
                }
            }
        }
        return jsonResult(payload)
    }

    private suspend fun listWorkflowRuns(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val runs = api.listWorkflowRuns(
            owner,
            repo,
            args.string("workflowId"),
            args.string("status"),
            args.int("page") ?: 1,
            args.int("perPage") ?: 30,
        )
        val payload = buildJsonObject {
            putJsonArray("runs") {
                runs.forEach { run ->
                    add(
                        buildJsonObject {
                            put("id", run.id)
                            put("name", run.name)
                            put("status", run.status)
                            put("conclusion", run.conclusion ?: "")
                            put("branch", run.headBranch)
                            put("event", run.event)
                            put("run_number", run.runNumber)
                            put("created_at", run.createdAt ?: "")
                            put("html_url", run.htmlUrl)
                        },
                    )
                }
            }
        }
        return jsonResult(payload)
    }

    private suspend fun rerunWorkflow(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val runId = args.long("runId") ?: return errorResult("`runId` is required")
        if (args.bool("failedOnly") == true) {
            api.rerunFailedJobs(owner, repo, runId)
        } else {
            api.rerunWorkflow(owner, repo, runId)
        }
        return jsonResult(buildJsonObject { put("rerun", true); put("run_id", runId) })
    }

    private suspend fun cancelWorkflowRun(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val runId = args.long("runId") ?: return errorResult("`runId` is required")
        api.cancelWorkflowRun(owner, repo, runId)
        return jsonResult(buildJsonObject { put("cancelled", true); put("run_id", runId) })
    }

    private suspend fun dispatchWorkflow(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val workflowId = args.string("workflowId") ?: return errorResult("`workflowId` is required")
        val ref = args.string("ref") ?: return errorResult("`ref` is required")
        val inputs = args.stringMap("inputs")
        api.dispatchWorkflow(owner, repo, workflowId, WorkflowDispatchRequest(ref, inputs))
        return jsonResult(
            buildJsonObject {
                put("dispatched", true)
                put("workflow_id", workflowId)
                put("ref", ref)
            },
        )
    }

    private suspend fun listReleases(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val releases = api.listReleases(owner, repo)
        val payload = buildJsonObject {
            putJsonArray("releases") {
                releases.forEach { release ->
                    add(
                        buildJsonObject {
                            put("tag", release.tag_name)
                            put("name", release.name ?: release.tag_name)
                            put("draft", release.draft)
                            put("prerelease", release.prerelease)
                            put("published_at", release.publishedAt ?: "")
                            put("html_url", release.htmlUrl)
                            putJsonArray("assets") {
                                release.assets.forEach { asset ->
                                    add(
                                        buildJsonObject {
                                            put("name", asset.name)
                                            put("size", asset.size)
                                            put("download_url", asset.browserDownloadUrl)
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
        return jsonResult(payload)
    }

    private suspend fun listTags(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val tags = api.listTags(owner, repo)
        val payload = buildJsonObject {
            putJsonArray("tags") {
                tags.forEach { tag ->
                    add(
                        buildJsonObject {
                            put("name", tag.name)
                            put("sha", tag.commit?.sha ?: "")
                        },
                    )
                }
            }
        }
        return jsonResult(payload)
    }

    private suspend fun getRepositoryTree(args: JsonObject): CallToolResult {
        val owner = args.string("owner") ?: return errorResult("`owner` is required")
        val repo = args.string("repo") ?: return errorResult("`repo` is required")
        val repoMeta = api.repository(owner, repo)
        val sha = args.string("sha") ?: repoMeta.defaultBranch
        val tree = api.tree(owner, repo, sha, args.bool("recursive") ?: true)
        val payload = buildJsonObject {
            put("sha", tree.sha)
            put("truncated", tree.truncated)
            putJsonArray("tree") {
                tree.tree.forEach { entry ->
                    add(
                        buildJsonObject {
                            put("path", entry.path)
                            put("type", entry.type)
                            put("size", entry.size)
                            put("sha", entry.sha)
                        },
                    )
                }
            }
        }
        return jsonResult(payload)
    }

    // ── Resource helpers ────────────────────────────────────────────────────

    /** The signed-in user's profile, used by the `github://user` resource. */
    suspend fun userProfile(): JsonObject {
        val user = api.currentUser()
        return buildJsonObject {
            put("login", user.login)
            put("name", user.name ?: "")
            put("avatar_url", user.avatarUrl)
            put("html_url", user.htmlUrl)
            put("public_repos", user.publicRepos)
            put("followers", user.followers)
        }
    }

    /** The README of a repository, used by the `repo://` resource. */
    suspend fun readmeText(owner: String, repo: String): String? {
        val element = api.contents(owner, repo, "README.md", null)
        val obj = dev.gitdroid.app.core.api.Json.obj(element) ?: return null
        val encoding = dev.gitdroid.app.core.api.Json.string(obj["encoding"])
        val raw = dev.gitdroid.app.core.api.Json.string(obj["content"]).orEmpty()
        return if (encoding == "base64") {
            runCatching {
                java.util.Base64.getMimeDecoder().decode(raw).toString(Charsets.UTF_8)
            }.getOrElse { raw }
        } else {
            raw
        }
    }

    // ── Result helpers ──────────────────────────────────────────────────────

    private fun jsonResult(payload: JsonObject): CallToolResult = CallToolResult(
        content = listOf(
            ToolContent(
                type = "text",
                text = GitHubJson.encodeToString(
                    kotlinx.serialization.json.JsonObject.serializer(),
                    payload,
                ),
            ),
        ),
        structuredContent = payload,
    )

    private fun errorResult(message: String): CallToolResult = CallToolResult(
        content = listOf(ToolContent(type = "text", text = message)),
        isError = true,
    )

    // ── Argument helpers ────────────────────────────────────────────────────

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content

    private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()

    private fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()

    private fun JsonObject.bool(key: String): Boolean? = when (val value = this[key]) {
        is JsonPrimitive -> when {
            value.isString -> value.content.toBooleanStrictOrNull()
            value.content == "true" -> true
            value.content == "false" -> false
            else -> null
        }

        else -> null
    }

    private fun JsonObject.stringList(key: String): List<String>? {
        val array = this[key] as? JsonArray ?: return null
        return array.mapNotNull { (it as? JsonPrimitive)?.content }
    }

    private fun JsonObject.stringMap(key: String): Map<String, String> {
        val obj = this[key] as? JsonObject ?: return emptyMap()
        return obj.mapNotNull { (k, v) ->
            val primitive = v as? JsonPrimitive ?: return@mapNotNull null
            k to primitive.content
        }.toMap()
    }
}
