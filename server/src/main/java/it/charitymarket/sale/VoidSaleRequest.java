package it.charitymarket.sale;

import jakarta.validation.constraints.Size;

public record VoidSaleRequest(
        @Size(max = 4000)
        String reason
) {
}
