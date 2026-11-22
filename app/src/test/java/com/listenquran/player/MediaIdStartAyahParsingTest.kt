package com.listenquran.player

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaIdStartAyahParsingTest {

    @Test
    fun standardMediaIdWithoutAyahDefaultsTo1() {
        assertEquals(1, PlaybackQueueManager.parseStartAyah("surah:2:reciter:7:uid:abc-123"))
        assertEquals(1, PlaybackQueueManager.parseStartAyah("surah:114:reciter:3:uid:xyz"))
    }

    @Test
    fun encodedAyahInMediaIdIsParsedAccurately() {
        assertEquals(255, PlaybackQueueManager.parseStartAyah("surah:2:reciter:7:ayah:255:uid:abc-123"))
        assertEquals(285, PlaybackQueueManager.parseStartAyah("surah:2:reciter:7:ayah:285:uid:xyz"))
        assertEquals(87, PlaybackQueueManager.parseStartAyah("surah:21:reciter:2:ayah:87:uid:def"))
    }

    @Test
    fun invalidOrZeroAyahValuesClampOrFallbackTo1() {
        assertEquals(1, PlaybackQueueManager.parseStartAyah("surah:2:reciter:7:ayah:0:uid:abc"))
        assertEquals(1, PlaybackQueueManager.parseStartAyah("surah:2:reciter:7:ayah:-5:uid:abc"))
        assertEquals(1, PlaybackQueueManager.parseStartAyah("surah:2:reciter:7:ayah:notanumber:uid:abc"))
        assertEquals(1, PlaybackQueueManager.parseStartAyah("radio:curated_1:12345:18:7"))
        assertEquals(1, PlaybackQueueManager.parseStartAyah(""))
    }
}
