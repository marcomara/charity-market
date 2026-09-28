package it.charitymarket.settings;

import it.charitymarket.sync.DataVersionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;

@ApplicationScoped
public class DatabaseResetExecutor {
    @Inject
    EntityManager entityManager;

    @Inject
    ApplicationSettingsService applicationSettingsService;

    @Inject
    DataVersionService dataVersionService;

    @Transactional
    public Instant resetApplicationData(
            String administratorUserId
    ) {
        execute("delete from sale_lines");
        execute("delete from sales");
        execute("delete from items");
        execute("delete from donors");
        execute(
                "delete from app_user_roles where user_id <> :administratorUserId",
                administratorUserId
        );
        execute(
                "delete from app_users where id <> :administratorUserId",
                administratorUserId
        );
        execute("delete from application_settings");
        execute("delete from application_data_version");

        applicationSettingsService.get();
        dataVersionService.markChanged();

        return Instant.now();
    }

    private int execute(String sql) {
        return entityManager
                .createNativeQuery(sql)
                .executeUpdate();
    }

    private int execute(
            String sql,
            String administratorUserId
    ) {
        return entityManager
                .createNativeQuery(sql)
                .setParameter(
                        "administratorUserId",
                        administratorUserId
                )
                .executeUpdate();
    }
}
