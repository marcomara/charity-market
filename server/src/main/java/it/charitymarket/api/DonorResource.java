package it.charitymarket.api;


import it.charitymarket.donor.CreateDonorRequest;
import it.charitymarket.donor.DonorResponse;
import it.charitymarket.donor.DonorService;
import it.charitymarket.donor.UpdateDonorRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.List;


@RolesAllowed({
        "SYSTEM_ADMINISTRATOR",
        "MARKET_MANAGER",
        "INVENTORY_MANAGER",
        "SELLER"
})
@Path("/api/donors")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DonorResource {

    @Inject
    DonorService donorService;

    @GET
    public List<DonorResponse> list(){
        return donorService.list();
    }

    @POST
    @RolesAllowed({
            "SYSTEM_ADMINISTRATOR",
            "MARKET_MANAGER",
            "INVENTORY_MANAGER"
    })
    public Response create(
            @Valid CreateDonorRequest request,
            @Context UriInfo uriInfo
    ){
        DonorResponse donor = donorService.create(request);

        URI location = uriInfo
                .getAbsolutePathBuilder()
                .path(donor.id())
                .build();

        return Response
                .created(location)
                .entity(donor)
                .build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({
            "SYSTEM_ADMINISTRATOR",
            "MARKET_MANAGER",
            "INVENTORY_MANAGER"
    })
    public DonorResponse update(
            @PathParam("id") String id,
            @Valid UpdateDonorRequest request
    ) {
        return donorService.update(id, request);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("SYSTEM_ADMINISTRATOR")
    public Response delete(
            @PathParam("id") String id
    ) {
        donorService.delete(id);
        return Response.noContent().build();
    }
}
