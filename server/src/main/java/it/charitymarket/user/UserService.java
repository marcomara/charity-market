package it.charitymarket.user;

import io.quarkus.panache.common.Sort;
import it.charitymarket.auth.PasswordService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;


@ApplicationScoped
public class UserService {
    @Inject
    UserRepository userRepository;

    @Inject
    PasswordService passwordService;

    public List<UserResponse> list() {
        return userRepository
                .listAll(Sort.by("username"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserResponse create(
            CreateUserRequest request
    ) {
        String username =
                userRepository.normalizeUsername(
                        request.username()
                );

        if (userRepository.usernameExists(username)) {
            throw new ClientErrorException(
                    "The username already exists",
                    409
            );
        }

        Instant now = Instant.now();

        UserEntity user = new UserEntity();
        user.id = UUID.randomUUID().toString();
        user.username = username;
        user.displayName = request.displayName().trim();
        user.email = normalizeOptional(request.email());
        user.passwordHash = passwordService.hash(
                request.temporaryPassword()
        );
        user.status = UserStatus.ACTIVE;
        user.mustChangePassword = true;
        user.roles = Set.copyOf(request.roles());
        user.createdAt = now;
        user.updatedAt = now;

        userRepository.persist(user);

        return toResponse(user);
    }

    @Transactional
    public UserResponse replaceRoles(
            String userId,
            Set<UserRole> roles
    ) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException(
                    "A user must have at least one role"
            );
        }

        UserEntity user = findEntity(userId);

        user.roles.clear();
        user.roles.addAll(roles);
        user.updatedAt = Instant.now();

        return toResponse(user);
    }

    @Transactional
    public UserResponse suspend(String userId) {
        UserEntity user = findEntity(userId);
        user.status = UserStatus.SUSPENDED;
        user.updatedAt = Instant.now();

        return toResponse(user);
    }

    @Transactional
    public UserResponse enable(String userId) {
        UserEntity user = findEntity(userId);
        user.status = UserStatus.ACTIVE;
        user.updatedAt = Instant.now();

        return toResponse(user);
    }

    private UserEntity findEntity(String userId) {
        return userRepository
                .findByIdOptional(userId)
                .orElseThrow(
                        () -> new NotFoundException(
                                "User not found"
                        )
                );
    }

    private UserResponse toResponse(UserEntity user) {
        return new UserResponse(
                user.id,
                user.username,
                user.displayName,
                user.email,
                user.status,
                user.mustChangePassword,
                Set.copyOf(user.roles),
                user.createdAt,
                user.lastLoginAt
        );
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}
