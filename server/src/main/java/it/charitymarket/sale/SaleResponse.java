package it.charitymarket.sale;

import java.time.Instant;
import java.util.List;

public record SaleResponse (
        String id,
        Instant soldAt,
        PaymentMethod paymentMethod,
        long totalCents,
        int itemCount,
        List<SaleLineResponse> lines,
        SaleStatus status,
        String comments,
        Instant updatedAt,
        Instant voidedAt,
        String voidedByUserId,
        String voidReason
){
}
