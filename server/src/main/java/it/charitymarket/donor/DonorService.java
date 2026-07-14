package it.charitymarket.donor;


import io.quarkus.panache.common.Sort;
import it.charitymarket.api.ApiErrorResponse;
import it.charitymarket.item.ItemRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DonorService {
    @Inject
    DonorRepository donorRepository;

    @Inject
    ItemRepository itemRepository;

    public List<DonorResponse> list() {
        return donorRepository
                .listAll(Sort.by("name"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DonorResponse create(CreateDonorRequest request) {
        Instant now = Instant.now();

        DonorEntity donor = new DonorEntity();

        donor.id = UUID.randomUUID().toString();
        donor.name = request.name().trim();
        donor.email = normalizeOptional(request.email());
        donor.phone = normalizeOptional(request.phone());
        donor.comments = normalizeOptional(request.comments());
        donor.createdAt = now;
        donor.updatedAt = now;

        donorRepository.persist(donor);

        return toResponse(donor);

    }

    @Transactional
    public DonorResponse update(
            String id,
            UpdateDonorRequest request) {
        DonorEntity donor = findEntity(id);
        donor.name = request.name().trim();
        donor.email =
                normalizeOptional(request.email());
        donor.phone =
                normalizeOptional(request.phone());
        donor.comments =
                normalizeOptional(request.comments());
        donor.updatedAt = Instant.now();

        return toResponse(donor);

    }

    @Transactional
    public void delete(String id) {
        DonorEntity donor = findEntity(id);

        long itemCount =
                itemRepository.count(
                        "donor.id",
                        id
                );

        if (itemCount > 0) {
            throw new ClientErrorException(
                    Response.status(
                                    Response.Status.CONFLICT
                            )
                            .type(
                                    MediaType
                                            .APPLICATION_JSON_TYPE
                            )
                            .entity(
                                    new ApiErrorResponse(
                                            "DONOR_HAS_ITEMS",
                                            "This donor cannot be deleted while items are still assigned to them. Remove eligible unsold items first."
                                    )
                            )
                            .build()
            );
        }

        donorRepository.delete(donor);
    }

    private DonorEntity findEntity(String id) {
        return donorRepository
                .findByIdOptional(id)
                .orElseThrow(
                        () -> new NotFoundException(
                                "Donor not found"
                        )
                );
    }

    private DonorResponse toResponse(DonorEntity donor){

        return new DonorResponse(
                donor.id,
                donor.name,
                donor.email,
                donor.phone,
                donor.comments,
                donor.createdAt,
                donor.updatedAt != null ? donor.updatedAt : donor.createdAt
        );
    }

    private String normalizeOptional(String value){
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
