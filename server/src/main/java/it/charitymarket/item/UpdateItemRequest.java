package it.charitymarket.item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
public record UpdateItemRequest(
        @NotBlank
        @Size(max = 50)
        String code,

        @NotBlank
        @Size(max = 200)
        String name,

        @NotBlank
        String donorId,

        @NotNull
        ItemCondition condition,

        @PositiveOrZero
        long suggestedPriceCents,

        @Size(max = 4000)
        String comments
) {
}
