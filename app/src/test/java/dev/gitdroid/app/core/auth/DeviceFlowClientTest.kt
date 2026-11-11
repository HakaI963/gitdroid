package dev.gitdroid.app.core.auth

import dev.gitdroid.app.core.api.GitHubJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Device-flow tests against a stubbed transport.
 *
 * The bug these guard against is the one that made sign-in hang forever: a
 * successful poll returns a body with **no `error` field**, and that was
 * treated the same as `authorization_pending`, so the app polled until the
 * code expired while the user watched a spinner.
 */
class DeviceFlowClientTest {

    /** The responses the stub serves, in order. */
    private val replies = ArrayDeque<JsonObject>()

    /** The request bodies the stub received. */
    private val sent = mutableListOf<JsonObject>()

    private fun json(text: String): JsonObject =
        GitHubJson.parseToJsonElement(text) as JsonObject

    private fun client() = DeviceFlowClient(clientId = "test-client", post = { _, body ->
        sent.add(body)
        if (replies.isEmpty()) json("""{"error":"authorization_pending"}""") else replies.removeFirst()
    })

    private fun code(expires: Int = 60, interval: Int = 0) = DeviceFlowClient.DeviceCode(
        deviceCode = "DEV-CODE",
        userCode = "ABCD-1234",
        verificationUri = "https://github.com/login/device",
        verificationUriComplete = null,
        expiresInSeconds = expires,
        intervalSeconds = interval,
    )

    @Test
    fun `requestCode returns the user code to show`() = runBlocking {
        replies.addLast(
            json(
                """{"device_code":"DEV-CODE","user_code":"ABCD-1234",
                   "verification_uri":"https://github.com/login/device",
                   "verification_uri_complete":"https://github.com/login/device?code=ABCD-1234",
                   "expires_in":900,"interval":5}""",
            ),
        )
        val result = client().requestCode()
        assertEquals("ABCD-1234", result.userCode)
        assertEquals("DEV-CODE", result.deviceCode)
        assertEquals(900, result.expiresInSeconds)
        assertEquals(5, result.intervalSeconds)
    }

    @Test
    fun `awaitToken returns the token when the response carries no error field`() {
        // The regression. A body with no `error` field *is* the token.
        replies.addLast(
            json(
                """{"access_token":"ghu_token","token_type":"bearer",
                   "scope":"repo workflow","expires_in":28800,"refresh_token":"ghr_refresh"}""",
            ),
        )

        val result = runBlocking { client().awaitToken(code()) }

        assertEquals("ghu_token", result.accessToken)
        assertEquals("ghr_refresh", result.refreshToken)
        assertEquals("repo workflow", result.scope)
        assertEquals(28800, result.expiresInSeconds)
    }

    @Test
    fun `awaitToken polls through pending and then returns the token`() {
        var ticks = 0
        replies.addLast(json("""{"error":"authorization_pending"}"""))
        replies.addLast(json("""{"error":"authorization_pending"}"""))
        replies.addLast(json("""{"access_token":"ghu_third","token_type":"bearer","expires_in":0}"""))

        val result = runBlocking { client().awaitToken(code(), onTick = { ticks++ }) }

        assertEquals("ghu_third", result.accessToken)
        assertEquals("expected three polls, saw $ticks", 3, ticks)
    }

    @Test
    fun `the poll sends the device flow grant type and the device code`() {
        replies.addLast(json("""{"access_token":"ghu_x","token_type":"bearer","expires_in":0}"""))
        runBlocking { client().awaitToken(code()) }

        val body = sent.first()
        assertEquals(
            "urn:ietf:params:oauth:grant-type:device_code",
            body["grant_type"]?.toString()?.trim('"'),
        )
        assertEquals("\"DEV-CODE\"", body["device_code"].toString())
        assertEquals("\"test-client\"", body["client_id"].toString())
    }

    @Test
    fun `a declined authorisation surfaces as AuthorizationDenied`() {
        replies.addLast(json("""{"error":"access_denied","error_description":"the user declined"}"""))
        val failure = runCatching { runBlocking { client().awaitToken(code()) } }
        assertTrue(
            "expected AuthorizationDenied, got ${failure.exceptionOrNull()}",
            failure.exceptionOrNull() is DeviceFlowClient.AuthorizationDenied,
        )
    }

    @Test
    fun `an expired device code surfaces as CodeExpired`() {
        replies.addLast(json("""{"error":"expired_token","error_description":"code expired"}"""))
        val failure = runCatching { runBlocking { client().awaitToken(code()) } }
        assertTrue(
            "expected CodeExpired, got ${failure.exceptionOrNull()}",
            failure.exceptionOrNull() is DeviceFlowClient.CodeExpired,
        )
    }

    @Test
    fun `polling stops with CodeExpired once the deadline passes`() {
        // Every poll says pending, and the code's lifetime is already spent.
        val failure = runCatching {
            runBlocking { client().awaitToken(code(expires = 0)) }
        }
        assertTrue(
            "expected CodeExpired, got ${failure.exceptionOrNull()}",
            failure.exceptionOrNull() is DeviceFlowClient.CodeExpired,
        )
    }

    @Test
    fun `refresh exchanges the refresh token for a new access token`() {
        replies.addLast(
            json("""{"access_token":"ghu_rotated","token_type":"bearer","expires_in":7200}"""),
        )
        val result = runBlocking { client().refresh("ghr_old") }
        assertEquals("ghu_rotated", result.accessToken)
        assertEquals("\"refresh_token\"", sent.first()["grant_type"].toString())
    }
}
