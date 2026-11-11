package dev.gitdroid.app.screenshot

import dev.gitdroid.app.core.api.Issue
import dev.gitdroid.app.core.api.Label
import dev.gitdroid.app.core.api.User
import dev.gitdroid.app.core.api.WorkflowRun

/** The sample issues the screenshot tests render. */
val SampleIssues: List<Issue> = listOf(
    Issue(
        number = 412,
        title = "The MCP server drops the session when the phone sleeps",
        state = "open",
        comments = 3,
        user = User(login = "HakaI963"),
        labels = listOf(Label(name = "bug", color = "d73a4a")),
    ),
    Issue(
        number = 409,
        title = "Add a stashed-away search for release assets",
        state = "open",
        comments = 0,
        user = User(login = "nolyf"),
        labels = listOf(
            Label(name = "enhancement", color = "a2eeef"),
            Label(name = "good first issue", color = "7057ff"),
        ),
    ),
    Issue(
        number = 388,
        title = "Document the adb reverse setup",
        state = "open",
        comments = 7,
        user = User(login = "HakaI963"),
    ),
)

/** The sample workflow runs the screenshot tests render. */
val SampleRuns: List<WorkflowRun> = listOf(
    WorkflowRun(
        id = 1,
        runNumber = 128,
        name = "Build APK",
        headBranch = "main",
        status = "completed",
        conclusion = "success",
        event = "push",
    ),
    WorkflowRun(
        id = 2,
        runNumber = 127,
        name = "Unit tests",
        headBranch = "feature/mcp",
        status = "in_progress",
        conclusion = null,
        event = "pull_request",
    ),
    WorkflowRun(
        id = 3,
        runNumber = 126,
        name = "Lint",
        headBranch = "main",
        status = "completed",
        conclusion = "failure",
        event = "push",
    ),
    WorkflowRun(
        id = 4,
        runNumber = 125,
        name = "Release notes",
        headBranch = "main",
        status = "completed",
        conclusion = "cancelled",
        event = "schedule",
    ),
)
