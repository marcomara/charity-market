package it.charitymarket.settings;

import java.time.Instant;

public record ApplicationSettingsResponse(
        String currencyCode,
        boolean autoRefreshEnabled,
        boolean usersCanCustomizeAutoRefresh,
        int defaultRefreshIntervalSeconds,
        int minimumRefreshIntervalSeconds,
        int maximumRefreshIntervalSeconds,
        Instant updatedAt,
        String updatedByUserId
) {
}
