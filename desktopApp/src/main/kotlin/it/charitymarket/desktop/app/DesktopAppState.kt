package it.charitymarket.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.client.engine.cio.CIO
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
import it.charitymarket.desktop.model.defaultCurrencyCode
import it.charitymarket.desktop.startup.LocalHostMode
import it.charitymarket.desktop.startup.LocalServerShutdownStatus
import it.charitymarket.desktop.startup.LocalSqliteServerController
import it.charitymarket.desktop.startup.ServerConnection
import it.charitymarket.desktop.startup.StartupLoginCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AppStage {
    STARTUP,
    LOGIN,
    PASSWORD_CHANGE,
    MAIN,
    LOCAL_SERVER_FAILED
}

enum class ShutdownDialogState {
    NONE,
    AUTHENTICATION_REQUIRED,
    CONFIRMATION_REQUIRED,
    FAILED
}

data class LocalServerFailure(
    val message: String,
    val logFile: String?
)

data class AppUiState(
    val stage: AppStage = AppStage.STARTUP,
    val connection: ServerConnection? = null,
    val authenticatedUser: AuthenticatedUserResponse? = null,
    val selectedDestination: AppDestination = AppDestination.DASHBOARD,
    val authMessage: String? = null,
    val globalMessage: String? = null,
    val globalMessageIsError: Boolean = false,
    val pendingShutdownAuthentication: Boolean = false,
    val shutdownDialogState: ShutdownDialogState =
        ShutdownDialogState.NONE,
    val shuttingDown: Boolean = false,
    val shutdownFailureMessage: String? = null,
    val localServerFailure: LocalServerFailure? = null,
    val currencyCode: String = defaultCurrencyCode(),
    val autoRefreshPolicy: AutoRefreshPolicy = AutoRefreshPolicy(),
    val autoRefreshPreference: ClientAutoRefreshPreference =
        ClientAutoRefreshPreference(),
    val autoRefreshGeneration: Long = 0
)

class DesktopAppState(
    private val localServerController:
    LocalSqliteServerController,
    private val scope: CoroutineScope
) {
    var uiState by mutableStateOf(AppUiState())
        private set

    private var apiClient: CharityMarketApiClient? = null
    private val authSession = InMemoryAuthSession()
    private var localServerMonitorJob: Job? = null
    private var autoRefreshJob: Job? = null
    private var lastDataVersion: Long? = null

    private var sessionMonitorJob: Job? = null
    private var pendingExitApplication: (() -> Unit)? = null

    fun requireApiClient(): CharityMarketApiClient =
        apiClient
            ?: throw IllegalStateException(
                "The API client is not connected."
            )

    fun onConnected(
        connection: ServerConnection,
        startupLoginCredentials: StartupLoginCredentials? = null
    ) {
        apiClient?.close()
        authSession.clear()
        lastDataVersion = null
        apiClient = CharityMarketApiClient(
            baseUrl = connection.baseUrl,
            engineFactory = CIO,
            tokenProvider = { authSession.accessToken },
            onUnauthorized = {
                handleUnauthorized()
            },
            onAccountSuspended = { message ->
                handleAccountSuspended(message)
            }
        )

        uiState = AppUiState(
            stage = AppStage.LOGIN,
            connection = connection,
            authMessage = if (connection.isLocalServer) {
                if (startupLoginCredentials == null) {
                    "Local server running. Sign in to continue."
                } else {
                    "Local server running. Signing in..."
                }
            } else {
                "Server connection ready. Sign in to continue."
            }
        )

        if (connection.isLocalServer) {
            startLocalServerMonitor(connection)
        } else {
            localServerMonitorJob?.cancel()
            localServerMonitorJob = null
        }

        if (startupLoginCredentials != null) {
            signInWithStartupCredentials(
                startupLoginCredentials
            )
        }
    }

    suspend fun login(
        username: String,
        password: String
    ) {
        val response = requireApiClient().login(
            LoginRequest(
                username = username,
                password = password
            )
        )

        applyLoginResponse(
            token = response.accessToken,
            user = response.user,
            passwordChangeRequired =
                response.passwordChangeRequired
        )
    }

    suspend fun changeInitialPassword(
        currentPassword: String,
        newPassword: String,
        confirmation: String
    ) {
        val response = requireApiClient()
            .changeInitialPassword(
                ChangePasswordRequest(
                    currentPassword = currentPassword,
                    newPassword = newPassword,
                    confirmation = confirmation
                )
            )

        applyLoginResponse(
            token = response.accessToken,
            user = response.user,
            passwordChangeRequired = false
        )
    }

    fun logout(
        message: String? = null
    ) {
        val connection = uiState.connection

        autoRefreshJob?.cancel()
        autoRefreshJob = null
        lastDataVersion = null

        sessionMonitorJob?.cancel()
        sessionMonitorJob = null
        authSession.clear()

        uiState = uiState.copy(
            stage = if (connection == null) {
                AppStage.STARTUP
            } else {
                AppStage.LOGIN
            },
            authenticatedUser = null,
            selectedDestination = AppDestination.DASHBOARD,
            authMessage = message
                ?: if (connection?.isLocalServer == true) {
                    "Local server running. Sign in to continue."
                } else {
                    "Signed out."
                },
            globalMessage = null,
            autoRefreshPreference = ClientAutoRefreshPreference(),
            autoRefreshGeneration = 0,
            pendingShutdownAuthentication =
                uiState.pendingShutdownAuthentication,
            shutdownDialogState =
                uiState.shutdownDialogState
        )
    }

    fun returnToServerSelection() {
        val connection = uiState.connection ?: return

        if (connection.isLocalServer) {
            return
        }

        apiClient?.close()
        apiClient = null
        authSession.clear()
        lastDataVersion = null

        uiState = AppUiState()
    }

    fun selectDestination(
        destination: AppDestination
    ) {
        val roles = uiState.authenticatedUser?.roles.orEmpty()

        if (destination in RoleRules.destinationsFor(roles)) {
            uiState = uiState.copy(
                selectedDestination = destination
            )
        }
    }

    fun handleRequestFailure(
        throwable: Throwable
    ): String {
        val apiException = throwable as? ApiException
            ?: return throwable.message
                ?: "The operation failed."

        return when (apiException.error) {
            is ApiError.Unauthorized -> {
                handleUnauthorized()
                apiException.error.userMessage
            }

            is ApiError.AccountSuspended -> {
                handleAccountSuspended(
                    apiException.error.userMessage
                )
                apiException.error.userMessage
            }

            is ApiError.Forbidden -> {
                uiState = uiState.copy(
                    globalMessage =
                        "Insufficient permissions.",
                    globalMessageIsError = true
                )
                apiException.error.userMessage
            }

            else -> apiException.error.userMessage
        }
    }

    fun clearGlobalMessage() {
        uiState = uiState.copy(
            globalMessage = null,
            globalMessageIsError = false
        )
    }

    suspend fun updateCurrency(
        currencyCode: String
    ) {
        val settings = requireApiClient().updateSettings(
            settingsRequest(
                currencyCode = currencyCode,
                policy = uiState.autoRefreshPolicy
            )
        )

        applyServerSettings(settings)
        uiState = uiState.copy(
            globalMessage =
                "Currency changed to ${settings.currencyCode} for every user.",
            globalMessageIsError = false
        )
    }

    suspend fun updateAutoRefreshPolicy(policy: AutoRefreshPolicy) {
        val validatedPolicy = policy.validated()
        val settings = requireApiClient().updateSettings(
            settingsRequest(
                currencyCode = uiState.currencyCode,
                policy = validatedPolicy
            )
        )
        applyServerSettings(settings)
        uiState = uiState.copy(
            globalMessage = "Auto-refresh policy updated for every client.",
            globalMessageIsError = false
        )
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

    suspend fun refreshSettings() {
        val settings = requireApiClient().getSettings()
        applyServerSettings(settings)
    }

    fun requestWindowClose(
        exitApplication: () -> Unit
    ) {
        if (uiState.shuttingDown) {
            return
        }

        pendingExitApplication = exitApplication

        val connection = uiState.connection
        val localServerRunning =
            connection?.isLocalServer == true &&
                    localServerController
                        .isLocalServerRunning()

        if (!localServerRunning) {
            exitApplication()
            return
        }

        val user = uiState.authenticatedUser
        val canShutdown =
            user != null &&
                    RoleRules.canShutDownLocalServer(
                        user.roles
                    )

        uiState = if (canShutdown) {
            uiState.copy(
                shutdownDialogState =
                    ShutdownDialogState
                        .CONFIRMATION_REQUIRED
            )
        } else {
            uiState.copy(
                pendingShutdownAuthentication = true,
                shutdownDialogState =
                    ShutdownDialogState
                        .AUTHENTICATION_REQUIRED
            )
        }
    }

    fun cancelShutdownRequest() {
        uiState = uiState.copy(
            pendingShutdownAuthentication = false,
            shutdownDialogState = ShutdownDialogState.NONE,
            shutdownFailureMessage = null
        )
    }

    fun signInAsAdministratorForShutdown() {
        val message =
            "Sign in as a system administrator to close the local server."

        authSession.clear()
        uiState = uiState.copy(
            stage = AppStage.LOGIN,
            authenticatedUser = null,
            selectedDestination = AppDestination.DASHBOARD,
            authMessage = message,
            pendingShutdownAuthentication = true,
            shutdownDialogState = ShutdownDialogState.NONE
        )
    }

    fun confirmShutdown() {
        if (uiState.shuttingDown) {
            return
        }

        scope.launch {
            autoRefreshJob?.cancel()
            autoRefreshJob = null
            sessionMonitorJob?.cancel()
            sessionMonitorJob = null
            uiState = uiState.copy(
                shuttingDown = true,
                shutdownDialogState =
                    ShutdownDialogState
                        .CONFIRMATION_REQUIRED,
                globalMessage = null
            )

            val result =
                localServerController
                    .stopLocalServerGracefully()

            when (result.status) {
                LocalServerShutdownStatus.ALREADY_STOPPED,
                LocalServerShutdownStatus.STOPPED_GRACEFULLY,
                LocalServerShutdownStatus.FORCED_STOP -> {
                    authSession.clear()
                    apiClient?.close()
                    pendingExitApplication?.invoke()
                }

                LocalServerShutdownStatus.FAILED -> {
                    uiState = uiState.copy(
                        shuttingDown = false,
                        shutdownDialogState =
                            ShutdownDialogState.FAILED,
                        shutdownFailureMessage =
                            result.message
                    )
                }
            }
        }
    }

    fun restartLocalServer() {
        if (uiState.shuttingDown) {
            return
        }

        scope.launch {
            uiState = uiState.copy(
                globalMessage = "Restarting local server...",
                globalMessageIsError = false
            )

            runCatching {
                localServerController.restartLastLocalServer()
            }.onSuccess { connection ->
                onConnected(connection)
            }.onFailure { exception ->
                uiState = uiState.copy(
                    globalMessage =
                        exception.message
                            ?: "The local server could not be restarted.",
                    globalMessageIsError = true
                )
            }
        }
    }

    private fun applyLoginResponse(
        token: String,
        user: AuthenticatedUserResponse,
        passwordChangeRequired: Boolean
    ) {
        authSession.establish(token, user)
        lastDataVersion = null
        val destinations =
            RoleRules.destinationsFor(user.roles)

        val nextDestination =
            destinations.firstOrNull()
                ?: AppDestination.DASHBOARD

        val localPendingShutdown =
            uiState.connection?.isLocalServer == true &&
                    uiState.pendingShutdownAuthentication

        val shutdownDialog =
            if (localPendingShutdown &&
                !passwordChangeRequired
            ) {
                if (
                    RoleRules.canShutDownLocalServer(
                        user.roles
                    )
                ) {
                    ShutdownDialogState
                        .CONFIRMATION_REQUIRED
                } else {
                    ShutdownDialogState
                        .AUTHENTICATION_REQUIRED
                }
            } else {
                ShutdownDialogState.NONE
            }

        uiState = uiState.copy(
            stage = if (passwordChangeRequired) {
                AppStage.PASSWORD_CHANGE
            } else {
                AppStage.MAIN
            },
            authenticatedUser = user,
            selectedDestination = nextDestination,
            authMessage = null,
            globalMessage = null,
            shutdownDialogState = shutdownDialog
        )

        if (!passwordChangeRequired) {
            startAutoRefresh()
            startSessionMonitor()
        }
    }

    private fun startSessionMonitor() {
        sessionMonitorJob?.cancel()

        sessionMonitorJob = scope.launch {
            while (authSession.isAuthenticated) {
                delay(5_000)

                try {
                    requireApiClient().tokenInfo()
                } catch (exception: ApiException) {
                    when (exception.error) {
                        is ApiError.AccountSuspended,
                        is ApiError.Unauthorized -> {
                            /*
                             * The API client callback has already
                             * cleared the authenticated session.
                             */
                            return@launch
                        }

                        else -> {
                            /*
                             * Temporary network or server errors
                             * should not immediately log the user out.
                             */
                        }
                    }
                }
            }
        }
    }

    private fun signInWithStartupCredentials(
        credentials: StartupLoginCredentials
    ) {
        scope.launch {
            runCatching {
                login(
                    username = credentials.username,
                    password = credentials.password
                )
            }.onFailure { exception ->
                val message =
                    handleRequestFailure(exception)

                if (uiState.stage != AppStage.LOCAL_SERVER_FAILED) {
                    authSession.clear()
                    uiState = uiState.copy(
                        stage = AppStage.LOGIN,
                        authenticatedUser = null,
                        selectedDestination =
                            AppDestination.DASHBOARD,
                        authMessage =
                            "Automatic sign-in failed: $message"
                    )
                }
            }
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()

        autoRefreshJob = scope.launch {
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
                if (changedSettings.isFailure) continue

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

    private fun applyServerSettings(
        settings: ApplicationSettingsResponse
    ) {
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

    private fun handleAccountSuspended(message: String) {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        lastDataVersion = null

        sessionMonitorJob?.cancel()
        sessionMonitorJob = null
        authSession.clear()

        val connection = uiState.connection
        uiState = uiState.copy(
            stage = if (connection == null) {
                AppStage.STARTUP
            } else {
                AppStage.LOGIN
            },
            authenticatedUser = null,
            selectedDestination = AppDestination.DASHBOARD,
            authMessage = message,
            globalMessage = null,
            globalMessageIsError = false
        )
    }

    private fun handleUnauthorized() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        lastDataVersion = null

        sessionMonitorJob?.cancel()
        sessionMonitorJob = null

        if (uiState.stage == AppStage.STARTUP ||
            uiState.stage == AppStage.LOCAL_SERVER_FAILED
        ) {
            return
        }

        authSession.clear()
        uiState = uiState.copy(
            stage = AppStage.LOGIN,
            authenticatedUser = null,
            selectedDestination = AppDestination.DASHBOARD,
            authMessage =
                "Authentication expired. Sign in again."
        )
    }

    private fun startLocalServerMonitor(
        connection: ServerConnection
    ) {
        localServerMonitorJob?.cancel()

        val localMode =
            connection.mode as? LocalHostMode

        localServerMonitorJob = scope.launch {
            delay(1_500)

            while (
                uiState.connection?.isLocalServer == true &&
                !uiState.shuttingDown
            ) {
                if (!localServerController.isLocalServerRunning()) {
                    val logFile =
                        localMode?.logFile
                            ?: localServerController
                                .lastLocalLogFile()

                    apiClient?.close()
                    apiClient = null
                    autoRefreshJob?.cancel()
                    autoRefreshJob = null
                    lastDataVersion = null
                    sessionMonitorJob?.cancel()
                    sessionMonitorJob = null
                    authSession.clear()

                    uiState = uiState.copy(
                        stage = AppStage.LOCAL_SERVER_FAILED,
                        authenticatedUser = null,
                        globalMessage = null,
                        authMessage = null,
                        localServerFailure =
                            LocalServerFailure(
                                message =
                                    "The local server stopped unexpectedly.",
                                logFile = logFile
                            )
                    )
                    return@launch
                }

                delay(1_500)
            }
        }
    }
}
