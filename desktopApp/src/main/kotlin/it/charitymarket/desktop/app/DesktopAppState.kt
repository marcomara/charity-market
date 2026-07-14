package it.charitymarket.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import it.charitymarket.desktop.api.ApiError
import it.charitymarket.desktop.api.ApiException
import it.charitymarket.desktop.api.AuthenticatedUserResponse
import it.charitymarket.desktop.api.ChangePasswordRequest
import it.charitymarket.desktop.api.CharityMarketApiClient
import it.charitymarket.desktop.api.LoginRequest
import it.charitymarket.desktop.model.AppDestination
import it.charitymarket.desktop.model.RoleRules
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
    val accessToken: String? = null,
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
    val currencyCode: String = defaultCurrencyCode()
)

class DesktopAppState(
    private val localServerController:
    LocalSqliteServerController,
    private val scope: CoroutineScope
) {
    var uiState by mutableStateOf(AppUiState())
        private set

    private var apiClient: CharityMarketApiClient? = null
    private var localServerMonitorJob: Job? = null
    private var settingsRefreshJob: Job? = null

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
        apiClient = CharityMarketApiClient(
            baseUrl = connection.baseUrl,
            tokenProvider = {
                uiState.accessToken
            },
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

        settingsRefreshJob?.cancel()
        settingsRefreshJob = null

        sessionMonitorJob?.cancel()
        sessionMonitorJob = null

        uiState = uiState.copy(
            stage = if (connection == null) {
                AppStage.STARTUP
            } else {
                AppStage.LOGIN
            },
            authenticatedUser = null,
            accessToken = null,
            selectedDestination = AppDestination.DASHBOARD,
            authMessage = message
                ?: if (connection?.isLocalServer == true) {
                    "Local server running. Sign in to continue."
                } else {
                    "Signed out."
                },
            globalMessage = null,
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
        val settings = requireApiClient()
            .updateSettings(currencyCode)

        uiState = uiState.copy(
            currencyCode = settings.currencyCode,
            globalMessage =
                "Currency changed to ${settings.currencyCode} for every user.",
            globalMessageIsError = false
        )
    }

    suspend fun refreshSettings() {
        val settings = requireApiClient().getSettings()
        uiState = uiState.copy(
            currencyCode = settings.currencyCode
        )
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

        uiState = uiState.copy(
            stage = AppStage.LOGIN,
            authenticatedUser = null,
            accessToken = null,
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
            accessToken = token,
            authenticatedUser = user,
            selectedDestination = nextDestination,
            authMessage = null,
            globalMessage = null,
            shutdownDialogState = shutdownDialog
        )

        if (!passwordChangeRequired) {
            startSettingsRefresh()
            startSessionMonitor()
        }
    }

    private fun startSessionMonitor() {
        sessionMonitorJob?.cancel()

        sessionMonitorJob = scope.launch {
            while (uiState.accessToken != null) {
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
                    uiState = uiState.copy(
                        stage = AppStage.LOGIN,
                        authenticatedUser = null,
                        accessToken = null,
                        selectedDestination =
                            AppDestination.DASHBOARD,
                        authMessage =
                            "Automatic sign-in failed: $message"
                    )
                }
            }
        }
    }

    private fun startSettingsRefresh() {
        settingsRefreshJob?.cancel()

        settingsRefreshJob = scope.launch {
            while (uiState.accessToken != null) {
                runCatching {
                    refreshSettings()
                }.onFailure { exception ->
                    if (exception is ApiException &&
                        exception.error is ApiError.AccountSuspended
                    ) {
                        return@launch
                    }
                }

                delay(30_000)
            }
        }
    }

    private fun handleAccountSuspended(message: String) {
        settingsRefreshJob?.cancel()
        settingsRefreshJob = null

        sessionMonitorJob?.cancel()
        sessionMonitorJob = null

        val connection = uiState.connection
        uiState = uiState.copy(
            stage = if (connection == null) {
                AppStage.STARTUP
            } else {
                AppStage.LOGIN
            },
            authenticatedUser = null,
            accessToken = null,
            selectedDestination = AppDestination.DASHBOARD,
            authMessage = message,
            globalMessage = null,
            globalMessageIsError = false
        )
    }

    private fun handleUnauthorized() {
        settingsRefreshJob?.cancel()
        settingsRefreshJob = null

        sessionMonitorJob?.cancel()
        sessionMonitorJob = null

        if (uiState.stage == AppStage.STARTUP ||
            uiState.stage == AppStage.LOCAL_SERVER_FAILED
        ) {
            return
        }

        uiState = uiState.copy(
            stage = AppStage.LOGIN,
            authenticatedUser = null,
            accessToken = null,
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

                    uiState = uiState.copy(
                        stage = AppStage.LOCAL_SERVER_FAILED,
                        authenticatedUser = null,
                        accessToken = null,
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
