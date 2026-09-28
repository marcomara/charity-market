package it.charitymarket.shared.api

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CharityMarketApiClientTest {
    @Test
    fun authenticatedRequestUsesCentralBearerToken() = runBlocking {
        var receivedAuthorization: String? = null
        var token: String? = null
        var unauthorizedCallbackCalled = false
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        server.createContext("/q/health") { exchange ->
            respond(exchange, 200, """{"status":"UP"}""")
        }
        server.createContext("/api/auth/login") { exchange ->
            respond(
                exchange,
                200,
                """
                {
                  "accessToken":"access-token",
                  "tokenType":"Bearer",
                  "expiresInSeconds":900,
                  "passwordChangeRequired":false,
                  "user":{
                    "id":"user-1",
                    "username":"admin",
                    "displayName":"Administrator",
                    "email":null,
                    "status":"ACTIVE",
                    "mustChangePassword":false,
                    "roles":["SYSTEM_ADMINISTRATOR"]
                  }
                }
                """.trimIndent()
            )
        }
        server.createContext("/api/auth/token-info") { exchange ->
            receivedAuthorization =
                exchange.requestHeaders.getFirst("Authorization")
            respond(
                exchange,
                200,
                """{"userId":"user-1","username":"admin","roles":["SYSTEM_ADMINISTRATOR"]}"""
            )
        }
        server.createContext("/api/sync/status") { exchange ->
            receivedAuthorization =
                exchange.requestHeaders.getFirst("Authorization")
            respond(
                exchange,
                200,
                """{"version":42,"updatedAt":"2026-07-15T12:00:00Z"}"""
            )
        }
        server.createContext("/api/settings") { exchange ->
            respond(
                exchange,
                200,
                """
                {
                  "currencyCode":"EUR",
                  "autoRefreshEnabled":true,
                  "usersCanCustomizeAutoRefresh":false,
                  "defaultRefreshIntervalSeconds":10,
                  "minimumRefreshIntervalSeconds":5,
                  "maximumRefreshIntervalSeconds":15,
                  "updatedAt":"2026-07-15T12:00:00Z",
                  "updatedByUserId":null
                }
                """.trimIndent()
            )
        }
        server.start()

        val client = CharityMarketApiClient(
            baseUrl = "http://127.0.0.1:${server.address.port}",
            engineFactory = CIO,
            tokenProvider = { token },
            onUnauthorized = { unauthorizedCallbackCalled = true },
            onAccountSuspended = {}
        )

        try {
            client.health()
            token = client.login(LoginRequest("admin", "password"))
                .accessToken
            val info = client.tokenInfo()
            val status = client.dataVersion()
            val settings = client.getSettings()

            assertEquals("user-1", info.userId)
            assertEquals(42, status.version)
            assertEquals("Bearer access-token", receivedAuthorization)
            assertEquals(10, settings.defaultRefreshIntervalSeconds)
            assertEquals(5, settings.minimumRefreshIntervalSeconds)
            assertEquals(15, settings.maximumRefreshIntervalSeconds)
            assertFalse(unauthorizedCallbackCalled)
        } finally {
            client.close()
            server.stop(0)
        }
    }

    private fun respond(
        exchange: HttpExchange,
        status: Int,
        body: String
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
