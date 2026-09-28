package it.charitymarket.desktop.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

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
