package dev.gitdroid.app.core.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub REST API v3 payloads.
 *
 * Every field carries a default so a partial response never blows up
 * deserialization, and unknown keys are ignored globally (see [GitHubJson]).
 */

@Serializable
data class User(
    val login: String = "",
    val id: Long = 0,
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val name: String? = null,
    val company: String? = null,
    val blog: String? = null,
    val location: String? = null,
    val bio: String? = null,
    @SerialName("public_repos") val publicRepos: Int = 0,
    @SerialName("public_gists") val publicGists: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    val type: String = "User",
)

@Serializable
data class Repository(
    val id: Long = 0,
    val name: String = "",
    @SerialName("full_name") val fullName: String = "",
    val description: String? = null,
    val private: Boolean = false,
    val fork: Boolean = false,
    val archived: Boolean = false,
    val disabled: Boolean = false,
    @SerialName("is_template") val isTemplate: Boolean = false,
    val language: String? = null,
    @SerialName("stargazers_count") val stargazersCount: Int = 0,
    @SerialName("forks_count") val forksCount: Int = 0,
    @SerialName("watchers_count") val watchersCount: Int = 0,
    @SerialName("open_issues_count") val openIssuesCount: Int = 0,
    val size: Int = 0,
    val topics: List<String> = emptyList(),
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("clone_url") val cloneUrl: String = "",
    @SerialName("ssh_url") val sshUrl: String = "",
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("pushed_at") val pushedAt: String? = null,
    val owner: User? = null,
    val permissions: Permissions? = null,
    val visibility: String? = null,
) {
    val ownerLogin: String get() = owner?.login ?: fullName.substringBefore('/')
    val isPrivate: Boolean get() = private || visibility == "private"
}

@Serializable
data class Permissions(
    val admin: Boolean = false,
    val push: Boolean = false,
    val pull: Boolean = true,
    val triage: Boolean = false,
    val maintain: Boolean = false,
)

@Serializable
data class Branch(
    val name: String = "",
    @SerialName("commit") val commit: BranchCommit? = null,
    @SerialName("protected") val isProtected: Boolean = false,
) {
    val sha: String get() = commit?.sha ?: ""
}

@Serializable
data class BranchCommit(
    val sha: String = "",
    @SerialName("url") val url: String = "",
)

@Serializable
data class GitRef(
    val ref: String = "",
    @SerialName("node_id") val nodeId: String = "",
    val url: String = "",
    @SerialName("object") val target: GitObject = GitObject(),
)

@Serializable
data class GitObject(
    val sha: String = "",
    val type: String = "",
    val url: String = "",
)

@Serializable
data class GitTree(
    val sha: String = "",
    val url: String = "",
    val tree: List<TreeEntry> = emptyList(),
    val truncated: Boolean = false,
)

@Serializable
data class TreeEntry(
    val path: String = "",
    val mode: String = "",
    val type: String = "",
    val sha: String = "",
    val size: Long = 0,
    val url: String = "",
)

@Serializable
data class GitBlob(
    val sha: String = "",
    val url: String = "",
    @SerialName("content") val content: String = "",
    val encoding: String = "base64",
    val size: Long = 0,
)

@Serializable
data class Commit(
    val sha: String = "",
    @SerialName("node_id") val nodeId: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val commit: CommitDetail = CommitDetail(),
    val author: User? = null,
    val committer: User? = null,
    val parents: List<CommitParent> = emptyList(),
)

@Serializable
data class CommitDetail(
    val message: String = "",
    @SerialName("author") val author: CommitSignature? = null,
    @SerialName("committer") val committer: CommitSignature? = null,
    @SerialName("authored_date") val authoredDate: String? = null,
    @SerialName("committed_date") val committedDate: String? = null,
    val tree: GitObject = GitObject(),
)

@Serializable
data class CommitSignature(
    val name: String = "",
    val email: String = "",
    val date: String? = null,
)

@Serializable
data class CommitParent(
    val sha: String = "",
    val url: String = "",
)

@Serializable
data class Issue(
    val number: Int = 0,
    val title: String = "",
    val body: String? = null,
    val state: String = "open",
    @SerialName("state_reason") val stateReason: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val user: User? = null,
    val labels: List<Label> = emptyList(),
    val assignees: List<User> = emptyList(),
    val milestone: Milestone? = null,
    val comments: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("pull_request") val pullRequest: PullRequestMarker? = null,
) {
    val isPullRequest: Boolean get() = pullRequest != null
}

@Serializable
data class PullRequestMarker(
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("diff_url") val diffUrl: String = "",
)

@Serializable
data class Label(
    val name: String = "",
    val color: String = "",
    val description: String? = null,
)

@Serializable
data class Milestone(
    val number: Int = 0,
    val title: String = "",
    val state: String = "",
)

@Serializable
data class PullRequest(
    val number: Int = 0,
    val title: String = "",
    val body: String? = null,
    val state: String = "open",
    val draft: Boolean = false,
    val merged: Boolean = false,
    val mergeable: Boolean? = null,
    @SerialName("mergeable_state") val mergeableState: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val user: User? = null,
    val head: PullRef = PullRef(),
    val base: PullRef = PullRef(),
    val labels: List<Label> = emptyList(),
    val assignees: List<User> = emptyList(),
    val requested_reviewers: List<User> = emptyList(),
    val comments: Int = 0,
    @SerialName("review_comments") val reviewComments: Int = 0,
    val commits: Int = 0,
    @SerialName("additions") val additions: Int = 0,
    @SerialName("deletions") val deletions: Int = 0,
    @SerialName("changed_files") val changedFiles: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("merged_at") val mergedAt: String? = null,
)

@Serializable
data class PullRef(
    val label: String = "",
    val ref: String = "",
    val sha: String = "",
    val user: User? = null,
)

@Serializable
data class Workflow(
    val id: Long = 0,
    @SerialName("node_id") val nodeId: String = "",
    val name: String = "",
    val path: String = "",
    val state: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("badge_url") val badgeUrl: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class WorkflowRun(
    val id: Long = 0,
    @SerialName("run_number") val runNumber: Int = 0,
    val name: String = "",
    @SerialName("head_branch") val headBranch: String = "",
    @SerialName("head_sha") val headSha: String = "",
    val status: String = "",
    val conclusion: String? = null,
    val event: String = "",
    @SerialName("workflow_id") val workflowId: Long = 0,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("run_started_at") val runStartedAt: String? = null,
    val actor: User? = null,
    @SerialName("triggering_actor") val triggeringActor: User? = null,
    val jobs: List<WorkflowJob> = emptyList(),
)

@Serializable
data class WorkflowJob(
    val id: Long = 0,
    @SerialName("run_id") val runId: Long = 0,
    val name: String = "",
    val status: String = "",
    val conclusion: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val steps: List<WorkflowStep> = emptyList(),
    @SerialName("html_url") val htmlUrl: String = "",
)

@Serializable
data class WorkflowStep(
    val name: String = "",
    val status: String = "",
    val conclusion: String? = null,
    val number: Int = 0,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
data class Release(
    val id: Long = 0,
    val tag_name: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<ReleaseAsset> = emptyList(),
)

@Serializable
data class ReleaseAsset(
    val id: Long = 0,
    val name: String = "",
    val size: Long = 0,
    @SerialName("download_count") val downloadCount: Int = 0,
    @SerialName("content_type") val contentType: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Tag(
    val name: String = "",
    @SerialName("commit") val commit: BranchCommit? = null,
    val zipball_url: String = "",
    val tarball_url: String = "",
)

@Serializable
data class SearchResult<T>(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("incomplete_results") val incompleteResults: Boolean = false,
    val items: List<T> = emptyList(),
)

@Serializable
data class RateLimit(
    val resources: Resources = Resources(),
    val rate: Rate = Rate(),
) {
    @Serializable
    data class Resources(
        val core: Resource = Resource(),
        val search: Resource = Resource(),
        val graphql: Resource = Resource(),
    )

    @Serializable
    data class Resource(
        val limit: Int = 0,
        val used: Int = 0,
        val remaining: Int = 0,
        val reset: Long = 0,
    )

    @Serializable
    data class Rate(
        val limit: Int = 0,
        val used: Int = 0,
        val remaining: Int = 0,
        val reset: Long = 0,
    )
}

@Serializable
data class Notification(
    val id: String = "",
    val unread: Boolean = false,
    val reason: String = "",
    @SerialName("updated_at") val updatedAt: String? = null,
    val subject: Subject = Subject(),
    val repository: Repository? = null,
) {
    @Serializable
    data class Subject(
        val title: String = "",
        val type: String = "",
        val url: String = "",
        @SerialName("latest_comment_url") val latestCommentUrl: String = "",
    )
}

@Serializable
data class IssueComment(
    val id: Long = 0,
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val user: User? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class CreateRepoRequest(
    val name: String,
    val description: String? = null,
    val private: Boolean = false,
    @SerialName("auto_init") val autoInit: Boolean = true,
    @SerialName("has_issues") val hasIssues: Boolean = true,
    @SerialName("has_wiki") val hasWiki: Boolean = true,
)

@Serializable
data class UpdateRepoRequest(
    val name: String? = null,
    val description: String? = null,
    val private: Boolean? = null,
    val archived: Boolean? = null,
    @SerialName("default_branch") val defaultBranch: String? = null,
    val topics: List<String>? = null,
    @SerialName("has_issues") val hasIssues: Boolean? = null,
    @SerialName("has_wiki") val hasWiki: Boolean? = null,
)

@Serializable
data class CreateIssueRequest(
    val title: String,
    val body: String? = null,
    val labels: List<String>? = null,
    val assignees: List<String>? = null,
)

@Serializable
data class UpdateIssueRequest(
    val title: String? = null,
    val body: String? = null,
    val state: String? = null,
    @SerialName("state_reason") val stateReason: String? = null,
    val labels: List<String>? = null,
    val assignees: List<String>? = null,
)

@Serializable
data class CreatePullRequestRequest(
    val title: String,
    val head: String,
    val base: String,
    val body: String? = null,
    val draft: Boolean = false,
    @SerialName("maintainer_can_modify") val maintainerCanModify: Boolean = true,
)

@Serializable
data class CreateCommentRequest(val body: String)

@Serializable
data class CreateFileRequest(
    val message: String,
    val content: String,
    val branch: String? = null,
    val sha: String? = null,
)

@Serializable
data class CreateBranchRequest(
    @SerialName("ref") val ref: String,
    @SerialName("sha") val sha: String,
)

@Serializable
data class WorkflowDispatchRequest(
    @SerialName("ref") val ref: String,
    @SerialName("inputs") val inputs: Map<String, String>? = null,
)

@Serializable
data class EmptyRequest(
    @SerialName("commit_message") val commitMessage: String = "",
    @SerialName("branch") val branch: String = "",
    @SerialName("sha") val sha: String = "",
)
