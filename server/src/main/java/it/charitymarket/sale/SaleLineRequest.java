package it.charitymarket.sale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record SaleLineRequest(
        @NotBlank(message = "The item ID is required")
        String itemId,

        @PositiveOrZero(
                message = "The final price cannot be negative"
        )
        long finalPriceCents
) {
}
