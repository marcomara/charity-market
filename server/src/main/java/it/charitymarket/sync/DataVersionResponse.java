package it.charitymarket.sync;

import java.time.Instant;

public record DataVersionResponse(
        long version,
        Instant updatedAt
) {
}
