package it.charitymarket.desktop.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import it.charitymarket.desktop.app.DesktopAppState
import it.charitymarket.desktop.components.FeedbackMessage
import it.charitymarket.desktop.components.ScreenHeader
import it.charitymarket.desktop.model.supportedCurrencyCodes
import kotlinx.coroutines.launch
import java.util.Currency
import java.util.Locale

@Composable
fun SettingsScreen(
    appState: DesktopAppState
) {
    val selectedCurrency = appState.uiState.currencyCode
    val coroutineScope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    fun changeCurrency(currencyCode: String) {
        if (submitting || currencyCode == selectedCurrency) {
            return
        }

        coroutineScope.launch {
            submitting = true
            errorMessage = null

            runCatching {
                appState.updateCurrency(currencyCode)
            }.onFailure { exception ->
                errorMessage =
                    appState.handleRequestFailure(exception)
            }

            submitting = false
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(
            title = "Settings",
            subtitle = "Global server settings shared by every user.",
            action = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            submitting = true
                            errorMessage = null
                            runCatching {
                                appState.refreshSettings()
                            }.onFailure { exception ->
                                errorMessage =
                                    appState.handleRequestFailure(
                                        exception
                                    )
                            }
                            submitting = false
                        }
                    },
                    enabled = !submitting
                ) {
                    Text("Refresh")
                }
            }
        )

        FeedbackMessage(
            message = errorMessage,
            isError = true
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Currency",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "The selected currency is stored on the server and used by every connected desktop client. Monetary values remain stored as integer cents.",
                    color = MaterialTheme.colorScheme
                        .onSurfaceVariant
                )

                supportedCurrencyCodes().forEach { currencyCode ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected =
                                selectedCurrency == currencyCode,
                            onClick = {
                                changeCurrency(currencyCode)
                            },
                            enabled = !submitting
                        )

                        Text(
                            text = "$currencyCode - ${currencyDisplayName(currencyCode)}"
                        )
                    }
                }
            }
        }
    }
}

private fun currencyDisplayName(
    currencyCode: String
): String =
    runCatching {
        Currency
            .getInstance(currencyCode)
            .getDisplayName(Locale.getDefault())
    }.getOrDefault(currencyCode)
