package it.charitymarket.item;


import it.charitymarket.api.ApiErrorResponse;
import it.charitymarket.donor.DonorEntity;
import it.charitymarket.donor.DonorRepository;
import it.charitymarket.sale.SaleLineRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class ItemService {

    @Inject
    ItemRepository itemRepository;

    @Inject
    DonorRepository donorRepository;

    @Inject
    SaleLineRepository saleLineRepository;

    public List<ItemResponse> list(){
        return itemRepository
                .listAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }


    public ItemResponse findById(String id){
        ItemEntity item = itemRepository
                .findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        return toResponse(item);
    }

    public ItemResponse findByCode(String code) {
        String normalizedCode = normalizeCode(code);

        ItemEntity item = itemRepository
                .findByCode(normalizedCode)
                .orElseThrow(
                        () -> new NotFoundException("Item not found")
                );

        return toResponse(item);
    }

    @Transactional
    public ItemResponse create(CreateItemRequest request) {
        DonorEntity donor = donorRepository
                .findByIdOptional(request.donorId())
                .orElseThrow(
                        () -> new NotFoundException("Donor not found")
                );

        String code = request.code() == null
                || request.code().isBlank()
                ? generateItemCode()
                : normalizeCode(request.code());

        if (itemRepository.findByCode(code).isPresent()) {
            throw new ClientErrorException(
                    "An item with this code already exists",
                    409
            );
        }

        Instant now = Instant.now();

        ItemEntity item = new ItemEntity();
        item.id = UUID.randomUUID().toString();
        item.code = code;
        item.name = request.name().trim();
        item.donor = donor;
        item.condition = request.condition();
        item.suggestedPriceCents = request.suggestedPriceCents();
        item.status = ItemStatus.AVAILABLE;
        item.comments = normalizeOptional(request.comments());
        item.createdAt = now;
        item.updatedAt = now;

        itemRepository.persist(item);

        return toResponse(item);
    }

    @Transactional
    public void remove(String id) {
        ItemEntity item = findEntity(id);

        if (saleLineRepository.referencesItem(id)) {
            throw conflict(
                    "ITEM_REFERENCED_BY_SALE",
                    "This item cannot be deleted because it is referenced by a sale."
            );
        }

        itemRepository.delete(item);
    }

    @Transactional
    public ItemResponse update(
            String id,
            UpdateItemRequest request
    ) {
        ItemEntity item = findEntity(id);

        DonorEntity donor = donorRepository
                .findByIdOptional(request.donorId())
                .orElseThrow(
                        () -> new NotFoundException(
                                "Donor not found"
                        )
                );

        String code =
                normalizeCode(request.code());

        itemRepository.findByCode(code)
                .ifPresent(existing -> {
                    if (!existing.id.equals(id)) {
                        throw new ClientErrorException(
                                "An item with this code already exists",
                                409
                        );
                    }
                });

        boolean referenced =
                saleLineRepository.referencesItem(id);

        if (referenced) {
            boolean historicalFieldsChanged =
                    !item.code.equals(code)
                            || !item.name.equals(
                            request.name().trim()
                    )
                            || !item.donor.id.equals(
                            donor.id
                    )
                            || item.condition
                            != request.condition()
                            || item.suggestedPriceCents
                            != request
                            .suggestedPriceCents();

            if (historicalFieldsChanged) {
                throw conflict(
                        "ITEM_REFERENCED_BY_SALE",
                        "Only comments can be changed after an item has been referenced by a sale."
                );
            }
        }

        item.code = code;
        item.name = request.name().trim();
        item.donor = donor;
        item.condition = request.condition();
        item.suggestedPriceCents =
                request.suggestedPriceCents();
        item.comments =
                normalizeOptional(request.comments());
        item.updatedAt = Instant.now();

        return toResponse(item);
    }

    private String generateItemCode() {
        return "ITEM-" +
                UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase(Locale.ROOT);
    }

    private String normalizeCode(String code) {
        return code
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private ItemResponse toResponse(ItemEntity item) {
        return new ItemResponse(
                item.id,
                item.code,
                item.name,
                item.donor.id,
                item.donor.name,
                item.condition,
                item.suggestedPriceCents,
                item.status,
                item.comments,
                item.createdAt,
                item.updatedAt
        );
    }

    private ItemEntity findEntity(String id) {
        return itemRepository
                .findByIdOptional(id)
                .orElseThrow(
                        () -> new NotFoundException(
                                "Item not found"
                        )
                );
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    private ClientErrorException conflict(
            String code,
            String message
    ) {
        return new ClientErrorException(
                Response.status(
                                Response.Status.CONFLICT
                        )
                        .type(
                                MediaType
                                        .APPLICATION_JSON_TYPE
                        )
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
