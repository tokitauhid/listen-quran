package com.listenquran.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationSanitizerTest {

    @Test
    fun `strips single footnote correctly`() {
        val input = "In the name of Allāh,<sup foot_note=195932>1</sup> the Entirely Merciful"
        val expected = "In the name of Allāh, the Entirely Merciful"
        assertEquals(expected, TranslationSanitizer.sanitize(input))
    }

    @Test
    fun `strips multiple footnotes in single verse`() {
        val input = "In the name of Allāh,<sup foot_note=195932>1</sup> the Entirely Merciful, the Especially Merciful.<sup foot_note=195931>2</sup>"
        val expected = "In the name of Allāh, the Entirely Merciful, the Especially Merciful."
        assertEquals(expected, TranslationSanitizer.sanitize(input))
    }

    @Test
    fun `strips general HTML tags`() {
        val input = "<p>This is <b>bold</b> and <i>italic</i> text.</p>"
        val expected = "This is bold and italic text."
        assertEquals(expected, TranslationSanitizer.sanitize(input))
    }

    @Test
    fun `decodes named and numeric HTML entities`() {
        val input = "He said, &quot;We believe in Allāh&#39;s signs &amp; guidance.&quot;"
        val expected = "He said, \"We believe in Allāh's signs & guidance.\""
        assertEquals(expected, TranslationSanitizer.sanitize(input))
    }

    @Test
    fun `fixes space before punctuation resulting from footnote removal`() {
        val input = "Lord <sup foot_note=123>1</sup> of the worlds - Sovereign <sup foot_note=456>1</sup> ."
        val expected = "Lord of the worlds - Sovereign."
        assertEquals(expected, TranslationSanitizer.sanitize(input))
    }

    @Test
    fun `handles null and empty input safely`() {
        assertEquals("", TranslationSanitizer.sanitize(null))
        assertEquals("", TranslationSanitizer.sanitize(""))
        assertEquals("", TranslationSanitizer.sanitize("   "))
    }

    @Test
    fun `handles malformed entities and unclosed tags gracefully`() {
        val input = "Normal text <sup foot_note=123 unclosed tag &#99999999999999; and &invalid;"
        val result = TranslationSanitizer.sanitize(input)
        // Should not crash and should produce non-null trimmed string
        assert(!result.contains("<sup"))
    }
}
