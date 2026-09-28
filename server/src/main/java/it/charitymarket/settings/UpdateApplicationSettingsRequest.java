package it.charitymarket.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateApplicationSettingsRequest(
        @NotBlank(
                message = "The currency code is required"
        )
        @Size(
                min = 3,
                max = 3,
                message =
                        "The currency code must contain 3 characters"
        )
        String currencyCode,
        Boolean autoRefreshEnabled,
        Boolean usersCanCustomizeAutoRefresh,
        @Min(
                value = 5,
                message = "The minimum refresh interval is 5 seconds"
        )
        @Max(
                value = 3600,
                message = "The maximum refresh interval is 3600 seconds"
        )
        Integer defaultRefreshIntervalSeconds,
        @Min(
                value = 5,
                message = "The minimum refresh interval is 5 seconds"
        )
        @Max(
                value = 3600,
                message = "The maximum refresh interval is 3600 seconds"
        )
        Integer minimumRefreshIntervalSeconds,
        @Min(
                value = 5,
                message = "The minimum refresh interval is 5 seconds"
        )
        @Max(
                value = 3600,
                message = "The maximum refresh interval is 3600 seconds"
        )
        Integer maximumRefreshIntervalSeconds
) {
}
