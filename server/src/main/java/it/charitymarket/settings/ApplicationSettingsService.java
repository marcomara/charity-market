package it.charitymarket.settings;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Instant;
import java.util.Currency;
import java.util.Locale;


@ApplicationScoped
public class ApplicationSettingsService {
    @Inject
    ApplicationSettingsRepository repository;

    @Inject
    JsonWebToken token;

    @ConfigProperty(
            name = "charity.settings.default-currency",
            defaultValue = "EUR"
    )
    String defaultCurrencyCode;

    @Transactional
    public ApplicationSettingsResponse get() {
        return toResponse(findOrCreate());
    }

    @Transactional
    public ApplicationSettingsResponse update(
            UpdateApplicationSettingsRequest request
    ) {
        String currencyCode =
                normalizeCurrencyCode(
                        request.currencyCode()
                );

        ApplicationSettingsEntity settings =
                findOrCreate();

        settings.currencyCode = currencyCode;
        settings.updatedAt = Instant.now();
        settings.updatedByUserId =
                token.getSubject();

        return toResponse(settings);
    }

    private ApplicationSettingsEntity findOrCreate() {
        return repository
                .findByIdOptional(
                        ApplicationSettingsEntity.GLOBAL_ID
                )
                .orElseGet(() -> {
                    ApplicationSettingsEntity settings =
                            new ApplicationSettingsEntity();

                    settings.id =
                            ApplicationSettingsEntity.GLOBAL_ID;

                    settings.currencyCode =
                            normalizeCurrencyCode(
                                    defaultCurrencyCode
                            );

                    settings.updatedAt = Instant.now();
                    settings.updatedByUserId = null;

                    repository.persist(settings);

                    return settings;
                });
    }

    private String normalizeCurrencyCode(
            String value
    ) {
        String normalized = value
                .trim()
                .toUpperCase(Locale.ROOT);

        try {
            Currency.getInstance(normalized);
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    "Unsupported currency code: "
                            + normalized
            );
        }
    }

    private ApplicationSettingsResponse toResponse(
            ApplicationSettingsEntity settings
    ) {
        return new ApplicationSettingsResponse(
                settings.currencyCode,
                settings.updatedAt,
                settings.updatedByUserId
        );
    }
}
