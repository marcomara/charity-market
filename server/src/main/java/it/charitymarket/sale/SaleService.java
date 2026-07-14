package it.charitymarket.sale;


import io.quarkus.panache.common.Sort;
import it.charitymarket.item.ItemEntity;
import it.charitymarket.item.ItemRepository;
import it.charitymarket.item.ItemStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import it.charitymarket.api.ApiErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class SaleService {
    @Inject
    SaleRepository saleRepository;

    @Inject
    ItemRepository itemRepository;

    @Inject
    JsonWebToken token;

    @Transactional
    public SaleResponse create(CreateSaleRequest request) {
        Instant now = Instant.now();

        SaleEntity sale = new SaleEntity();
        sale.id = UUID.randomUUID().toString();
        sale.soldAt = now;
        sale.paymentMethod = request.paymentMethod();
        sale.updatedAt = now;
        sale.status = SaleStatus.COMPLETED;
        sale.comments = normalizeOptional(request.comments());

        Set<String> processedItemIds = new HashSet<>();
        long totalCents = 0;

        for (SaleLineRequest requestedLine : request.lines()) {
            String itemId = requestedLine.itemId().trim();

            if (!processedItemIds.add(itemId)) {
                throw new BadRequestException(
                        "The same item cannot appear twice in one sale"
                );
            }

            ItemEntity item = itemRepository.findById(
                    itemId,
                    LockModeType.PESSIMISTIC_WRITE
            );

            if (item == null) {
                throw new NotFoundException(
                        "Item not found: " + itemId
                );
            }

            if (item.status != ItemStatus.AVAILABLE) {
                throw new ClientErrorException(
                        "Item " + item.code +
                                " is not available for sale",
                        409
                );
            }

            SaleLineEntity saleLine = new SaleLineEntity();
            saleLine.id = UUID.randomUUID().toString();
            saleLine.item = item;
            saleLine.finalPriceCents =
                    requestedLine.finalPriceCents();

            sale.addLine(saleLine);

            totalCents = Math.addExact(
                    totalCents,
                    requestedLine.finalPriceCents()
            );

            item.status = ItemStatus.SOLD;
            item.updatedAt = now;
        }

        sale.totalCents = totalCents;

        saleRepository.persist(sale);

        return toResponse(sale);
    }

    @Transactional
    public List<SaleResponse> list() {
        return saleRepository
                .listAll(Sort.descending("soldAt"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SaleResponse findById(String id) {
        SaleEntity sale = saleRepository
                .findByIdOptional(id)
                .orElseThrow(
                        () -> new NotFoundException("Sale not found")
                );

        return toResponse(sale);
    }

    @Transactional
    public SaleResponse update(
            String id,
            UpdateSaleRequest request
    ) {
        SaleEntity sale = findEntity(id);
        ensureCompleted(sale);

        Map<String, SaleLineEntity> existingByItem =
                new HashMap<>();

        for (SaleLineEntity line : sale.lines) {
            existingByItem.put(
                    line.item.id,
                    line
            );
        }

        Set<String> requestedIds =
                new HashSet<>();

        long totalCents = 0;

        for (
                SaleLineRequest requestedLine
                : request.lines()
        ) {
            String itemId =
                    requestedLine.itemId().trim();

            if (!requestedIds.add(itemId)) {
                throw new BadRequestException(
                        "The same item cannot appear twice in one sale"
                );
            }

            SaleLineEntity existing =
                    existingByItem.get(itemId);

            if (existing == null) {
                throw conflict(
                        "SALE_ITEMS_IMMUTABLE",
                        "Items cannot be added to or removed from a completed sale."
                );
            }

            existing.finalPriceCents =
                    requestedLine.finalPriceCents();

            totalCents = Math.addExact(
                    totalCents,
                    requestedLine.finalPriceCents()
            );
        }

        if (requestedIds.size()
                != existingByItem.size()) {
            throw conflict(
                    "SALE_ITEMS_IMMUTABLE",
                    "Items cannot be added to or removed from a completed sale."
            );
        }

        sale.paymentMethod =
                request.paymentMethod();
        sale.comments =
                normalizeOptional(request.comments());
        sale.totalCents = totalCents;
        sale.updatedAt = Instant.now();

        return toResponse(sale);
    }

    @Transactional
    public SaleResponse voidSale(
            String id,
            VoidSaleRequest request
    ) {
        SaleEntity sale = findEntity(id);
        ensureCompleted(sale);

        Instant now = Instant.now();

        for (SaleLineEntity line : sale.lines) {
            ItemEntity item = itemRepository.findById(
                    line.item.id,
                    LockModeType.PESSIMISTIC_WRITE
            );

            if (item == null) {
                throw new NotFoundException(
                        "Item not found: " + line.item.id
                );
            }

            item.status = ItemStatus.AVAILABLE;
            item.updatedAt = now;
        }

        sale.status = SaleStatus.VOIDED;
        sale.voidedAt = now;
        sale.updatedAt = now;
        sale.voidedByUserId = token.getSubject();
        sale.voidReason = normalizeOptional(request.reason());

        return toResponse(sale);
    }

    private SaleResponse toResponse(SaleEntity sale) {
        List<SaleLineResponse> lineResponses = sale.lines
                .stream()
                .map(line -> new SaleLineResponse(
                        line.item.id,
                        line.item.code,
                        line.item.name,
                        line.finalPriceCents
                ))
                .toList();

        return new SaleResponse(
                sale.id,
                sale.soldAt,
                sale.paymentMethod,
                sale.totalCents,
                lineResponses.size(),
                lineResponses,
                effectiveStatus(sale),
                sale.comments,
                sale.updatedAt != null
                        ? sale.updatedAt
                        : sale.soldAt,
                sale.voidedAt,
                sale.voidedByUserId,
                sale.voidReason
        );
    }

    private SaleEntity findEntity(String id) {
        return saleRepository
                .findByIdOptional(id)
                .orElseThrow(
                        () -> new NotFoundException(
                                "Sale not found"
                        )
                );
    }

    private SaleStatus effectiveStatus(
            SaleEntity sale
    ) {
        return sale.status == null
                ? SaleStatus.COMPLETED
                : sale.status;
    }

    private void ensureCompleted(
            SaleEntity sale
    ) {
        if (effectiveStatus(sale)
                == SaleStatus.VOIDED) {
            throw conflict(
                    "SALE_ALREADY_VOIDED",
                    "A voided sale cannot be modified again."
            );
        }

        if (sale.status == null) {
            sale.status =
                    SaleStatus.COMPLETED;
        }
    }

    private String normalizeOptional(
            String value
    ) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    private ClientErrorException conflict(
            String code,
            String message
    ) {
        return new ClientErrorException(
                Response.status(Response.Status.CONFLICT)
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
