package it.charitymarket.sync;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/sync/status")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class DataVersionResource {
    @Inject
    DataVersionService service;

    @GET
    public DataVersionResponse get() {
        return service.get();
    }
}
