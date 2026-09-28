package it.charitymarket.shared.i18n

import it.charitymarket.shared.preferences.normalizeLanguageTag
import kotlin.test.Test
import kotlin.test.assertEquals

class AppLocalizationTest {
    @Test
    fun parsesSimplePropertyFile() {
        val values = parseProperties(
            """
            # comment
            language.name=English \(United States\)
            action.save=Save
            message=Line one\nLine two
            escaped\=key=value\:with\:colons
            """.trimIndent()
        )

        assertEquals(
            "English (United States)",
            values["language.name"]
        )
        assertEquals("Save", values["action.save"])
        assertEquals("Line one\nLine two", values["message"])
        assertEquals("value:with:colons", values["escaped=key"])
    }

    @Test
    fun languageTagsUseLowercaseUnderscoreForm() {
        assertEquals("en_us", normalizeLanguageTag("en-US"))
        assertEquals("it_it", normalizeLanguageTag("IT_it"))
        assertEquals("en_us", normalizeLanguageTag(""))
    }
}
