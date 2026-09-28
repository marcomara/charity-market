package it.charitymarket.shared.config

import io.ktor.http.DEFAULT_PORT
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol

const val DEFAULT_SERVER_PORT: Int = 8088

fun normalizeServerBaseUrl(rawBaseUrl: String): String {
    val value = normalizedServerAddress(rawBaseUrl)
    return if (value.hasHttpProtocol()) {
        validatedServerUrlBuilder(value).baseUrl()
    } else {
        serverConnectionCandidates(value).first()
    }
}

fun serverConnectionCandidates(rawBaseUrl: String): List<String> {
    val value = normalizedServerAddress(rawBaseUrl)
    return if (value.hasHttpProtocol()) {
        serverConnectionCandidatesForProtocolUrl(value)
    } else {
        serverConnectionCandidatesForBareAddress(value)
    }
}

private fun serverConnectionCandidatesForProtocolUrl(
    value: String
): List<String> {
    val builder = validatedServerUrlBuilder(value)
    val primaryBaseUrl = builder.baseUrl()
    val primaryPort = builder.port
    val protocolDefaultPort = when (builder.protocol) {
        URLProtocol.HTTP -> 80
        URLProtocol.HTTPS -> 443
        else -> DEFAULT_SERVER_PORT
    }

    if (
        primaryPort != DEFAULT_PORT &&
        primaryPort != protocolDefaultPort
    ) {
        return listOf(primaryBaseUrl)
    }

    builder.port = DEFAULT_SERVER_PORT
    return listOf(
        primaryBaseUrl,
        builder.baseUrl()
    ).distinct()
}

private fun serverConnectionCandidatesForBareAddress(
    value: String
): List<String> {
    require(!value.contains("://")) {
        "The server address must use http:// or https://"
    }

    val secureBuilder = validatedServerUrlBuilder("https://$value")
    val insecureBuilder = validatedServerUrlBuilder("http://$value")

    if (secureBuilder.port != DEFAULT_PORT) {
        return listOf(
            secureBuilder.baseUrl(),
            insecureBuilder.baseUrl()
        ).distinct()
    }

    insecureBuilder.port = DEFAULT_SERVER_PORT
    return listOf(
        secureBuilder.baseUrl(),
        validatedServerUrlBuilder("http://$value").baseUrl(),
        insecureBuilder.baseUrl()
    ).distinct()
}

private fun normalizedServerAddress(rawBaseUrl: String): String {
    val value = rawBaseUrl.trim().removeSuffix("/")
    require(value.isNotBlank()) {
        "The server address is required."
    }
    return value
}

private fun validatedServerUrlBuilder(value: String): URLBuilder {
    val builder = runCatching { URLBuilder(value) }
        .getOrElse {
            throw IllegalArgumentException(
                "The server address is invalid.",
                it
            )
        }

    require(
        builder.protocolOrNull == URLProtocol.HTTP ||
                builder.protocolOrNull == URLProtocol.HTTPS
    ) {
        "The server address must start with http:// or https://"
    }
    require(builder.host.isNotBlank()) {
        "The server address is invalid."
    }

    return builder
}

private fun String.hasHttpProtocol(): Boolean =
    startsWith("http://", ignoreCase = true) ||
            startsWith("https://", ignoreCase = true)

private fun URLBuilder.baseUrl(): String =
    buildString().removeSuffix("/")
