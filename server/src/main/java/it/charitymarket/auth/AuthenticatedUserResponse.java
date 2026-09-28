package it.charitymarket.auth;

import it.charitymarket.user.UserRole;
import it.charitymarket.user.UserStatus;

import java.util.Set;

public record AuthenticatedUserResponse(
        String id,
        String username,
        String displayName,
        String email,
        UserStatus status,
        boolean mustChangePassword,
        Set<UserRole> roles
) {
}
