package it.charitymarket.user;

import java.time.Instant;
import java.util.Set;

public record UserResponse(
        String id,
        String username,
        String displayName,
        String email,
        UserStatus status,
        boolean mustChangePassword,
        Set<UserRole> roles,
        Instant createdAt,
        Instant lastLoginAt
) {
}
