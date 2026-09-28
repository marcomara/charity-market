package it.charitymarket.app

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import it.charitymarket.desktop.app.CharityMarketDesktopApplication
import it.charitymarket.desktop.app.DesktopAppPreferences
import it.charitymarket.desktop.app.DesktopAppState
import it.charitymarket.desktop.startup.LocalSqliteServerController
import it.charitymarket.shared.preferences.AppPreferences

fun main() {
    val localServerController =
        LocalSqliteServerController()

    application {
        val windowState = rememberWindowState(
            size = DpSize(
                width = 1280.dp,
                height = 820.dp
            )
        )
        val coroutineScope = rememberCoroutineScope()
        val appState = remember {
            DesktopAppState(
                localServerController =
                    localServerController,
                scope = coroutineScope
            )
        }
        var appPreferences by remember {
            mutableStateOf(DesktopAppPreferences.load())
        }

        fun exitApplicationSafely() {
            exitApplication()
        }

        fun updateAppPreferences(preferences: AppPreferences) {
            appPreferences = preferences
            DesktopAppPreferences.save(preferences)
        }

        Window(
            onCloseRequest = {
                appState.requestWindowClose(
                    exitApplication =
                        ::exitApplicationSafely
                )
            },
            title = "Charity Market",
            state = windowState
        ) {
            CharityMarketDesktopApplication(
                localServerController =
                    localServerController,
                appState = appState,
                appPreferences = appPreferences,
                onAppPreferencesChange = ::updateAppPreferences,
                exitApplication =
                    ::exitApplicationSafely
            )
        }
    }
}
