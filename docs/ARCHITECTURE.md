# GitDroid — architecture

A compact GitHub client for Android that also acts as an **MCP server** so any
AI agent (Claude Code, OpenCode, Codex, Pi, Hermes, DeepSeek harness, …) can
drive the phone's GitHub session as a tool server.

```
┌──────────────────────── device ────────────────────────┐
│                                                          │
│  Compose UI                                              │
│   ├─ Auth      device-flow / PAT sign-in                 │
│   ├─ Repos     list · create · delete · switch           │
│   ├─ Repo      code · issues · PRs · actions · releases  │
│   ├─ Search    public repos · code                       │
│   ├─ Editor    readme / code / image viewer              │
│   └─ MCP       server dashboard · token · pairing        │
│                                                          │
│  ViewModel  ── StateFlow ──►  Repository                 │
│                                   │                      │
│                            ┌──────┴───────┐              │
│                            │  GitHubApi   │              │
│                            └──────┬───────┘              │
│                                   │                      │
│  ┌────────────────┐      ┌────────┴────────┐             │
│  │  McpService    │◄────►│  TokenStore     │             │
│  │  (foreground)  │      │  (encrypted)    │             │
│  └───────┬────────┘      └─────────────────┘             │
│          │                                               │
│   HTTP  │  MCP JSON-RPC (tools + resources + prompts)    │
└──────────┼───────────────────────────────────────────────┘
           │  LAN / adb reverse
    ┌──────▼─────────────────────────────────────────────┐
    │  Claude Code · OpenCode · Codex · Pi · Hermes …    │
    └────────────────────────────────────────────────────┘
```

## Why an app and not a web page

The official GitHub Android app and github.com both suffer from the same two
failures on a phone:

1. **The session dies.** Background the app for two minutes and the page
   reloads, losing the tab you were on and the branch you had selected.
2. **The surface is a web page.** Everything is a form. Creating a repo,
   re-running a workflow or downloading an artefact takes half a dozen taps
   through a viewport that was never designed for a thumb.

GitDroid fixes both:

* **Nothing reloads.** The session lives in a foreground service
  (`McpService`) with a warm `GitHubApi` and an on-disk token store. Repo
  lists, file trees and issue lists are cached in memory *and* on disk, so
  returning to a screen renders the previous content instantly and reconciles
  in the background.
* **The surface is native.** A compact, dense, thumb-first layout with a
  repository switcher that keeps every repo's scroll position and branch
  selection alive.

## The MCP half

The app runs a real MCP server (`dev.gitdroid.app.mcp`) over HTTP on the
device, speaking JSON-RPC 2.0 with the `initialize` / `tools/list` /
`tools/call` / `resources/*` / `prompts/*` methods.

* Bound to `0.0.0.0` so a laptop on the same network can reach it.
* A per-install bearer token (`Authorization: Bearer …`) protects it.
* `adb reverse tcp:PORT tcp:PORT` makes it reachable at `http://127.0.0.1:PORT`
  from a tethered machine.

Every MCP tool is a thin wrapper over the same `GitHubApi` the UI uses, so the
agent and the human are never out of sync.

## Packages

| Package | Responsibility |
|---|---|
| `core.api` | GitHub REST client, DTOs, pagination, rate limiting |
| `core.auth` | OAuth device flow, PAT, encrypted token persistence |
| `core.repo` | Repositories: repo lists, trees, issues, PRs, actions, releases |
| `core.cache` | Disk-backed cache so screens never reload empty |
| `core.download` | File / archive downloads to shared storage |
| `mcp` | MCP server, tool registry, foreground service |
| `ui.*` | Compose screens, design system, theme |
