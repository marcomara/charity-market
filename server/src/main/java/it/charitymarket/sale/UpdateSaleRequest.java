package it.charitymarket.sale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
public record UpdateSaleRequest(
        @NotEmpty
        List<@Valid SaleLineRequest> lines,

        @NotNull
        PaymentMethod paymentMethod,

        @Size(max = 4000)
        String comments
) {
}
