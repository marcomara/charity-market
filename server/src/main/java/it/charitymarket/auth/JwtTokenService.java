package it.charitymarket.auth;


import io.smallrye.jwt.build.Jwt;
import it.charitymarket.user.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class JwtTokenService {

    public static final String PASSWORD_CHANGE_ROLE =
            "PASSWORD_CHANGE_REQUIRED";

    @ConfigProperty(name = "charity.jwt.issuer")
    String issuer;

    @ConfigProperty(
            name = "charity.jwt.access-token-seconds"
    )
    long accessTokenSeconds;

    public String createAccessToken(UserEntity user) {
        Set<String> groups = user.roles
                .stream()
                .map(Enum::name)
                .collect(Collectors.toSet());

        return Jwt
                .issuer(issuer)
                .subject(user.id)
                .upn(user.username)
                .groups(groups)
                .claim("uid", user.id)
                .claim("displayName", user.displayName)
                .claim(
                        "mustChangePassword",
                        user.mustChangePassword
                )
                .sign();
    }

    public String createPasswordChangeToken(
            UserEntity user
    ) {
        return Jwt
                .issuer(issuer)
                .subject(user.id)
                .upn(user.username)
                .groups(Set.of(PASSWORD_CHANGE_ROLE))
                .claim("uid", user.id)
                .claim("mustChangePassword", true)
                .sign();
    }

    public long accessTokenSeconds() {
        return accessTokenSeconds;
    }
}
