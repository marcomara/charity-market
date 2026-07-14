package it.charitymarket.auth;


import it.charitymarket.user.UserEntity;
import it.charitymarket.user.UserRepository;
import it.charitymarket.user.UserStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotAuthorizedException;

import java.time.Instant;
import java.util.Set;

@ApplicationScoped
public class AuthenticationService {

    @Inject
    UserRepository userRepository;

    @Inject
    PasswordService passwordService;

    @Inject
    JwtTokenService jwtTokenService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserEntity user = userRepository
                .findByUsername(request.username())
                .orElseThrow(
                        () -> new NotAuthorizedException(
                                "Invalid username or password"
                        )
                );

        if (user.status != UserStatus.ACTIVE) {
            throw new NotAuthorizedException(
                    "The account is not active"
            );
        }

        if (!passwordService.matches(
                request.password(),
                user.passwordHash
        )) {
            throw new NotAuthorizedException(
                    "Invalid username or password"
            );
        }

        user.lastLoginAt = Instant.now();
        user.updatedAt = Instant.now();

        String token;

        if (user.mustChangePassword) {
            token = jwtTokenService
                    .createPasswordChangeToken(user);
        } else {
            token = jwtTokenService
                    .createAccessToken(user);
        }

        return new LoginResponse(
                token,
                "Bearer",
                jwtTokenService.accessTokenSeconds(),
                user.mustChangePassword,
                toResponse(user)
        );
    }

    public AuthenticatedUserResponse toResponse(
            UserEntity user
    ) {
        return new AuthenticatedUserResponse(
                user.id,
                user.username,
                user.displayName,
                user.email,
                user.status,
                user.mustChangePassword,
                Set.copyOf(user.roles)
        );
    }

    @Transactional
    public LoginResponse changeInitialPassword(
            String userId,
            ChangePasswordRequest request
    ) {
        if (!request.newPassword().equals(
                request.confirmation()
        )) {
            throw new IllegalArgumentException(
                    "The password confirmation does not match"
            );
        }

        UserEntity user = userRepository
                .findByIdOptional(userId)
                .orElseThrow(
                        () -> new NotAuthorizedException(
                                "User not found"
                        )
                );

        if (!user.mustChangePassword) {
            throw new IllegalArgumentException(
                    "An initial password change is not required"
            );
        }

        if (!passwordService.matches(
                request.currentPassword(),
                user.passwordHash
        )) {
            throw new NotAuthorizedException(
                    "The current password is incorrect"
            );
        }

        if (passwordService.matches(
                request.newPassword(),
                user.passwordHash
        )) {
            throw new IllegalArgumentException(
                    "The new password must be different"
            );
        }

        user.passwordHash =
                passwordService.hash(request.newPassword());
        user.mustChangePassword = false;
        user.updatedAt = Instant.now();

        String token =
                jwtTokenService.createAccessToken(user);

        return new LoginResponse(
                token,
                "Bearer",
                jwtTokenService.accessTokenSeconds(),
                false,
                toResponse(user)
        );
    }
}
