package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.Version
import org.cirjson.cirjackson.core.cirjson.PackageVersion
import org.cirjson.cirjackson.core.cirjson.UTF8CirJsonGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionUtilTest {

    @Test
    fun testVersionPartParsing() {
        assertEquals(13, VersionUtil.parseVersionPart("13"))
        assertEquals(27, VersionUtil.parseVersionPart("27.8"))
        assertEquals(0, VersionUtil.parseVersionPart("-3"))
        assertEquals(66, VersionUtil.parseVersionPart("66R"))
    }

    @Test
    fun testVersionParsing() {
        assertEquals(Version(1, 2, 15, "foo", "group", "artifact"),
                VersionUtil.parseVersion("1.2.15-foo", "group", "artifact"))
        val v = VersionUtil.parseVersion("1.2.3-SNAPSHOT", "group", "artifact")
        assertEquals("group/artifact/1.2.3-SNAPSHOT", v.toFullString())
    }

    @Test
    fun testParseVersionReturningVersionWhereGetMajorVersionIsZero() {
        val version =
                VersionUtil.parseVersion("#M&+m@569P", "#M&+m@569P", "org.cirjson.cirjackson.core.util.VersionUtil")
        assertEquals(0, version.majorVersion)
        assertEquals(0, version.minorVersion)
        assertEquals(0, version.patchLevel)
        assertFalse(version.isSnapshot)
        assertFalse(version.isUnknownVersion)
    }

    @Test
    fun testParseVersionWithEmptyStringAndEmptyString() {
        val version = VersionUtil.parseVersion(" ", "", "\"g2AT")

        assertTrue(version.isUnknownVersion)
    }

    @Test
    fun testParseVersionWithNullAndEmptyString() {
        val version = VersionUtil.parseVersion(null, "/nUmRN)3", "")

        assertFalse(version.isSnapshot)
    }

    @Test
    fun testPackageVersionMatches() {
        assertEquals(PackageVersion.VERSION, VersionUtil.versionFor(UTF8CirJsonGenerator::class.java))
    }

    @Test
    fun testVersionForUnknownVersion() {
        assertEquals(Version.unknownVersion(), VersionUtil.versionFor(VersionUtilTest::class.java))
    }

}