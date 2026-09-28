package it.charitymarket.android.app

import android.content.Context
import it.charitymarket.shared.preferences.AppPreferences
import it.charitymarket.shared.preferences.AppThemeMode
import it.charitymarket.shared.preferences.normalizeLanguageTag
import it.charitymarket.shared.preferences.normalizePaletteKey

object AndroidAppPreferences {
    private const val PREFERENCES_NAME = "charity-market"
    private const val THEME_MODE = "app-theme-mode"
    private const val PALETTE = "app-palette"
    private const val LANGUAGE_TAG = "app-language-tag"

    fun load(context: Context): AppPreferences {
        val preferences = context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

        return AppPreferences(
            themeMode = AppThemeMode.fromStorageKey(
                preferences.getString(THEME_MODE, null)
            ),
            paletteKey = normalizePaletteKey(
                preferences.getString(PALETTE, null)
            ),
            languageTag = normalizeLanguageTag(
                preferences.getString(LANGUAGE_TAG, null)
            )
        )
    }

    fun save(
        context: Context,
        appPreferences: AppPreferences
    ) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(
                THEME_MODE,
                appPreferences.themeMode.storageKey
            )
            .putString(
                PALETTE,
                normalizePaletteKey(appPreferences.paletteKey)
            )
            .putString(
                LANGUAGE_TAG,
                normalizeLanguageTag(appPreferences.languageTag)
            )
            .apply()
    }
}
