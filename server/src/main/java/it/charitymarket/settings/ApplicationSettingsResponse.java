package it.charitymarket.settings;

import java.time.Instant;

public record ApplicationSettingsResponse(
        String currencyCode,
        Instant updatedAt,
        String updatedByUserId
) {
}
