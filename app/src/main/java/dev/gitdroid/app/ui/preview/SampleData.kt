package dev.gitdroid.app.ui.preview

import dev.gitdroid.app.core.api.Repository
import dev.gitdroid.app.core.api.User

/**
 * The sample repositories the previews and the screenshot tests render.
 *
 * Keeping them here means the design review and the in-app previews always
 * show the same data.
 */
val SampleRepos: List<Repository> = listOf(
    Repository(
        id = 1,
        name = "gitdroid",
        fullName = "HakaI963/gitdroid",
        description = "A compact, always-on GitHub client for Android that doubles as an MCP server.",
        private = false,
        language = "Kotlin",
        stargazersCount = 128,
        forksCount = 12,
        openIssuesCount = 3,
        defaultBranch = "main",
        owner = User(login = "HakaI963", avatarUrl = ""),
    ),
    Repository(
        id = 2,
        name = "dsh-red",
        fullName = "nolyf/dsh-red",
        description = "A privacy-first Android runtime with a full agent harness.",
        private = true,
        language = "Dart",
        stargazersCount = 2041,
        forksCount = 310,
        openIssuesCount = 27,
        defaultBranch = "main",
        owner = User(login = "nolyf", avatarUrl = ""),
    ),
    Repository(
        id = 3,
        name = "compose-samples",
        fullName = "android/compose-samples",
        description = "Samples for the Jetpack Compose UI toolkit.",
        private = false,
        language = "Kotlin",
        stargazersCount = 12800,
        forksCount = 2400,
        defaultBranch = "main",
        owner = User(login = "android", avatarUrl = ""),
    ),
    Repository(
        id = 4,
        name = "infra",
        fullName = "HakaI963/infra",
        description = "Deployment configuration.",
        private = false,
        language = "TypeScript",
        stargazersCount = 4,
        forksCount = 0,
        archived = true,
        defaultBranch = "trunk",
        owner = User(login = "HakaI963", avatarUrl = ""),
    ),
)
