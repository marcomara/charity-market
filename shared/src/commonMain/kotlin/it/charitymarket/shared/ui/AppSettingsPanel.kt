package it.charitymarket.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.charitymarket.shared.i18n.LocalAppStrings
import it.charitymarket.shared.i18n.rememberAvailableLanguages
import it.charitymarket.shared.preferences.AppPreferences
import it.charitymarket.shared.preferences.AppThemeMode

@Composable
fun AppSettingsPanel(
    preferences: AppPreferences,
    onPreferencesChange: (AppPreferences) -> Unit,
    modifier: Modifier = Modifier,
    framed: Boolean = true
) {
    val strings = LocalAppStrings.current
    val languages by rememberAvailableLanguages(preferences.languageTag)

    if (framed) {
        Card(modifier = modifier.fillMaxWidth()) {
            AppSettingsPanelContent(
                preferences = preferences,
                languages = languages,
                onPreferencesChange = onPreferencesChange
            )
        }
    } else {
        AppSettingsPanelContent(
            preferences = preferences,
            languages = languages,
            onPreferencesChange = onPreferencesChange,
            modifier = modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AppSettingsPanelContent(
    preferences: AppPreferences,
    languages: List<it.charitymarket.shared.i18n.AppLanguage>,
    onPreferencesChange: (AppPreferences) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
        Column(
            modifier = modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    strings.text("app_settings.title"),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    strings.text("app_settings.subtitle"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsSection(strings.text("app_settings.language")) {
                languages.forEach { language ->
                    RadioOptionRow(
                        label = language.displayName,
                        selected = preferences.languageTag == language.tag,
                        onClick = {
                            onPreferencesChange(
                                preferences.copy(languageTag = language.tag)
                            )
                        }
                    )
                }
            }

            SettingsSection(strings.text("app_settings.theme")) {
                AppThemeMode.entries.forEach { themeMode ->
                    RadioOptionRow(
                        label = strings.text(themeMode.labelKey()),
                        selected = preferences.themeMode == themeMode,
                        onClick = {
                            onPreferencesChange(
                                preferences.copy(themeMode = themeMode)
                            )
                        }
                    )
                }
            }

            SettingsSection(strings.text("app_settings.palette")) {
                AppPalettes.all.forEach { palette ->
                    RadioOptionRow(
                        label = strings.text(palette.labelKey),
                        selected = preferences.paletteKey == palette.key,
                        onClick = {
                            onPreferencesChange(
                                preferences.copy(paletteKey = palette.key)
                            )
                        },
                        trailing = {
                            PaletteSwatch(palette)
                        }
                    )
                }
            }
        }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge
        )
        content()
    }
}

@Composable
private fun RadioOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Text(
            label,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
private fun PaletteSwatch(palette: AppPaletteDefinition) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        palette.swatches.forEach { color ->
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(
                        color = color,
                        shape = CircleShape
                    )
            )
        }
    }
}

private fun AppThemeMode.labelKey(): String = when (this) {
    AppThemeMode.SYSTEM -> "theme.system"
    AppThemeMode.LIGHT -> "theme.light"
    AppThemeMode.DARK -> "theme.dark"
}
