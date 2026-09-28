package it.charitymarket.settings;

import jakarta.validation.constraints.NotBlank;

public record ResetDatabaseRequest(
        @NotBlank(message = "The administrator password is required")
        String administratorPassword
) {
}
