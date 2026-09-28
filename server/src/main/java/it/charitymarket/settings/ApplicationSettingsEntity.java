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

    @Column(name = "auto_refresh_enabled")
    public Boolean autoRefreshEnabled;

    @Column(name = "users_can_customize_auto_refresh")
    public Boolean usersCanCustomizeAutoRefresh;

    @Column(name = "default_refresh_interval_seconds")
    public Integer defaultRefreshIntervalSeconds;

    @Column(name = "minimum_refresh_interval_seconds")
    public Integer minimumRefreshIntervalSeconds;

    @Column(name = "maximum_refresh_interval_seconds")
    public Integer maximumRefreshIntervalSeconds;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(
            name = "updated_by_user_id",
            length = 36
    )
    public String updatedByUserId;
}
