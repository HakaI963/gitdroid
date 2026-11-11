package dev.gitdroid.app.core.auth

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/**
 * Device-flow tests against a stub of GitHub's endpoints.
 *
 * The bug these guard against: a successful poll returns a body with **no
 * `error` field**, and that was treated the same as `authorization_pending`,
 * so the app polled until the code expired while the user watched a spinner.
 */
class DeviceFlowClientTest {

    private lateinit var server: HttpServer
    private var port = 0

    /** The responses the stub serves, in order. */
    private val queue = ArrayDeque<String>()

    /** Bodies the stub actually received, so the test can assert on them. */
    private val received = mutableListOf<String>()

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/login/device/code") { exchange ->
            val body = """
                {"device_code":"DEV-CODE","user_code":"ABCD-1234",
                 "verification_uri":"https://github.com/login/device",
                 "verification_uri_complete":"https://github.com/login/device?code=ABCD-1234",
                 "expires_in":900,"interval":0}
            """.trimIndent()
            respond(exchange, body)
        }
        server.createContext("/login/oauth/access_token") { exchange ->
            received.add(exchange.requestBody.readBytes().decodeToString())
            val next = if (queue.isEmpty()) {
                """{"error":"authorization_pending"}"""
            } else {
                queue.removeFirst()
            }
            respond(exchange, next)
        }
        server.start()
        port = server.address.port
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    private fun respond(exchange: com.sun.net.httpserver.HttpExchange, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun client() = DeviceFlowClient(
        clientId = "test-client",
        deviceCodeUrl = "http://127.0.0.1:$port/login/device/code",
        accessTokenUrl = "http://127.0.0.1:$port/login/oauth/access_token",
    )

    @Test
    fun `requestCode returns the user code to show`() = runBlocking {
        val code = client().requestCode()
        assertEquals("ABCD-1234", code.userCode)
        assertEquals("DEV-CODE", code.deviceCode)
        assertEquals(900, code.expiresInSeconds)
    }

    @Test
    fun `awaitToken returns the token as soon as GitHub stops reporting an error`() {
        // This is the regression: a body with no `error` field *is* the token.
        queue.addLast(
            """{"access_token":"ghu_token","token_type":"bearer",
               "scope":"repo workflow","expires_in":28800,"refresh_token":"ghr_refresh"}""",
        )

        val result = runBlocking {
            client().awaitToken(
                DeviceFlowClient.DeviceCode(
                    deviceCode = "DEV-CODE",
                    userCode = "ABCD-1234",
                    verificationUri = "https://github.com/login/device",
                    verificationUriComplete = null,
                    expiresInSeconds = 60,
                    intervalSeconds = 0,
                ),
            )
        }

        assertEquals("ghu_token", result.accessToken)
        assertEquals("ghr_refresh", result.refreshToken)
        assertEquals("repo workflow", result.scope)
        assertEquals(28800, result.expiresInSeconds)
    }

    @Test
    fun `awaitToken keeps polling through authorization_pending then succeeds`() {
        var ticks = 0
        // Two pending responses, then the token.
        queue.addLast("""{"error":"authorization_pending"}""")
        queue.addLast(
            """{"access_token":"ghu_after_two","token_type":"bearer","scope":"repo","expires_in":10}""",
        )

        val result = runBlocking {
            client().awaitToken(
                DeviceFlowClient.DeviceCode(
                    deviceCode = "DEV-CODE",
                    userCode = "ABCD-1234",
                    verificationUri = "",
                    verificationUriComplete = null,
                    expiresInSeconds = 60,
                    intervalSeconds = 0,
                ),
                onTick = { ticks++ },
            )
        }

        assertEquals("ghu_after_two", result.accessToken)
        // One poll answers pending, the second returns the token.
        assertEquals("expected exactly two polls, saw $ticks", 2, ticks)
    }

    @Test
    fun `the poll sends the device flow grant type`() {
        queue.addLast("""{"access_token":"ghu_x","token_type":"bearer","scope":"","expires_in":0}""")
        runBlocking {
            client().awaitToken(
                DeviceFlowClient.DeviceCode("DEV-CODE", "ABCD", "", null, 60, 0),
            )
        }
        val body = received.first()
        assertTrue(body, body.contains("urn:ietf:params:oauth:grant-type:device_code"))
        assertTrue(body, body.contains("DEV-CODE"))
        assertTrue(body, body.contains("test-client"))
    }

    @Test
    fun `a declined authorisation surfaces as AuthorizationDenied`() {
        queue.addLast("""{"error":"access_denied","error_description":"the user declined"}""")
        val failure = runCatching {
            runBlocking {
                client().awaitToken(
                    DeviceFlowClient.DeviceCode("DEV-CODE", "ABCD", "", null, 60, 0),
                )
            }
        }
        assertTrue(
            "expected AuthorizationDenied, got ${failure.exceptionOrNull()}",
            failure.exceptionOrNull() is DeviceFlowClient.AuthorizationDenied,
        )
    }

    @Test
    fun `an expired device code surfaces as CodeExpired`() {
        queue.addLast("""{"error":"expired_token","error_description":"code expired"}""")
        val failure = runCatching {
            runBlocking {
                client().awaitToken(
                    DeviceFlowClient.DeviceCode("DEV-CODE", "ABCD", "", null, 60, 0),
                )
            }
        }
        assertTrue(
            "expected CodeExpired, got ${failure.exceptionOrNull()}",
            failure.exceptionOrNull() is DeviceFlowClient.CodeExpired,
        )
    }
}
