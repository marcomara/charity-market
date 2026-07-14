package it.charitymarket.auth;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        boolean passwordChangeRequired,
        AuthenticatedUserResponse user
) {
}
