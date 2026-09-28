package it.charitymarket.user;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Locale;
import java.util.Optional;


@ApplicationScoped
public class UserRepository implements PanacheRepositoryBase<UserEntity, String> {

    public Optional<UserEntity> findByUsername(
            String username
    ) {
        return find(
                "username",
                normalizeUsername(username)
        ).firstResultOptional();
    }

    public boolean usernameExists(String username) {
        return count(
                "username",
                normalizeUsername(username)
        ) > 0;
    }

    public String normalizeUsername(String username) {
        return username
                .trim()
                .toLowerCase(Locale.ROOT);
    }

}
