package it.charitymarket.auth;


import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PasswordService {

    public String hash(String plainPassword) {
        validatePassword(plainPassword);

        return BcryptUtil.bcryptHash(plainPassword);
    }

    public boolean matches(
            String plainPassword,
            String passwordHash
    ) {
        if (plainPassword == null || passwordHash == null) {
            return false;
        }

        return BcryptUtil.matches(
                plainPassword,
                passwordHash
        );
    }

    public void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException(
                    "The password must contain at least 8 characters"
            );
        }

        if (password.length() > 128) {
            throw new IllegalArgumentException(
                    "The password cannot exceed 128 characters"
            );
        }
    }
}
