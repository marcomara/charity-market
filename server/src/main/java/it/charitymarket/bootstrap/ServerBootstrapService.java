package it.charitymarket.bootstrap;

import io.quarkus.runtime.StartupEvent;
import it.charitymarket.auth.PasswordService;
import it.charitymarket.user.UserEntity;
import it.charitymarket.user.UserRepository;
import it.charitymarket.user.UserRole;
import it.charitymarket.user.UserStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class ServerBootstrapService {

    private static final Logger LOG =
            Logger.getLogger(ServerBootstrapService.class);

    @Inject
    UserRepository userRepository;

    @Inject
    PasswordService passwordService;

    @ConfigProperty(
            name = "charity.bootstrap.admin.username",
            defaultValue = "admin"
    )
    String administratorUsername;

    @ConfigProperty(
            name = "charity.bootstrap.admin.must-change-password",
            defaultValue = "true"
    )
    boolean administratorMustChangePassword;

    @ConfigProperty(
            name = "charity.bootstrap.admin.password"
    )
    String administratorPassword;

    @Transactional
    void initialize(@Observes StartupEvent event) {
        if (userRepository.count() > 0) {
            return;
        }

        if (administratorPassword == null
                || administratorPassword.isBlank()) {
            throw new IllegalStateException(
                    "The first administrator password is missing. "
                            + "Start the server with "
                            + "-Dcharity.bootstrap.admin.password=<temporary-password>"
            );
        }

        Instant now = Instant.now();

        UserEntity administrator = new UserEntity();
        administrator.id = UUID.randomUUID().toString();
        administrator.username =
                userRepository.normalizeUsername(
                        administratorUsername
                );
        administrator.displayName =
                "Server Administrator";
        administrator.passwordHash =
                passwordService.hash(
                        administratorPassword
                );
        administrator.status = UserStatus.ACTIVE;
        administrator.mustChangePassword = administratorMustChangePassword;
        administrator.roles = Set.of(
                UserRole.SYSTEM_ADMINISTRATOR
        );
        administrator.createdAt = now;
        administrator.updatedAt = now;

        userRepository.persist(administrator);

        LOG.infof(
                "Initial administrator created with username '%s'. "
                        + "Password change required: %s",
                administrator.username,
                administrator.mustChangePassword
        );
    }
}
