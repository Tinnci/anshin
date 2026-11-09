package com.driezy.medlog.capability.update

import com.driezy.medlog.capability.update.model.SemVer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {

    @Test
    fun parseStandardVersions() {
        val v1 = SemVer.parseOrNull("1.26.0")
        assertNotNull(v1)
        assertEquals(1, v1?.major)
        assertEquals(26, v1?.minor)
        assertEquals(0, v1?.patch)
        assertNull(v1?.preRelease)

        val v2 = SemVer.parseOrNull("v2.0.1")
        assertNotNull(v2)
        assertEquals(2, v2?.major)
        assertEquals(0, v2?.minor)
        assertEquals(1, v2?.patch)

        val v3 = SemVer.parseOrNull("V10.3")
        assertNotNull(v3)
        assertEquals(10, v3?.major)
        assertEquals(3, v3?.minor)
        assertEquals(0, v3?.patch)
    }

    @Test
    fun parsePreReleaseVersions() {
        val vRc = SemVer.parseOrNull("v1.26.0-rc1")
        assertNotNull(vRc)
        assertEquals("rc1", vRc?.preRelease)

        val vBeta = SemVer.parseOrNull("1.27.0-beta.2")
        assertNotNull(vBeta)
        assertEquals("beta.2", vBeta?.preRelease)
    }

    @Test
    fun parseInvalidVersions() {
        assertNull(SemVer.parseOrNull(null))
        assertNull(SemVer.parseOrNull(""))
        assertNull(SemVer.parseOrNull("   "))
        assertNull(SemVer.parseOrNull("alpha"))
        assertNull(SemVer.parseOrNull("1"))
    }

    @Test
    fun compareVersionsCorrectly() {
        val current = SemVer.parseOrNull("1.26.0")!!
        val newerPatch = SemVer.parseOrNull("1.26.1")!!
        val newerMinor = SemVer.parseOrNull("1.27.0")!!
        val newerMajor = SemVer.parseOrNull("2.0.0")!!
        val olderPatch = SemVer.parseOrNull("1.25.9")!!
        val same = SemVer.parseOrNull("v1.26.0")!!

        assertTrue(newerPatch > current)
        assertTrue(newerMinor > current)
        assertTrue(newerMajor > current)
        assertFalse(olderPatch > current)
        assertEquals(0, current.compareTo(same))
    }

    @Test
    fun stableOutranksPreRelease() {
        val stable = SemVer.parseOrNull("1.26.0")!!
        val rc = SemVer.parseOrNull("1.26.0-rc1")!!
        val rc2 = SemVer.parseOrNull("1.26.0-rc2")!!

        assertTrue(stable > rc)
        assertTrue(rc2 > rc)
        assertTrue(stable > rc2)
    }
}
