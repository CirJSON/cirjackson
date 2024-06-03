package org.cirjson.cirjackson.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionTest : TestBase() {

    @Test
    fun testEqualsAndHashCode() {
        val version1 = Version(1, 2, 3, "", "", "")
        val version2 = Version(1, 2, 3, "", "", "")

        assertEquals(version1, version2)
        assertEquals(version2, version1)

        assertEquals(version1.hashCode(), version2.hashCode())
    }

    @Test
    fun testCompareToOne() {
        val version = Version.unknownVersion()
        val version1 = Version(0, -263, -1820, "", "", "")

        assertEquals(1, version.compareTo(version1))
    }

    @Test
    fun testCompareToReturningZero() {
        val version = Version.unknownVersion()
        val version1 = Version(0, 0, 0, "", "", "")

        assertEquals(0, version.compareTo(version1))
    }

    @Test
    fun testCreatesVersionTaking6ArgumentsAndCallsCompareTo() {
        val version = Version(0, 0, 0, null, null, "")
        val version1 = Version(0, 0, 0, "", "", "//0.0.0")

        assertTrue(version.compareTo(version1) < 0)
    }

    @Test
    fun testCompareToTwo() {
        val version = Version.unknownVersion()
        val version1 = Version(-1, 0, 0, "0.0.0", "", "")

        assertTrue(version.compareTo(version1) > 0)
    }

    @Test
    fun testCompareToAndCreatesVersionTaking6ArgumentsAndUnknownVersion() {
        val version = Version.unknownVersion()
        val version1 = Version(0, 0, 0, "//0.0.0", "//0.0.0", "//0.0.0")

        assertTrue(version.compareTo(version1) < 0)
    }

    @Test
    fun testCompareToSnapshotSame() {
        val version = Version(0, 0, 0, "alpha", "org.cirjson", "dummy")
        val version1 = Version(0, 0, 0, "alpha", "org.cirjson", "dummy")

        assertEquals(0, version.compareTo(version1))
    }

    @Test
    fun testCompareToSnapshotDifferent() {
        val version = Version(0, 0, 0, "alpha", "org.cirjson", "dummy")
        val version1 = Version(0, 0, 0, "beta", "org.cirjson", "dummy")

        assertTrue(version.compareTo(version1) < 0)
        assertTrue(version1.compareTo(version) > 0)
    }

    @Test
    fun testWhenOnlyFirstHasSnapshot() {
        val version = Version(0, 0, 0, "alpha", "org.cirjson", "dummy")
        val version1 = Version(0, 0, 0, null, "org.cirjson", "dummy")

        assertTrue(version.compareTo(version1) < 0)
        assertTrue(version1.compareTo(version) > 0)
    }

    @Test
    fun testWhenOnlySecondHasSnapshot() {
        val version = Version(0, 0, 0, "", "org.cirjson", "dummy")
        val version1 = Version(0, 0, 0, "beta", "org.cirjson", "dummy")

        assertTrue(version.compareTo(version1) > 0)
        assertTrue(version1.compareTo(version) < 0)
    }

}