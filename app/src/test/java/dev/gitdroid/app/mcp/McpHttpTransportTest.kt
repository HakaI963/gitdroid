package dev.gitdroid.app.mcp

import dev.gitdroid.app.core.api.GitHubApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Tests for the MCP HTTP transport.
 *
 * These run against a real socket, because the bugs they guard against —
 * a body read as characters instead of bytes, a `BufferedReader` that reads
 * past the head — only appear over the wire.
 */
class McpHttpTransportTest {

    private fun startServer(scope: CoroutineScope): Pair<McpServer, Int> {
        val api = GitHubApi(tokenProvider = { "test-token" })
        val server = McpServer(
            tools = McpTools(api),
            scope = scope,
            // Port 0 lets the OS pick a free port, so the tests never fight
            // over 8787.
            port = 0,
            bearerToken = { "test-token" },
        )
        val port = server.start()
        return server to port
    }

    @Test
    fun `health endpoint answers without a token`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val (server, port) = startServer(scope)
        try {
            val connection = URL("http://127.0.0.1:$port/health").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000

            assertEquals(200, connection.responseCode)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            assertTrue(body.contains("\"ok\""))
        } finally {
            server.stop()
        }
    }

    @Test
    fun `a request without the bearer token is rejected`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val (server, port) = startServer(scope)
        try {
            val connection = URL("http://127.0.0.1:$port/mcp").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use {
                it.write("""{"jsonrpc":"2.0","id":1,"method":"ping"}""".toByteArray())
            }
            assertEquals(401, connection.responseCode)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `a request with the bearer token is answered`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val (server, port) = startServer(scope)
        try {
            val connection = URL("http://127.0.0.1:$port/mcp").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer test-token")
            connection.outputStream.use {
                it.write("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""".toByteArray())
            }

            assertEquals(200, connection.responseCode)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            assertTrue(body.contains("search_repositories"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun `a body with multi-byte characters survives the round trip`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val (server, port) = startServer(scope)
        try {
            // The repository name carries a non-ASCII character, and the
            // Content-Length is a *byte* count. Reading the body as UTF-16
            // characters would corrupt it.
            val payload = """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_repository","arguments":{"owner":"ユーザー","repo":"café-Ω"}}}"""

            val connection = URL("http://127.0.0.1:$port/mcp").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer test-token")
            connection.outputStream.use {
                it.write(payload.toByteArray(StandardCharsets.UTF_8))
            }

            assertEquals(200, connection.responseCode)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            // The tool ran and reported its own validation error, which means
            // the arguments arrived intact and were parsed as UTF-8.
            assertTrue(
                "the multi-byte arguments were mangled: $body",
                body.contains("isError"),
            )
            assertTrue(body.contains("true"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun `an sse accept header switches the response framing`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val (server, port) = startServer(scope)
        try {
            val connection = URL("http://127.0.0.1:$port/mcp").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json, text/event-stream")
            connection.setRequestProperty("Authorization", "Bearer test-token")
            connection.outputStream.use {
                it.write("""{"jsonrpc":"2.0","id":1,"method":"ping"}""".toByteArray())
            }

            assertEquals(200, connection.responseCode)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            assertTrue(body.contains("event: message"))
            assertTrue(body.contains("data: "))
        } finally {
            server.stop()
        }
    }
}
