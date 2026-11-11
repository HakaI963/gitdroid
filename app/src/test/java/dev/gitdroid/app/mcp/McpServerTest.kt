package dev.gitdroid.app.mcp

import dev.gitdroid.app.core.api.GitHubApi
import dev.gitdroid.app.core.api.GitHubJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the MCP protocol surface.
 *
 * The tool implementations need a live GitHub account, so these tests cover
 * the protocol layer: the handshake, the tool registry and the error
 * contract — the parts an agent depends on.
 */
class McpServerTest {

    private fun server(scope: CoroutineScope): McpServer {
        val api = GitHubApi(tokenProvider = { "test-token" })
        return McpServer(
            tools = McpTools(api),
            scope = scope,
            bearerToken = { "test-token" },
        )
    }

    @Test
    fun `initialize returns the protocol version and capabilities`() = runTest {
        val server = server(this)
        val response = server.handleRpc(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}""",
        )

        val result = response["result"]!!.jsonObject
        assertEquals("2025-06-18", result["protocolVersion"]!!.jsonPrimitive.content)
        assertEquals("gitdroid", result["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertNotNull(result["capabilities"]!!.jsonObject["tools"])
        assertEquals("2.0", response["jsonrpc"]!!.jsonPrimitive.content)
    }

    @Test
    fun `initialize echoes a supported protocol version the client asked for`() = runTest {
        val server = server(this)
        val response = server.handleRpc(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05"}}""",
        )
        assertEquals(
            "2024-11-05",
            response["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `initialize falls back to its own version for an unknown one`() = runTest {
        val server = server(this)
        val response = server.handleRpc(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"1999-01-01"}}""",
        )
        assertEquals(
            MCP_PROTOCOL_VERSION,
            response["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `tools list advertises the GitHub tool names`() = runTest {
        val server = server(this)
        val response = server.handleRpc("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")

        val tools = response["result"]!!.jsonObject["tools"]!!.jsonArray
        val names = tools.map { it.jsonObject["name"]!!.jsonPrimitive.content }.toSet()

        // The names deliberately match the official GitHub MCP server, so an
        // agent's existing instructions keep working.
        assertTrue("search_repositories" in names)
        assertTrue("get_file_contents" in names)
        assertTrue("create_or_update_file" in names)
        assertTrue("list_workflow_runs" in names)
        assertTrue("dispatch_workflow" in names)
        assertTrue("merge_pull_request" in names)
        assertTrue(names.size >= 25)
    }

    @Test
    fun `every tool has a description and a schema`() = runTest {
        val server = server(this)
        val response = server.handleRpc("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")

        response["result"]!!.jsonObject["tools"]!!.jsonArray.forEach { entry ->
            val tool = entry.jsonObject
            val name = tool["name"]!!.jsonPrimitive.content
            assertTrue("$name has no description", tool["description"]!!.jsonPrimitive.content.isNotBlank())
            val schema = tool["inputSchema"]!!.jsonObject
            assertEquals("object", schema["type"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun `tools call with an unknown tool returns an error result, not a protocol error`() = runTest {
        val server = server(this)
        val response = server.handleRpc(
            """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"nope","arguments":{}}}""",
        )

        // The MCP spec wants failures reported in the result, so the model
        // can see them and correct itself.
        assertTrue(response["error"] == null)
        val result = response["result"]!!.jsonObject
        assertEquals(true, result["isError"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `resources list exposes the current user and repository`() = runTest {
        val server = server(this)
        val response = server.handleRpc("""{"jsonrpc":"2.0","id":4,"method":"resources/list"}""")

        val uris = response["result"]!!.jsonObject["resources"]!!.jsonArray
            .map { it.jsonObject["uri"]!!.jsonPrimitive.content }
        assertTrue("github://user" in uris)
    }

    @Test
    fun `prompts list exposes the review and triage prompts`() = runTest {
        val server = server(this)
        val response = server.handleRpc("""{"jsonrpc":"2.0","id":5,"method":"prompts/list"}""")

        val names = response["result"]!!.jsonObject["prompts"]!!.jsonArray
            .map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertTrue("review_repo" in names)
        assertTrue("triage_issues" in names)
    }

    @Test
    fun `an unknown method returns a protocol error`() = runTest {
        val server = server(this)
        val response = server.handleRpc("""{"jsonrpc":"2.0","id":6,"method":"nope"}""")

        val error = response["error"]!!.jsonObject
        assertEquals(-32601, error["code"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `a notification gets no response body`() = runTest {
        val server = server(this)
        val response = server.handleRpc("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        assertEquals(0, response.size)
    }

    @Test
    fun `malformed json returns a parse error`() = runTest {
        val server = server(this)
        val response = server.handleRpc("not json at all")
        val error = response["error"]!!.jsonObject
        assertEquals(-32700, error["code"]!!.jsonPrimitive.content.toInt())
    }
}
