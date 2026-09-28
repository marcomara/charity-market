package it.charitymarket.android.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolunteerActivism
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.charitymarket.android.auth.LoginScreen
import it.charitymarket.android.auth.PasswordChangeScreen
import it.charitymarket.android.auth.ServerSelectionScreen
import it.charitymarket.android.components.RoleBadges
import it.charitymarket.android.dashboard.DashboardScreen
import it.charitymarket.android.donors.DonorsScreen
import it.charitymarket.android.items.ItemsScreen
import it.charitymarket.shared.auth.AppDestination
import it.charitymarket.shared.auth.RoleRules
import it.charitymarket.android.sales.SalesScreen
import it.charitymarket.android.settings.SettingsScreen
import it.charitymarket.android.users.UsersScreen
import it.charitymarket.shared.i18n.AppLocalization
import it.charitymarket.shared.i18n.LocalAppStrings
import it.charitymarket.shared.preferences.AppPreferences
import it.charitymarket.shared.ui.CharityMarketTheme
import kotlinx.coroutines.launch

@Composable
fun CharityMarketAndroidApplication(
    appState: AndroidAppState = viewModel()
) {
    val context = LocalContext.current
    var appPreferences by remember {
        mutableStateOf(AndroidAppPreferences.load(context))
    }

    fun updateAppPreferences(preferences: AppPreferences) {
        appPreferences = preferences
        AndroidAppPreferences.save(context, preferences)
    }

    CharityMarketTheme(appPreferences) {
        AppLocalization(appPreferences.languageTag) {
        Surface(
            modifier = Modifier.fillMaxSize().safeDrawingPadding()
        ) {
            when (appState.uiState.stage) {
                AppStage.SERVER_SELECTION -> ServerSelectionScreen(
                    appState = appState,
                    appPreferences = appPreferences,
                    onAppPreferencesChange = ::updateAppPreferences
                )
                AppStage.LOGIN -> LoginScreen(
                    appState = appState,
                    appPreferences = appPreferences,
                    onAppPreferencesChange = ::updateAppPreferences
                )
                AppStage.PASSWORD_CHANGE -> PasswordChangeScreen(appState)
                AppStage.MAIN -> AuthenticatedShell(
                    appState = appState,
                    appPreferences = appPreferences,
                    onAppPreferencesChange = ::updateAppPreferences
                )
            }
        }
        }
    }
}

@Composable
private fun AuthenticatedShell(
    appState: AndroidAppState,
    appPreferences: AppPreferences,
    onAppPreferencesChange: (AppPreferences) -> Unit
) {
    val uiState = appState.uiState
    val user = uiState.authenticatedUser ?: return
    val destinations = RoleRules.destinationsFor(user.roles)
    val strings = LocalAppStrings.current
    val selected = if (uiState.selectedDestination in destinations) {
        uiState.selectedDestination
    } else destinations.first()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.globalMessage) {
        val message = uiState.globalMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        appState.consumeGlobalMessage()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            NavigationDrawerContent(
                destinations = destinations,
                selected = selected,
                onDestinationSelected = { destination ->
                    appState.selectDestination(destination)
                    coroutineScope.launch {
                        drawerState.close()
                    }
                }
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Surface(tonalElevation = 3.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        drawerState.open()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription =
                                        strings.text("navigation.show")
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    strings.text("app.name"),
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    uiState.baseUrl.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color =
                                        MaterialTheme.colorScheme
                                            .onSurfaceVariant
                                )
                            }
                            Button(onClick = { appState.logout() }) {
                                Text(strings.text("action.logout"))
                            }
                        }
                        Text(
                            "${user.displayName} - ${user.username}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            if (
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
                        RoleBadges(user.roles)
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(padding)
                    .padding(12.dp)
            ) {
                when (selected) {
                    AppDestination.DASHBOARD -> DashboardScreen(appState)
                    AppDestination.USERS -> UsersScreen(appState)
                    AppDestination.DONORS -> DonorsScreen(appState)
                    AppDestination.ITEMS -> ItemsScreen(appState)
                    AppDestination.SALES -> SalesScreen(appState)
                    AppDestination.SETTINGS -> SettingsScreen(
                        appState = appState,
                        appPreferences = appPreferences,
                        onAppPreferencesChange = onAppPreferencesChange
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationDrawerContent(
    destinations: List<AppDestination>,
    selected: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit
) {
    val strings = LocalAppStrings.current
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = strings.text("navigation.title"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                )
            )
            HorizontalDivider()

            destinations.forEach { destination ->
                NavigationDrawerItem(
                    label = {
                        Text(strings.text(destination.labelKey()))
                    },
                    selected = destination == selected,
                    icon = {
                        Icon(
                            imageVector = destination.icon(),
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onDestinationSelected(destination)
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
        }
    }
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
