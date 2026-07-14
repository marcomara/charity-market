package it.charitymarket.auth;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthenticationResource {

    @Inject
    AuthenticationService authenticationService;

    @Inject
    JsonWebToken token;

    @POST
    @Path("/login")
    @PermitAll
    public LoginResponse login(
            @Valid LoginRequest request
    ) {
        return authenticationService.login(request);
    }


    @POST
    @Path("/change-initial-password")
    @jakarta.annotation.security.RolesAllowed(
            JwtTokenService.PASSWORD_CHANGE_ROLE
    )
    public LoginResponse changeInitialPassword(
            @Valid ChangePasswordRequest request
    ) {
        return authenticationService
                .changeInitialPassword(
                        token.getSubject(),
                        request
                );
    }


    @GET
    @Path("/token-info")
    @Authenticated
    public TokenInfoResponse tokenInfo() {
        return new TokenInfoResponse(
                token.getSubject(),
                token.getName(),
                token.getGroups()
        );
    }

    public record TokenInfoResponse(
            String userId,
            String username,
            java.util.Set<String> roles
    ) {
    }
}
