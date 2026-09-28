package it.charitymarket.desktop.app

import it.charitymarket.shared.preferences.AppPreferences
import it.charitymarket.shared.preferences.AppThemeMode
import it.charitymarket.shared.preferences.normalizeLanguageTag
import it.charitymarket.shared.preferences.normalizePaletteKey
import java.util.prefs.Preferences

object DesktopAppPreferences {
    private const val NODE_NAME = "it.charitymarket.desktop"
    private const val THEME_MODE = "app-theme-mode"
    private const val PALETTE = "app-palette"
    private const val LANGUAGE_TAG = "app-language-tag"

    fun load(): AppPreferences =
        runCatching {
            val preferences = preferences()
            AppPreferences(
                themeMode = AppThemeMode.fromStorageKey(
                    preferences.get(THEME_MODE, null)
                ),
                paletteKey = normalizePaletteKey(
                    preferences.get(PALETTE, null)
                ),
                languageTag = normalizeLanguageTag(
                    preferences.get(LANGUAGE_TAG, null)
                )
            )
        }.getOrDefault(AppPreferences())

    fun save(appPreferences: AppPreferences) {
        runCatching {
            preferences().apply {
                put(THEME_MODE, appPreferences.themeMode.storageKey)
                put(PALETTE, normalizePaletteKey(appPreferences.paletteKey))
                put(
                    LANGUAGE_TAG,
                    normalizeLanguageTag(appPreferences.languageTag)
                )
            }
        }
    }

    private fun preferences(): Preferences =
        Preferences.userRoot().node(NODE_NAME)
}
