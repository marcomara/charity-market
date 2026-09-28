package it.charitymarket.settings;

import it.charitymarket.sync.DataVersionService;
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
    DataVersionService dataVersionService;

    @Inject
    JsonWebToken token;

    @ConfigProperty(
            name = "charity.settings.default-currency",
            defaultValue = "EUR"
    )
    String defaultCurrencyCode;

    @ConfigProperty(
            name = "charity.settings.auto-refresh-enabled",
            defaultValue = "true"
    )
    boolean defaultAutoRefreshEnabled;

    @ConfigProperty(
            name = "charity.settings.users-can-customize-auto-refresh",
            defaultValue = "false"
    )
    boolean defaultUsersCanCustomizeAutoRefresh;

    @ConfigProperty(
            name = "charity.settings.default-refresh-interval-seconds",
            defaultValue = "10"
    )
    int defaultRefreshIntervalSeconds;

    @ConfigProperty(
            name = "charity.settings.minimum-refresh-interval-seconds",
            defaultValue = "5"
    )
    int defaultMinimumRefreshIntervalSeconds;

    @ConfigProperty(
            name = "charity.settings.maximum-refresh-interval-seconds",
            defaultValue = "15"
    )
    int defaultMaximumRefreshIntervalSeconds;

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
        settings.autoRefreshEnabled = valueOrCurrent(
                request.autoRefreshEnabled(),
                settings.autoRefreshEnabled
        );
        settings.usersCanCustomizeAutoRefresh = valueOrCurrent(
                request.usersCanCustomizeAutoRefresh(),
                settings.usersCanCustomizeAutoRefresh
        );
        settings.defaultRefreshIntervalSeconds = valueOrCurrent(
                request.defaultRefreshIntervalSeconds(),
                settings.defaultRefreshIntervalSeconds
        );
        settings.minimumRefreshIntervalSeconds = valueOrCurrent(
                request.minimumRefreshIntervalSeconds(),
                settings.minimumRefreshIntervalSeconds
        );
        settings.maximumRefreshIntervalSeconds = valueOrCurrent(
                request.maximumRefreshIntervalSeconds(),
                settings.maximumRefreshIntervalSeconds
        );

        validateRefreshPolicy(settings);
        settings.updatedAt = Instant.now();
        settings.updatedByUserId =
                token.getSubject();

        dataVersionService.markChanged();

        return toResponse(settings);
    }

    private ApplicationSettingsEntity findOrCreate() {
        ApplicationSettingsEntity settings = repository
                .findByIdOptional(
                        ApplicationSettingsEntity.GLOBAL_ID
                )
                .orElseGet(() -> {
                    ApplicationSettingsEntity createdSettings =
                            new ApplicationSettingsEntity();

                    createdSettings.id =
                            ApplicationSettingsEntity.GLOBAL_ID;

                    createdSettings.currencyCode =
                            normalizeCurrencyCode(
                                    defaultCurrencyCode
                            );

                    createdSettings.autoRefreshEnabled =
                            defaultAutoRefreshEnabled;
                    createdSettings.usersCanCustomizeAutoRefresh =
                            defaultUsersCanCustomizeAutoRefresh;
                    createdSettings.defaultRefreshIntervalSeconds =
                            defaultRefreshIntervalSeconds;
                    createdSettings.minimumRefreshIntervalSeconds =
                            defaultMinimumRefreshIntervalSeconds;
                    createdSettings.maximumRefreshIntervalSeconds =
                            defaultMaximumRefreshIntervalSeconds;

                    createdSettings.updatedAt = Instant.now();
                    createdSettings.updatedByUserId = null;

                    repository.persist(createdSettings);

                    return createdSettings;
                });

        applyMissingRefreshDefaults(settings);
        validateRefreshPolicy(settings);
        return settings;
    }

    private void applyMissingRefreshDefaults(
            ApplicationSettingsEntity settings
    ) {
        if (settings.autoRefreshEnabled == null) {
            settings.autoRefreshEnabled = defaultAutoRefreshEnabled;
        }
        if (settings.usersCanCustomizeAutoRefresh == null) {
            settings.usersCanCustomizeAutoRefresh =
                    defaultUsersCanCustomizeAutoRefresh;
        }
        if (settings.defaultRefreshIntervalSeconds == null) {
            settings.defaultRefreshIntervalSeconds =
                    defaultRefreshIntervalSeconds;
        }
        if (settings.minimumRefreshIntervalSeconds == null) {
            settings.minimumRefreshIntervalSeconds =
                    defaultMinimumRefreshIntervalSeconds;
        }
        if (settings.maximumRefreshIntervalSeconds == null) {
            settings.maximumRefreshIntervalSeconds =
                    defaultMaximumRefreshIntervalSeconds;
        }
    }

    private void validateRefreshPolicy(
            ApplicationSettingsEntity settings
    ) {
        int minimum = settings.minimumRefreshIntervalSeconds;
        int maximum = settings.maximumRefreshIntervalSeconds;
        int defaultInterval = settings.defaultRefreshIntervalSeconds;

        if (minimum < 5 || maximum > 3600) {
            throw new BadRequestException(
                    "Refresh intervals must be between 5 and 3600 seconds"
            );
        }
        if (minimum > maximum) {
            throw new BadRequestException(
                    "The minimum refresh interval cannot exceed the maximum"
            );
        }
        if (defaultInterval < minimum || defaultInterval > maximum) {
            throw new BadRequestException(
                    "The default refresh interval must be within the configured range"
            );
        }
    }

    private <T> T valueOrCurrent(T requested, T current) {
        return requested == null ? current : requested;
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
                settings.autoRefreshEnabled,
                settings.usersCanCustomizeAutoRefresh,
                settings.defaultRefreshIntervalSeconds,
                settings.minimumRefreshIntervalSeconds,
                settings.maximumRefreshIntervalSeconds,
                settings.updatedAt,
                settings.updatedByUserId
        );
    }
}
