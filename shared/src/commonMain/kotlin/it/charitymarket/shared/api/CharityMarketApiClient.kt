package it.charitymarket.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CharityMarketApiClient(
    baseUrl: String,
    engineFactory: HttpClientEngineFactory<*>,
    private val tokenProvider: () -> String?,
    private val onUnauthorized: () -> Unit,
    private val onAccountSuspended: (String) -> Unit
) {
    private val baseUrl = baseUrl.trim().removeSuffix("/")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val client = HttpClient(engineFactory) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 20_000
        }
    }

    suspend fun health() {
        val response = executeHealthCheck()
        if (response.status.value !in 200..299) {
            val statusCode = response.status.value
            throw ApiException(
                ApiError.Server(
                    statusCode = statusCode,
                    userMessage =
                        "The server health check returned HTTP $statusCode."
                )
            )
        }
    }

    suspend fun login(request: LoginRequest): LoginResponse =
        requestWithBody(HttpMethod.Post, "/api/auth/login", false, request)

    suspend fun changeInitialPassword(
        request: ChangePasswordRequest
    ): LoginResponse = requestWithBody(
        HttpMethod.Post,
        "/api/auth/change-initial-password",
        true,
        request
    )

    suspend fun tokenInfo(): TokenInfoResponse =
        requestWithoutBody(HttpMethod.Get, "/api/auth/token-info", true)

    suspend fun dataVersion(): DataVersionResponse =
        requestWithoutBody(HttpMethod.Get, "/api/sync/status", true)

    suspend fun getSettings(): ApplicationSettingsResponse =
        requestWithoutBody(HttpMethod.Get, "/api/settings", true)

    suspend fun updateSettings(
        request: UpdateApplicationSettingsRequest
    ): ApplicationSettingsResponse = requestWithBody(
        HttpMethod.Put,
        "/api/settings",
        true,
        request
    )

    suspend fun resetDatabase(
        request: ResetDatabaseRequest
    ): DatabaseResetResponse = requestWithBody(
        HttpMethod.Post,
        "/api/settings/database-reset",
        true,
        request
    )

    suspend fun listUsers(): List<UserResponse> =
        requestWithoutBody(HttpMethod.Get, "/api/users", true)

    suspend fun createUser(request: CreateUserRequest): UserResponse =
        requestWithBody(HttpMethod.Post, "/api/users", true, request)

    suspend fun replaceUserRoles(
        userId: String,
        roles: Set<UserRole>
    ): UserResponse = requestWithBody(
        HttpMethod.Put,
        "/api/users/${pathSegment(userId)}/roles",
        true,
        ReplaceRolesRequest(roles)
    )

    suspend fun suspendUser(userId: String): UserResponse =
        requestWithoutBody(
            HttpMethod.Post,
            "/api/users/${pathSegment(userId)}/suspend",
            true
        )

    suspend fun enableUser(userId: String): UserResponse =
        requestWithoutBody(
            HttpMethod.Post,
            "/api/users/${pathSegment(userId)}/enable",
            true
        )

    suspend fun listDonors(): List<DonorResponse> =
        requestWithoutBody(HttpMethod.Get, "/api/donors", true)

    suspend fun createDonor(request: CreateDonorRequest): DonorResponse =
        requestWithBody(HttpMethod.Post, "/api/donors", true, request)

    suspend fun updateDonor(
        id: String,
        request: UpdateDonorRequest
    ): DonorResponse = requestWithBody(
        HttpMethod.Put,
        "/api/donors/${pathSegment(id)}",
        true,
        request
    )

    suspend fun deleteDonor(id: String) {
        executeWithoutBody(
            HttpMethod.Delete,
            "/api/donors/${pathSegment(id)}",
            true
        )
    }

    suspend fun listItems(): List<ItemResponse> =
        requestWithoutBody(HttpMethod.Get, "/api/items", true)

    suspend fun getItemById(id: String): ItemResponse =
        requestWithoutBody(
            HttpMethod.Get,
            "/api/items/${pathSegment(id)}",
            true
        )

    suspend fun findItemByCode(code: String): ItemResponse =
        requestWithoutBody(
            HttpMethod.Get,
            "/api/items/by-code/${pathSegment(code)}",
            true
        )

    suspend fun createItem(request: CreateItemRequest): ItemResponse =
        requestWithBody(HttpMethod.Post, "/api/items", true, request)

    suspend fun updateItem(
        id: String,
        request: UpdateItemRequest
    ): ItemResponse = requestWithBody(
        HttpMethod.Put,
        "/api/items/${pathSegment(id)}",
        true,
        request
    )

    suspend fun deleteItem(id: String) {
        executeWithoutBody(
            HttpMethod.Delete,
            "/api/items/${pathSegment(id)}",
            true
        )
    }

    suspend fun listSales(): List<SaleResponse> =
        requestWithoutBody(HttpMethod.Get, "/api/sales", true)

    suspend fun getSaleById(id: String): SaleResponse =
        requestWithoutBody(
            HttpMethod.Get,
            "/api/sales/${pathSegment(id)}",
            true
        )

    suspend fun createSale(request: CreateSaleRequest): SaleResponse =
        requestWithBody(HttpMethod.Post, "/api/sales", true, request)

    suspend fun updateSale(
        id: String,
        request: UpdateSaleRequest
    ): SaleResponse = requestWithBody(
        HttpMethod.Put,
        "/api/sales/${pathSegment(id)}",
        true,
        request
    )

    suspend fun voidSale(
        id: String,
        reason: String?
    ): SaleResponse = requestWithBody(
        HttpMethod.Post,
        "/api/sales/${pathSegment(id)}/void",
        true,
        VoidSaleRequest(reason)
    )

    fun close() {
        client.close()
    }

    private suspend inline fun <reified Response> requestWithoutBody(
        method: HttpMethod,
        path: String,
        authenticated: Boolean
    ): Response = decodeResponse(
        executeWithoutBody(method, path, authenticated)
    )

    private suspend inline fun <reified Request : Any, reified Response>
            requestWithBody(
        method: HttpMethod,
        path: String,
        authenticated: Boolean,
        body: Request
    ): Response = decodeResponse(
        execute(
            method = method,
            path = path,
            authenticated = authenticated,
            bodyWriter = {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(body)
            }
        )
    )

    private suspend fun executeWithoutBody(
        method: HttpMethod,
        path: String,
        authenticated: Boolean
    ): HttpResponse = execute(method, path, authenticated)

    private suspend fun execute(
        method: HttpMethod,
        path: String,
        authenticated: Boolean,
        bodyWriter: (HttpRequestBuilder.() -> Unit)? = null
    ): HttpResponse {
        if (authenticated && tokenProvider().isNullOrBlank()) {
            onUnauthorized()
            throw ApiException(ApiError.Unauthorized())
        }

        return try {
            val response = when (method) {
                HttpMethod.Get -> client.get("$baseUrl$path") {
                    configureRequest(authenticated)
                }

                HttpMethod.Post -> client.post("$baseUrl$path") {
                    configureRequest(authenticated)
                    bodyWriter?.invoke(this)
                }

                HttpMethod.Put -> client.put("$baseUrl$path") {
                    configureRequest(authenticated)
                    bodyWriter?.invoke(this)
                }

                HttpMethod.Delete -> client.delete("$baseUrl$path") {
                    configureRequest(authenticated)
                }

                else -> throw IllegalArgumentException(
                    "Unsupported HTTP method: $method"
                )
            }

            if (response.status.value !in 200..299) {
                throw mapHttpError(response, authenticated)
            }
            response
        } catch (exception: ApiException) {
            throw exception
        } catch (_: HttpRequestTimeoutException) {
            throw ApiException(ApiError.Timeout("The server request timed out."))
        } catch (exception: IOException) {
            throw mapNetworkError(exception)
        }
    }

    private suspend fun executeHealthCheck(): HttpResponse =
        try {
            client.get("$baseUrl/q/health") {
                configureRequest(authenticated = false)
            }
        } catch (exception: ApiException) {
            throw exception
        } catch (_: HttpRequestTimeoutException) {
            throw ApiException(ApiError.Timeout("The server request timed out."))
        } catch (exception: IOException) {
            throw mapNetworkError(exception)
        }

    private fun mapNetworkError(exception: IOException): ApiException {
        val message = exception.message ?: "The network request failed."
        val error = if (
            message.contains("timed out", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true)
        ) {
            ApiError.Timeout("The server request timed out.")
        } else {
            ApiError.ConnectionFailure(message)
        }
        return ApiException(error)
    }

    private fun HttpRequestBuilder.configureRequest(authenticated: Boolean) {
        accept(ContentType.Application.Json)
        if (authenticated) {
            bearerAuth(tokenProvider().orEmpty())
        }
    }

    private suspend inline fun <reified Response> decodeResponse(
        response: HttpResponse
    ): Response = try {
        response.body()
    } catch (_: SerializationException) {
        throw ApiException(
            ApiError.MalformedResponse("The server response could not be read.")
        )
    } catch (_: IllegalStateException) {
        throw ApiException(
            ApiError.MalformedResponse("The server response could not be read.")
        )
    }

    private suspend fun mapHttpError(
        response: HttpResponse,
        authenticated: Boolean
    ): ApiException {
        val statusCode = response.status.value
        val serverError = readServerError(response)
        val message = serverError.message ?: when (statusCode) {
            400 -> "The request was not valid."
            401 -> if (authenticated) {
                "Authentication expired. Sign in again."
            } else {
                "The username or password is incorrect."
            }
            403 -> "Insufficient permissions."
            404 -> "The requested record was not found."
            409 -> "The request conflicts with existing data."
            in 500..599 -> "The server failed to complete the request."
            else -> "The server returned HTTP $statusCode."
        }

        val error = when (statusCode) {
            400 -> ApiError.Validation(message)
            401 -> {
                if (authenticated) onUnauthorized()
                ApiError.Unauthorized(message)
            }
            403 -> if (
                serverError.code == "ACCOUNT_SUSPENDED" ||
                serverError.code == "ACCOUNT_DISABLED"
            ) {
                onAccountSuspended(message)
                ApiError.AccountSuspended(message)
            } else {
                ApiError.Forbidden(message)
            }
            409 -> ApiError.Conflict(message)
            else -> ApiError.Server(statusCode, message)
        }
        return ApiException(error)
    }

    private suspend fun readServerError(
        response: HttpResponse
    ): ServerErrorDetails {
        val text = runCatching { response.bodyAsText() }.getOrNull()
        if (text.isNullOrBlank()) return ServerErrorDetails()

        return runCatching {
            val value = json.parseToJsonElement(text).jsonObject
            val code = value["code"]?.jsonPrimitive?.contentOrNull
            val message = value["message"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: value["error"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                ?: value["details"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                ?: value["violations"]?.jsonArray
                    ?.joinToString("; ") { violation ->
                        val item = violation.jsonObject
                        listOfNotNull(
                            item["field"]?.jsonPrimitive?.contentOrNull,
                            item["message"]?.jsonPrimitive?.contentOrNull
                        ).joinToString(": ")
                    }?.takeIf { it.isNotBlank() }
            ServerErrorDetails(code, message)
        }.getOrElse {
            ServerErrorDetails(message = text.take(240))
        }
    }

    private fun pathSegment(value: String): String =
        value.encodeURLPathPart()

    private data class ServerErrorDetails(
        val code: String? = null,
        val message: String? = null
    )
}
