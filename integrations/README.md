# Agent setup

GitDroid is an MCP server on your phone. Every agent below talks to it the
same way — HTTP, JSON-RPC, a bearer token — so the only thing that differs is
the shape of the configuration file.

## Before you start

1. Open **GitDroid → MCP** on the phone.
2. Turn the server on.
3. Copy the **bearer token** and the **URL**.

Over USB, forward the port first:

```bash
adb reverse tcp:8787 tcp:8787
```

Then use `http://127.0.0.1:8787/mcp`. On Wi-Fi, use the LAN address the app
shows (`http://192.168.x.x:8787/mcp`).

---

## Claude Code

```bash
claude mcp add --transport http gitdroid http://127.0.0.1:8787/mcp \
  --header "Authorization: Bearer YOUR_TOKEN"
```

Or in `.mcp.json`:

```json
{
  "mcpServers": {
    "gitdroid": {
      "type": "http",
      "url": "http://127.0.0.1:8787/mcp",
      "headers": { "Authorization": "Bearer YOUR_TOKEN" }
    }
  }
}
```

## OpenCode

`opencode.json` — see [`integrations/opencode.json`](integrations/opencode.json).

```json
{
  "mcp": {
    "gitdroid": {
      "type": "remote",
      "url": "http://127.0.0.1:8787/mcp",
      "enabled": true,
      "headers": { "Authorization": "Bearer YOUR_TOKEN" }
    }
  }
}
```

## Codex

`~/.codex/config.toml` — see [`integrations/codex.toml`](integrations/codex.toml).

```toml
[mcp_servers.gitdroid]
url = "http://127.0.0.1:8787/mcp"
http_headers = { "Authorization" = "Bearer YOUR_TOKEN" }
```

## Pi

See [`integrations/pi.json`](integrations/pi.json).

```json
{
  "mcpServers": {
    "gitdroid": {
      "type": "http",
      "url": "http://127.0.0.1:8787/mcp",
      "headers": { "Authorization": "Bearer YOUR_TOKEN" }
    }
  }
}
```

## Hermes

See [`integrations/hermes.json`](integrations/hermes.json).

```json
{
  "mcp": {
    "servers": {
      "gitdroid": {
        "transport": "streamable-http",
        "url": "http://127.0.0.1:8787/mcp",
        "headers": { "Authorization": "Bearer YOUR_TOKEN" }
      }
    }
  }
}
```

## DeepSeek harness plugin

See [`integrations/deepseek-harness-plugin.json`](integrations/deepseek-harness-plugin.json).
The plugin declares the transport, the endpoint and the tools it expects, so
the harness can wire itself up without any extra prompting.

```json
{
  "name": "gitdroid",
  "type": "mcp",
  "mcp": {
    "transport": "streamable-http",
    "url": "http://127.0.0.1:8787/mcp",
    "env": { "GITDROID_TOKEN": "YOUR_TOKEN" }
  }
}
```

## Anything else

The protocol is plain HTTP + JSON-RPC 2.0. See
[`docs/MCP.md`](../docs/MCP.md) for the wire format, or use this snippet to
prove the server is alive:

```bash
curl -s http://127.0.0.1:8787/health
# {"status":"ok","server":"gitdroid"}
```

```bash
curl -s http://127.0.0.1:8787/mcp \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' \
  | python3 -m json.tool | head -20
```

## Security notes

- The token is per-install, encrypted at rest, and rotatable from the app.
  Rotating it invalidates every client that had the old one.
- The server binds `0.0.0.0`, so anything on the same network can *reach*
  it — but nothing can use it without the token.
- The `delete_repository` and `delete_file` tools are irreversible. Give the
  agent a repo it is allowed to touch, and rotate the token when you are done.
