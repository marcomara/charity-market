package it.charitymarket.android.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.cio.CIO
import it.charitymarket.android.model.defaultCurrencyCode
import it.charitymarket.shared.api.ApiError
import it.charitymarket.shared.api.ApiException
import it.charitymarket.shared.api.ApplicationSettingsResponse
import it.charitymarket.shared.api.AuthenticatedUserResponse
import it.charitymarket.shared.api.ChangePasswordRequest
import it.charitymarket.shared.api.CharityMarketApiClient
import it.charitymarket.shared.api.LoginRequest
import it.charitymarket.shared.api.ResetDatabaseRequest
import it.charitymarket.shared.api.UpdateApplicationSettingsRequest
import it.charitymarket.shared.auth.AppDestination
import it.charitymarket.shared.auth.InMemoryAuthSession
import it.charitymarket.shared.auth.RoleRules
import it.charitymarket.shared.settings.AutoRefreshPolicy
import it.charitymarket.shared.settings.ClientAutoRefreshPreference
import it.charitymarket.shared.settings.toAutoRefreshPolicy
import it.charitymarket.shared.config.serverConnectionCandidates
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AppStage {
    SERVER_SELECTION,
    LOGIN,
    PASSWORD_CHANGE,
    MAIN
}

data class AndroidAppUiState(
    val stage: AppStage = AppStage.SERVER_SELECTION,
    val baseUrl: String? = null,
    val authenticatedUser: AuthenticatedUserResponse? = null,
    val selectedDestination: AppDestination = AppDestination.DASHBOARD,
    val authMessage: String? = null,
    val globalMessage: String? = null,
    val globalMessageIsError: Boolean = false,
    val currencyCode: String = defaultCurrencyCode(),
    val autoRefreshPolicy: AutoRefreshPolicy = AutoRefreshPolicy(),
    val autoRefreshPreference: ClientAutoRefreshPreference =
        ClientAutoRefreshPreference(),
    val autoRefreshGeneration: Long = 0
)

class AndroidAppState : ViewModel() {
    var uiState by mutableStateOf(AndroidAppUiState())
        private set

    private var apiClient: CharityMarketApiClient? = null
    private val authSession = InMemoryAuthSession()
    private var sessionMonitor: Job? = null
    private var autoRefreshJob: Job? = null
    private var lastDataVersion: Long? = null

    fun requireApiClient(): CharityMarketApiClient = apiClient
        ?: error("The API client is not connected.")

    suspend fun connect(rawBaseUrl: String) {
        val candidateBaseUrls = serverConnectionCandidates(rawBaseUrl)

        candidateBaseUrls.forEachIndexed { index, baseUrl ->
            val candidate = createClient(baseUrl)
            try {
                candidate.health()
            } catch (exception: Throwable) {
                candidate.close()
                if (
                    exception.isNetworkConnectionFailure() &&
                    index < candidateBaseUrls.lastIndex
                ) {
                    return@forEachIndexed
                }
                throw exception
            }

            apiClient?.close()
            apiClient = candidate
            authSession.clear()
            lastDataVersion = null
            uiState = AndroidAppUiState(
                stage = AppStage.LOGIN,
                baseUrl = baseUrl,
                authMessage = "Server connection ready. Sign in to continue."
            )
            return
        }
    }

    suspend fun login(username: String, password: String) {
        val response = requireApiClient().login(
            LoginRequest(username, password)
        )
        applyLogin(
            token = response.accessToken,
            user = response.user,
            passwordChangeRequired = response.passwordChangeRequired
        )
    }

    suspend fun changeInitialPassword(
        currentPassword: String,
        newPassword: String,
        confirmation: String
    ) {
        val response = requireApiClient().changeInitialPassword(
            ChangePasswordRequest(
                currentPassword,
                newPassword,
                confirmation
            )
        )
        applyLogin(response.accessToken, response.user, false)
    }

    fun logout(message: String = "Signed out.") {
        sessionMonitor?.cancel()
        sessionMonitor = null
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        lastDataVersion = null
        authSession.clear()
        uiState = uiState.copy(
            stage = if (uiState.baseUrl == null) {
                AppStage.SERVER_SELECTION
            } else {
                AppStage.LOGIN
            },
            authenticatedUser = null,
            selectedDestination = AppDestination.DASHBOARD,
            authMessage = message,
            globalMessage = null,
            autoRefreshPreference = ClientAutoRefreshPreference(),
            autoRefreshGeneration = 0
        )
    }

    fun disconnect() {
        sessionMonitor?.cancel()
        sessionMonitor = null
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        lastDataVersion = null
        apiClient?.close()
        apiClient = null
        authSession.clear()
        uiState = AndroidAppUiState()
    }

    fun selectDestination(destination: AppDestination) {
        val roles = uiState.authenticatedUser?.roles.orEmpty()
        if (destination in RoleRules.destinationsFor(roles)) {
            uiState = uiState.copy(selectedDestination = destination)
        }
    }

    fun handleRequestFailure(throwable: Throwable): String {
        val exception = throwable as? ApiException
            ?: return throwable.message ?: "The operation failed."

        when (exception.error) {
            is ApiError.Unauthorized -> {
                if (authSession.isAuthenticated) {
                    expireSession()
                }
            }

            is ApiError.AccountSuspended ->
                expireSession(exception.error.userMessage)

            is ApiError.Forbidden -> showMessage(
                "Insufficient permissions.",
                isError = true
            )

            else -> Unit
        }
        return exception.error.userMessage
    }

    fun showMessage(message: String, isError: Boolean = false) {
        uiState = uiState.copy(
            globalMessage = message,
            globalMessageIsError = isError
        )
    }

    fun consumeGlobalMessage() {
        uiState = uiState.copy(
            globalMessage = null,
            globalMessageIsError = false
        )
    }

    suspend fun refreshSettings() {
        val settings = requireApiClient().getSettings()
        applyServerSettings(settings)
    }

    suspend fun updateCurrency(currencyCode: String) {
        val settings = requireApiClient().updateSettings(
            settingsRequest(currencyCode, uiState.autoRefreshPolicy)
        )
        applyServerSettings(settings)
        showMessage("Currency changed to ${settings.currencyCode}.")
    }

    suspend fun updateAutoRefreshPolicy(policy: AutoRefreshPolicy) {
        val validatedPolicy = policy.validated()
        val settings = requireApiClient().updateSettings(
            settingsRequest(uiState.currencyCode, validatedPolicy)
        )
        applyServerSettings(settings)
        showMessage("Auto-refresh policy updated for every client.")
        startAutoRefresh()
    }

    suspend fun resetDatabase(administratorPassword: String) {
        val response = requireApiClient().resetDatabase(
            ResetDatabaseRequest(administratorPassword)
        )
        val settings = requireApiClient().getSettings()
        applyServerSettings(settings)
        lastDataVersion = null
        uiState = uiState.copy(
            autoRefreshGeneration = uiState.autoRefreshGeneration + 1,
            globalMessage =
                "Database emptied. Backup created at ${response.backupPath}.",
            globalMessageIsError = false
        )
        startAutoRefresh()
    }

    fun updateAutoRefreshPreference(
        enabled: Boolean,
        intervalSeconds: Int?
    ) {
        val policy = uiState.autoRefreshPolicy
        val wasActive = policy.isActive(uiState.autoRefreshPreference)
        require(policy.usersCanCustomize) {
            "The administrator has disabled personal auto-refresh settings."
        }
        require(
            intervalSeconds == null || intervalSeconds in policy.intervalRange
        ) {
            "Choose an interval between ${policy.minimumIntervalSeconds} and ${policy.maximumIntervalSeconds} seconds."
        }
        uiState = uiState.copy(
            autoRefreshPreference = ClientAutoRefreshPreference(
                enabled = enabled,
                intervalSeconds = intervalSeconds
            ),
            autoRefreshGeneration = if (
                !wasActive && policy.isActive(
                    ClientAutoRefreshPreference(enabled, intervalSeconds)
                )
            ) {
                uiState.autoRefreshGeneration + 1
            } else {
                uiState.autoRefreshGeneration
            }
        )
        startAutoRefresh()
    }

    private fun createClient(baseUrl: String) = CharityMarketApiClient(
        baseUrl = baseUrl,
        engineFactory = CIO,
        tokenProvider = { authSession.accessToken },
        onUnauthorized = {
            viewModelScope.launch { expireSession() }
        },
        onAccountSuspended = { message ->
            viewModelScope.launch { expireSession(message) }
        }
    )

    private fun applyLogin(
        token: String,
        user: AuthenticatedUserResponse,
        passwordChangeRequired: Boolean
    ) {
        authSession.establish(token, user)
        lastDataVersion = null
        uiState = uiState.copy(
            stage = if (passwordChangeRequired) {
                AppStage.PASSWORD_CHANGE
            } else {
                AppStage.MAIN
            },
            authenticatedUser = user,
            selectedDestination = AppDestination.DASHBOARD,
            authMessage = null,
            globalMessage = null
        )
        if (!passwordChangeRequired) {
            startSessionMonitor()
            startAutoRefresh()
        }
    }

    private fun startSessionMonitor() {
        sessionMonitor?.cancel()
        sessionMonitor = viewModelScope.launch {
            while (authSession.isAuthenticated) {
                delay(30_000)
                runCatching { requireApiClient().tokenInfo() }
                    .onFailure { exception ->
                        if (exception is ApiException &&
                            (exception.error is ApiError.Unauthorized ||
                                    exception.error is ApiError.AccountSuspended)
                        ) {
                            handleRequestFailure(exception)
                            return@launch
                        }
                    }
            }
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            val settingsResult = runCatching { refreshSettings() }
            if (isTerminalRefreshFailure(settingsResult.exceptionOrNull())) {
                return@launch
            }

            val initialStatus = runCatching {
                requireApiClient().dataVersion()
            }
            if (isTerminalRefreshFailure(initialStatus.exceptionOrNull())) {
                return@launch
            }
            initialStatus.getOrNull()?.let { status ->
                val previous = lastDataVersion
                lastDataVersion = status.version
                if (previous != null && previous != status.version) {
                    publishDataChangeIfEnabled()
                }
            }

            while (authSession.isAuthenticated) {
                val intervalSeconds = uiState.autoRefreshPolicy
                    .effectiveIntervalSeconds(uiState.autoRefreshPreference)
                delay(intervalSeconds * 1_000L)

                val statusResult = runCatching {
                    requireApiClient().dataVersion()
                }
                if (isTerminalRefreshFailure(statusResult.exceptionOrNull())) {
                    return@launch
                }

                val status = statusResult.getOrNull() ?: continue
                val previous = lastDataVersion
                if (previous == null) {
                    lastDataVersion = status.version
                    publishDataChangeIfEnabled()
                    continue
                }
                if (previous == status.version) continue

                val changedSettings = runCatching { refreshSettings() }
                if (isTerminalRefreshFailure(changedSettings.exceptionOrNull())) {
                    return@launch
                }
                if (changedSettings.isFailure) {
                    continue
                }

                lastDataVersion = status.version
                publishDataChangeIfEnabled()
            }
        }
    }

    private fun publishDataChangeIfEnabled() {
        if (authSession.isAuthenticated &&
            uiState.autoRefreshPolicy.isActive(uiState.autoRefreshPreference)
        ) {
            uiState = uiState.copy(
                autoRefreshGeneration = uiState.autoRefreshGeneration + 1
            )
        }
    }

    private fun isTerminalRefreshFailure(throwable: Throwable?): Boolean =
        throwable is ApiException &&
                (throwable.error is ApiError.AccountSuspended ||
                        throwable.error is ApiError.Unauthorized)

    private fun applyServerSettings(settings: ApplicationSettingsResponse) {
        val policy = settings.toAutoRefreshPolicy()
        val preference = uiState.autoRefreshPreference
        uiState = uiState.copy(
            currencyCode = settings.currencyCode,
            autoRefreshPolicy = policy,
            autoRefreshPreference = preference.copy(
                intervalSeconds = preference.intervalSeconds?.coerceIn(
                    policy.minimumIntervalSeconds,
                    policy.maximumIntervalSeconds
                )
            )
        )
    }

    private fun settingsRequest(
        currencyCode: String,
        policy: AutoRefreshPolicy
    ): UpdateApplicationSettingsRequest =
        UpdateApplicationSettingsRequest(
            currencyCode = currencyCode,
            autoRefreshEnabled = policy.enabled,
            usersCanCustomizeAutoRefresh = policy.usersCanCustomize,
            defaultRefreshIntervalSeconds = policy.defaultIntervalSeconds,
            minimumRefreshIntervalSeconds = policy.minimumIntervalSeconds,
            maximumRefreshIntervalSeconds = policy.maximumIntervalSeconds
        )

    private fun expireSession(
        message: String = "Authentication expired. Sign in again."
    ) {
        if (uiState.stage == AppStage.SERVER_SELECTION) return
        sessionMonitor?.cancel()
        sessionMonitor = null
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        lastDataVersion = null
        authSession.clear()
        uiState = uiState.copy(
            stage = AppStage.LOGIN,
            authenticatedUser = null,
            selectedDestination = AppDestination.DASHBOARD,
            authMessage = message,
            globalMessage = null
        )
    }

    override fun onCleared() {
        apiClient?.close()
        apiClient = null
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        lastDataVersion = null
        authSession.clear()
    }

    private fun Throwable.isNetworkConnectionFailure(): Boolean =
        this is ApiException &&
                (error is ApiError.ConnectionFailure ||
                        error is ApiError.Timeout)
}
