package it.charitymarket.android.settings

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
import it.charitymarket.android.app.AndroidAppState
import it.charitymarket.android.components.FeedbackMessage
import it.charitymarket.android.components.PasswordField
import it.charitymarket.android.components.ScreenHeader
import it.charitymarket.android.model.supportedCurrencyCodes
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
    appState: AndroidAppState,
    appPreferences: AppPreferences,
    onAppPreferencesChange: (AppPreferences) -> Unit
) {
    val uiState = appState.uiState
    val policy = uiState.autoRefreshPolicy
    val preference = uiState.autoRefreshPreference
    val strings = LocalAppStrings.current
    val canManageGlobalSettings = RoleRules.canManageSettings(
        uiState.authenticatedUser?.roles.orEmpty()
    )
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var personalInterval by remember(policy, preference.intervalSeconds) {
        mutableStateOf(
            (preference.intervalSeconds ?: policy.defaultIntervalSeconds)
                .toString()
        )
    }
    var globalEnabled by remember(policy) { mutableStateOf(policy.enabled) }
    var usersCanCustomize by remember(policy) {
        mutableStateOf(policy.usersCanCustomize)
    }
    var defaultInterval by remember(policy) {
        mutableStateOf(policy.defaultIntervalSeconds.toString())
    }
    var minimumInterval by remember(policy) {
        mutableStateOf(policy.minimumIntervalSeconds.toString())
    }
    var maximumInterval by remember(policy) {
        mutableStateOf(policy.maximumIntervalSeconds.toString())
    }
    var showResetDatabaseDialog by remember { mutableStateOf(false) }
    var administratorPassword by remember { mutableStateOf("") }
    var administratorPasswordVisible by remember { mutableStateOf(false) }
    var resetDatabaseError by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            runCatching { appState.refreshSettings() }
                .onFailure { error = appState.handleRequestFailure(it) }
            busy = false
        }
    }

    fun changeCurrency(currencyCode: String) {
        if (busy || currencyCode == uiState.currencyCode) return
        scope.launch {
            busy = true
            error = null
            runCatching { appState.updateCurrency(currencyCode) }
                .onFailure { error = appState.handleRequestFailure(it) }
            busy = false
        }
    }

    fun applyPersonalInterval() {
        val seconds = personalInterval.toIntOrNull()
        if (seconds == null || seconds !in policy.intervalRange) {
            error =
                "Choose an interval between ${policy.minimumIntervalSeconds} and ${policy.maximumIntervalSeconds} seconds."
            return
        }
        error = null
        runCatching {
            appState.updateAutoRefreshPreference(
                enabled = preference.enabled,
                intervalSeconds = seconds
            )
        }.onFailure { error = it.message }
    }

    fun saveGlobalPolicy() {
        val defaultSeconds = defaultInterval.toIntOrNull()
        val minimumSeconds = minimumInterval.toIntOrNull()
        val maximumSeconds = maximumInterval.toIntOrNull()
        if (defaultSeconds == null || minimumSeconds == null ||
            maximumSeconds == null
        ) {
            error = "All refresh intervals must be whole seconds."
            return
        }
        val draft = AutoRefreshPolicy(
            enabled = globalEnabled,
            usersCanCustomize = usersCanCustomize,
            defaultIntervalSeconds = defaultSeconds,
            minimumIntervalSeconds = minimumSeconds,
            maximumIntervalSeconds = maximumSeconds
        )
        val validationError = runCatching { draft.validated() }
            .exceptionOrNull()
        if (validationError != null) {
            error = validationError.message
            return
        }

        scope.launch {
            busy = true
            error = null
            runCatching { appState.updateAutoRefreshPolicy(draft) }
                .onFailure { error = appState.handleRequestFailure(it) }
            busy = false
        }
    }

    fun resetDatabase() {
        if (administratorPassword.isBlank()) {
            resetDatabaseError = "Enter your administrator password."
            return
        }

        scope.launch {
            busy = true
            error = null
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
            busy = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(
            strings.text("settings.title"),
            strings.text("settings.subtitle"),
            strings.text("action.refresh"),
            !busy,
            ::refresh
        )
        FeedbackMessage(error, true)

        AppSettingsPanel(
            preferences = appPreferences,
            onPreferencesChange = onAppPreferencesChange
        )

        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    SwitchRow(
                        label = "Enable automatic refresh",
                        checked = preference.enabled,
                        enabled = policy.enabled && !busy
                    ) { enabled ->
                        error = null
                        runCatching {
                            appState.updateAutoRefreshPreference(
                                enabled = enabled,
                                intervalSeconds = personalInterval.toIntOrNull()
                                    ?.coerceIn(
                                        policy.minimumIntervalSeconds,
                                        policy.maximumIntervalSeconds
                                    )
                            )
                        }.onFailure { error = it.message }
                    }
                    OutlinedTextField(
                        value = personalInterval,
                        onValueChange = {
                            personalInterval = it.filter(Char::isDigit)
                        },
                        label = { Text("Interval in seconds") },
                        supportingText = {
                            Text(
                                "Allowed: ${policy.minimumIntervalSeconds}–${policy.maximumIntervalSeconds}"
                            )
                        },
                        singleLine = true,
                        enabled = policy.enabled && preference.enabled && !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = ::applyPersonalInterval,
                        enabled = policy.enabled && preference.enabled && !busy
                    ) {
                        Text("Apply to this client")
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
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Currency", style = MaterialTheme.typography.titleMedium)
                    supportedCurrencyCodes().forEach { code ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.currencyCode == code,
                                onClick = { changeCurrency(code) },
                                enabled = !busy
                            )
                            Text("$code — ${currencyName(code)}")
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Global automatic refresh",
                        style = MaterialTheme.typography.titleMedium
                    )
                    SwitchRow(
                        "Enable automatic data refresh",
                        globalEnabled,
                        !busy
                    ) { globalEnabled = it }
                    SwitchRow(
                        "Allow personal client settings",
                        usersCanCustomize,
                        !busy
                    ) { usersCanCustomize = it }
                    IntervalField(
                        "Default seconds",
                        defaultInterval,
                        !busy
                    ) { defaultInterval = it.filter(Char::isDigit) }
                    IntervalField(
                        "Minimum seconds",
                        minimumInterval,
                        !busy
                    ) { minimumInterval = it.filter(Char::isDigit) }
                    IntervalField(
                        "Maximum seconds",
                        maximumInterval,
                        !busy
                    ) { maximumInterval = it.filter(Char::isDigit) }
                    Text(
                        "Use 5–3600 seconds. The default must be inside the range.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = ::saveGlobalPolicy, enabled = !busy) {
                        Text("Save global policy")
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        enabled = !busy,
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
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Server settings", style = MaterialTheme.typography.titleMedium)
                    Text("Currency: ${uiState.currencyCode}")
                    Text(
                        "Only a system administrator can change the global policy.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showResetDatabaseDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
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
                        enabled = !busy,
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
                    enabled = !busy
                ) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                Button(
                    onClick = ::resetDatabase,
                    enabled = !busy && administratorPassword.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(if (busy) "Emptying..." else "Empty database")
                }
            }
        )
    }
}

@Composable
private fun SwitchRow(
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
private fun IntervalField(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun currencyName(code: String): String = runCatching {
    Currency.getInstance(code).getDisplayName(Locale.getDefault())
}.getOrDefault(code)
