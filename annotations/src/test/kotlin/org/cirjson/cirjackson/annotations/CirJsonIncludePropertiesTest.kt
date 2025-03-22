package org.cirjson.cirjackson.annotations

import kotlin.test.*

class CirJsonIncludePropertiesTest {

    @Test
    fun testAll() {
        assertSame(ALL, CirJsonIncludeProperties.Value.from(null))
        assertNull(ALL.included)
        assertEquals(ALL, ALL)
        assertEquals("CirJsonIncludeProperties.Value(included=null)", ALL.toString())
        assertEquals(0, ALL.hashCode())
    }

    @Test
    fun testFromAnnotation() {
        val annotation = Bogus::class.annotations.find { it is CirJsonIncludeProperties } as CirJsonIncludeProperties
        val value = CirJsonIncludeProperties.Value.from(annotation)
        val included = value.included!!
        assertEquals(2, included.size)
        assertEquals(setOf("foo", "bar"), included)
        val str = value.toString()
        val ts1 = str == "CirJsonIncludeProperties.Value(included=[foo, bar])"
        val ts2 = str == "CirJsonIncludeProperties.Value(included=[bar, foo])"
        assertTrue(ts1 || ts2)
        assertEquals(value, CirJsonIncludeProperties.Value.from(
                Bogus::class.annotations.find { it is CirJsonIncludeProperties } as CirJsonIncludeProperties))
    }

    @Test
    fun testWithOverridesAll() {
        val annotation = Bogus::class.annotations.find { it is CirJsonIncludeProperties } as CirJsonIncludeProperties
        var value = CirJsonIncludeProperties.Value.from(annotation)
        value = value.withOverrides(ALL)
        val included = value.included!!
        assertEquals(2, included.size)
        assertEquals(setOf("foo", "bar"), included)
    }

    @Test
    fun testWithOverridesEmpty() {
        val annotation = Bogus::class.annotations.find { it is CirJsonIncludeProperties } as CirJsonIncludeProperties
        var value = CirJsonIncludeProperties.Value.from(annotation)
        value = value.withOverrides(CirJsonIncludeProperties.Value(emptySet()))
        val included = value.included!!
        assertEquals(0, included.size)
    }

    @Test
    fun testWithOverridesMerge() {
        val annotation = Bogus::class.annotations.find { it is CirJsonIncludeProperties } as CirJsonIncludeProperties
        var value = CirJsonIncludeProperties.Value.from(annotation)
        value = value.withOverrides(CirJsonIncludeProperties.Value(setOf("foo")))
        val included = value.included!!
        assertEquals(1, included.size)
        assertEquals(setOf("foo"), included)
    }

    @CirJsonIncludeProperties(value = ["foo", "bar"])
    private class Bogus

    companion object {

        val ALL = CirJsonIncludeProperties.Value.ALL

    }

}