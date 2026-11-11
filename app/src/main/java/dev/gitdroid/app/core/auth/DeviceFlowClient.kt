package dev.gitdroid.app.core.auth

import dev.gitdroid.app.core.api.GitHubException
import dev.gitdroid.app.core.api.GitHubJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
 *
 * The HTTP call is injected rather than hard-wired. The polling state machine
 * is the part worth testing, and a socket makes it slow and flaky.
 */
class DeviceFlowClient(
    private val clientId: String,
    private val post: suspend (url: String, body: JsonObject) -> JsonObject = defaultPost,
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
    suspend fun requestCode(scope: String = DEFAULT_SCOPE): DeviceCode =
        withContext(Dispatchers.IO) {
            val json = post(
                deviceCodeUrl,
                buildJsonObject {
                    put("client_id", clientId)
                    put("scope", scope)
                },
            )
            val deviceCode = json.string("device_code")
                ?: throw IOException("GitHub did not return a device code")
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
     * Polls until the user approves, declines, or the code expires.
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

            val json = post(
                accessTokenUrl,
                buildJsonObject {
                    put("client_id", clientId)
                    put("device_code", deviceCode.deviceCode)
                    put("grant_type", GRANT_TYPE)
                    if (scope.isNotBlank()) put("scope", scope)
                },
            )

            when (val error = json.string("error")) {
                // The absence of an `error` field *is* the success case: GitHub
                // answers with the access token. Treating it like a pending
                // request would keep polling until the code expired while the
                // user watched a spinner — which is exactly the bug this
                // comment guards against.
                null -> return@withContext TokenResult(
                    accessToken = json.string("access_token")
                        ?: throw GitHubException(0, "GitHub sent no access token"),
                    refreshToken = json.string("refresh_token"),
                    tokenType = json.string("token_type").orEmpty(),
                    scope = json.string("scope").orEmpty(),
                    expiresInSeconds = json.int("expires_in") ?: 0,
                )

                "authorization_pending" -> delay(deviceCode.intervalSeconds * 1000L)

                // GitHub asks for a longer gap; honour it or the flow stalls.
                "slow_down" -> delay((deviceCode.intervalSeconds + 5) * 1000L)

                "access_denied" -> throw AuthorizationDenied(
                    json.string("error_description") ?: "Sign-in was declined",
                )

                "expired_token" -> throw CodeExpired(
                    json.string("error_description") ?: "The code expired — start again",
                )

                else -> throw GitHubException(0, json.string("error_description") ?: "Sign-in failed")
            }
        }
        throw CodeExpired("The code expired — start again")
    }

    /** Exchanges a refresh token for a new access token. */
    suspend fun refresh(refreshToken: String): TokenResult = withContext(Dispatchers.IO) {
        val json = post(
            accessTokenUrl,
            buildJsonObject {
                put("client_id", clientId)
                put("refresh_token", refreshToken)
                put("grant_type", "refresh_token")
            },
        )
        TokenResult(
            accessToken = json.string("access_token")
                ?: throw IOException("Could not refresh the token"),
            refreshToken = json.string("refresh_token"),
            tokenType = json.string("token_type").orEmpty(),
            scope = json.string("scope").orEmpty(),
            expiresInSeconds = json.int("expires_in") ?: 0,
        )
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"

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

        /** The real transport: POST JSON to GitHub and read JSON back. */
        private val defaultPost: suspend (String, JsonObject) -> JsonObject = { url, body ->
            val client = sharedClient
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "GitDroid/1.0 (Android)")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw GitHubException(response.code, "GitHub returned HTTP ${response.code}")
                }
                GitHubJson.parseToJsonElement(text) as? JsonObject
                    ?: throw IOException("Unexpected response from GitHub")
            }
        }

        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }
    }
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)
        ?.takeIf { it.isString || it.content != "null" }
        ?.content

private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()
