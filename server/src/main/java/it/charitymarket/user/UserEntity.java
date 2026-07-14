package it.charitymarket.user;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;


@Entity
@Table(name = "app_users")
public class UserEntity extends PanacheEntityBase {

    @Id
    @Column(length = 36, nullable = false)
    public String id;

    @Column(
            nullable = false,
            unique = true,
            length = 100
    )
    public String username;

    @Column(
            name = "display_name",
            nullable = false,
            length = 200
    )
    public String displayName;

    @Column(length = 320)
    public String email;

    @Column(
            name = "password_hash",
            nullable = false,
            length = 200
    )
    public String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    public UserStatus status;

    @Column(
            name = "must_change_password",
            nullable = false
    )
    public boolean mustChangePassword;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "app_user_roles",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    public Set<UserRole> roles = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(name = "last_login_at")
    public Instant lastLoginAt;
}
