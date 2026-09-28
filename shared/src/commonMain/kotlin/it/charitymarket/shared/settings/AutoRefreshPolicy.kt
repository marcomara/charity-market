package it.charitymarket.shared.settings

import it.charitymarket.shared.api.ApplicationSettingsResponse

const val MINIMUM_ALLOWED_REFRESH_INTERVAL_SECONDS = 5
const val MAXIMUM_ALLOWED_REFRESH_INTERVAL_SECONDS = 3600

data class AutoRefreshPolicy(
    val enabled: Boolean = true,
    val usersCanCustomize: Boolean = false,
    val defaultIntervalSeconds: Int = 10,
    val minimumIntervalSeconds: Int = 5,
    val maximumIntervalSeconds: Int = 15
) {
    fun validated(): AutoRefreshPolicy {
        require(
            minimumIntervalSeconds in
                    MINIMUM_ALLOWED_REFRESH_INTERVAL_SECONDS..MAXIMUM_ALLOWED_REFRESH_INTERVAL_SECONDS
        ) {
            "The minimum refresh interval must be between 5 and 3600 seconds."
        }
        require(
            maximumIntervalSeconds in
                    MINIMUM_ALLOWED_REFRESH_INTERVAL_SECONDS..MAXIMUM_ALLOWED_REFRESH_INTERVAL_SECONDS
        ) {
            "The maximum refresh interval must be between 5 and 3600 seconds."
        }
        require(minimumIntervalSeconds <= maximumIntervalSeconds) {
            "The minimum refresh interval cannot exceed the maximum."
        }
        require(defaultIntervalSeconds in intervalRange) {
            "The default refresh interval must be within the configured range."
        }
        return this
    }

    val intervalRange: IntRange
        get() = minimumIntervalSeconds..maximumIntervalSeconds

    fun effectiveIntervalSeconds(
        preference: ClientAutoRefreshPreference
    ): Int = if (usersCanCustomize) {
        preference.intervalSeconds
            ?.coerceIn(minimumIntervalSeconds, maximumIntervalSeconds)
            ?: defaultIntervalSeconds
    } else {
        defaultIntervalSeconds
    }

    fun isActive(preference: ClientAutoRefreshPreference): Boolean =
        enabled && (!usersCanCustomize || preference.enabled)
}

data class ClientAutoRefreshPreference(
    val enabled: Boolean = true,
    val intervalSeconds: Int? = null
)

fun ApplicationSettingsResponse.toAutoRefreshPolicy(): AutoRefreshPolicy =
    AutoRefreshPolicy(
        enabled = autoRefreshEnabled,
        usersCanCustomize = usersCanCustomizeAutoRefresh,
        defaultIntervalSeconds = defaultRefreshIntervalSeconds,
        minimumIntervalSeconds = minimumRefreshIntervalSeconds,
        maximumIntervalSeconds = maximumRefreshIntervalSeconds
    ).validated()
