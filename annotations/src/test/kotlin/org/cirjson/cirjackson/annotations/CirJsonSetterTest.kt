package org.cirjson.cirjackson.annotations

import kotlin.reflect.full.memberProperties
import kotlin.test.*

class CirJsonSetterTest {

    @Test
    fun testEmpty() {
        assertEquals(Nulls.DEFAULT, EMPTY.nulls)
        assertEquals(Nulls.DEFAULT, EMPTY.contentNulls)

        assertEquals(CirJsonSetter::class, EMPTY.valueFor())

        assertNull(EMPTY.nonDefaultNulls())
        assertNull(EMPTY.nonDefaultContentNulls())
    }

    @Test
    fun testStandardOverrides() {
        assertEquals("CirJsonSetter.Value(nulls=DEFAULT,contentNulls=DEFAULT)", EMPTY.toString())
        assertNotEquals(0, EMPTY.hashCode())
        assertEquals(EMPTY, EMPTY)
        assertNotEquals(null, EMPTY as Any?)
        assertNotEquals("xyz", EMPTY as Any)
    }

    @Test
    fun testFromAnnotation() {
        assertSame(EMPTY, CirJsonSetter.Value.from(null))

        val annotation =
                Bogus::class.memberProperties.find { it.name == "setterField" }!!.annotations.find { it is CirJsonSetter } as CirJsonSetter
        val value = CirJsonSetter.Value.from(annotation)
        assertEquals(Nulls.FAIL, value.nulls)
        assertEquals(Nulls.SKIP, value.contentNulls)
    }

    @Test
    fun testConstruct() {
        val value = CirJsonSetter.Value.construct(null, null)
        assertSame(EMPTY, value)
    }

    @Test
    fun testFactories() {
        val value1 = CirJsonSetter.Value.forNulls(Nulls.SET)
        assertEquals(Nulls.SET, value1.nulls)
        assertEquals(Nulls.DEFAULT, value1.contentNulls)
        assertEquals(Nulls.SET, value1.nonDefaultNulls())
        assertNull(value1.nonDefaultContentNulls())

        val value2 = CirJsonSetter.Value.forContentNulls(Nulls.SKIP)
        assertEquals(Nulls.DEFAULT, value2.nulls)
        assertEquals(Nulls.SKIP, value2.contentNulls)
        assertNull(value2.nonDefaultNulls())
        assertEquals(Nulls.SKIP, value2.nonDefaultContentNulls())
    }

    @Test
    fun testSimpleMerge() {
        val value1 = CirJsonSetter.Value.forContentNulls(Nulls.SKIP)
        assertSame(value1, value1.withOverrides(null))
        assertSame(value1, value1.withOverrides(EMPTY))
        assertSame(value1, value1.withOverrides(CirJsonSetter.Value.forContentNulls(value1.nulls)))

        val value2 = CirJsonSetter.Value.forNulls(Nulls.FAIL)
        val merged = value1.withOverrides(value2)
        assertEquals(Nulls.FAIL, merged.nulls)
        assertEquals(Nulls.SKIP, merged.contentNulls)
    }

    @Test
    fun testWithMethods() {
        var value1 = EMPTY.withContentNulls(null)
        assertSame(EMPTY, value1)
        value1 = value1.withContentNulls(Nulls.FAIL)
        assertEquals(Nulls.FAIL, value1.contentNulls)
        assertSame(value1, value1.withContentNulls(Nulls.FAIL))

        val value2 = value1.withNulls(Nulls.SKIP)
        assertEquals(Nulls.SKIP, value2.nulls)
        assertNotEquals(value1, value2)
        assertNotEquals(value2, value1)

        val value3 = value2.with(null, null)
        assertEquals(Nulls.DEFAULT, value3.nulls)
        assertEquals(Nulls.DEFAULT, value3.contentNulls)
        assertSame(value3, value3.with(null, null))

        val merged = value3.withOverrides(value2)
        assertNotSame(value2, merged)
        assertEquals(merged, value2)
        assertEquals(value2, merged)
    }

    private class Bogus {

        @CirJsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.SKIP)
        var setterField = 0

    }

    companion object {

        val EMPTY = CirJsonSetter.Value.EMPTY

    }

}