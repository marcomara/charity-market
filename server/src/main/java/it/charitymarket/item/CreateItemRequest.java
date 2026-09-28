package it.charitymarket.item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateItemRequest (
        @Size(
                max = 50,
                message = "The item code cannot exceed 50 characters"
        )
        String code,

        @NotBlank(message = "The item name is required")
        @Size(
                max = 200,
                message = "The item name cannot exceed 200 characters"
        )
        String name,

        @NotBlank(message = "The donor ID is required")
        String donorId,

        @NotNull(message = "The item condition is required")
        ItemCondition condition,

        @PositiveOrZero(
                message = "The suggested price cannot be negative"
        )
        long suggestedPriceCents,

        @Size(
                max = 4000,
                message = "Comments cannot exceed 4000 characters"
        )
        String comments
){
}
