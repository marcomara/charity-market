package it.charitymarket.desktop.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.dp
import it.charitymarket.desktop.app.DesktopAppState
import it.charitymarket.desktop.components.FeedbackMessage
import it.charitymarket.desktop.components.PasswordField
import it.charitymarket.desktop.startup.LocalHostMode
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    appState: DesktopAppState
) {
    val uiState = appState.uiState
    val connection = uiState.connection
    val localMode = connection?.mode as? LocalHostMode
    val coroutineScope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    fun submit() {
        if (submitting) {
            return
        }

        val normalizedUsername = username.trim()

        if (normalizedUsername.isBlank() ||
            password.isBlank()
        ) {
            errorMessage =
                "Enter both username and password."
            return
        }

        coroutineScope.launch {
            submitting = true
            errorMessage = null

            runCatching {
                appState.login(
                    username = normalizedUsername,
                    password = password
                )
            }.onFailure { exception ->
                errorMessage =
                    appState.handleRequestFailure(
                        exception
                    )
            }

            submitting = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.55f)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp &&
                            event.key == Key.Enter
                        ) {
                            submit()
                            true
                        } else {
                            false
                        }
                    },
                verticalArrangement =
                    Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (localMode == null) {
                        "Sign in to Charity Market"
                    } else {
                        "Sign in to the local Charity Market server"
                    },
                    style =
                        MaterialTheme.typography.headlineSmall
                )

                Text(
                    text = connection?.baseUrl.orEmpty(),
                    style =
                        MaterialTheme.typography.bodyMedium,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )

                if (localMode != null) {
                    Text(
                        text = "Local server running. Data directory: ${localMode.dataDirectory}",
                        style =
                            MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme.primary
                    )
                }

                FeedbackMessage(
                    message = uiState.authMessage,
                    isError = false
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                    },
                    label = {
                        Text("Username")
                    },
                    singleLine = true,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth()
                )

                PasswordField(
                    value = password,
                    onValueChange = {
                        password = it
                    },
                    label = "Password",
                    visible = passwordVisible,
                    onToggleVisible = {
                        passwordVisible = !passwordVisible
                    },
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth()
                )

                FeedbackMessage(
                    message = errorMessage,
                    isError = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    if (localMode == null) {
                        OutlinedButton(
                            onClick = {
                                appState
                                    .returnToServerSelection()
                            },
                            enabled = !submitting
                        ) {
                            Text("Server selection")
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Button(
                        onClick = ::submit,
                        enabled = !submitting
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                modifier =
                                    Modifier
                                        .height(18.dp)
                                        .width(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }

                        Text("Sign in")
                    }
                }
            }
        }
    }
}

@Composable
fun PasswordChangeScreen(
    appState: DesktopAppState
) {
    val coroutineScope = rememberCoroutineScope()
    val user = appState.uiState.authenticatedUser

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPasswords by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    val passwordTooShort =
        newPassword.isNotEmpty() &&
                newPassword.length < 8
    val mismatch =
        confirmation.isNotEmpty() &&
                newPassword != confirmation

    fun submit() {
        if (submitting) {
            return
        }

        if (currentPassword.isBlank()) {
            errorMessage =
                "Enter the current password."
            return
        }

        if (newPassword.length < 8) {
            errorMessage =
                "The new password must contain at least 8 characters."
            return
        }

        if (newPassword != confirmation) {
            errorMessage =
                "The password confirmation does not match."
            return
        }

        coroutineScope.launch {
            submitting = true
            errorMessage = null

            runCatching {
                appState.changeInitialPassword(
                    currentPassword = currentPassword,
                    newPassword = newPassword,
                    confirmation = confirmation
                )
            }.onFailure { exception ->
                errorMessage =
                    appState.handleRequestFailure(
                        exception
                    )
            }

            submitting = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.55f)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp &&
                            event.key == Key.Enter
                        ) {
                            submit()
                            true
                        } else {
                            false
                        }
                    },
                verticalArrangement =
                    Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Change password",
                    style =
                        MaterialTheme.typography.headlineSmall
                )

                Text(
                    text = "The server requires ${user?.displayName ?: "this user"} to set a new password before continuing.",
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )

                PasswordField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                    },
                    label = "Current password",
                    visible = showPasswords,
                    onToggleVisible = {
                        showPasswords = !showPasswords
                    },
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth()
                )

                PasswordField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                    },
                    label = "New password",
                    visible = showPasswords,
                    onToggleVisible = {
                        showPasswords = !showPasswords
                    },
                    enabled = !submitting,
                    supportingText =
                        "At least 8 characters.",
                    isError = passwordTooShort,
                    modifier = Modifier.fillMaxWidth()
                )

                PasswordField(
                    value = confirmation,
                    onValueChange = {
                        confirmation = it
                    },
                    label = "Confirm new password",
                    visible = showPasswords,
                    onToggleVisible = {
                        showPasswords = !showPasswords
                    },
                    enabled = !submitting,
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        "The confirmation does not match."
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                FeedbackMessage(
                    message = errorMessage,
                    isError = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.End
                ) {
                    Button(
                        onClick = ::submit,
                        enabled = !submitting
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                modifier =
                                    Modifier
                                        .height(18.dp)
                                        .width(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }

                        Text("Change password")
                    }
                }
            }
        }
    }
}
