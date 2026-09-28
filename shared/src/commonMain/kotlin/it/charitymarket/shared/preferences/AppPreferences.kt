package it.charitymarket.shared.preferences

const val DEFAULT_LANGUAGE_TAG: String = "en_us"
const val DEFAULT_PALETTE_KEY: String = "charity"

data class AppPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val paletteKey: String = DEFAULT_PALETTE_KEY,
    val languageTag: String = DEFAULT_LANGUAGE_TAG
)

enum class AppThemeMode(val storageKey: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorageKey(value: String?): AppThemeMode =
            entries.firstOrNull { it.storageKey == value } ?: SYSTEM
    }
}

fun normalizeLanguageTag(value: String?): String =
    value
        ?.trim()
        ?.lowercase()
        ?.replace('-', '_')
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_LANGUAGE_TAG

fun normalizePaletteKey(value: String?): String =
    value
        ?.trim()
        ?.lowercase()
        ?.replace('-', '_')
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_PALETTE_KEY
