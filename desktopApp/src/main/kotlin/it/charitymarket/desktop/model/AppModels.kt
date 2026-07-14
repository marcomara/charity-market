package it.charitymarket.desktop.model

import it.charitymarket.desktop.api.UserRole
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

enum class AppDestination(
    val title: String
) {
    DASHBOARD("Dashboard"),
    USERS("Users"),
    DONORS("Donors"),
    ITEMS("Items"),
    SALES("Transactions"),
    SETTINGS("Settings")
}

object RoleRules {
    private val businessReaders = setOf(
        UserRole.SYSTEM_ADMINISTRATOR,
        UserRole.MARKET_MANAGER,
        UserRole.INVENTORY_MANAGER,
        UserRole.SELLER
    )

    private val inventoryEditors = setOf(
        UserRole.SYSTEM_ADMINISTRATOR,
        UserRole.MARKET_MANAGER,
        UserRole.INVENTORY_MANAGER
    )

    private val sellers = setOf(
        UserRole.SYSTEM_ADMINISTRATOR,
        UserRole.MARKET_MANAGER,
        UserRole.SELLER
    )

    fun destinationsFor(
        roles: Set<UserRole>
    ): List<AppDestination> {
        val destinations = mutableListOf(
            AppDestination.DASHBOARD
        )

        if (UserRole.SYSTEM_ADMINISTRATOR in roles) {
            destinations += AppDestination.USERS
        }

        if (roles.any { it in businessReaders }) {
            destinations += AppDestination.DONORS
            destinations += AppDestination.ITEMS
        }

        if (roles.any { it in sellers }) {
            destinations += AppDestination.SALES
        }

        if (UserRole.SYSTEM_ADMINISTRATOR in roles) {
            destinations += AppDestination.SETTINGS
        }

        return destinations
    }

    fun canManageUsers(roles: Set<UserRole>): Boolean =
        UserRole.SYSTEM_ADMINISTRATOR in roles

    fun canAccessDonors(roles: Set<UserRole>): Boolean =
        roles.any { it in businessReaders }

    fun canAccessItems(roles: Set<UserRole>): Boolean =
        roles.any { it in businessReaders }

    fun canEditDonors(
        roles: Set<UserRole>
    ): Boolean =
        roles.any { it in inventoryEditors }

    fun canDeleteDonors(roles: Set<UserRole>): Boolean =
        UserRole.SYSTEM_ADMINISTRATOR in roles

    fun canEditItems(
        roles: Set<UserRole>
    ): Boolean =
        roles.any { it in inventoryEditors }

    fun canDeleteItems(roles: Set<UserRole>): Boolean =
        UserRole.SYSTEM_ADMINISTRATOR in roles

    fun canManageSales(roles: Set<UserRole>): Boolean =
        UserRole.SYSTEM_ADMINISTRATOR in roles

    fun canAccessSales(roles: Set<UserRole>): Boolean =
        roles.any { it in sellers }

    fun canShutDownLocalServer(roles: Set<UserRole>): Boolean =
        UserRole.SYSTEM_ADMINISTRATOR in roles
}

fun UserRole.displayName(): String =
    name
        .lowercase(Locale.ROOT)
        .split("_")
        .joinToString(" ") { word ->
            word.replaceFirstChar {
                if (it.isLowerCase()) {
                    it.titlecase(Locale.ROOT)
                } else {
                    it.toString()
                }
            }
        }

fun formatInstant(
    value: String?
): String {
    if (value.isNullOrBlank()) {
        return "Never"
    }

    return runCatching {
        val instant = Instant.parse(value)
        DateTimeFormatter
            .ofLocalizedDateTime(
                FormatStyle.MEDIUM,
                FormatStyle.SHORT
            )
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }.getOrElse {
        value
    }
}

fun formatMoney(
    cents: Long,
    currencyCode: String = defaultCurrencyCode()
): String {
    val amount = BigDecimal(cents)
        .movePointLeft(2)
        .setScale(2, RoundingMode.UNNECESSARY)

    val formatter =
        NumberFormat
            .getCurrencyInstance(Locale.getDefault())

    runCatching {
        formatter.currency =
            Currency.getInstance(currencyCode)
    }

    return formatter.format(amount)
}

fun defaultCurrencyCode(): String =
    runCatching {
        Currency
            .getInstance(Locale.getDefault())
            .currencyCode
    }.getOrDefault("EUR")

fun supportedCurrencyCodes(): List<String> =
    listOf(
        "EUR",
        "USD",
        "GBP",
        "CHF",
        "CAD",
        "AUD",
        "JPY"
    )

fun parseMoneyToCents(
    rawValue: String
): Long? {
    val value = rawValue.trim()

    if (value.isBlank()) {
        return null
    }

    val filtered = value.filter {
        it.isDigit() ||
                it == ',' ||
                it == '.' ||
                it == '-'
    }

    if (filtered.isBlank() || filtered == "-") {
        return null
    }

    val decimalSeparatorIndex = maxOf(
        filtered.lastIndexOf('.'),
        filtered.lastIndexOf(',')
    )

    val normalized = if (decimalSeparatorIndex >= 0) {
        val integerPart = filtered
            .substring(0, decimalSeparatorIndex)
            .filter { it.isDigit() || it == '-' }
        val decimalPart = filtered
            .substring(decimalSeparatorIndex + 1)
            .filter { it.isDigit() }

        "$integerPart.$decimalPart"
    } else {
        filtered.filter { it.isDigit() || it == '-' }
    }

    return runCatching {
        val amount = BigDecimal(normalized)
            .setScale(2, RoundingMode.HALF_UP)

        if (amount.signum() < 0) {
            return null
        }

        amount
            .movePointRight(2)
            .setScale(0, RoundingMode.UNNECESSARY)
            .longValueExact()
    }.getOrNull()
}
