package it.charitymarket.sale;


import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales")
public class SaleEntity {

    @Id
    @Column(length = 36, nullable = false)
    public String id;

    @Column(name = "sold_at", nullable = false)
    public Instant soldAt;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "payment_method",
            length = 30,
            nullable = false
    )
    public PaymentMethod paymentMethod;

    @Column(name = "total_cents", nullable = false)
    public long totalCents;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    public SaleStatus status;

    @Column(length = 4000)
    public String comments;

    @Column(name = "updated_at")
    public Instant updatedAt;

    @Column(name = "voided_at")
    public Instant voidedAt;

    @Column(
            name = "voided_by_user_id",
            length = 36
    )
    public String voidedByUserId;

    @Column(
            name = "void_reason",
            length = 4000
    )
    public String voidReason;

    @OneToMany(
            mappedBy = "sale",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    public List<SaleLineEntity> lines = new ArrayList<>();

    public void addLine(SaleLineEntity line) {
        lines.add(line);
        line.sale = this;
    }
}
