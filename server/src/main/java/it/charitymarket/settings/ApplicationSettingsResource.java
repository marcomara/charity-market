package it.charitymarket.settings;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;


@Path("/api/settings")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ApplicationSettingsResource {

    @Inject
    ApplicationSettingsService service;

    @Inject
    DatabaseResetService databaseResetService;

    @GET
    @Authenticated
    public ApplicationSettingsResponse get() {
        return service.get();
    }

    @PUT
    @RolesAllowed("SYSTEM_ADMINISTRATOR")
    public ApplicationSettingsResponse update(
            @Valid
            UpdateApplicationSettingsRequest request
    ) {
        return service.update(request);
    }

    @POST
    @Path("/database-reset")
    @RolesAllowed("SYSTEM_ADMINISTRATOR")
    public DatabaseResetResponse resetDatabase(
            @Valid
            ResetDatabaseRequest request
    ) {
        return databaseResetService.reset(request);
    }

}
