package com.listenquran.moshi

import com.listenquran.data.remote.dto.ChapterAudioResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertNotNull
import org.junit.Test

class MoshiTimingTest {
    @Test
    fun testMoshiWithDoubleSegments() {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val adapter = moshi.adapter(ChapterAudioResponse::class.java)

        // Real Quran.com response for Reciter 2 (AbdulBaset) or Reciter 3 (Sudais)
        val json = """
            {
              "audio_file": {
                "id": 7470,
                "chapter_id": 1,
                "file_size": 1132672.0,
                "format": "mp3",
                "audio_url": "https://download.quranicaudio.com/qdc/abdul_baset/murattal/1.mp3",
                "timestamps": [
                  {
                    "verse_key": "1:1",
                    "timestamp_from": 0,
                    "timestamp_to": 4370,
                    "duration": -4370,
                    "segments": [
                      [1, 0.0, 1040.0],
                      [2, 1040.0, 1660.0]
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val parsed = adapter.fromJson(json)
        assertNotNull(parsed)
        val segs = parsed!!.audioFile.timestamps!!.first().segments
        println("Deserialized segments: $segs")
        org.junit.Assert.assertEquals(1.0, segs!![0][0], 0.001)
        org.junit.Assert.assertEquals(0.0, segs[0][1], 0.001)
        org.junit.Assert.assertEquals(1040.0, segs[0][2], 0.001)
    }
}
