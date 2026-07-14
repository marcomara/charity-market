package it.charitymarket.api;


import it.charitymarket.sale.*;
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
        "SELLER"
})
@Path("/api/sales")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SaleResource {
    @Inject
    SaleService saleService;

    @GET
    public List<SaleResponse> list() {
        return saleService.list();
    }

    @GET
    @Path("/{id}")
    public SaleResponse findById(
            @PathParam("id") String id
    ) {
        return saleService.findById(id);
    }

    @POST
    public Response create(
            @Valid CreateSaleRequest request,
            @Context UriInfo uriInfo
    ) {
        SaleResponse sale = saleService.create(request);

        URI location = uriInfo
                .getAbsolutePathBuilder()
                .path(sale.id())
                .build();

        return Response
                .created(location)
                .entity(sale)
                .build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("SYSTEM_ADMINISTRATOR")
    public SaleResponse update(
            @PathParam("id") String id,
            @Valid UpdateSaleRequest request
    ) {
        return saleService.update(
                id,
                request
        );
    }

    @POST
    @Path("/{id}/void")
    @RolesAllowed("SYSTEM_ADMINISTRATOR")
    public SaleResponse voidSale(
            @PathParam("id") String id,
            @Valid VoidSaleRequest request
    ) {
        return saleService.voidSale(
                id,
                request
        );
    }
}
