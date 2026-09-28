package it.charitymarket.android.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun defaultCurrencyCode(): String = runCatching {
    Currency.getInstance(Locale.getDefault()).currencyCode
}.getOrDefault("EUR")

fun supportedCurrencyCodes(): List<String> = listOf(
    "EUR", "USD", "GBP", "CHF", "CAD", "AUD", "JPY"
)

fun formatMoney(cents: Long, currencyCode: String): String {
    val amount = BigDecimal(cents).movePointLeft(2)
    val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
    runCatching {
        formatter.currency = Currency.getInstance(currencyCode)
    }
    return formatter.format(amount)
}

fun parseMoneyToCents(rawValue: String): Long? {
    val value = rawValue.trim()
    if (value.isBlank()) return null

    val filtered = value.filter {
        it.isDigit() || it == ',' || it == '.' || it == '-'
    }
    if (filtered.isBlank() || filtered == "-") return null

    val separator = maxOf(
        filtered.lastIndexOf('.'),
        filtered.lastIndexOf(',')
    )
    val normalized = if (separator >= 0) {
        val integerPart = filtered.substring(0, separator)
            .filter { it.isDigit() || it == '-' }
        val decimalPart = filtered.substring(separator + 1)
            .filter { it.isDigit() }
        "$integerPart.$decimalPart"
    } else {
        filtered.filter { it.isDigit() || it == '-' }
    }

    return runCatching {
        val amount = BigDecimal(normalized)
            .setScale(2, RoundingMode.HALF_UP)
        if (amount.signum() < 0) return null
        amount.movePointRight(2)
            .setScale(0, RoundingMode.UNNECESSARY)
            .longValueExact()
    }.getOrNull()
}

fun centsToInput(cents: Long): String = BigDecimal(cents)
    .movePointLeft(2)
    .setScale(2)
    .toPlainString()

fun formatInstant(value: String?): String {
    if (value.isNullOrBlank()) return "Never"

    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX"
    )
    val parsed: Date = formats.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }.parse(value)
        }.getOrNull()
    } ?: return value

    return java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM,
        java.text.DateFormat.SHORT,
        Locale.getDefault()
    ).format(parsed)
}

fun isEmailValid(value: String): Boolean =
    value.isBlank() ||
            value.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
