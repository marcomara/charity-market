package it.charitymarket.sync;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.time.Instant;

@ApplicationScoped
public class DataVersionService {
    @Inject
    DataVersionRepository repository;

    @Transactional
    void initialize(@Observes StartupEvent event) {
        findOrCreate();
    }

    @Transactional
    public DataVersionResponse get() {
        return toResponse(findOrCreate());
    }

    /**
     * Joins the caller's transaction, so a failed business mutation cannot publish a version
     * that does not correspond to committed data.
     */
    @Transactional
    public void markChanged() {
        DataVersionEntity status = repository.findById(
                DataVersionEntity.GLOBAL_ID,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (status == null) {
            status = create();
        }

        status.version = Math.addExact(status.version, 1L);
        status.updatedAt = Instant.now();
    }

    private DataVersionEntity findOrCreate() {
        DataVersionEntity status = repository.findById(DataVersionEntity.GLOBAL_ID);
        return status != null ? status : create();
    }

    private DataVersionEntity create() {
        DataVersionEntity status = new DataVersionEntity();
        status.id = DataVersionEntity.GLOBAL_ID;
        status.version = 0L;
        status.updatedAt = Instant.now();
        repository.persist(status);
        return status;
    }

    private DataVersionResponse toResponse(DataVersionEntity status) {
        return new DataVersionResponse(status.version, status.updatedAt);
    }
}
