package it.charitymarket.sync;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DataVersionRepository implements PanacheRepositoryBase<DataVersionEntity, String> {
}
