package dev.gitdroid.app.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The MCP wire format.
 *
 * GitDroid speaks the **2025-06-18** revision of the Model Context Protocol:
 * JSON-RPC 2.0 over HTTP, with the `initialize` / `tools/list` / `tools/call`
 * handshake plus the optional `resources` and `prompts` surfaces.
 */
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String,
    val params: JsonElement? = null,
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

/** The `initialize` result. */
@Serializable
data class InitializeResult(
    val protocolVersion: String,
    val capabilities: ServerCapabilities,
    val serverInfo: ServerInfo,
    val instructions: String? = null,
)

@Serializable
data class ServerCapabilities(
    val tools: ToolCapability? = ToolCapability(listChanged = false),
    val resources: ResourceCapability? = ResourceCapability(subscribe = false, listChanged = false),
    val prompts: PromptCapability? = PromptCapability(listChanged = false),
    val logging: Map<String, String> = emptyMap(),
)

@Serializable
data class ToolCapability(val listChanged: Boolean = false)

@Serializable
data class ResourceCapability(val subscribe: Boolean = false, val listChanged: Boolean = false)

@Serializable
data class PromptCapability(val listChanged: Boolean = false)

@Serializable
data class ServerInfo(
    val name: String,
    val version: String,
    val title: String? = null,
)

/** A tool as advertised by `tools/list`. */
@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: ToolSchema,
)

@Serializable
data class ToolSchema(
    val type: String = "object",
    val properties: Map<String, ToolProperty> = emptyMap(),
    val required: List<String> = emptyList(),
    val additionalProperties: Boolean = false,
)

@Serializable
data class ToolProperty(
    val type: String,
    val description: String,
    val enum: List<String>? = null,
    val default: JsonElement? = null,
)

/** The `tools/list` result. */
@Serializable
data class ListToolsResult(
    val tools: List<ToolDefinition>,
    val nextCursor: String? = null,
)

/** The `tools/call` result. */
@Serializable
data class CallToolResult(
    val content: List<ToolContent>,
    val isError: Boolean = false,
    val structuredContent: JsonElement? = null,
)

@Serializable
data class ToolContent(
    val type: String = "text",
    val text: String? = null,
    @SerialName("mimeType") val mimeType: String? = null,
    @SerialName("resource") val resource: JsonElement? = null,
)

/** A resource as advertised by `resources/list`. */
@Serializable
data class ResourceDefinition(
    val uri: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null,
)

@Serializable
data class ListResourcesResult(
    val resources: List<ResourceDefinition>,
    val nextCursor: String? = null,
)

@Serializable
data class ListResourceTemplatesResult(
    val resourceTemplates: List<ResourceTemplate>,
)

@Serializable
data class ResourceTemplate(
    val uriTemplate: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null,
)

@Serializable
data class ResourceContents(
    val contents: List<ResourceContent>,
)

@Serializable
data class ResourceContent(
    val uri: String,
    val mimeType: String? = null,
    val text: String? = null,
    val blob: String? = null,
)

/** A prompt as advertised by `prompts/list`. */
@Serializable
data class PromptDefinition(
    val name: String,
    val description: String? = null,
    val arguments: List<PromptArgument> = emptyList(),
)

@Serializable
data class PromptArgument(
    val name: String,
    val description: String? = null,
    val required: Boolean = false,
)

@Serializable
data class ListPromptsResult(
    val prompts: List<PromptDefinition>,
)

@Serializable
data class GetPromptResult(
    val description: String? = null,
    val messages: List<PromptMessage>,
)

@Serializable
data class PromptMessage(
    val role: String = "user",
    val content: ToolContent,
)

/** The protocol revision GitDroid implements. */
const val MCP_PROTOCOL_VERSION = "2025-06-18"

/** Protocol revisions this server also accepts, newest first. */
val SUPPORTED_PROTOCOL_VERSIONS = listOf(
    MCP_PROTOCOL_VERSION,
    "2025-03-26",
    "2024-11-05",
)
