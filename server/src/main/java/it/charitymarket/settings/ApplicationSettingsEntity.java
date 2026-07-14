package it.charitymarket.settings;


import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "application_settings")
public class ApplicationSettingsEntity extends PanacheEntityBase {
    public static final String GLOBAL_ID = "global";

    @Id
    @Column(length = 50, nullable = false)
    public String id;

    @Column(
            name = "currency_code",
            length = 3,
            nullable = false
    )
    public String currencyCode;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(
            name = "updated_by_user_id",
            length = 36
    )
    public String updatedByUserId;
}
