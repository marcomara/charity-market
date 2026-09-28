package it.charitymarket.settings;

import it.charitymarket.auth.PasswordService;
import it.charitymarket.user.UserEntity;
import it.charitymarket.user.UserRepository;
import it.charitymarket.user.UserRole;
import it.charitymarket.user.UserStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.nio.file.Path;
import java.time.Instant;

@ApplicationScoped
public class DatabaseResetService {
    @Inject
    UserRepository userRepository;

    @Inject
    PasswordService passwordService;

    @Inject
    JsonWebToken token;

    @Inject
    DatabaseBackupService backupService;

    @Inject
    DatabaseResetExecutor resetExecutor;

    public DatabaseResetResponse reset(
            ResetDatabaseRequest request
    ) {
        UserEntity administrator =
                verifyAdministratorPassword(request);

        Path backupPath = backupService.createBackup();
        Instant resetAt = resetExecutor.resetApplicationData(
                administrator.id
        );

        return new DatabaseResetResponse(
                backupPath.toString(),
                resetAt
        );
    }

    private UserEntity verifyAdministratorPassword(
            ResetDatabaseRequest request
    ) {
        String userId = token.getSubject();
        if (userId == null || userId.isBlank()) {
            throw new NotAuthorizedException(
                    "Authentication is required"
            );
        }

        UserEntity user = userRepository.findByIdOptional(userId)
                .orElseThrow(
                        () -> new NotAuthorizedException(
                                "The authenticated account no longer exists."
                        )
                );

        if (user.status != UserStatus.ACTIVE ||
                !user.roles.contains(UserRole.SYSTEM_ADMINISTRATOR)) {
            throw new ForbiddenException(
                    "Only an active system administrator can empty the database."
            );
        }

        if (!passwordService.matches(
                request.administratorPassword(),
                user.passwordHash
        )) {
            throw new ForbiddenException(
                    "The administrator password is incorrect."
            );
        }

        return user;
    }
}
