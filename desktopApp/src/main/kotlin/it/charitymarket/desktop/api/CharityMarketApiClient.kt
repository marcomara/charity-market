package it.charitymarket.desktop.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.nio.charset.StandardCharsets

class CharityMarketApiClient(
    baseUrl: String,
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

    private val client = HttpClient(CIO) {
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
        executeWithoutBody(
            method = HttpMethod.Get,
            path = "/q/health",
            authenticated = false
        )
    }

    suspend fun login(request: LoginRequest): LoginResponse =
        requestWithBody(
            method = HttpMethod.Post,
            path = "/api/auth/login",
            authenticated = false,
            body = request
        )

    suspend fun changeInitialPassword(
        request: ChangePasswordRequest
    ): LoginResponse =
        requestWithBody(
            method = HttpMethod.Post,
            path = "/api/auth/change-initial-password",
            authenticated = true,
            body = request
        )

    suspend fun tokenInfo(): TokenInfoResponse =
        requestWithoutBody(
            method = HttpMethod.Get,
            path = "/api/auth/token-info",
            authenticated = true
        )

    suspend fun getSettings(): ApplicationSettingsResponse =
        requestWithoutBody(
            method = HttpMethod.Get,
            path = "/api/settings",
            authenticated = true
        )

    suspend fun updateSettings(
        currencyCode: String
    ): ApplicationSettingsResponse =
        requestWithBody(
            method = HttpMethod.Put,
            path = "/api/settings",
            authenticated = true,
            body = UpdateApplicationSettingsRequest(currencyCode)
        )

    suspend fun listUsers(): List<UserResponse> =
        requestWithoutBody(
            method = HttpMethod.Get,
            path = "/api/users",
            authenticated = true
        )

    suspend fun createUser(request: CreateUserRequest): UserResponse =
        requestWithBody(
            method = HttpMethod.Post,
            path = "/api/users",
            authenticated = true,
            body = request
        )

    suspend fun replaceUserRoles(
        userId: String,
        roles: Set<UserRole>
    ): UserResponse =
        requestWithBody(
            method = HttpMethod.Put,
            path = "/api/users/${pathSegment(userId)}/roles",
            authenticated = true,
            body = ReplaceRolesRequest(roles)
        )

    suspend fun suspendUser(userId: String): UserResponse =
        requestWithoutBody(
            method = HttpMethod.Post,
            path = "/api/users/${pathSegment(userId)}/suspend",
            authenticated = true
        )

    suspend fun enableUser(userId: String): UserResponse =
        requestWithoutBody(
            method = HttpMethod.Post,
            path = "/api/users/${pathSegment(userId)}/enable",
            authenticated = true
        )

    suspend fun listDonors(): List<DonorResponse> =
        requestWithoutBody(
            method = HttpMethod.Get,
            path = "/api/donors",
            authenticated = true
        )

    suspend fun createDonor(
        request: CreateDonorRequest
    ): DonorResponse =
        requestWithBody(
            method = HttpMethod.Post,
            path = "/api/donors",
            authenticated = true,
            body = request
        )

    suspend fun updateDonor(
        id: String,
        request: UpdateDonorRequest
    ): DonorResponse =
        requestWithBody(
            method = HttpMethod.Put,
            path = "/api/donors/${pathSegment(id)}",
            authenticated = true,
            body = request
        )

    suspend fun deleteDonor(id: String) {
        executeWithoutBody(
            method = HttpMethod.Delete,
            path = "/api/donors/${pathSegment(id)}",
            authenticated = true
        )
    }

    suspend fun listItems(): List<ItemResponse> =
        requestWithoutBody(
            method = HttpMethod.Get,
            path = "/api/items",
            authenticated = true
        )

    suspend fun getItemById(id: String): ItemResponse =
        requestWithoutBody(
            method = HttpMethod.Get,
            path = "/api/items/${pathSegment(id)}",
            authenticated = true
        )

    suspend fun findItemByCode(code: String): ItemResponse =
        requestWithoutBody(
            method = HttpMethod.Get,
            path = "/api/items/by-code/${pathSegment(code)}",
            authenticated = true
        )

    suspend fun createItem(request: CreateItemRequest): ItemResponse =
        requestWithBody(
            method = HttpMethod.Post,
            path = "/api/items",
            authenticated = true,
            body = request
        )

    suspend fun updateItem(
        id: String,
        request: UpdateItemRequest
    ): ItemResponse =
        requestWithBody(
            method = HttpMethod.Put,
            path = "/api/items/${pathSegment(id)}",
            authenticated = true,
            body = request
        )

    suspend fun deleteItem(id: String) {
        executeWithoutBody(
            method = HttpMethod.Delete,
            path = "/api/items/${pathSegment(id)}",
            authenticated = true
        )
    }

    suspend fun listSales(): List<SaleResponse> =
        requestWithoutBody(
            method = HttpMethod.Get,
            path = "/api/sales",
            authenticated = true
        )

    suspend fun getSaleById(id: String): SaleResponse =
        requestWithoutBody(
            method = HttpMethod.Get,
            path = "/api/sales/${pathSegment(id)}",
            authenticated = true
        )

    suspend fun createSale(request: CreateSaleRequest): SaleResponse =
        requestWithBody(
            method = HttpMethod.Post,
            path = "/api/sales",
            authenticated = true,
            body = request
        )

    suspend fun updateSale(
        id: String,
        request: UpdateSaleRequest
    ): SaleResponse =
        requestWithBody(
            method = HttpMethod.Put,
            path = "/api/sales/${pathSegment(id)}",
            authenticated = true,
            body = request
        )

    suspend fun voidSale(
        id: String,
        reason: String?
    ): SaleResponse =
        requestWithBody(
            method = HttpMethod.Post,
            path = "/api/sales/${pathSegment(id)}/void",
            authenticated = true,
            body = VoidSaleRequest(reason)
        )

    fun close() {
        client.close()
    }

    private suspend inline fun <reified Response>
            requestWithoutBody(
        method: HttpMethod,
        path: String,
        authenticated: Boolean
    ): Response {
        val response = executeWithoutBody(
            method = method,
            path = path,
            authenticated = authenticated
        )

        return decodeResponse(response)
    }

    private suspend inline fun <
            reified Request : Any,
            reified Response
            > requestWithBody(
        method: HttpMethod,
        path: String,
        authenticated: Boolean,
        body: Request
    ): Response {
        val response = executeWithBody(
            method = method,
            path = path,
            authenticated = authenticated,
            body = body
        )

        return decodeResponse(response)
    }

    private suspend fun executeWithoutBody(
        method: HttpMethod,
        path: String,
        authenticated: Boolean
    ): HttpResponse =
        execute(
            method = method,
            path = path,
            authenticated = authenticated
        )

    private suspend inline fun <reified Request : Any>
            executeWithBody(
        method: HttpMethod,
        path: String,
        authenticated: Boolean,
        body: Request
    ): HttpResponse =
        execute(
            method = method,
            path = path,
            authenticated = authenticated,
            bodyWriter = {
                header(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString()
                )
                setBody(body)
            }
        )

    private suspend fun execute(
        method: HttpMethod,
        path: String,
        authenticated: Boolean,
        bodyWriter: (io.ktor.client.request.HttpRequestBuilder.() -> Unit)? = null
    ): HttpResponse {
        if (authenticated && tokenProvider().isNullOrBlank()) {
            onUnauthorized()
            throw ApiException(ApiError.Unauthorized())
        }

        return try {
            val response = when (method) {
                HttpMethod.Get ->
                    client.get("$baseUrl$path") {
                        configureRequest(authenticated)
                    }

                HttpMethod.Post ->
                    client.post("$baseUrl$path") {
                        configureRequest(authenticated)
                        bodyWriter?.invoke(this)
                    }

                HttpMethod.Put ->
                    client.put("$baseUrl$path") {
                        configureRequest(authenticated)
                        bodyWriter?.invoke(this)
                    }

                HttpMethod.Delete ->
                    client.delete("$baseUrl$path") {
                        configureRequest(authenticated)
                    }

                else -> throw IllegalArgumentException(
                    "Unsupported HTTP method: $method"
                )
            }

            if (response.status.value !in 200..299) {
                throw mapHttpError(response)
            }

            response
        } catch (exception: ApiException) {
            throw exception
        } catch (exception: HttpRequestTimeoutException) {
            throw ApiException(
                ApiError.Timeout("The server request timed out.")
            )
        } catch (exception: SocketTimeoutException) {
            throw ApiException(
                ApiError.Timeout("The server request timed out.")
            )
        } catch (exception: ConnectException) {
            throw ApiException(
                ApiError.ConnectionFailure(
                    "Could not connect to the server."
                )
            )
        } catch (exception: UnknownHostException) {
            throw ApiException(
                ApiError.ConnectionFailure(
                    "The server address could not be resolved."
                )
            )
        } catch (exception: UnresolvedAddressException) {
            throw ApiException(
                ApiError.ConnectionFailure(
                    "The server address could not be resolved."
                )
            )
        } catch (exception: IOException) {
            throw ApiException(
                ApiError.ConnectionFailure(
                    exception.message
                        ?: "The network request failed."
                )
            )
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder
            .configureRequest(
        authenticated: Boolean
    ) {
        accept(ContentType.Application.Json)

        if (authenticated) {
            bearerAuth(tokenProvider().orEmpty())
        }
    }

    private suspend inline fun <reified Response>
            decodeResponse(
        response: HttpResponse
    ): Response =
        try {
            response.body()
        } catch (exception: SerializationException) {
            throw ApiException(
                ApiError.MalformedResponse(
                    "The server response could not be read."
                )
            )
        } catch (exception: IllegalStateException) {
            throw ApiException(
                ApiError.MalformedResponse(
                    "The server response could not be read."
                )
            )
        }

    private suspend fun mapHttpError(
        response: HttpResponse
    ): ApiException {
        val statusCode = response.status.value
        val serverError = readServerError(response)
        val message = serverError.message
            ?: when (statusCode) {
                400 -> "The request was not valid."
                401 -> "Authentication expired. Sign in again."
                403 -> "Insufficient permissions."
                404 -> "The requested record was not found."
                409 -> "The request conflicts with existing data."
                in 500..599 -> "The server failed to complete the request."
                else -> "The server returned HTTP $statusCode."
            }

        val error = when (statusCode) {
            400 -> ApiError.Validation(message)
            401 -> {
                onUnauthorized()
                ApiError.Unauthorized(message)
            }

            403 -> {
                if (serverError.code == "ACCOUNT_SUSPENDED" ||
                    serverError.code == "ACCOUNT_DISABLED"
                ) {
                    onAccountSuspended(message)
                    ApiError.AccountSuspended(message)
                } else {
                    ApiError.Forbidden(message)
                }
            }

            409 -> ApiError.Conflict(message)
            else -> ApiError.Server(statusCode, message)
        }

        return ApiException(error)
    }

    private suspend fun readServerError(
        response: HttpResponse
    ): ServerErrorDetails {
        val text = runCatching {
            response.bodyAsText()
        }.getOrNull()

        if (text.isNullOrBlank()) {
            return ServerErrorDetails()
        }

        return runCatching {
            val objectValue = json
                .parseToJsonElement(text)
                .jsonObject

            val code = objectValue["code"]
                ?.jsonPrimitive
                ?.contentOrNull

            val message = objectValue["message"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: objectValue["error"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                ?: objectValue["details"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                ?: objectValue["violations"]
                    ?.jsonArray
                    ?.joinToString("; ") { violation ->
                        val violationObject = violation.jsonObject
                        val field = violationObject["field"]
                            ?.jsonPrimitive
                            ?.contentOrNull
                        val violationMessage =
                            violationObject["message"]
                                ?.jsonPrimitive
                                ?.contentOrNull

                        listOfNotNull(field, violationMessage)
                            .joinToString(": ")
                    }
                    ?.takeIf { it.isNotBlank() }

            ServerErrorDetails(code, message)
        }.getOrElse {
            ServerErrorDetails(message = text.take(240))
        }
    }

    private fun pathSegment(value: String): String =
        URLEncoder
            .encode(value, StandardCharsets.UTF_8)
            .replace("+", "%20")

    private data class ServerErrorDetails(
        val code: String? = null,
        val message: String? = null
    )
}
