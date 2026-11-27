package com.listenquran.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {

    @Test
    fun `parses valid version strings and compares semver cleanly`() {
        val v133 = SemVer.parse("v1.3.3")!!
        val v140 = SemVer.parse("1.4.0")!!
        val v1100 = SemVer.parse("v1.10.0")!!
        val v200 = SemVer.parse("V2.0.0")!!

        assertEquals(SemVer(1, 3, 3), v133)
        assertTrue(v140 > v133)
        assertTrue(v1100 > v140)
        assertTrue(v200 > v1100)
        assertEquals(0, v140.compareTo(SemVer.parse("v1.4.0")!!))
    }

    @Test
    fun `rejects invalid version tags cleanly`() {
        assertNull(SemVer.parse(null))
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse("latest"))
        assertNull(SemVer.parse("1.4"))
    }
}
