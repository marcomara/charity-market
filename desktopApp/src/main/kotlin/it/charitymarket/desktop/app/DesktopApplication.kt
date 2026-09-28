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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import it.charitymarket.shared.api.UserRole
import it.charitymarket.desktop.auth.LoginScreen
import it.charitymarket.desktop.auth.PasswordChangeScreen
import it.charitymarket.desktop.components.ConfirmationDialog
import it.charitymarket.desktop.components.FeedbackMessage
import it.charitymarket.desktop.components.RoleBadges
import it.charitymarket.desktop.dashboard.DashboardScreen
import it.charitymarket.desktop.donors.DonorsScreen
import it.charitymarket.desktop.items.ItemsScreen
import it.charitymarket.shared.auth.AppDestination
import it.charitymarket.shared.auth.RoleRules
import it.charitymarket.desktop.sales.SalesScreen
import it.charitymarket.desktop.settings.SettingsScreen
import it.charitymarket.desktop.startup.LocalHostMode
import it.charitymarket.desktop.startup.LocalSqliteServerController
import it.charitymarket.desktop.startup.StartupScreen
import it.charitymarket.desktop.users.UsersScreen
import it.charitymarket.shared.i18n.AppLocalization
import it.charitymarket.shared.i18n.LocalAppStrings
import it.charitymarket.shared.preferences.AppPreferences
import it.charitymarket.shared.ui.CharityMarketTheme
import kotlinx.coroutines.launch

@Composable
fun CharityMarketDesktopApplication(
    localServerController: LocalSqliteServerController,
    appState: DesktopAppState,
    appPreferences: AppPreferences,
    onAppPreferencesChange: (AppPreferences) -> Unit,
    exitApplication: () -> Unit
) {
    CharityMarketTheme(appPreferences) {
        AppLocalization(appPreferences.languageTag) {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            when (appState.uiState.stage) {
                AppStage.STARTUP ->
                    StartupScreen(
                        controller = localServerController,
                        appPreferences = appPreferences,
                        onAppPreferencesChange = onAppPreferencesChange,
                        onConnected = appState::onConnected
                    )

                AppStage.LOGIN ->
                    LoginScreen(
                        appState = appState,
                        appPreferences = appPreferences,
                        onAppPreferencesChange = onAppPreferencesChange
                    )

                AppStage.PASSWORD_CHANGE ->
                    PasswordChangeScreen(appState)

                AppStage.MAIN ->
                    AuthenticatedShell(
                        appState = appState,
                        appPreferences = appPreferences,
                        onAppPreferencesChange = onAppPreferencesChange
                    )

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
}

@Composable
private fun AuthenticatedShell(
    appState: DesktopAppState,
    appPreferences: AppPreferences,
    onAppPreferencesChange: (AppPreferences) -> Unit
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val localMode = uiState.connection?.mode as? LocalHostMode

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavigationDrawerContent(
                destinations = destinations,
                selectedDestination = selectedDestination,
                roles = user.roles,
                localMode = localMode,
                onSelect = { destination ->
                    appState.selectDestination(destination)
                    coroutineScope.launch {
                        drawerState.close()
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            TopBar(
                appState = appState,
                roles = user.roles,
                onNavigationClick = {
                    coroutineScope.launch {
                        drawerState.open()
                    }
                }
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
                    .weight(1f)
                    .fillMaxWidth()
            ) {
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
                                appState = appState,
                                appPreferences = appPreferences,
                                onAppPreferencesChange =
                                    onAppPreferencesChange
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    appState: DesktopAppState,
    roles: Set<UserRole>,
    onNavigationClick: () -> Unit
) {
    val strings = LocalAppStrings.current
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
        IconButton(
            onClick = onNavigationClick
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = strings.text("navigation.show")
            )
        }

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
            Text(
                text = if (
                    uiState.autoRefreshPolicy
                        .isActive(uiState.autoRefreshPreference)
                ) {
                    strings.text(
                        "auto_refresh.every",
                        uiState.autoRefreshPolicy
                            .effectiveIntervalSeconds(
                                uiState.autoRefreshPreference
                            )
                    )
                } else {
                    strings.text("auto_refresh.off")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            Text(strings.text("action.logout"))
        }
    }
}

@Composable
private fun NavigationDrawerContent(
    destinations: List<AppDestination>,
    selectedDestination: AppDestination,
    roles: Set<UserRole>,
    localMode: LocalHostMode?,
    onSelect: (AppDestination) -> Unit
) {
    val strings = LocalAppStrings.current
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(300.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = strings.text("app.name"),
                style = MaterialTheme.typography.titleLarge
            )

            if (localMode != null) {
                Text(
                    text = selectedServerLabel(localMode),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider()

            destinations.forEach { destination ->
                NavigationDrawerItem(
                    label = {
                        Text(strings.text(destination.labelKey()))
                    },
                    selected = destination == selectedDestination,
                    icon = {
                        Icon(
                            imageVector = destination.icon(),
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onSelect(destination)
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor =
                            MaterialTheme.colorScheme.secondaryContainer,
                        selectedIconColor =
                            MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor =
                            MaterialTheme.colorScheme.onSecondaryContainer,
                        unselectedIconColor =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = "Roles",
                style = MaterialTheme.typography.labelLarge
            )
            RoleBadges(roles)

            if (localMode != null) {
                HorizontalDivider()
                Text(
                    text = localMode.dataDirectory,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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

private fun AppDestination.icon(): ImageVector = when (this) {
    AppDestination.DASHBOARD -> Icons.Default.Dashboard
    AppDestination.USERS -> Icons.Default.People
    AppDestination.DONORS -> Icons.Default.VolunteerActivism
    AppDestination.ITEMS -> Icons.Default.Inventory2
    AppDestination.SALES -> Icons.AutoMirrored.Filled.ReceiptLong
    AppDestination.SETTINGS -> Icons.Default.Settings
}

private fun AppDestination.labelKey(): String = when (this) {
    AppDestination.DASHBOARD -> "destination.dashboard"
    AppDestination.USERS -> "destination.users"
    AppDestination.DONORS -> "destination.donors"
    AppDestination.ITEMS -> "destination.items"
    AppDestination.SALES -> "destination.sales"
    AppDestination.SETTINGS -> "destination.settings"
}
