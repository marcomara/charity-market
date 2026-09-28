package it.charitymarket.sale;


import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SaleRepository implements PanacheRepositoryBase<SaleEntity, String> {
}
