package it.charitymarket.shared.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServerDefaultsTest {
    @Test
    fun missingPortUsesProtocolDefaultInNormalizedUrl() {
        assertEquals(
            "http://charity-server",
            normalizeServerBaseUrl("http://charity-server")
        )
        assertEquals(
            "https://example.org/charity",
            normalizeServerBaseUrl("https://example.org/charity/")
        )
    }

    @Test
    fun missingPortCreatesProtocolDefaultAndCharityMarketCandidates() {
        assertEquals(
            listOf(
                "http://charity-server",
                "http://charity-server:8088"
            ),
            serverConnectionCandidates("http://charity-server")
        )
        assertEquals(
            listOf(
                "https://example.org/charity",
                "https://example.org:8088/charity"
            ),
            serverConnectionCandidates("https://example.org/charity/")
        )
    }

    @Test
    fun explicitPortIsPreserved() {
        assertEquals(
            "http://charity-server:9123",
            normalizeServerBaseUrl("http://charity-server:9123")
        )
        assertEquals(
            listOf("http://charity-server:9123"),
            serverConnectionCandidates("http://charity-server:9123")
        )
    }

    @Test
    fun explicitProtocolDefaultPortCanFallbackToCharityMarketDefault() {
        assertEquals(
            listOf(
                "http://charity-server",
                "http://charity-server:8088"
            ),
            serverConnectionCandidates("http://charity-server:80")
        )
        assertEquals(
            listOf(
                "https://charity-server",
                "https://charity-server:8088"
            ),
            serverConnectionCandidates("https://charity-server:443")
        )
    }

    @Test
    fun bareAddressCreatesSecureFirstDefaultCandidates() {
        assertEquals(
            "https://cms.example.it",
            normalizeServerBaseUrl("cms.example.it")
        )
        assertEquals(
            listOf(
                "https://cms.example.it",
                "http://cms.example.it",
                "http://cms.example.it:8088"
            ),
            serverConnectionCandidates("cms.example.it")
        )
        assertEquals(
            listOf(
                "https://192.168.1.25",
                "http://192.168.1.25",
                "http://192.168.1.25:8088"
            ),
            serverConnectionCandidates("192.168.1.25")
        )
    }

    @Test
    fun bareAddressWithExplicitPortTriesSecureProtocolFirst() {
        assertEquals(
            listOf(
                "https://cms.example.it:9443",
                "http://cms.example.it:9443"
            ),
            serverConnectionCandidates("cms.example.it:9443")
        )
    }

    @Test
    fun invalidAddressIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            normalizeServerBaseUrl("ftp://charity-server")
        }
    }
}
