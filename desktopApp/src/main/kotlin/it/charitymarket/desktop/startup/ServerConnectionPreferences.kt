package it.charitymarket.desktop.startup

import java.util.prefs.Preferences

object ServerConnectionPreferences {
    private const val NODE_NAME = "it.charitymarket.desktop"
    private const val LAST_SERVER_BASE_URL = "last-server-base-url"

    fun lastServerBaseUrl(): String =
        runCatching {
            preferences().get(LAST_SERVER_BASE_URL, "")
        }.getOrDefault("")

    fun rememberServerBaseUrl(baseUrl: String) {
        if (baseUrl.isBlank()) return

        runCatching {
            preferences().put(LAST_SERVER_BASE_URL, baseUrl)
        }
    }

    private fun preferences(): Preferences =
        Preferences.userRoot().node(NODE_NAME)
}
