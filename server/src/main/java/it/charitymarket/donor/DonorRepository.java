package it.charitymarket.donor;


import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DonorRepository implements PanacheRepositoryBase<DonorEntity, String> {
}
