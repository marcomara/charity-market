package it.charitymarket.desktop.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import it.charitymarket.shared.config.DEFAULT_SERVER_PORT
import it.charitymarket.shared.i18n.LocalAppStrings
import it.charitymarket.shared.preferences.AppPreferences
import it.charitymarket.shared.ui.AppSettingsPanel
import kotlinx.coroutines.launch
import java.nio.file.Path

@Composable
fun StartupScreen(
    controller: LocalSqliteServerController,
    appPreferences: AppPreferences,
    onAppPreferencesChange: (AppPreferences) -> Unit,
    onConnected: (
        ServerConnection,
        StartupLoginCredentials?
    ) -> Unit
) {
    val strings = LocalAppStrings.current
    var mode by remember {
        mutableStateOf(
            StartupMode.CONNECT_TO_SERVER
        )
    }

    var serverUrl by remember {
        mutableStateOf(
            ServerConnectionPreferences.lastServerBaseUrl()
        )
    }

    var dataDirectory by remember {
        mutableStateOf(
            Path.of(
                System.getProperty("user.home"),
                ".charity-market",
                "data"
            ).toString()
        )
    }

    var localPort by remember {
        mutableStateOf(DEFAULT_SERVER_PORT.toString())
    }

    var administratorUsername by remember {
        mutableStateOf("admin")
    }

    var administratorPassword by remember {
        mutableStateOf("")
    }

    var busy by remember {
        mutableStateOf(false)
    }

    var statusMessage by remember {
        mutableStateOf<String?>(null)
    }

    var isError by remember {
        mutableStateOf(false)
    }
    var showClientSettings by remember {
        mutableStateOf(false)
    }

    val coroutineScope = rememberCoroutineScope()

    fun connect() {
        coroutineScope.launch {
            busy = true
            statusMessage = null
            isError = false
            val selectedMode = mode

            val result = runCatching {
                when (selectedMode) {
                    StartupMode.CONNECT_TO_SERVER -> {
                        controller.connectToServer(
                            serverUrl
                        )
                    }

                    StartupMode.HOST_LOCAL_SQLITE -> {
                        controller.startLocalServer(
                            LocalSqliteServerConfig(
                                dataDirectory =
                                    dataDirectory.trim(),

                                port =
                                    localPort.trim()
                                        .takeIf { it.isNotEmpty() }
                                        ?.toIntOrNull()
                                        ?: if (localPort.isBlank()) {
                                            DEFAULT_SERVER_PORT
                                        } else {
                                            throw IllegalArgumentException(
                                                "The server port is invalid."
                                            )
                                        },

                                administratorUsername =
                                    administratorUsername.trim(),

                                administratorPassword =
                                    administratorPassword
                            )
                        )
                    }
                }
            }

            result.onSuccess { connection ->
                if (selectedMode == StartupMode.CONNECT_TO_SERVER) {
                    ServerConnectionPreferences.rememberServerBaseUrl(
                        connection.baseUrl
                    )
                }

                statusMessage = if (
                    connection.isLocalServer
                ) {
                    strings.text("server_selection.status.local_started")
                } else {
                    strings.text("server_selection.status.connected")
                }

                onConnected(
                    connection,
                    if (selectedMode == StartupMode.HOST_LOCAL_SQLITE) {
                        StartupLoginCredentials(
                            username =
                                administratorUsername.trim(),
                            password =
                                administratorPassword
                        )
                    } else {
                        null
                    }
                )
            }

            result.onFailure { exception ->
                isError = true
                statusMessage =
                    exception.message
                        ?: "The operation failed."
            }

            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(32.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = strings.text("app.name"),
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text = strings.text("server_selection.desktop.subtitle"),
            style = MaterialTheme.typography.bodyLarge
        )

        OutlinedButton(
            onClick = {
                showClientSettings = !showClientSettings
            },
            enabled = !busy
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
                onPreferencesChange = onAppPreferencesChange
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected =
                    mode ==
                            StartupMode.CONNECT_TO_SERVER,

                onClick = {
                    if (!busy) {
                        mode =
                            StartupMode.CONNECT_TO_SERVER
                    }
                },

                enabled = !busy
            )

            Column {
                Text(
                    text = strings.text("server_selection.mode.connect.title"),
                    style =
                        MaterialTheme.typography.titleMedium
                )

                Text(
                    text = strings.text("server_selection.mode.connect.subtitle")
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected =
                    mode ==
                            StartupMode.HOST_LOCAL_SQLITE,

                onClick = {
                    if (!busy) {
                        mode =
                            StartupMode.HOST_LOCAL_SQLITE
                    }
                },

                enabled = !busy
            )

            Column {
                Text(
                    text = strings.text("server_selection.mode.local.title"),
                    style =
                        MaterialTheme.typography.titleMedium
                )

                Text(
                    text = strings.text("server_selection.mode.local.subtitle")
                )
            }
        }

        HorizontalDivider()

        when (mode) {
            StartupMode.CONNECT_TO_SERVER -> {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = {
                        serverUrl = it
                    },
                    label = {
                        Text(strings.text("server_selection.address.label"))
                    },
                    supportingText = {
                        Text(
                            strings.text("server_selection.address.help")
                        )
                    },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            StartupMode.HOST_LOCAL_SQLITE -> {
                Text(
                    text = strings.text("server_selection.local_settings"),
                    style =
                        MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = dataDirectory,
                    onValueChange = {
                        dataDirectory = it
                    },
                    label = {
                        Text(strings.text("server_selection.data_directory.label"))
                    },
                    supportingText = {
                        Text(
                            strings.text("server_selection.data_directory.help")
                        )
                    },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = localPort,
                    onValueChange = {
                        localPort = it
                    },
                    label = {
                        Text(strings.text("server_selection.local_port.label"))
                    },
                    supportingText = {
                        Text(
                            strings.text(
                                "server_selection.local_port.help",
                                DEFAULT_SERVER_PORT
                            )
                        )
                    },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = strings.text("server_selection.initial_admin.title"),
                    style =
                        MaterialTheme.typography.titleMedium
                )

                Text(
                    text = strings.text("server_selection.initial_admin.help")
                )

                OutlinedTextField(
                    value = administratorUsername,
                    onValueChange = {
                        administratorUsername = it
                    },
                    label = {
                        Text(strings.text("server_selection.admin_username"))
                    },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = administratorPassword,
                    onValueChange = {
                        administratorPassword = it
                    },
                    label = {
                        Text(strings.text("server_selection.admin_password"))
                    },
                    supportingText = {
                        Text(strings.text("server_selection.admin_password.help"))
                    },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation =
                        PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        statusMessage?.let { message ->
            Text(
                text = message,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }

        Button(
            onClick = ::connect,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp
                )

                Spacer(Modifier.padding(4.dp))
            }

            Text(
                when (mode) {
                    StartupMode.CONNECT_TO_SERVER ->
                        strings.text("action.connect")

                    StartupMode.HOST_LOCAL_SQLITE ->
                        strings.text("action.start_local_server")
                }
            )
        }
    }
}
