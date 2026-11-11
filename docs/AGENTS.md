# Working on GitDroid

## Build

The APK is built by GitHub Actions. Push and watch:

```bash
git push origin main
gh run list
gh run download
```

To build locally you need JDK 17 and the Android SDK (platform 36,
build-tools 36.0.0):

```bash
./gradlew :app:assembleDebug
```

## Fast type-checking

The CI build takes minutes. `tools/typecheck.sh` type-checks the Kotlin
sources against the exact dependency versions CI uses and finishes in seconds
(it needs the cached jars described in `tools/typecheck.sh`'s header):

```bash
./tools/typecheck.sh
```

It expects the dependency jars to be cached under `/tmp/opencode/apicache`.
If that directory is gone, the script will report unresolved references —
run it in a fresh environment only after a CI build has populated the cache.

`tools/clean_imports.py` removes unused imports; it keeps the operator
imports (`getValue`, `setValue`, …) that property delegation needs.

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
│   ├── repo/               Repository: the single source of truth
│   └── download/           Downloads to shared storage
├── mcp/                    MCP protocol, tool registry, HTTP server, service
└── ui/
    ├── theme/              Colour, type, shape and motion tokens
    ├── components/         Shared composables
    └── screens/            Auth, inbox, repos, repo detail, code, file,
                            search, MCP, settings
```

## Rules

**The cache comes first.** A screen shows whatever was cached and *then*
refreshes. A failed refresh keeps the old data on screen and surfaces the
error as a banner — never blank the list.

**One source of truth.** The UI and the MCP server read the same
`GitRepository`. An agent and a human must never disagree about the repo list.

**The MCP tool names are the official ones.** Do not rename them; agents
carry instructions that reference `search_repositories` and friends.

**Motion is a token, not a duration.** Use `LocalMotion.current` and its
`press()`, `quick()`, `standard()`, `emphasized()`, `sheet()` and `slow()`
specs instead of hard-coded millisecond values.

**Colour comes from the theme.** Use `MaterialTheme.colorScheme` for the
Material roles and `LocalGitDroidColors` for the extras (code well, rules,
status tones). Never hard-code a hex value in a composable.

**No emoji as icons.** Use the Material icon set; the extended set is already
a dependency.

## Adding an MCP tool

1. Add the definition in `mcp/McpTools.kt` — a `tool(...)` entry with a
   description the model can act on.
2. Add the implementation and a `when` branch in `call(...)`.
The advertised count comes from `container.mcpToolCount`, and `McpToolsTest`
fails if the registry and the dispatcher ever disagree.

The tool names should match the official GitHub MCP server where an
equivalent exists.

## Adding a screen

1. Add it under `ui/screens/`.
2. Read state from `GitRepository` flows; write through its methods.
3. Wire it into the shell in `ui/GitDroidApp.kt`.
4. Keep the layout dense and thumb-first — this is a phone, not a desktop.

## Style

- Kotlin, 4-space indent, trailing commas.
- KDoc on anything whose purpose is not obvious from the signature.
- Comments explain *why*, never *what*.
