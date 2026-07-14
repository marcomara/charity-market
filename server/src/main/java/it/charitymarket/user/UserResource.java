package it.charitymarket.user;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.Set;


@Path("/api/users")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("SYSTEM_ADMINISTRATOR")
public class UserResource {

    @Inject
    UserService userService;

    @GET
    public List<UserResponse> list() {
        return userService.list();
    }

    @POST
    public Response create(
            @Valid CreateUserRequest request
    ) {
        UserResponse user = userService.create(request);

        return Response
                .created(
                        URI.create("/api/users/" + user.id())
                )
                .entity(user)
                .build();
    }

    @PUT
    @Path("/{id}/roles")
    public UserResponse replaceRoles(
            @PathParam("id") String id,
            @Valid ReplaceRolesRequest request
    ) {
        return userService.replaceRoles(
                id,
                request.roles()
        );
    }

    @POST
    @Path("/{id}/suspend")
    public UserResponse suspend(
            @PathParam("id") String id
    ) {
        return userService.suspend(id);
    }

    @POST
    @Path("/{id}/enable")
    public UserResponse enable(
            @PathParam("id") String id
    ) {
        return userService.enable(id);
    }

    public record ReplaceRolesRequest(
            @NotEmpty Set<UserRole> roles
    ) {
    }
}
