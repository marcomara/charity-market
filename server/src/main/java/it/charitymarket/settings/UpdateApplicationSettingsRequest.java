package it.charitymarket.settings;

import jakarta.validation.constraints.NotBlank;
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
        String currencyCode
) {
}
