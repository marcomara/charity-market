package it.charitymarket.donor;

import java.time.Instant;

public record DonorResponse (
        String id,
        String name,
        String email,
        String phone,
        String comments,
        Instant createdAt,
        Instant updatedAt
) {
}
