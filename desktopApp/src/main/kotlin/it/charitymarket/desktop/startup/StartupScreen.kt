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
import kotlinx.coroutines.launch
import java.nio.file.Path

@Composable
fun StartupScreen(
    controller: LocalSqliteServerController,
    onConnected: (
        ServerConnection,
        StartupLoginCredentials?
    ) -> Unit
) {
    var mode by remember {
        mutableStateOf(
            StartupMode.CONNECT_TO_SERVER
        )
    }

    var serverUrl by remember {
        mutableStateOf("http://localhost:8080")
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
        mutableStateOf("8080")
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

    val coroutineScope = rememberCoroutineScope()

    fun connect() {
        coroutineScope.launch {
            busy = true
            statusMessage = null
            isError = false

            val result = runCatching {
                when (mode) {
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
                                    localPort.toIntOrNull()
                                        ?: throw IllegalArgumentException(
                                            "The server port is invalid."
                                        ),

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
                statusMessage = if (
                    connection.isLocalServer
                ) {
                    "Local SQLite server started. Signing in..."
                } else {
                    "Server connection successful."
                }

                onConnected(
                    connection,
                    if (mode == StartupMode.HOST_LOCAL_SQLITE) {
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
            text = "Charity Market",
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text = "Choose how this desktop application should connect.",
            style = MaterialTheme.typography.bodyLarge
        )

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
                    text = "Connect to a server",
                    style =
                        MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Use an existing standalone or hosted server."
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
                    text = "Host locally with SQLite",
                    style =
                        MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Start a private server on this computer without Docker."
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
                        Text("Server address")
                    },
                    supportingText = {
                        Text(
                            "Example: https://market.example.org"
                        )
                    },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            StartupMode.HOST_LOCAL_SQLITE -> {
                Text(
                    text = "Local server settings",
                    style =
                        MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = dataDirectory,
                    onValueChange = {
                        dataDirectory = it
                    },
                    label = {
                        Text("Data directory")
                    },
                    supportingText = {
                        Text(
                            "The SQLite database and local server log are stored here."
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
                        Text("Local server port")
                    },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Initial administrator",
                    style =
                        MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "These credentials create the local administrator when the database is new."
                )

                OutlinedTextField(
                    value = administratorUsername,
                    onValueChange = {
                        administratorUsername = it
                    },
                    label = {
                        Text("Administrator username")
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
                        Text("Administrator password")
                    },
                    supportingText = {
                        Text("Permanent password, at least 8 characters.")
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
                        "Connect"

                    StartupMode.HOST_LOCAL_SQLITE ->
                        "Start local server"
                }
            )
        }
    }
}
