package it.charitymarket.shared.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import charity_market.shared.generated.resources.Res
import it.charitymarket.shared.preferences.DEFAULT_LANGUAGE_TAG
import it.charitymarket.shared.preferences.normalizeLanguageTag

data class AppLanguage(
    val tag: String,
    val displayName: String
)

data class AppStrings(
    val languageTag: String,
    private val values: Map<String, String>,
    private val fallbackValues: Map<String, String> = englishFallbackStrings
) {
    fun text(key: String, vararg args: Any?): String {
        val template = values[key] ?: fallbackValues[key] ?: key
        return args.withIndex().fold(template) { text, argument ->
            text.replace("{${argument.index}}", argument.value.toString())
        }
    }
}

val LocalAppStrings = staticCompositionLocalOf {
    AppStrings(
        languageTag = DEFAULT_LANGUAGE_TAG,
        values = englishFallbackStrings
    )
}

@Composable
fun AppLocalization(
    languageTag: String,
    content: @Composable () -> Unit
) {
    val strings = rememberAppStrings(languageTag)

    CompositionLocalProvider(
        LocalAppStrings provides strings.value,
        content = content
    )
}

@Composable
fun rememberAvailableLanguages(
    currentLanguageTag: String
): State<List<AppLanguage>> =
    produceState(
        initialValue = defaultLanguageOptions(currentLanguageTag),
        key1 = currentLanguageTag
    ) {
        value = loadAvailableLanguages()
            .withCurrentLanguage(currentLanguageTag)
    }

@Composable
private fun rememberAppStrings(
    languageTag: String
): State<AppStrings> =
    produceState(
        initialValue = AppStrings(
            languageTag = DEFAULT_LANGUAGE_TAG,
            values = englishFallbackStrings
        ),
        key1 = languageTag
    ) {
        value = loadAppStrings(languageTag)
    }

suspend fun loadAvailableLanguages(): List<AppLanguage> {
    val index = readProperties("files/i18n/index.properties")
    val languages = index.map { (tag, name) ->
        AppLanguage(
            tag = normalizeLanguageTag(tag),
            displayName = name
        )
    }
    return languages
        .ifEmpty { defaultLanguageOptions(DEFAULT_LANGUAGE_TAG) }
        .distinctBy { it.tag }
        .sortedBy { it.displayName }
}

suspend fun loadAppStrings(languageTag: String): AppStrings {
    val normalizedTag = normalizeLanguageTag(languageTag)
    val values = if (normalizedTag == DEFAULT_LANGUAGE_TAG) {
        readProperties("files/i18n/$DEFAULT_LANGUAGE_TAG.properties")
            .ifEmpty { englishFallbackStrings }
    } else {
        readProperties("files/i18n/$normalizedTag.properties")
    }

    return AppStrings(
        languageTag = normalizedTag,
        values = values.ifEmpty { englishFallbackStrings }
    )
}

fun defaultLanguageOptions(currentLanguageTag: String): List<AppLanguage> =
    listOf(
        AppLanguage(
            tag = DEFAULT_LANGUAGE_TAG,
            displayName =
                englishFallbackStrings["language.name"]
                    ?: "English (United States)"
        )
    ).withCurrentLanguage(currentLanguageTag)

private fun List<AppLanguage>.withCurrentLanguage(
    currentLanguageTag: String
): List<AppLanguage> {
    val normalizedTag = normalizeLanguageTag(currentLanguageTag)
    return if (any { it.tag == normalizedTag }) {
        this
    } else {
        this + AppLanguage(
            tag = normalizedTag,
            displayName = normalizedTag
        )
    }
}

private suspend fun readProperties(path: String): Map<String, String> =
    runCatching {
        parseProperties(
            Res.readBytes(path).decodeToString()
        )
    }.getOrDefault(emptyMap())

internal fun parseProperties(text: String): Map<String, String> =
    text.lineSequence()
        .map { it.trim() }
        .filter { line ->
            line.isNotEmpty() &&
                    !line.startsWith("#") &&
                    !line.startsWith("!")
        }
        .mapNotNull { line ->
            val separator = line.indexOfFirstUnescapedSeparator()
            if (separator < 0) {
                null
            } else {
                val key = line.substring(0, separator)
                    .trim()
                    .decodePropertyEscapes()
                val value = line.substring(separator + 1)
                    .trim()
                    .decodePropertyEscapes()
                key.takeIf { it.isNotBlank() }?.let { it to value }
            }
        }
        .toMap()

private fun String.indexOfFirstUnescapedSeparator(): Int {
    var escaped = false
    forEachIndexed { index, character ->
        if (escaped) {
            escaped = false
            return@forEachIndexed
        }
        when (character) {
            '\\' -> escaped = true
            '=', ':' -> return index
        }
    }
    return -1
}

private fun String.decodePropertyEscapes(): String {
    val decoded = StringBuilder(length)
    var index = 0
    while (index < length) {
        val character = this[index]
        if (character != '\\' || index == lastIndex) {
            decoded.append(character)
            index += 1
            continue
        }

        val next = this[index + 1]
        when (next) {
            'n' -> decoded.append('\n')
            'r' -> decoded.append('\r')
            't' -> decoded.append('\t')
            '\\', '=', ':', '#', '!' -> decoded.append(next)
            'u' -> {
                val code = substring(
                    startIndex = index + 2,
                    endIndex = (index + 6).coerceAtMost(length)
                )
                val value = code
                    .takeIf { it.length == 4 }
                    ?.toIntOrNull(radix = 16)
                    ?.toChar()
                if (value == null) {
                    decoded.append("\\u")
                } else {
                    decoded.append(value)
                    index += 4
                }
            }
            else -> decoded.append(next)
        }
        index += 2
    }
    return decoded.toString()
}

private val englishFallbackStrings = mapOf(
    "language.name" to "English (United States)",
    "app.name" to "Charity Market",
    "action.refresh" to "Refresh",
    "action.logout" to "Logout",
    "action.connect" to "Connect",
    "action.connecting" to "Connecting...",
    "action.sign_in" to "Sign in",
    "action.start_local_server" to "Start local server",
    "navigation.title" to "Navigation",
    "navigation.show" to "Show navigation",
    "navigation.hide" to "Hide navigation",
    "navigation.minimize" to "Minimize navigation",
    "navigation.expand" to "Expand navigation",
    "client_settings.show" to "Client settings",
    "client_settings.hide" to "Hide client settings",
    "destination.dashboard" to "Dashboard",
    "destination.users" to "Users",
    "destination.donors" to "Donors",
    "destination.items" to "Items",
    "destination.sales" to "Transactions",
    "destination.settings" to "Settings",
    "auto_refresh.every" to "Auto refresh every {0}s",
    "auto_refresh.off" to "Auto refresh off",
    "settings.title" to "Settings",
    "settings.subtitle" to "Client refresh preferences and server-wide policy.",
    "app_settings.title" to "App preferences",
    "app_settings.subtitle" to "Language, appearance, and local display choices for this device.",
    "app_settings.language" to "Language",
    "app_settings.theme" to "Theme",
    "app_settings.palette" to "Palette",
    "theme.system" to "Use system setting",
    "theme.light" to "Light",
    "theme.dark" to "Dark",
    "palette.charity" to "Charity",
    "palette.ocean" to "Ocean",
    "palette.forest" to "Forest",
    "palette.rose" to "Rose",
    "server_selection.android.subtitle" to
            "Connect this Android device to an existing Charity Market server.",
    "server_selection.desktop.subtitle" to
            "Choose how this desktop application should connect.",
    "server_selection.mode.connect.title" to "Connect to a server",
    "server_selection.mode.connect.subtitle" to
            "Use an existing standalone or hosted server.",
    "server_selection.mode.local.title" to "Host locally with SQLite",
    "server_selection.mode.local.subtitle" to
            "Start a private server on this computer without Docker.",
    "server_selection.address.label" to "Server URL or IP address",
    "server_selection.address.help" to
            "Enter a URL or IP address. Include http:// or https:// for a specific protocol; add :port only when the server is not on 80, 443, or 8088.",
    "server_selection.local_settings" to "Local server settings",
    "server_selection.data_directory.label" to "Data directory",
    "server_selection.data_directory.help" to
            "The SQLite database and local server log are stored here.",
    "server_selection.local_port.label" to "Local server port",
    "server_selection.local_port.help" to "Leave blank to use {0}.",
    "server_selection.initial_admin.title" to "Initial administrator",
    "server_selection.initial_admin.help" to
            "These credentials create the local administrator when the database is new.",
    "server_selection.admin_username" to "Administrator username",
    "server_selection.admin_password" to "Administrator password",
    "server_selection.admin_password.help" to
            "Permanent password, at least 8 characters.",
    "server_selection.status.local_started" to
            "Local SQLite server started. Signing in...",
    "server_selection.status.connected" to "Server connection successful.",
    "login.title" to "Sign in to Charity Market",
    "login.local_title" to "Sign in to the local Charity Market server",
    "login.username" to "Username",
    "login.password" to "Password",
    "login.enter_credentials" to "Enter both username and password.",
    "login.choose_another_server" to "Choose another server",
    "login.server_selection" to "Server selection",
    "login.signing_in" to "Signing in...",
    "login.local_running" to
            "Local server running. Data directory: {0}"
)
