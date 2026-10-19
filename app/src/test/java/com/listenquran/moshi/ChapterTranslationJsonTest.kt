package com.listenquran.moshi

import com.listenquran.data.remote.dto.ChapterTranslationResponse
import com.listenquran.util.TranslationSanitizer
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Test

class ChapterTranslationJsonTest {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(ChapterTranslationResponse::class.java)

    @Test
    fun `deserializes real Quran com translation response correctly`() {
        val json = """
            {
              "translations": [
                {
                  "resource_id": 20,
                  "text": "In the name of Allāh,<sup foot_note=195932>1</sup> the Entirely Merciful, the Especially Merciful.<sup foot_note=195931>2</sup>"
                },
                {
                  "resource_id": 20,
                  "text": "[All] praise is [due] to Allāh, Lord<sup foot_note=195933>1</sup> of the worlds -"
                }
              ],
              "meta": {
                "translation_name": "Saheeh International",
                "author_name": "Saheeh International",
                "filters": {
                  "resource_id": 20,
                  "chapter_number": "1"
                }
              }
            }
        """.trimIndent()

        val parsed = adapter.fromJson(json)
        assertNotNull(parsed)
        assertEquals(2, parsed!!.translations.size)
        assertEquals("Saheeh International", parsed.meta?.translationName)

        val sanitized0 = TranslationSanitizer.sanitize(parsed.translations[0].text)
        assertEquals("In the name of Allāh, the Entirely Merciful, the Especially Merciful.", sanitized0)

        val sanitized1 = TranslationSanitizer.sanitize(parsed.translations[1].text)
        assertEquals("[All] praise is [due] to Allāh, Lord of the worlds -", sanitized1)
    }

    @Test
    fun `handles missing meta and empty translations list safely`() {
        val json = """
            {
              "translations": []
            }
        """.trimIndent()

        val parsed = adapter.fromJson(json)
        assertNotNull(parsed)
        assertTrue(parsed!!.translations.isEmpty())
        assertNull(parsed.meta)
    }
}
