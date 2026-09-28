package it.charitymarket.donor;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "donors")
public class DonorEntity extends PanacheEntityBase {

    @Id
    @Column(length = 36, nullable = false)
    public String id;

    @Column(length = 200, nullable = false)
    public String name;

    @Column(length = 320)
    public String email;

    @Column(length = 40)
    public String phone;

    @Column(length = 4000)
    public String comments;

    @Column(name = "updated_at")
    public Instant updatedAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

}
