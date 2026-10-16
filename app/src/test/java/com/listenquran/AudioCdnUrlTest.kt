package com.listenquran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import org.json.JSONArray

class AudioCdnUrlTest {

    @Test
    fun testQuranicAudioUrlConstruction() {
        val server = "https://download.quranicaudio.com/qdc/mishari_al_afasy/murattal/"
        val chapterId = 1
        val url = if (server.contains("mp3quran.net")) {
            server + String.format("%03d.mp3", chapterId)
        } else {
            server + "$chapterId.mp3"
        }
        assertEquals("https://download.quranicaudio.com/qdc/mishari_al_afasy/murattal/1.mp3", url)
    }

    @Test
    fun testMp3QuranUrlConstruction() {
        val server = "https://cdn.mp3quran.net/audio/zain-ahmad/r1/"
        val chapterId = 1
        val url = if (server.contains("mp3quran.net")) {
            server + String.format("%03d.mp3", chapterId)
        } else {
            server + "$chapterId.mp3"
        }
        assertEquals("https://cdn.mp3quran.net/audio/zain-ahmad/r1/001.mp3", url)
    }

    @Test
    fun testRecitersAssetIntegrity() {
        val file = File("src/main/assets/reciters.json")
        assertTrue("reciters.json must exist in assets", file.exists())
        val json = JSONArray(file.readText())
        assertEquals("Must have 255 reciters", 255, json.length())

        for (i in 0 until json.length()) {
            val reciter = json.getJSONObject(i)
            assertTrue("Reciter must have valid id", reciter.getInt("id") > 0)
            assertTrue("Reciter must have englishName", reciter.getString("englishName").isNotBlank())
            assertTrue("Reciter must have audioServer", reciter.getString("audioServer").startsWith("http"))
        }
    }

    @Test
    fun testChaptersAssetIntegrity() {
        val file = File("src/main/assets/chapters.json")
        assertTrue("chapters.json must exist in assets", file.exists())
        val json = JSONArray(file.readText())
        assertEquals("Must have 114 Surahs", 114, json.length())

        for (i in 0 until json.length()) {
            val chapter = json.getJSONObject(i)
            assertEquals("Chapter id must be 1-indexed sequential", i + 1, chapter.getInt("id"))
            assertTrue("Chapter must have name_simple", chapter.getString("name_simple").isNotBlank())
            assertTrue("Chapter must have verses_count", chapter.getInt("verses_count") > 0)
        }
    }
}
