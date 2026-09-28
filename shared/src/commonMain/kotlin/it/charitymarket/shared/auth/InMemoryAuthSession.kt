package it.charitymarket.shared.auth

import it.charitymarket.shared.api.AuthenticatedUserResponse
import it.charitymarket.shared.api.LoginResponse

class InMemoryAuthSession {
    var accessToken: String? = null
        private set

    var authenticatedUser: AuthenticatedUserResponse? = null
        private set

    val isAuthenticated: Boolean
        get() = !accessToken.isNullOrBlank() && authenticatedUser != null

    fun establish(response: LoginResponse) {
        establish(response.accessToken, response.user)
    }

    fun establish(
        accessToken: String,
        user: AuthenticatedUserResponse
    ) {
        require(accessToken.isNotBlank()) { "The access token is required." }
        this.accessToken = accessToken
        authenticatedUser = user
    }

    fun clear() {
        accessToken = null
        authenticatedUser = null
    }
}
