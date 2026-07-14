package it.charitymarket.sale;


import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import it.charitymarket.item.ItemEntity;
import jakarta.persistence.*;

@Entity
@Table(
        name = "sale_lines",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_sale_lines_item",
                        columnNames = "item_id"
                )
        }
)
public class SaleLineEntity extends PanacheEntityBase {

    @Id
    @Column(length = 36, nullable = false)
    public String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    public SaleEntity sale;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    public ItemEntity item;

    @Column(name = "final_price_cents", nullable = false)
    public long finalPriceCents;
}
