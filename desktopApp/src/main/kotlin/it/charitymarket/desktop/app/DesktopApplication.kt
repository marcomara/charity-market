package it.charitymarket.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.charitymarket.desktop.api.UserRole
import it.charitymarket.desktop.auth.LoginScreen
import it.charitymarket.desktop.auth.PasswordChangeScreen
import it.charitymarket.desktop.components.ConfirmationDialog
import it.charitymarket.desktop.components.FeedbackMessage
import it.charitymarket.desktop.components.RoleBadges
import it.charitymarket.desktop.components.StatusBadge
import it.charitymarket.desktop.dashboard.DashboardScreen
import it.charitymarket.desktop.donors.DonorsScreen
import it.charitymarket.desktop.items.ItemsScreen
import it.charitymarket.desktop.model.AppDestination
import it.charitymarket.desktop.model.RoleRules
import it.charitymarket.desktop.sales.SalesScreen
import it.charitymarket.desktop.settings.SettingsScreen
import it.charitymarket.desktop.startup.LocalHostMode
import it.charitymarket.desktop.startup.LocalSqliteServerController
import it.charitymarket.desktop.startup.StartupScreen
import it.charitymarket.desktop.users.UsersScreen

@Composable
fun CharityMarketDesktopApplication(
    localServerController: LocalSqliteServerController,
    appState: DesktopAppState,
    exitApplication: () -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            when (appState.uiState.stage) {
                AppStage.STARTUP ->
                    StartupScreen(
                        controller = localServerController,
                        onConnected = appState::onConnected
                    )

                AppStage.LOGIN ->
                    LoginScreen(appState)

                AppStage.PASSWORD_CHANGE ->
                    PasswordChangeScreen(appState)

                AppStage.MAIN ->
                    AuthenticatedShell(appState)

                AppStage.LOCAL_SERVER_FAILED ->
                    LocalServerFailureScreen(
                        appState = appState,
                        exitApplication = exitApplication
                    )
            }

            ShutdownDialogs(appState)
        }
    }
}

@Composable
private fun AuthenticatedShell(
    appState: DesktopAppState
) {
    val uiState = appState.uiState
    val user = uiState.authenticatedUser ?: return
    val apiClient = appState.requireApiClient()
    val destinations =
        RoleRules.destinationsFor(user.roles)
    val selectedDestination =
        if (uiState.selectedDestination in destinations) {
            uiState.selectedDestination
        } else {
            destinations.first()
        }

    Row(
        modifier = Modifier.fillMaxSize()
    ) {
        NavigationPanel(
            destinations = destinations,
            selectedDestination = selectedDestination,
            onSelect = appState::selectDestination,
            roles = user.roles,
            connectionMode =
                uiState.connection?.mode as? LocalHostMode
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            TopBar(
                appState = appState,
                roles = user.roles
            )

            HorizontalDivider()

            FeedbackMessage(
                message = uiState.globalMessage,
                isError = uiState.globalMessageIsError,
                modifier = Modifier.padding(
                    horizontal = 20.dp,
                    vertical = 8.dp
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                when (selectedDestination) {
                    AppDestination.DASHBOARD ->
                        DashboardScreen(
                            apiClient = apiClient,
                            appState = appState,
                            roles = user.roles,
                            currencyCode =
                                uiState.currencyCode
                        )

                    AppDestination.USERS ->
                        UsersScreen(
                            apiClient = apiClient,
                            appState = appState
                        )

                    AppDestination.DONORS ->
                        DonorsScreen(
                            apiClient = apiClient,
                            appState = appState,
                            currencyCode =
                                uiState.currencyCode
                        )

                    AppDestination.ITEMS ->
                        ItemsScreen(
                            apiClient = apiClient,
                            appState = appState,
                            currencyCode =
                                uiState.currencyCode
                        )

                    AppDestination.SALES ->
                        SalesScreen(
                            apiClient = apiClient,
                            appState = appState,
                            currencyCode =
                                uiState.currencyCode
                        )

                    AppDestination.SETTINGS ->
                        SettingsScreen(
                            appState = appState
                        )
                }
            }
        }
    }
}

@Composable
private fun NavigationPanel(
    destinations: List<AppDestination>,
    selectedDestination: AppDestination,
    onSelect: (AppDestination) -> Unit,
    roles: Set<UserRole>,
    connectionMode: LocalHostMode?
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Charity Market",
                style =
                    MaterialTheme.typography.titleLarge
            )

            if (connectionMode != null) {
                StatusBadge(
                    text = "Local server running",
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider()

            destinations.forEach { destination ->
                if (destination == selectedDestination) {
                    Button(
                        onClick = {
                            onSelect(destination)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(destination.title)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            onSelect(destination)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(destination.title)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = "Roles",
                style =
                    MaterialTheme.typography.labelLarge
            )
            RoleBadges(roles)

            if (connectionMode != null) {
                HorizontalDivider()
                Text(
                    text = connectionMode.dataDirectory,
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    appState: DesktopAppState,
    roles: Set<UserRole>
) {
    val uiState = appState.uiState
    val user = uiState.authenticatedUser ?: return
    val connection = uiState.connection
    val localMode = connection?.mode as? LocalHostMode

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface
            )
            .padding(
                horizontal = 20.dp,
                vertical = 12.dp
            ),
        horizontalArrangement =
            Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = selectedServerLabel(localMode),
                style =
                    MaterialTheme.typography.titleMedium
            )
            Text(
                text = connection?.baseUrl.orEmpty(),
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(user.displayName)
            Text(
                user.username,
                style = MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }

        RoleBadges(roles)

        Button(
            onClick = {
                appState.logout()
            },
            enabled = !uiState.shuttingDown
        ) {
            Text("Logout")
        }
    }
}

@Composable
private fun LocalServerFailureScreen(
    appState: DesktopAppState,
    exitApplication: () -> Unit
) {
    val failure = appState.uiState.localServerFailure

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(0.65f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement =
                    Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Local server stopped",
                    style =
                        MaterialTheme.typography.headlineSmall
                )

                Text(
                    text = failure?.message
                        ?: "The local server is not running."
                )

                val logFile = failure?.logFile
                if (!logFile.isNullOrBlank()) {
                    Text(
                        text = "Server log: $logFile",
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                    )
                }

                FeedbackMessage(
                    message = appState.uiState.globalMessage,
                    isError =
                        appState.uiState.globalMessageIsError
                )

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            appState.restartLocalServer()
                        }
                    ) {
                        Text("Restart local server")
                    }

                    OutlinedButton(
                        onClick = exitApplication
                    ) {
                        Text("Exit")
                    }
                }
            }
        }
    }
}

@Composable
private fun ShutdownDialogs(
    appState: DesktopAppState
) {
    when (appState.uiState.shutdownDialogState) {
        ShutdownDialogState.NONE -> Unit

        ShutdownDialogState.AUTHENTICATION_REQUIRED ->
            AlertDialog(
                onDismissRequest =
                    appState::cancelShutdownRequest,
                title = {
                    Text("Administrator required")
                },
                text = {
                    Text(
                        "The local Charity Market server is running. A system administrator must be signed in before the server and application can be closed."
                    )
                },
                dismissButton = {
                    TextButton(
                        onClick =
                            appState::cancelShutdownRequest
                    ) {
                        Text("Cancel")
                    }
                },
                confirmButton = {
                    Button(
                        onClick =
                            appState
                                ::signInAsAdministratorForShutdown
                    ) {
                        Text("Sign in as administrator")
                    }
                }
            )

        ShutdownDialogState.CONFIRMATION_REQUIRED ->
            ConfirmationDialog(
                title = "Shut down local server?",
                message = "Closing Charity Market will stop the local server. Other clients connected to this computer will be disconnected. All committed SQLite data will remain saved.",
                confirmLabel = "Shut down and exit",
                busy = appState.uiState.shuttingDown,
                busyMessage =
                    "Stopping local server safely...",
                onCancel =
                    appState::cancelShutdownRequest,
                onConfirm =
                    appState::confirmShutdown
            )

        ShutdownDialogState.FAILED ->
            AlertDialog(
                onDismissRequest =
                    appState::cancelShutdownRequest,
                title = {
                    Text("Shutdown failed")
                },
                text = {
                    Text(
                        appState.uiState
                            .shutdownFailureMessage
                            ?: "The local server could not be stopped."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick =
                            appState::cancelShutdownRequest
                    ) {
                        Text("Close")
                    }
                }
            )
    }
}

private fun selectedServerLabel(
    localMode: LocalHostMode?
): String =
    if (localMode == null) {
        "Connected server"
    } else {
        "Local server running"
    }
