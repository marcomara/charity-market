package it.charitymarket.shared.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import it.charitymarket.shared.preferences.AppPreferences
import it.charitymarket.shared.preferences.AppThemeMode

@Composable
fun CharityMarketTheme(
    preferences: AppPreferences,
    content: @Composable () -> Unit
) {
    val darkTheme = when (preferences.themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = AppPalettes.byKey(
            preferences.paletteKey
        ).colorScheme(darkTheme),
        content = content
    )
}

private fun AppPaletteDefinition.colorScheme(darkTheme: Boolean): ColorScheme {
    val primaryColor = primary
    val secondaryColor = secondary
    val tertiaryColor = tertiary
    return if (darkTheme) {
        darkColorScheme(
            primary = primaryColor.lightened(),
            secondary = secondaryColor.lightened(),
            tertiary = tertiaryColor.lightened(),
            primaryContainer = primaryColor.darkContainer(),
            secondaryContainer = secondaryColor.darkContainer(),
            tertiaryContainer = tertiaryColor.darkContainer(),
            background = Color(0xFF111827),
            surface = Color(0xFF18212F),
            surfaceVariant = Color(0xFF243244),
            onBackground = Color(0xFFF9FAFB),
            onSurface = Color(0xFFF9FAFB),
            onSurfaceVariant = Color(0xFFD1D5DB),
            onPrimary = Color(0xFF0B1220),
            onSecondary = Color(0xFF0B1220),
            onTertiary = Color(0xFF0B1220),
            onPrimaryContainer = Color(0xFFF9FAFB),
            onSecondaryContainer = Color(0xFFF9FAFB),
            onTertiaryContainer = Color(0xFFF9FAFB),
            error = Color(0xFFFCA5A5)
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            secondary = secondaryColor,
            tertiary = tertiaryColor,
            primaryContainer = primaryColor.lightContainer(),
            secondaryContainer = secondaryColor.lightContainer(),
            tertiaryContainer = tertiaryColor.lightContainer(),
            background = Color(0xFFFAFAF9),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE7ECEB),
            onBackground = Color(0xFF1F2937),
            onSurface = Color(0xFF1F2937),
            onSurfaceVariant = Color(0xFF4B5563),
            onPrimary = Color(0xFFFFFFFF),
            onSecondary = Color(0xFFFFFFFF),
            onTertiary = Color(0xFFFFFFFF),
            onPrimaryContainer = primaryColor.darkText(),
            onSecondaryContainer = secondaryColor.darkText(),
            onTertiaryContainer = tertiaryColor.darkText(),
            error = Color(0xFFB91C1C)
        )
    }
}

private fun Color.lightened(): Color =
    Color(
        red = red + (1f - red) * 0.36f,
        green = green + (1f - green) * 0.36f,
        blue = blue + (1f - blue) * 0.36f,
        alpha = alpha
    )

private fun Color.lightContainer(): Color =
    mixedWith(Color.White, 0.84f)

private fun Color.darkContainer(): Color =
    mixedWith(Color.Black, 0.52f)

private fun Color.darkText(): Color =
    mixedWith(Color.Black, 0.58f)

private fun Color.mixedWith(
    other: Color,
    amount: Float
): Color =
    Color(
        red = red + (other.red - red) * amount,
        green = green + (other.green - green) * amount,
        blue = blue + (other.blue - blue) * amount,
        alpha = alpha + (other.alpha - alpha) * amount
    )
