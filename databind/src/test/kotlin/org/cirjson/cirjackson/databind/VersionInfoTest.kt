package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.Versioned
import org.cirjson.cirjackson.databind.cirjson.CirJsonMapper
import org.cirjson.cirjackson.databind.configuration.PackageVersion
import org.cirjson.cirjackson.databind.introspection.CirJacksonAnnotationIntrospector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Tests to ensure that we get proper Version information via things defined as Versioned.
 */
class VersionInfoTest {

    @Test
    fun testMapperVersions() {
        val mapper = CirJsonMapper()
        assertVersion(mapper)
        assertVersion(mapper.reader())
        assertVersion(mapper.writer())
        assertVersion(CirJacksonAnnotationIntrospector())
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    private fun assertVersion(versioned: Versioned) {
        val version = versioned.version()
        assertFalse(version.isUnknownVersion, "Should find version information (got $version)")
        val expected = PackageVersion.VERSION
        assertEquals(expected.toFullString(), version.toFullString())
        assertEquals(expected, version)
    }

}