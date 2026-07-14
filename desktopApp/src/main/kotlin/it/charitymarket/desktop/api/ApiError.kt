package it.charitymarket.desktop.api

sealed class ApiError(
    open val userMessage: String
) {
    data class ConnectionFailure(
        override val userMessage: String
    ) : ApiError(userMessage)

    data class Timeout(
        override val userMessage: String
    ) : ApiError(userMessage)

    data class Unauthorized(
        override val userMessage: String =
            "Authentication expired. Sign in again."
    ) : ApiError(userMessage)

    data class AccountSuspended(
        override val userMessage: String =
            "Your account has been suspended. Contact an administrator."
    ) : ApiError(userMessage)

    data class Forbidden(
        override val userMessage: String =
            "Insufficient permissions."
    ) : ApiError(userMessage)

    data class Validation(
        override val userMessage: String
    ) : ApiError(userMessage)

    data class Conflict(
        override val userMessage: String
    ) : ApiError(userMessage)

    data class Server(
        val statusCode: Int,
        override val userMessage: String
    ) : ApiError(userMessage)

    data class MalformedResponse(
        override val userMessage: String
    ) : ApiError(userMessage)
}

class ApiException(
    val error: ApiError
) : RuntimeException(error.userMessage)
