package it.charitymarket.desktop.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.charitymarket.desktop.app.DesktopAppState
import it.charitymarket.desktop.components.FeedbackMessage
import it.charitymarket.desktop.components.PasswordField
import it.charitymarket.desktop.components.ScreenHeader
import it.charitymarket.desktop.model.supportedCurrencyCodes
import it.charitymarket.shared.auth.RoleRules
import it.charitymarket.shared.i18n.LocalAppStrings
import it.charitymarket.shared.preferences.AppPreferences
import it.charitymarket.shared.settings.AutoRefreshPolicy
import it.charitymarket.shared.ui.AppSettingsPanel
import kotlinx.coroutines.launch
import java.util.Currency
import java.util.Locale

@Composable
fun SettingsScreen(
    appState: DesktopAppState,
    appPreferences: AppPreferences,
    onAppPreferencesChange: (AppPreferences) -> Unit
) {
    val uiState = appState.uiState
    val policy = uiState.autoRefreshPolicy
    val preference = uiState.autoRefreshPreference
    val strings = LocalAppStrings.current
    val roles = uiState.authenticatedUser?.roles.orEmpty()
    val canManageGlobalSettings = RoleRules.canManageSettings(roles)
    val coroutineScope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var personalIntervalText by remember(
        policy,
        preference.intervalSeconds
    ) {
        mutableStateOf(
            (preference.intervalSeconds ?: policy.defaultIntervalSeconds)
                .toString()
        )
    }
    var globalEnabled by remember(policy) { mutableStateOf(policy.enabled) }
    var usersCanCustomize by remember(policy) {
        mutableStateOf(policy.usersCanCustomize)
    }
    var defaultIntervalText by remember(policy) {
        mutableStateOf(policy.defaultIntervalSeconds.toString())
    }
    var minimumIntervalText by remember(policy) {
        mutableStateOf(policy.minimumIntervalSeconds.toString())
    }
    var maximumIntervalText by remember(policy) {
        mutableStateOf(policy.maximumIntervalSeconds.toString())
    }
    var showResetDatabaseDialog by remember { mutableStateOf(false) }
    var administratorPassword by remember { mutableStateOf("") }
    var administratorPasswordVisible by remember { mutableStateOf(false) }
    var resetDatabaseError by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (submitting) return
        coroutineScope.launch {
            submitting = true
            errorMessage = null
            runCatching { appState.refreshSettings() }
                .onFailure {
                    errorMessage = appState.handleRequestFailure(it)
                }
            submitting = false
        }
    }

    fun changeCurrency(currencyCode: String) {
        if (submitting || currencyCode == uiState.currencyCode) return
        coroutineScope.launch {
            submitting = true
            errorMessage = null
            runCatching { appState.updateCurrency(currencyCode) }
                .onFailure {
                    errorMessage = appState.handleRequestFailure(it)
                }
            submitting = false
        }
    }

    fun applyPersonalPreference() {
        val interval = personalIntervalText.toIntOrNull()
        if (interval == null || interval !in policy.intervalRange) {
            errorMessage =
                "Choose an interval between ${policy.minimumIntervalSeconds} and ${policy.maximumIntervalSeconds} seconds."
            return
        }
        errorMessage = null
        runCatching {
            appState.updateAutoRefreshPreference(
                enabled = preference.enabled,
                intervalSeconds = interval
            )
        }.onFailure { errorMessage = it.message }
    }

    fun saveGlobalPolicy() {
        val defaultInterval = defaultIntervalText.toIntOrNull()
        val minimumInterval = minimumIntervalText.toIntOrNull()
        val maximumInterval = maximumIntervalText.toIntOrNull()
        if (defaultInterval == null || minimumInterval == null ||
            maximumInterval == null
        ) {
            errorMessage = "All refresh intervals must be whole seconds."
            return
        }

        val draft = AutoRefreshPolicy(
            enabled = globalEnabled,
            usersCanCustomize = usersCanCustomize,
            defaultIntervalSeconds = defaultInterval,
            minimumIntervalSeconds = minimumInterval,
            maximumIntervalSeconds = maximumInterval
        )
        val validationError = runCatching { draft.validated() }
            .exceptionOrNull()
        if (validationError != null) {
            errorMessage = validationError.message
            return
        }

        coroutineScope.launch {
            submitting = true
            errorMessage = null
            runCatching { appState.updateAutoRefreshPolicy(draft) }
                .onFailure {
                    errorMessage = appState.handleRequestFailure(it)
                }
            submitting = false
        }
    }

    fun resetDatabase() {
        if (administratorPassword.isBlank()) {
            resetDatabaseError = "Enter your administrator password."
            return
        }

        coroutineScope.launch {
            submitting = true
            errorMessage = null
            resetDatabaseError = null
            runCatching {
                appState.resetDatabase(administratorPassword)
            }.onSuccess {
                administratorPassword = ""
                administratorPasswordVisible = false
                showResetDatabaseDialog = false
            }.onFailure {
                resetDatabaseError = appState.handleRequestFailure(it)
            }
            submitting = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(
            title = strings.text("settings.title"),
            subtitle = strings.text("settings.subtitle"),
            action = {
                Button(onClick = ::refresh, enabled = !submitting) {
                    Text(strings.text("action.refresh"))
                }
            }
        )

        FeedbackMessage(message = errorMessage, isError = true)

        AppSettingsPanel(
            preferences = appPreferences,
            onPreferencesChange = onAppPreferencesChange
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("This client", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (!policy.enabled) {
                        "Automatic data refresh is disabled by the administrator."
                    } else if (policy.isActive(preference)) {
                        "Data refreshes every ${policy.effectiveIntervalSeconds(preference)} seconds."
                    } else {
                        "Automatic data refresh is disabled for this client."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (policy.usersCanCustomize) {
                    SettingSwitchRow(
                        label = "Enable automatic refresh on this client",
                        checked = preference.enabled,
                        enabled = policy.enabled && !submitting,
                        onCheckedChange = { enabled ->
                            errorMessage = null
                            runCatching {
                                appState.updateAutoRefreshPreference(
                                    enabled = enabled,
                                    intervalSeconds = personalIntervalText
                                        .toIntOrNull()
                                        ?.coerceIn(
                                            policy.minimumIntervalSeconds,
                                            policy.maximumIntervalSeconds
                                        )
                                )
                            }.onFailure { errorMessage = it.message }
                        }
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = personalIntervalText,
                            onValueChange = {
                                personalIntervalText =
                                    it.filter(Char::isDigit)
                            },
                            label = { Text("Interval (seconds)") },
                            supportingText = {
                                Text(
                                    "Allowed: ${policy.minimumIntervalSeconds}–${policy.maximumIntervalSeconds}"
                                )
                            },
                            enabled = policy.enabled && preference.enabled &&
                                    !submitting,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = ::applyPersonalPreference,
                            enabled = policy.enabled && preference.enabled &&
                                    !submitting
                        ) {
                            Text("Apply")
                        }
                    }
                } else {
                    Text(
                        "The refresh interval is managed by the administrator.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (canManageGlobalSettings) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Currency", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "The server stores monetary values as integer cents.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    supportedCurrencyCodes().forEach { currencyCode ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.currencyCode == currencyCode,
                                onClick = { changeCurrency(currencyCode) },
                                enabled = !submitting
                            )
                            Text(
                                "$currencyCode - ${currencyDisplayName(currencyCode)}"
                            )
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Global automatic refresh",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "This policy is returned by the server to every authenticated client.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SettingSwitchRow(
                        label = "Enable automatic data refresh",
                        checked = globalEnabled,
                        enabled = !submitting,
                        onCheckedChange = { globalEnabled = it }
                    )
                    SettingSwitchRow(
                        label = "Allow users to customize their client",
                        checked = usersCanCustomize,
                        enabled = !submitting,
                        onCheckedChange = { usersCanCustomize = it }
                    )
                    IntervalFields(
                        defaultValue = defaultIntervalText,
                        minimumValue = minimumIntervalText,
                        maximumValue = maximumIntervalText,
                        enabled = !submitting,
                        onDefaultChange = {
                            defaultIntervalText = it.filter(Char::isDigit)
                        },
                        onMinimumChange = {
                            minimumIntervalText = it.filter(Char::isDigit)
                        },
                        onMaximumChange = {
                            maximumIntervalText = it.filter(Char::isDigit)
                        }
                    )
                    Text(
                        "Intervals must be between 5 and 3600 seconds; the default must be inside the selected range.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = ::saveGlobalPolicy,
                        enabled = !submitting
                    ) {
                        Text("Save global policy")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Database reset",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Creates a backup on the server, then removes donors, items, transactions, and other user accounts. Your administrator account stays active.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            resetDatabaseError = null
                            showResetDatabaseDialog = true
                        },
                        enabled = !submitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Empty database")
                    }
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Server settings", style = MaterialTheme.typography.titleMedium)
                    Text("Currency: ${uiState.currencyCode}")
                    Text(
                        "Only a system administrator can change the global refresh policy.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showResetDatabaseDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!submitting) {
                    showResetDatabaseDialog = false
                    resetDatabaseError = null
                }
            },
            title = {
                Text("Empty database")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "This action creates a backup first, then empties server data. Re-enter your administrator password to continue."
                    )
                    PasswordField(
                        value = administratorPassword,
                        onValueChange = {
                            administratorPassword = it
                            resetDatabaseError = null
                        },
                        label = "Administrator password",
                        visible = administratorPasswordVisible,
                        onToggleVisible = {
                            administratorPasswordVisible =
                                !administratorPasswordVisible
                        },
                        enabled = !submitting,
                        supportingText = resetDatabaseError,
                        isError = resetDatabaseError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showResetDatabaseDialog = false
                        resetDatabaseError = null
                    },
                    enabled = !submitting
                ) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                Button(
                    onClick = ::resetDatabase,
                    enabled = !submitting && administratorPassword.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(
                        if (submitting) {
                            "Emptying..."
                        } else {
                            "Empty database"
                        }
                    )
                }
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun IntervalFields(
    defaultValue: String,
    minimumValue: String,
    maximumValue: String,
    enabled: Boolean,
    onDefaultChange: (String) -> Unit,
    onMinimumChange: (String) -> Unit,
    onMaximumChange: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = defaultValue,
            onValueChange = onDefaultChange,
            label = { Text("Default seconds") },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = minimumValue,
            onValueChange = onMinimumChange,
            label = { Text("Minimum seconds") },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = maximumValue,
            onValueChange = onMaximumChange,
            label = { Text("Maximum seconds") },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun currencyDisplayName(currencyCode: String): String = runCatching {
    Currency.getInstance(currencyCode).getDisplayName(Locale.getDefault())
}.getOrDefault(currencyCode)
