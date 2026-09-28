package it.charitymarket.api;


import it.charitymarket.item.CreateItemRequest;
import it.charitymarket.item.ItemResponse;
import it.charitymarket.item.ItemService;
import it.charitymarket.item.UpdateItemRequest;
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
@Path("/api/items")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ItemResource {

    @Inject
    ItemService itemService;

    @GET
    public List<ItemResponse> list() {
        return itemService.list();
    }

    @GET
    @Path("/{id}")
    public ItemResponse findById(
            @PathParam("id") String id
    ) {
        return itemService.findById(id);
    }

    @GET
    @Path("/by-code/{code}")
    public ItemResponse findByCode(
            @PathParam("code") String code
    ) {
        return itemService.findByCode(code);
    }

    @POST
    @RolesAllowed({
            "SYSTEM_ADMINISTRATOR",
            "MARKET_MANAGER",
            "INVENTORY_MANAGER"
    })
    public Response create(
            @Valid CreateItemRequest request,
            @Context UriInfo uriInfo
    ) {
        ItemResponse item = itemService.create(request);

        URI location = uriInfo
                .getAbsolutePathBuilder()
                .path(item.id())
                .build();

        return Response
                .created(location)
                .entity(item)
                .build();
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({
            "SYSTEM_ADMINISTRATOR",
            "MARKET_MANAGER",
            "INVENTORY_MANAGER"
    })
    public Response remove(
            @PathParam("id") String id
    ) {
        itemService.remove(id);

        return Response
                .noContent()
                .build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({
            "SYSTEM_ADMINISTRATOR",
            "MARKET_MANAGER",
            "INVENTORY_MANAGER"
    })
    public ItemResponse update(
            @PathParam("id") String id,
            @Valid UpdateItemRequest request
    ) {
        return itemService.update(id, request);
    }
}
