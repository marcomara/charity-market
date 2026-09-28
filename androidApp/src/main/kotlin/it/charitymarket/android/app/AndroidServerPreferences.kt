package it.charitymarket.android.app

import android.content.Context

object AndroidServerPreferences {
    private const val PREFERENCES_NAME = "charity-market"
    private const val LAST_SERVER_BASE_URL = "last-server-base-url"

    fun lastServerBaseUrl(context: Context): String =
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(LAST_SERVER_BASE_URL, "")
            .orEmpty()

    fun rememberServerBaseUrl(
        context: Context,
        baseUrl: String
    ) {
        if (baseUrl.isBlank()) return

        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LAST_SERVER_BASE_URL, baseUrl)
            .apply()
    }
}
