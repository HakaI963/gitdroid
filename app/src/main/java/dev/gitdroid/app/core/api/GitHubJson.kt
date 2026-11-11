package dev.gitdroid.app.core.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Shared JSON codec: GitHub adds fields over time, so unknown keys are ignored. */
val GitHubJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}

/**
 * A failed GitHub call.
 *
 * [status] is the HTTP status when the failure came from the API, or `0` for
 * transport-level problems (no network, DNS failure, TLS error).
 */
class GitHubException(
    val status: Int,
    override val message: String,
    val documentationUrl: String? = null,
    val body: String? = null,
) : Exception(message) {

    val isAuthFailure: Boolean get() = status == 401 || status == 403
    val isNotFound: Boolean get() = status == 404
    val isRateLimited: Boolean get() = status == 403 && body?.contains("rate limit", ignoreCase = true) == true

    /** A short, human-readable reason suitable for a snackbar. */
    fun displayMessage(): String = when {
        status == 0 -> "Network unavailable — check your connection"
        status == 401 -> "Token rejected — sign in again"
        isRateLimited -> "GitHub API rate limit reached"
        status == 403 -> "Not permitted — the token lacks this scope"
        status == 404 -> "Not found"
        status == 422 -> message
        status in 500..599 -> "GitHub is having trouble — try again"
        else -> message
    }
}

/** Thrown when the server returns a body that is not the JSON we expect. */
class ParseException(message: String) : Exception(message)

/** Helpers for reading loosely-typed GitHub payloads. */
object Json {
    fun obj(element: JsonElement?): JsonObject? = element as? JsonObject

    fun arr(element: JsonElement?): JsonArray? = element as? JsonArray

    fun string(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content

    fun int(element: JsonElement?): Int? = string(element)?.toIntOrNull()

    fun long(element: JsonElement?): Long? = string(element)?.toLongOrNull()

    fun bool(element: JsonElement?): Boolean? = when (element) {
        is JsonPrimitive -> when {
            element.isString -> element.content.toBooleanStrictOrNull()
            element.content == "true" -> true
            element.content == "false" -> false
            else -> null
        }

        else -> null
    }

    fun strings(element: JsonElement?): List<String> =
        (element as? JsonArray)?.mapNotNull { string(it) } ?: emptyList()
}
