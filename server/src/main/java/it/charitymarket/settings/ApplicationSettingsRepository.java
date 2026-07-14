package it.charitymarket.settings;


import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ApplicationSettingsRepository implements PanacheRepositoryBase<ApplicationSettingsEntity, String> {
}
