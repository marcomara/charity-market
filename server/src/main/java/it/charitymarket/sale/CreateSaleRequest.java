package it.charitymarket.sale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateSaleRequest(

        @NotEmpty(message = "A sale must contain at least one item")
        List<@Valid SaleLineRequest> lines,

        @NotNull(message = "The payment method is required")
        PaymentMethod paymentMethod,

        @Size(
                max = 4000,
                message = "Comments cannot exceed 4000 characters"
        )
        String comments
) {
}
