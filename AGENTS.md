# GitDroid — working on this repository

GitDroid is a compact GitHub client for Android that also hosts an **MCP
server**, so an AI agent can drive the phone's GitHub session.

Read this before you change anything.

## The two rules

**The cache comes first.** A screen shows whatever was cached and *then*
refreshes. A failed refresh keeps the old data on screen and surfaces the
error as a banner — it never blanks the list. This is the whole point of the
app: the official client reloads itself into nothing every two minutes.

**The MCP tool names are the official ones.** `search_repositories`,
`get_file_contents`, `create_or_update_file`, `list_workflow_runs` — do not
rename them. Agents carry instructions that reference those names.

## Build

The APK is built by GitHub Actions. No local Gradle, Android SDK or NDK is
needed to produce a release.

```bash
git push origin main
gh run list
gh run download
```

To build locally you need JDK 17 and the Android SDK (platform 36,
build-tools 36.0.0):

```bash
./gradlew :app:assembleRelease
```

## Fast feedback

`tools/typecheck.sh` type-checks the Kotlin sources against the same
dependency versions CI uses, in seconds instead of minutes. `tools/rt.sh`
runs the unit tests locally. `tools/clean_imports.py` removes unused imports
(it keeps the operator imports that property delegation needs).

## Tests

```bash
./gradlew :app:testDebugUnitTest          # unit tests
./gradlew :app:updateDebugScreenshotTest   # render the screens to PNG
```

The screenshot tests render the real Compose UI through Robolectric, so the
design can be reviewed without a device.

## Layout

```
app/src/main/java/dev/gitdroid/app/
├── GitDroidApp.kt          Application: builds the container, restores the session
├── AppContainer.kt         Manual dependency injection
├── MainActivity.kt         The single activity
├── core/
│   ├── api/                GitHub REST client and DTOs
│   ├── auth/               Device flow, PAT, Keystore-backed token storage
│   ├── cache/              Disk cache — the reason screens never reload empty
│   ├── download/           Downloads to shared storage
│   ├── repo/               Repository: the single source of truth
│   └── Settings.kt         Preferences
├── mcp/                    MCP protocol, tool registry, HTTP server, service
└── ui/
    ├── theme/              Colour, type, shape and motion tokens
    ├── components/         Shared composables
    ├── preview/            Sample data and @Preview functions
    └── screens/            Auth, repos, repo detail, file, search, MCP, settings
```

## Style

- Kotlin, 4-space indent, trailing commas.
- KDoc on anything whose purpose is not obvious from the signature.
- Comments explain *why*, never *what*.
- Colour comes from the theme: `MaterialTheme.colorScheme` for the Material
  roles, `LocalGitDroidColors` for the extras. Never a hex literal in a
  composable.
- Motion comes from `LocalMotion.current` and its `press()`, `quick()`,
  `standard()`, `emphasized()`, `sheet()` and `slow()` specs.
- No emoji as icons — the Material icon set is already a dependency.

## Adding an MCP tool

1. Add the definition in `mcp/McpTools.kt` — a `tool(...)` entry whose
   description tells the model what it does.
2. Add the implementation and a branch in `dispatch(...)`.
3. The required arguments are validated against the schema before the tool
   runs, and **every** missing argument is reported at once.

## Adding a screen

1. Add it under `ui/screens/`.
2. Read state from `GitRepository` flows; write through its methods.
3. Wire it into the shell in `ui/GitDroidApp.kt`.
4. Keep it dense and thumb-first — this is a phone, not a desktop.
5. Add a preview to `ui/preview/Previews.kt` and a screenshot to
   `app/src/screenshotTest/`.

More detail: [`docs/AGENTS.md`](docs/AGENTS.md).
