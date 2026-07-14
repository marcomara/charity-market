package it.charitymarket.item;

import java.time.Instant;

public record ItemResponse (
        String id,
        String code,
        String name,
        String donorId,
        String donorName,
        ItemCondition condition,
        long suggestedPriceCents,
        ItemStatus status,
        String comments,
        Instant createdAt,
        Instant updatedAt
){
}
