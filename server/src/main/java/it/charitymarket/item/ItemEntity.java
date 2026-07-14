package it.charitymarket.item;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import it.charitymarket.donor.DonorEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "items",
        uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_items_code",
                columnNames = "code"
        )
        }


)
public class ItemEntity extends PanacheEntityBase {

    @Id
    @Column(length = 36, nullable = false)
    public String id;

    @Column(length = 50, nullable = false)
    public String code;

    @Column(length = 200, nullable = false)
    public String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donor_id", nullable = false)
    public DonorEntity donor;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    public ItemCondition condition;

    @Column(name = "suggested_price_cents", nullable = false)
    public long suggestedPriceCents;

    @Column(length = 4000)
    public String comments;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    public ItemStatus status;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
