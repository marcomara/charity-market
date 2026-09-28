package it.charitymarket.sale;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SaleLineRepository implements PanacheRepositoryBase<SaleLineEntity, String> {
    public boolean referencesItem(String itemId) {
        return count("item.id", itemId) > 0;
    }
}
