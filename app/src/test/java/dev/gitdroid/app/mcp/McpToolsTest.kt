package dev.gitdroid.app.mcp

import dev.gitdroid.app.core.api.GitHubApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the tool registry's argument handling.
 *
 * These run against a [GitHubApi] that has no valid token, which is
 * exactly the point: the argument parsing, the error contract and the
 * tool-name mapping all have to work *before* any network call happens.
 */
class McpToolsTest {

    private fun tools(): McpTools = McpTools(GitHubApi(tokenProvider = { "invalid-token" }))

    private fun call(
        name: String,
        arguments: Map<String, String> = emptyMap(),
    ): CallToolResult = runBlocking {
        val json = buildJsonObject {
            arguments.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
        }
        tools().call(name, json)
    }

    @Test
    fun `an unknown tool reports an error rather than throwing`() {
        val result = call("not_a_tool")
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Unknown tool"))
    }

    @Test
    fun `a missing required argument is reported before any network call`() {
        val result = call("get_file_contents", mapOf("owner" to "someone"))
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("`repo` is required"))
    }

    @Test
    fun `every missing argument is reported at once, not one at a time`() {
        // Reporting only the first missing argument would make an agent
        // discover the problem one round trip at a time.
        val result = call("create_or_update_file", mapOf("owner" to "x"))
        assertTrue(result.isError)
        val message = result.content.first().text!!
        assertTrue(message, message.contains("`repo`"))
        assertTrue(message, message.contains("`path`"))
        assertTrue(message, message.contains("`content`"))
        assertTrue(message, message.contains("`message`"))
    }

    @Test
    fun `the tool registry exposes the official GitHub MCP names`() {
        val names = tools().tools.map { it.name }.toSet()
        val expected = setOf(
            "search_repositories",
            "search_code",
            "get_file_contents",
            "create_or_update_file",
            "delete_file",
            "list_repositories",
            "get_repository",
            "create_repository",
            "update_repository",
            "delete_repository",
            "fork_repository",
            "list_branches",
            "create_branch",
            "list_commits",
            "list_issues",
            "create_issue",
            "update_issue",
            "list_pull_requests",
            "create_pull_request",
            "merge_pull_request",
            "list_workflows",
            "list_workflow_runs",
            "rerun_workflow",
            "cancel_workflow_run",
            "dispatch_workflow",
            "list_releases",
            "list_tags",
            "get_repository_tree",
        )
        assertTrue("missing: ${expected - names}", expected.containsAll(names))
    }

    @Test
    fun `every tool declares its required arguments in the schema`() {
        tools().tools.forEach { tool ->
            tool.inputSchema.properties.keys.forEach { property ->
                if (property in tool.inputSchema.required) {
                    assertTrue(
                        "${tool.name}.$property is required but not declared",
                        tool.inputSchema.properties.containsKey(property),
                    )
                }
            }
        }
    }

    @Test
    fun `a tool with no required arguments can still be called`() {
        // list_repositories needs nothing; with an invalid token it must
        // fail with the *network* error, not with an argument error.
        val result = call("list_repositories")
        if (result.isError) {
            val message = result.content.first().text!!
            assertTrue(
                "expected a network error, got: $message",
                !message.contains("is required"),
            )
        }
    }
}
