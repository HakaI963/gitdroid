package dev.gitdroid.app.mcp

import dev.gitdroid.app.core.api.GitHubJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The MCP server.
 *
 * A deliberately small HTTP/1.1 implementation that speaks the MCP
 * JSON-RPC dialect. It listens on the device so an agent on the same network
 * — or on a tethered machine via `adb reverse` — can drive the phone's
 * GitHub session.
 *
 * Transport notes:
 *
 * * `POST /mcp` — the Streamable HTTP transport. The body is a JSON-RPC
 *   request; the response is JSON (or SSE when the client asks for it).
 * * `POST /message` — the older HTTP+SSE transport, kept so older clients
 *   still work.
 * * `GET /health` — a liveness probe the agent config can point at.
 */
class McpServer(
    private val tools: McpTools,
    private val scope: CoroutineScope,
    private val port: Int = DEFAULT_PORT,
    private val bearerToken: () -> String?,
) {

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    /** The port the server actually bound to. */
    @Volatile
    var boundPort: Int = port
        private set

    val isRunning: Boolean get() = running.get()

    /** Starts the server. Returns the port it is listening on. */
    fun start(port: Int = this.port): Int {
        if (running.getAndSet(true)) return boundPort
        // The preferred port may already be taken — another instance of the
        // app, or a leftover socket. The caller decides whether to fall back
        // to an ephemeral port.
        val socket = ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"))
        serverSocket = socket
        boundPort = socket.localPort
        acceptJob = scope.launch(Dispatchers.IO) {
            while (running.get()) {
                val client = try {
                    socket.accept()
                } catch (e: Exception) {
                    if (running.get()) continue else break
                }
                scope.launch(Dispatchers.IO) { handle(client) }
            }
        }
        return boundPort
    }

    /** Stops the server and closes the listening socket. */
    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
    }

    private suspend fun handle(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 30_000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // The request head has to be read at the *byte* level. A
            // BufferedReader would happily read past the blank line and
            // swallow the first bytes of the body, and the body length is a
            // byte count while the payload is UTF-8 — decoding it as UTF-16
            // would mangle every non-ASCII character in a commit message.
            val head = readHead(input) ?: return
            val requestLine = head.firstLine
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val path = parts[1].substringBefore('?')

            val headers = mutableMapOf<String, String>()
            for (line in head.lines.drop(1)) {
                if (line.isEmpty()) continue
                val idx = line.indexOf(':')
                if (idx > 0) {
                    headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
            }

            val body = readBody(input, headers)

            when {
                path == "/health" || path == "/healthz" -> {
                    writeJson(output, 200, buildJsonObject { put("status", "ok"); put("server", "gitdroid") })
                }

                path == "/" -> {
                    writeJson(
                        output,
                        200,
                        buildJsonObject {
                            put("name", "GitDroid MCP Server")
                            put("protocolVersion", MCP_PROTOCOL_VERSION)
                            put("endpoint", "/mcp")
                            put("tools", tools.tools.size)
                        },
                    )
                }

                method == "POST" && (path == "/mcp" || path == "/message" || path == "/") -> {
                    if (!isAuthorized(headers)) {
                        writeJson(
                            output,
                        401,
                            buildJsonObject {
                                put("error", "unauthorized")
                                put("message", "Missing or invalid bearer token")
                            },
                        )
                        return
                    }
                    val acceptsSse = headers["accept"]?.contains("text/event-stream") == true
                    val payload = handleRpc(body)
                    if (acceptsSse) {
                        writeSse(output, payload)
                    } else {
                        writeJson(output, 200, payload)
                    }
                }

                method == "GET" && path == "/mcp" -> {
                    // Server-initiated stream: nothing to push right now, but
                    // answering keeps clients from treating the server as dead.
                    writeJson(output, 200, buildJsonObject { put("status", "ready") })
                }

                else -> {
                    writeJson(
                        output,
                        404,
                        buildJsonObject { put("error", "not_found"); put("path", path) },
                    )
                }
            }
            output.flush()
        }
    }

    /**
     * Reads the request head — the request line and the headers — up to the
     * blank line that ends it.
     *
     * Returns `null` when the peer closed the connection before sending a
     * complete head.
     */
    private fun readHead(input: InputStream): Head? {
        val buffer = ByteArrayOutputStream()
        while (buffer.size() < MAX_HEAD_BYTES) {
            val byte = input.read()
            if (byte < 0) return null
            buffer.write(byte)
            val text = buffer.toString(StandardCharsets.UTF_8.name())
            if (text.endsWith("\r\n\r\n") || text.endsWith("\n\n")) break
        }
        val text = buffer.toString(StandardCharsets.UTF_8.name())
        if (text.isBlank()) return null
        val lines = text.split("\r\n", "\n").filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        return Head(lines.first(), lines.drop(1))
    }

    /** The request head, split into its first line and the rest. */
    private data class Head(val firstLine: String, val lines: List<String>)

    /**
     * Reads the request body.
     *
     * Handles both `Content-Length` and chunked transfer encoding, because
     * different MCP clients pick different ones.
     */
    private fun readBody(input: InputStream, headers: Map<String, String>): String {
        val chunked = headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true
        if (chunked) {
            val out = ByteArrayOutputStream()
            while (true) {
                val sizeLine = readLine(input) ?: break
                val size = sizeLine.substringBefore(';').trim().toIntOrNull(16) ?: break
                if (size == 0) {
                    readLine(input) // the CRLF of the terminating chunk
                    break
                }
                val chunk = ByteArray(size)
                var read = 0
                while (read < size) {
                    val n = input.read(chunk, read, size - read)
                    if (n < 0) break
                    read += n
                }
                out.write(chunk, 0, read)
                readLine(input) // the CRLF after the chunk
            }
            return out.toString(StandardCharsets.UTF_8.name())
        }

        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length <= 0) return ""
        val buffer = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buffer, read, length - read)
            if (n < 0) break
            read += n
        }
        return String(buffer, 0, read, StandardCharsets.UTF_8)
    }

    /** Reads one CRLF-terminated line at the byte level. */
    private fun readLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte < 0) {
                return if (out.size() == 0) null else out.toString(StandardCharsets.UTF_8.name())
            }
            if (byte == NEWLINE) {
                val text = out.toString(StandardCharsets.UTF_8.name())
                return if (text.endsWith("\r")) text.dropLast(1) else text
            }
            out.write(byte)
            if (out.size() > MAX_LINE_BYTES) return out.toString(StandardCharsets.UTF_8.name())
        }
    }

    private fun isAuthorized(headers: Map<String, String>): Boolean {
        val expected = bearerToken()?.takeIf { it.isNotBlank() } ?: return true
        val header = headers["authorization"].orEmpty()
        if (header.startsWith("Bearer ", ignoreCase = true)) {
            return header.substring(7).trim() == expected
        }
        val apiKey = headers["x-api-key"].orEmpty()
        if (apiKey == expected) return true
        return false
    }

    /**
     * Handles one JSON-RPC request and returns the response payload.
     *
     * The method is public so the unit tests can drive the protocol without
     * opening a socket.
     */
    suspend fun handleRpc(body: String): JsonObject {
        val request = runCatching {
            GitHubJson.decodeFromString(JsonRpcRequest.serializer(), body)
        }.getOrElse {
            return errorResponse(
                null,
                -32700,
                "Parse error: ${it.message}",
            )
        }

        // A notification (no id) expects no response.
        val isNotification = request.id == null

        return when (request.method) {
            "initialize" -> {
                val params = request.params as? JsonObject
                val requested = params?.get("protocolVersion")?.let {
                    (it as? JsonPrimitive)?.content
                }
                val negotiated = if (requested != null && requested in SUPPORTED_PROTOCOL_VERSIONS) {
                    requested
                } else {
                    MCP_PROTOCOL_VERSION
                }
                val result = InitializeResult(
                    protocolVersion = negotiated,
                    capabilities = ServerCapabilities(),
                    serverInfo = ServerInfo(
                        name = "gitdroid",
                        version = "1.0.0",
                        title = "GitDroid — your GitHub on Android",
                    ),
                    instructions = INSTRUCTIONS,
                )
                if (isNotification) emptyResponse() else successResponse(request.id, result)
            }

            "notifications/initialized", "notifications/cancelled" -> emptyResponse()

            "ping" -> if (isNotification) emptyResponse() else successResponse(request.id, buildJsonObject { })

            "tools/list" -> if (isNotification) {
                emptyResponse()
            } else {
                successResponse(request.id, ListToolsResult(tools.tools))
            }

            "tools/call" -> {
                val params = request.params as? JsonObject
                val name = params?.get("name")?.let { (it as? JsonPrimitive)?.content }
                if (name.isNullOrBlank()) {
                    errorResponse(request.id, -32602, "Missing tool name")
                } else {
                    val arguments = params?.get("arguments") as? JsonObject ?: JsonObject(emptyMap())
                    val result = tools.call(name, arguments)
                    if (isNotification) emptyResponse() else successResponse(request.id, result)
                }
            }

            "resources/list" -> if (isNotification) {
                emptyResponse()
            } else {
                successResponse(request.id, ListResourcesResult(resourceDefinitions()))
            }

            "resources/templates/list" -> if (isNotification) {
                emptyResponse()
            } else {
                successResponse(
                    request.id,
                    ListResourceTemplatesResult(
                        listOf(
                            ResourceTemplate(
                                uriTemplate = "repo://{owner}/{repo}/tree/{ref}",
                                name = "Repository tree",
                                description = "The file tree of a repository at a given ref.",
                                mimeType = "application/json",
                            ),
                            ResourceTemplate(
                                uriTemplate = "repo://{owner}/{repo}/file/{path}",
                                name = "Repository file",
                                description = "A single file from a repository.",
                                mimeType = "text/plain",
                            ),
                        ),
                    ),
                )
            }

            "resources/read" -> {
                val params = request.params as? JsonObject
                val uri = params?.get("uri")?.let { (it as? JsonPrimitive)?.content }
                if (uri.isNullOrBlank()) {
                    errorResponse(request.id, -32602, "Missing resource uri")
                } else {
                    readResource(uri)
                }
            }

            "prompts/list" -> if (isNotification) {
                emptyResponse()
            } else {
                successResponse(request.id, ListPromptsResult(promptDefinitions()))
            }

            "prompts/get" -> {
                val params = request.params as? JsonObject
                val name = params?.get("name")?.let { (it as? JsonPrimitive)?.content }
                val prompt = promptDefinitions().firstOrNull { it.name == name }
                    ?: return errorResponse(request.id, -32602, "Unknown prompt: $name")
                successResponse(request.id, GetPromptResult(messages = promptMessages(prompt.name)))
            }

            "logging/setLevel" -> if (isNotification) emptyResponse() else successResponse(request.id, buildJsonObject { })

            else -> if (isNotification) {
                emptyResponse()
            } else {
                errorResponse(request.id, -32601, "Method not found: ${request.method}")
            }
        }
    }

    private fun resourceDefinitions(): List<ResourceDefinition> = listOf(
        ResourceDefinition(
            uri = "repo://current",
            name = "Current repository",
            description = "The repository GitDroid is currently showing.",
            mimeType = "application/json",
        ),
        ResourceDefinition(
            uri = "github://user",
            name = "Signed-in user",
            description = "The GitHub account GitDroid is signed in as.",
            mimeType = "application/json",
        ),
    )

    private fun promptDefinitions(): List<PromptDefinition> = listOf(
        PromptDefinition(
            name = "review_repo",
            description = "Review a repository's README, structure and recent activity.",
            arguments = listOf(
                PromptArgument(name = "owner", description = "Repository owner", required = true),
                PromptArgument(name = "repo", description = "Repository name", required = true),
            ),
        ),
        PromptDefinition(
            name = "triage_issues",
            description = "Triage the open issues of a repository and suggest labels.",
            arguments = listOf(
                PromptArgument(name = "owner", description = "Repository owner", required = true),
                PromptArgument(name = "repo", description = "Repository name", required = true),
            ),
        ),
    )

    private fun promptMessages(name: String): List<PromptMessage> = when (name) {
        "review_repo" -> listOf(
            PromptMessage(
                role = "user",
                content = ToolContent(
                    type = "text",
                    text = "Review the repository {owner}/{repo}. Start with its README, then look at " +
                        "the file tree and the most recent commits. Summarise what the project does, " +
                        "how it is structured, and anything that looks unfinished.",
                ),
            ),
        )

        "triage_issues" -> listOf(
            PromptMessage(
                role = "user",
                content = ToolContent(
                    type = "text",
                    text = "Triage the open issues of {owner}/{repo}. Group them by theme, flag the " +
                        "ones that look stale, and suggest a label for each.",
                ),
            ),
        )

        else -> emptyList()
    }

    private suspend fun readResource(uri: String): JsonObject {
        val parts = uri.removePrefix("repo://").split('/')
        return when {
            uri == "github://user" -> {
                val user = runCatching { tools.userProfile() }.getOrNull()
                successResponse(
                    null,
                    ResourceContents(
                        listOf(
                            ResourceContent(
                                uri = uri,
                                mimeType = "application/json",
                                text = GitHubJson.encodeToString(
                                    JsonObject.serializer(),
                                    user ?: buildJsonObject { },
                                ),
                            ),
                        ),
                    ),
                )
            }

            parts.size >= 2 -> {
                val owner = parts[0]
                val repo = parts[1]
                val contents = runCatching { tools.readmeText(owner, repo) }.getOrNull()
                successResponse(
                    null,
                    ResourceContents(
                        listOf(
                            ResourceContent(
                                uri = uri,
                                mimeType = "text/markdown",
                                text = contents ?: "",
                            ),
                        ),
                    ),
                )
            }

            else -> errorResponse(null, -32602, "Unsupported resource uri: $uri")
        }
    }

    // ── JSON-RPC envelope helpers ───────────────────────────────────────────

    private fun successResponse(id: JsonElement?, result: Any): JsonObject {
        val encoded: JsonElement = when (result) {
            is JsonElement -> result
            is InitializeResult -> GitHubJson.encodeToJsonElement(
                InitializeResult.serializer(),
                result,
            )

            is ListToolsResult -> GitHubJson.encodeToJsonElement(
                ListToolsResult.serializer(),
                result,
            )

            is CallToolResult -> GitHubJson.encodeToJsonElement(
                CallToolResult.serializer(),
                result,
            )

            is ListResourcesResult -> GitHubJson.encodeToJsonElement(
                ListResourcesResult.serializer(),
                result,
            )

            is ListResourceTemplatesResult -> GitHubJson.encodeToJsonElement(
                ListResourceTemplatesResult.serializer(),
                result,
            )

            is ResourceContents -> GitHubJson.encodeToJsonElement(
                ResourceContents.serializer(),
                result,
            )

            is ListPromptsResult -> GitHubJson.encodeToJsonElement(
                ListPromptsResult.serializer(),
                result,
            )

            is GetPromptResult -> GitHubJson.encodeToJsonElement(
                GetPromptResult.serializer(),
                result,
            )

            else -> GitHubJson.encodeToJsonElement(
                kotlinx.serialization.json.JsonObject.serializer(),
                buildJsonObject { },
            )
        }
        return buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id ?: JsonNull)
            put("result", encoded)
        }
    }

    private fun errorResponse(id: JsonElement?, code: Int, message: String): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id ?: JsonNull)
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }

    private fun emptyResponse(): JsonObject = buildJsonObject { }

    // ── HTTP writing ────────────────────────────────────────────────────────

    private fun writeJson(output: OutputStream, status: Int, payload: JsonObject) {
        val body = GitHubJson.encodeToString(JsonObject.serializer(), payload)
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $status ${statusText(status)}\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun writeSse(output: OutputStream, payload: JsonObject) {
        val body = GitHubJson.encodeToString(JsonObject.serializer(), payload)
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/event-stream\r\n")
            append("Cache-Control: no-cache\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write("event: message\n".toByteArray(StandardCharsets.UTF_8))
        output.write("data: $body\n\n".toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    private fun statusText(status: Int): String = when (status) {
        200 -> "OK"
        401 -> "Unauthorized"
        404 -> "Not Found"
        500 -> "Internal Server Error"
        else -> "OK"
    }

    companion object {
        const val DEFAULT_PORT = 8787

        /** A request head larger than this is treated as hostile. */
        private const val MAX_HEAD_BYTES = 64 * 1024

        /** A single header line larger than this is treated as hostile. */
        private const val MAX_LINE_BYTES = 16 * 1024

        /** The LF byte, as an Int, so it can be compared with a read byte. */
        private const val NEWLINE = 10

        private val INSTRUCTIONS = """
            GitDroid exposes the GitHub account that is signed in on the phone.

            Useful tools:
            - list_repositories / get_repository — inspect repositories
            - search_repositories / search_code — find anything on GitHub, public or private
            - get_file_contents / get_repository_tree — read code
            - create_or_update_file / delete_file — change code (needs the blob sha to update)
            - list_issues / create_issue / update_issue — work on issues
            - list_pull_requests / create_pull_request / merge_pull_request — work on pull requests
            - list_workflows / list_workflow_runs / rerun_workflow / cancel_workflow_run / dispatch_workflow — drive CI
            - list_releases / list_tags — inspect releases

            The tool names match the official GitHub MCP server, so the same
            calls work against either.
        """.trimIndent()
    }
}
