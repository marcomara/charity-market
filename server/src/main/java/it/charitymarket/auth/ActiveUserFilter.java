package it.charitymarket.auth;

import it.charitymarket.api.ApiErrorResponse;
import it.charitymarket.user.UserEntity;
import it.charitymarket.user.UserRepository;
import it.charitymarket.user.UserStatus;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Provider
@Priority(Priorities.AUTHORIZATION)
public class ActiveUserFilter implements ContainerRequestFilter {

    @Inject
    UserRepository userRepository;

    @Inject
    JsonWebToken token;

    @Override
    public void filter(
            ContainerRequestContext requestContext
    ) {
        String path = requestContext
                .getUriInfo()
                .getPath();

        /*
         * Normalize both:
         * api/items
         * /api/items
         */
        while (path.startsWith("/")) {
            path = path.substring(1);
        }

        /*
         * Ignore non-API endpoints and the public login endpoint.
         */
        if (!path.startsWith("api/")
                || path.equals("api/auth/login")
                || path.equals("api/auth/login/")) {
            return;
        }

        String userId = token.getSubject();

        /*
         * Anonymous requests will still be rejected by the normal
         * Quarkus authenticated policy.
         */
        if (userId == null || userId.isBlank()) {
            return;
        }

        UserEntity user = userRepository
                .findByIdOptional(userId)
                .orElse(null);

        if (user == null) {
            abort(
                    requestContext,
                    Response.Status.UNAUTHORIZED,
                    "ACCOUNT_NOT_FOUND",
                    "The authenticated account no longer exists."
            );
            return;
        }

        if (user.status == UserStatus.SUSPENDED) {
            abort(
                    requestContext,
                    Response.Status.FORBIDDEN,
                    "ACCOUNT_SUSPENDED",
                    "Your account has been suspended. Contact an administrator."
            );
            return;
        }

        if (user.status != UserStatus.ACTIVE) {
            abort(
                    requestContext,
                    Response.Status.FORBIDDEN,
                    "ACCOUNT_DISABLED",
                    "Your account is disabled. Contact an administrator."
            );
        }
    }

    private void abort(
            ContainerRequestContext requestContext,
            Response.Status status,
            String code,
            String message
    ) {
        requestContext.abortWith(
                Response.status(status)
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .entity(
                                new ApiErrorResponse(
                                        code,
                                        message
                                )
                        )
                        .build()
        );
    }
}
