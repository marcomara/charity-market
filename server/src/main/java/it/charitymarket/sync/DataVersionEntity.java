package it.charitymarket.sync;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "application_data_version")
public class DataVersionEntity extends PanacheEntityBase {
    public static final String GLOBAL_ID = "global";

    @Id
    @Column(length = 50, nullable = false)
    public String id;

    @Column(name = "data_version", nullable = false)
    public long version;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
