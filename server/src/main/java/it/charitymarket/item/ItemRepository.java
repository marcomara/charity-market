package it.charitymarket.item;



import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class ItemRepository implements PanacheRepositoryBase<ItemEntity, String> {
    public Optional<ItemEntity> findByCode(String code) {
        return find("code", code).firstResultOptional();
    }
}
