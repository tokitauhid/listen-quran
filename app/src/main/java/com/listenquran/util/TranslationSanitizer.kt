package com.listenquran.util

import java.util.regex.Pattern

object TranslationSanitizer {
    private val FOOTNOTE_PATTERN = Pattern.compile("<sup[^>]*>.*?</sup>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    private val HTML_TAG_PATTERN = Pattern.compile("<[^>]+>")
    private val MULTI_SPACE_PATTERN = Pattern.compile("\\s+")
    private val SPACE_BEFORE_PUNCTUATION_PATTERN = Pattern.compile("\\s+([,.:;?!])")
    private val NUMERIC_ENTITY_PATTERN = Pattern.compile("&#(\\d+);")

    fun sanitize(rawText: String?): String {
        if (rawText.isNullOrBlank()) return ""

        var text = rawText

        // 1. Remove footnotes completely (e.g. <sup foot_note=195932>1</sup>)
        text = FOOTNOTE_PATTERN.matcher(text).replaceAll("")

        // 2. Remove any remaining HTML tags (e.g. <b>, <i>, <p>, etc.)
        text = HTML_TAG_PATTERN.matcher(text).replaceAll("")
        // Clean any unclosed tag remnants
        if (text.contains("<")) {
            text = text.replace("<", "")
        }

        // 3. Decode common named HTML entities
        text = text.replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")

        // 4. Decode decimal numeric entities (e.g. &#39;)
        val matcher = NUMERIC_ENTITY_PATTERN.matcher(text)
        val sb = StringBuffer()
        while (matcher.find()) {
            try {
                val codePoint = matcher.group(1)?.toInt() ?: 0
                matcher.appendReplacement(sb, String(Character.toChars(codePoint)))
            } catch (_: Exception) {
                matcher.appendReplacement(sb, "")
            }
        }
        matcher.appendTail(sb)
        text = sb.toString()

        // 5. Clean up spaces before punctuation (e.g. "Allāh ," -> "Allāh,")
        text = SPACE_BEFORE_PUNCTUATION_PATTERN.matcher(text).replaceAll("$1")

        // 6. Collapse multiple whitespace and trim
        text = MULTI_SPACE_PATTERN.matcher(text).replaceAll(" ").trim()

        return text
    }
}
