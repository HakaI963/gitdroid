package dev.gitdroid.app.core.auth

import dev.gitdroid.app.core.api.GitHubException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** How the current session was established. */
enum class AuthMethod {
    /** GitHub OAuth device flow — the token refreshes on its own. */
    DEVICE_FLOW,

    /** A personal access token pasted by the user. */
    PAT,
}

/**
 * GitHub's OAuth *device flow*.
 *
 * This is the right flow for a phone: the user types a short code on any
 * device, the app polls for the token in the background, and no browser
 * redirect has to be plumbed through the app. It also works with an
 * OAuth App client id that ships in the binary — no client secret is needed.
 */
class DeviceFlowClient(
    private val clientId: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val deviceCodeUrl: String = "https://github.com/login/device/code",
    private val accessTokenUrl: String = "https://github.com/login/oauth/access_token",
) {

    /** The codes shown to the user. */
    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val verificationUriComplete: String?,
        val expiresInSeconds: Int,
        val intervalSeconds: Int,
    )

    /** Result of a completed device-flow exchange. */
    data class TokenResult(
        val accessToken: String,
        val refreshToken: String?,
        val tokenType: String,
        val scope: String,
        val expiresInSeconds: Int,
    )

    /** Raised when the user denies the request. */
    class AuthorizationDenied(message: String) : Exception(message)

    /** Raised when the device code expires before the user finishes. */
    class CodeExpired(message: String) : Exception(message)

    /** Asks GitHub for a fresh user code. */
    suspend fun requestCode(scope: String = DEFAULT_SCOPE): DeviceCode = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("client_id", clientId)
            put("scope", scope)
        }.toString()

        val request = Request.Builder()
            .url(deviceCodeUrl)
            .header("Accept", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val json = executeJson(request)
        val deviceCode = json.string("device_code") ?: throw IOException("GitHub did not return a device code")
        DeviceCode(
            deviceCode = deviceCode,
            userCode = json.string("user_code").orEmpty(),
            verificationUri = json.string("verification_uri").orEmpty(),
            verificationUriComplete = json.string("verification_uri_complete"),
            expiresInSeconds = json.int("expires_in") ?: 900,
            intervalSeconds = json.int("interval") ?: 5,
        )
    }

    /**
     * Polls until the user approves (or the code expires).
     *
     * [onTick] is called once per poll so the UI can show progress.
     */
    suspend fun awaitToken(
        deviceCode: DeviceCode,
        scope: String = DEFAULT_SCOPE,
        onTick: (attempt: Int) -> Unit = {},
    ): TokenResult = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + deviceCode.expiresInSeconds * 1000L
        var attempt = 0

        while (System.currentTimeMillis() < deadline) {
            attempt++
            onTick(attempt)

            val body = buildJsonObject {
                put("client_id", clientId)
                put("device_code", deviceCode.deviceCode)
                put("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                if (scope.isNotBlank()) put("scope", scope)
            }.toString()

            val request = Request.Builder()
                .url(accessTokenUrl)
                .header("Accept", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val json = executeJson(request)
            val error = json.string("error")
            when {
                // No `error` field means GitHub sent the token. This has to
                // be checked before the pending branch, or a successful
                // sign-in is mistaken for "keep waiting" and the code
                // eventually expires while the user watches a spinner.
                error == null -> {
                    val token = json.string("access_token")
                        ?: throw GitHubException(0, "GitHub sent no access token")
                    return@withContext TokenResult(
                        accessToken = token,
                        refreshToken = json.string("refresh_token"),
                        tokenType = json.string("token_type").orEmpty(),
                        scope = json.string("scope").orEmpty(),
                        expiresInSeconds = json.int("expires_in") ?: 0,
                    )
                }

                error == "authorization_pending" -> {
                    delay(deviceCode.intervalSeconds * 1000L)
                }

                error == "slow_down" -> {
                    delay((deviceCode.intervalSeconds + 5) * 1000L)
                }

                error == "access_denied" -> {
                    throw AuthorizationDenied("Sign-in was declined")
                }

                error == "expired_token" -> {
                    throw CodeExpired("The code expired — start again")
                }

                else -> throw GitHubException(0, json.string("error_description") ?: "Sign-in failed")
            }
        }
        throw CodeExpired("The code expired — start again")
    }

    /** Exchanges a refresh token for a new access token. */
    suspend fun refresh(refreshToken: String): TokenResult = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("client_id", clientId)
            put("refresh_token", refreshToken)
            put("grant_type", "refresh_token")
        }.toString()

        val request = Request.Builder()
            .url(accessTokenUrl)
            .header("Accept", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val json = executeJson(request)
        val token = json.string("access_token") ?: throw IOException("Could not refresh the token")
        TokenResult(
            accessToken = token,
            refreshToken = json.string("refresh_token"),
            tokenType = json.string("token_type").orEmpty(),
            scope = json.string("scope").orEmpty(),
            expiresInSeconds = json.int("expires_in") ?: 0,
        )
    }

    private fun executeJson(request: Request): JsonObject {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw GitHubException(0, e.message ?: "Network error")
        }
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                throw GitHubException(it.code, "GitHub returned HTTP ${it.code}")
            }
            val element = runCatching { Json.parseToJsonElement(body) }.getOrNull()
            return element as? JsonObject ?: throw IOException("Unexpected response from GitHub")
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNullSafe()

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.contentOrNullSafe()?.toIntOrNull()

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (this is JsonPrimitive && content != "null") content else null

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * The scope GitDroid asks for.
         *
         * `repo` covers private repositories, `workflow` allows dispatching
         * Actions runs, and `read:org` lets the app list your organizations.
         */
        const val DEFAULT_SCOPE = "repo workflow read:org"

        /**
         * The OAuth App client id the app ships with.
         *
         * Device flow needs a client id but **no client secret**, which is why
         * it is safe to embed. Replace it with your own registered OAuth App
         * when you fork this project — see the README.
         */
        const val CLIENT_ID = "Iv1.b507a08c87ecfe98"
    }
}
