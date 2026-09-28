package it.charitymarket.shared.ui

import androidx.compose.ui.graphics.Color
import it.charitymarket.shared.preferences.DEFAULT_PALETTE_KEY
import it.charitymarket.shared.preferences.normalizePaletteKey

data class AppPaletteDefinition(
    val key: String,
    val labelKey: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
) {
    val swatches: List<Color>
        get() = listOf(primary, secondary, tertiary)
}

object AppPalettes {
    /*
     * Add new palettes here, then add matching `palette.<key>`
     * labels in every file under composeResources/files/i18n.
     */
    val all: List<AppPaletteDefinition> = listOf(
        AppPaletteDefinition(
            key = "charity",
            labelKey = "palette.charity",
            primary = Color(0xFF0F766E),
            secondary = Color(0xFFB45309),
            tertiary = Color(0xFFBE123C)
        ),
        AppPaletteDefinition(
            key = "ocean",
            labelKey = "palette.ocean",
            primary = Color(0xFF0369A1),
            secondary = Color(0xFF0F766E),
            tertiary = Color(0xFFDC2626)
        ),
        AppPaletteDefinition(
            key = "forest",
            labelKey = "palette.forest",
            primary = Color(0xFF166534),
            secondary = Color(0xFF92400E),
            tertiary = Color(0xFF7C3AED)
        ),
        AppPaletteDefinition(
            key = "rose",
            labelKey = "palette.rose",
            primary = Color(0xFFBE123C),
            secondary = Color(0xFF0F766E),
            tertiary = Color(0xFF4F46E5)
        )
    )

    fun byKey(key: String?): AppPaletteDefinition {
        val normalizedKey = normalizePaletteKey(key)
        return all.firstOrNull { it.key == normalizedKey }
            ?: all.first { it.key == DEFAULT_PALETTE_KEY }
    }
}
