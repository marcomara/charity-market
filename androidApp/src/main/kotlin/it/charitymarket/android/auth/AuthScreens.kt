package it.charitymarket.android.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.charitymarket.android.app.AndroidServerPreferences
import it.charitymarket.android.app.AndroidAppState
import it.charitymarket.android.components.FeedbackMessage
import it.charitymarket.android.components.PasswordField
import it.charitymarket.shared.i18n.LocalAppStrings
import it.charitymarket.shared.preferences.AppPreferences
import it.charitymarket.shared.ui.AppSettingsPanel
import kotlinx.coroutines.launch

@Composable
fun ServerSelectionScreen(
    appState: AndroidAppState,
    appPreferences: AppPreferences,
    onAppPreferencesChange: (AppPreferences) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var serverUrl by remember {
        mutableStateOf(
            AndroidServerPreferences.lastServerBaseUrl(context)
        )
    }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showClientSettings by remember { mutableStateOf(false) }

    fun connect() {
        if (submitting) return
        scope.launch {
            submitting = true
            error = null
            runCatching { appState.connect(serverUrl) }
                .onSuccess {
                    appState.uiState.baseUrl?.let { baseUrl ->
                        AndroidServerPreferences.rememberServerBaseUrl(
                            context,
                            baseUrl
                        )
                    }
                }
                .onFailure { error = appState.handleRequestFailure(it) }
            submitting = false
        }
    }

    CenteredAuthCard {
        Text(
            strings.text("app.name"),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            strings.text("server_selection.android.subtitle"),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = {
                Text(strings.text("server_selection.address.label"))
            },
            supportingText = {
                Text(
                    strings.text("server_selection.address.help")
                )
            },
            singleLine = true,
            enabled = !submitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(onGo = { connect() }),
            modifier = Modifier.fillMaxWidth()
        )
        FeedbackMessage(error, true)
        Button(
            onClick = ::connect,
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (submitting) {
                CircularProgressIndicator()
                Spacer(Modifier.height(4.dp))
            }
            Text(
                if (submitting) {
                    strings.text("action.connecting")
                } else {
                    strings.text("action.connect")
                }
            )
        }
        OutlinedButton(
            onClick = { showClientSettings = !showClientSettings },
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                strings.text(
                    if (showClientSettings) {
                        "client_settings.hide"
                    } else {
                        "client_settings.show"
                    }
                )
            )
        }
        if (showClientSettings) {
            AppSettingsPanel(
                preferences = appPreferences,
                onPreferencesChange = onAppPreferencesChange,
                framed = false
            )
        }
    }
}

@Composable
fun LoginScreen(
    appState: AndroidAppState,
    appPreferences: AppPreferences,
    onAppPreferencesChange: (AppPreferences) -> Unit
) {
    val scope = rememberCoroutineScope()
    val uiState = appState.uiState
    val strings = LocalAppStrings.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showClientSettings by remember { mutableStateOf(false) }

    fun login() {
        if (submitting) return
        if (username.trim().isBlank() || password.isBlank()) {
            error = strings.text("login.enter_credentials")
            return
        }
        scope.launch {
            submitting = true
            error = null
            runCatching { appState.login(username.trim(), password) }
                .onFailure { error = appState.handleRequestFailure(it) }
            submitting = false
        }
    }

    CenteredAuthCard {
        Text(
            strings.text("login.title"),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            uiState.baseUrl.orEmpty(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FeedbackMessage(uiState.authMessage, false)
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(strings.text("login.username")) },
            singleLine = true,
            enabled = !submitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        PasswordField(
            value = password,
            onValueChange = { password = it },
            label = strings.text("login.password"),
            visible = passwordVisible,
            onToggleVisible = { passwordVisible = !passwordVisible },
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth()
        )
        FeedbackMessage(error, true)
        Button(
            onClick = ::login,
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (submitting) {
                    strings.text("login.signing_in")
                } else {
                    strings.text("action.sign_in")
                }
            )
        }
        OutlinedButton(
            onClick = appState::disconnect,
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(strings.text("login.choose_another_server"))
        }
        OutlinedButton(
            onClick = { showClientSettings = !showClientSettings },
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                strings.text(
                    if (showClientSettings) {
                        "client_settings.hide"
                    } else {
                        "client_settings.show"
                    }
                )
            )
        }
        if (showClientSettings) {
            AppSettingsPanel(
                preferences = appPreferences,
                onPreferencesChange = onAppPreferencesChange,
                framed = false
            )
        }
    }
}

@Composable
fun PasswordChangeScreen(appState: AndroidAppState) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (submitting) return
        error = when {
            current.isBlank() -> "Enter the current password."
            newPassword.length < 8 ->
                "The new password must contain at least 8 characters."
            newPassword != confirmation ->
                "The password confirmation does not match."
            else -> null
        }
        if (error != null) return

        scope.launch {
            submitting = true
            runCatching {
                appState.changeInitialPassword(
                    current,
                    newPassword,
                    confirmation
                )
            }.onFailure { error = appState.handleRequestFailure(it) }
            submitting = false
        }
    }

    CenteredAuthCard {
        Text("Change password", style = MaterialTheme.typography.headlineMedium)
        Text("Set a new password before continuing.")
        PasswordField(
            current, { current = it }, "Current password",
            visible, { visible = !visible }, !submitting,
            Modifier.fillMaxWidth()
        )
        PasswordField(
            newPassword, { newPassword = it }, "New password",
            visible, { visible = !visible }, !submitting,
            Modifier.fillMaxWidth(), "At least 8 characters.",
            newPassword.isNotEmpty() && newPassword.length < 8
        )
        PasswordField(
            confirmation, { confirmation = it }, "Confirm new password",
            visible, { visible = !visible }, !submitting,
            Modifier.fillMaxWidth(),
            if (confirmation.isNotEmpty() && confirmation != newPassword) {
                "The confirmation does not match."
            } else null,
            confirmation.isNotEmpty() && confirmation != newPassword
        )
        FeedbackMessage(error, true)
        Button(
            onClick = ::submit,
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (submitting) "Changing password..." else "Change password")
        }
    }
}

@Composable
private fun CenteredAuthCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}
