package it.charitymarket.settings;

import java.time.Instant;

public record DatabaseResetResponse(
        String backupPath,
        Instant resetAt
) {
}
