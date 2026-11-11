# The GitDroid MCP server

GitDroid hosts a **Model Context Protocol** server on the device. Any MCP
client can talk to it and drive the GitHub account that is signed in on the
phone — no token juggling, no second login.

```
AI agent  ──HTTP/JSON-RPC──▶  GitDroid (Android)  ──HTTPS──▶  api.github.com
   (laptop)                    0.0.0.0:8787
```

---

## Transport

The server implements the Streamable HTTP transport:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/mcp` | JSON-RPC 2.0 request → JSON or SSE response |
| `POST` | `/message` | The older HTTP+SSE transport, kept for compatibility |
| `GET` | `/mcp` | Liveness / server-to-client stream |
| `GET` | `/health` | `{"status":"ok"}` |

A request looks like this:

```http
POST /mcp HTTP/1.1
Host: 192.168.1.42:8787
Authorization: Bearer <token>
Content-Type: application/json
Accept: application/json, text/event-stream

{"jsonrpc":"2.0","id":1,"method":"initialize","params":{
  "protocolVersion":"2025-06-18",
  "capabilities":{},
  "clientInfo":{"name":"claude-code","version":"1.0"}
}}
```

and the server answers:

```json
{"jsonrpc":"2.0","id":1,"result":{
  "protocolVersion":"2025-06-18",
  "capabilities":{"tools":{"listChanged":false},"resources":{},"prompts":{}},
  "serverInfo":{"name":"gitdroid","version":"1.0.0"},
  "instructions":"GitDroid exposes the GitHub account that is signed in on the phone…"
}}
```

`Accept: text/event-stream` switches the response to SSE framing
(`event: message` / `data: …`), which is what the HTTP+SSE clients expect.

### Protocol versions

The server implements `2025-06-18` and accepts `2025-03-26` and `2024-11-05`.
When a client asks for a version the server knows, it is echoed back
unchanged; otherwise the server replies with its own.

---

## Authentication

Every request needs the bearer token shown on the **MCP** tab:

```http
Authorization: Bearer <token>
```

`X-API-Key: <token>` is accepted too. The token is generated on first use,
stored encrypted, and can be rotated from the app — rotating invalidates
every client that had the old one, which is the point.

---

## Reaching the phone

### Over the network

The server binds `0.0.0.0`, so a machine on the same Wi-Fi can reach it at
`http://<phone-ip>:8787/mcp`. The app shows the exact address on the MCP tab.

### Over USB

```bash
adb reverse tcp:8787 tcp:8787
```

Now `http://127.0.0.1:8787/mcp` works from the tethered machine — no Wi-Fi,
no port forwarding, no cloud.

---

## Tools

Tool names match the official GitHub MCP server, so an agent's existing
instructions keep working.

### Reading

| Tool | Arguments | Notes |
|---|---|---|
| `list_repositories` | `page`, `perPage`, `sort` | The signed-in user's repositories. |
| `get_repository` | `owner`, `repo` | Metadata for one repository. |
| `get_file_contents` | `owner`, `repo`, `path`, `ref` | A file's text or a directory listing. |
| `get_repository_tree` | `owner`, `repo`, `recursive`, `sha` | The whole tree in one call. |
| `list_branches` | `owner`, `repo`, `perPage` | |
| `list_commits` | `owner`, `repo`, `sha`, `perPage` | |
| `list_tags` | `owner`, `repo`, `perPage` | |
| `list_releases` | `owner`, `repo`, `perPage` | Includes assets. |

### Searching

| Tool | Arguments | Notes |
|---|---|---|
| `search_repositories` | `query`, `sort`, `page`, `perPage` | Supports GitHub's qualifiers (`language:kotlin stars:>100`). |
| `search_code` | `query`, `page`, `perPage` | Code search; scope with `repo:owner/name`. |

### Writing

| Tool | Arguments | Notes |
|---|---|---|
| `create_repository` | `name`, `description`, `private`, `autoInit`, `hasIssues`, `hasWiki` | |
| `update_repository` | `owner`, `repo`, `name`, `description`, `private`, `archived`, `defaultBranch`, `topics`, … | |
| `delete_repository` | `owner`, `repo` | **Irreversible.** |
| `fork_repository` | `owner`, `repo`, `organization` | |
| `create_or_update_file` | `owner`, `repo`, `path`, `content`, `message`, `branch`, `sha` | `sha` is required when updating. |
| `delete_file` | `owner`, `repo`, `path`, `message`, `sha`, `branch` | |
| `create_branch` | `owner`, `repo`, `branch`, `fromSha` | Falls back to the default branch head. |

### Issues and pull requests

| Tool | Arguments | Notes |
|---|---|---|
| `list_issues` | `owner`, `repo`, `state`, `page`, `perPage` | |
| `create_issue` | `owner`, `repo`, `title`, `body`, `labels`, `assignees` | |
| `update_issue` | `owner`, `repo`, `issueNumber`, `title`, `body`, `state`, `stateReason`, `labels`, `assignees` | |
| `list_pull_requests` | `owner`, `repo`, `state`, `page`, `perPage` | |
| `create_pull_request` | `owner`, `repo`, `title`, `head`, `base`, `body`, `draft` | |
| `merge_pull_request` | `owner`, `repo`, `pullNumber`, `commitMessage`, `mergeMethod` | `merge`, `squash` or `rebase`. |

### Actions

| Tool | Arguments | Notes |
|---|---|---|
| `list_workflows` | `owner`, `repo` | |
| `list_workflow_runs` | `owner`, `repo`, `workflowId`, `status`, `page`, `perPage` | |
| `rerun_workflow` | `owner`, `repo`, `runId`, `failedOnly` | |
| `cancel_workflow_run` | `owner`, `repo`, `runId` | |
| `dispatch_workflow` | `owner`, `repo`, `workflowId`, `ref`, `inputs` | Triggers a `workflow_dispatch` workflow. |

### Errors

A failing call comes back as a **successful** JSON-RPC response whose result
has `isError: true` and a text content block explaining what went wrong. That
is what the MCP specification asks for: the model sees the failure and can
correct itself instead of the transport dying.

```json
{"jsonrpc":"2.0","id":7,"result":{
  "content":[{"type":"text","text":"Not found"}],
  "isError":true
}}
```

---

## Resources and prompts

| Surface | Name | Description |
|---|---|---|
| Resource | `github://user` | The signed-in user, as JSON. |
| Resource | `repo://current` | The repository GitDroid is showing. |
| Resource template | `repo://{owner}/{repo}/tree/{ref}` | A repository tree. |
| Resource template | `repo://{owner}/{repo}/file/{path}` | A single file. |
| Prompt | `review_repo` | Review a repository's README, structure and recent activity. |
| Prompt | `triage_issues` | Group open issues by theme and suggest labels. |

---

## Keeping the connection alive

The server runs inside `McpService`, an Android foreground service. That is
what stops the phone from reclaiming the process while the screen is off, so
an agent's request is answered in milliseconds and the token never has to be
re-entered.

The notification shows the port and can be tapped to jump back into the app.

---

## Writing your own client

```python
import json, urllib.request

URL = "http://192.168.1.42:8787/mcp"
TOKEN = "…"

def call(method, params=None, request_id=1):
    body = json.dumps({"jsonrpc": "2.0", "id": request_id, "method": method,
                       "params": params or {}}).encode()
    request = urllib.request.Request(URL, data=body, method="POST", headers={
        "Authorization": f"Bearer {TOKEN}",
        "Content-Type": "application/json",
        "Accept": "application/json",
    })
    with urllib.request.urlopen(request) as response:
        return json.loads(response.read())

call("initialize", {"protocolVersion": "2025-06-18", "capabilities": {}})
print(call("tools/list")["result"]["tools"])
print(call("tools/call", {"name": "list_repositories", "arguments": {}}))
```
