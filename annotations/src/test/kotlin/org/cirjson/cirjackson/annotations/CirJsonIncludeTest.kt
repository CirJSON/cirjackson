package org.cirjson.cirjackson.annotations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CirJsonIncludeTest {

    @Test
    fun testEquality() {
        assertEquals(EMPTY, EMPTY)

        val value1 = CirJsonInclude.Value.construct(CirJsonInclude.Include.NON_ABSENT, null)
        val value2 = CirJsonInclude.Value.construct(CirJsonInclude.Include.NON_ABSENT, null)
        val value3 = CirJsonInclude.Value.construct(CirJsonInclude.Include.NON_ABSENT, CirJsonInclude.Include.NON_EMPTY)

        assertEquals(value1, value2)
        assertEquals(value2, value1)

        assertNotEquals(value1, value3)
        assertNotEquals(value3, value1)
        assertNotEquals(value2, value3)
        assertNotEquals(value3, value2)
    }

    @Test
    fun testFromAnnotation() {
        val annotation = Bogus::class.annotations.find { it is CirJsonInclude } as CirJsonInclude
        val value = CirJsonInclude.Value.from(annotation)
        assertEquals(CirJsonInclude.Include.NON_EMPTY, value.valueInclusion)
        assertEquals(CirJsonInclude.Include.NON_DEFAULT, value.contentInclusion)
    }

    @Test
    fun testFromAnnotationWithCustom() {
        val annotation = Custom::class.annotations.find { it is CirJsonInclude } as CirJsonInclude
        val value = CirJsonInclude.Value.from(annotation)
        assertEquals(CirJsonInclude.Include.CUSTOM, value.valueInclusion)
        assertEquals(CirJsonInclude.Include.CUSTOM, value.contentInclusion)
        assertEquals(Int::class, value.valueFilter)
        assertEquals(Long::class, value.contentFilter)

        assertEquals(
                "CirJsonInclude.Value(value=CUSTOM,content=CUSTOM,valueFilter=int::class,contentFilter=long::class)",
                value.toString())
    }

    @Test
    fun testStandardOverrides() {
        assertEquals("CirJsonInclude.Value(value=NON_ABSENT,content=USE_DEFAULTS)",
                CirJsonInclude.Value.construct(CirJsonInclude.Include.NON_ABSENT, null).toString())
        assertNotEquals(0, EMPTY.hashCode())
        assertNotEquals(null, EMPTY as Any?)
        assertNotEquals("", EMPTY as Any)
    }

    @Test
    fun testSimpleMerge() {
        assertEquals(CirJsonInclude.Include.USE_DEFAULTS, EMPTY.valueInclusion)
        assertEquals(CirJsonInclude.Include.USE_DEFAULTS, EMPTY.contentInclusion)

        val value2 = EMPTY.withValueInclusion(CirJsonInclude.Include.NON_ABSENT)

        assertEquals(CirJsonInclude.Include.NON_ABSENT, value2.valueInclusion)
        assertEquals(CirJsonInclude.Include.USE_DEFAULTS, value2.contentInclusion)

        val value3 = CirJsonInclude.Value(CirJsonInclude.Include.NON_EMPTY, CirJsonInclude.Include.ALWAYS, null, null)

        assertEquals(CirJsonInclude.Include.NON_EMPTY, value3.valueInclusion)
        assertEquals(CirJsonInclude.Include.ALWAYS, value3.contentInclusion)

        var merged = value3.withOverrides(EMPTY)
        assertEquals(value3.valueInclusion, merged.valueInclusion)
        assertEquals(value3.contentInclusion, merged.contentInclusion)

        merged = CirJsonInclude.Value.merge(value3, value2)!!
        assertEquals(value2.valueInclusion, merged.valueInclusion)
        assertEquals(value3.contentInclusion, merged.contentInclusion)

        merged = CirJsonInclude.Value.mergeAll(EMPTY, value3)!!
        assertEquals(value3.valueInclusion, merged.valueInclusion)
        assertEquals(value3.contentInclusion, merged.contentInclusion)
    }

    @Test
    fun testContentMerge() {
        val value1 = EMPTY.withContentInclusion(CirJsonInclude.Include.ALWAYS)
                .withValueInclusion(CirJsonInclude.Include.NON_ABSENT)
        val value2 = EMPTY.withContentInclusion(CirJsonInclude.Include.NON_EMPTY)
                .withValueInclusion(CirJsonInclude.Include.USE_DEFAULTS)

        val value12 = value2.withOverrides(value1)
        val value21 = value1.withOverrides(value2)

        assertEquals(CirJsonInclude.Include.ALWAYS, value12.contentInclusion)
        assertEquals(CirJsonInclude.Include.NON_ABSENT, value12.valueInclusion)

        assertEquals(CirJsonInclude.Include.NON_EMPTY, value21.contentInclusion)
        assertEquals(CirJsonInclude.Include.NON_ABSENT, value21.valueInclusion)
    }

    @Test
    fun testFilters() {
        assertNull(EMPTY.valueFilter)
        assertNull(EMPTY.contentFilter)

        val value1 = EMPTY.withValueFilter(String::class)
        assertEquals(CirJsonInclude.Include.CUSTOM, value1.valueInclusion)
        assertEquals(String::class, value1.valueFilter)
        assertNull(value1.withValueFilter(null).valueFilter)
        assertNull(value1.withValueFilter(Nothing::class).valueFilter)

        val value2 = EMPTY.withContentFilter(Long::class)
        assertEquals(CirJsonInclude.Include.CUSTOM, value2.contentInclusion)
        assertEquals(Long::class, value2.contentFilter)
        assertNull(value2.withContentFilter(null).contentFilter)
        assertNull(value2.withContentFilter(Nothing::class).contentFilter)
    }

    @CirJsonInclude(value = CirJsonInclude.Include.NON_EMPTY, content = CirJsonInclude.Include.NON_DEFAULT)
    private class Bogus

    @CirJsonInclude(value = CirJsonInclude.Include.CUSTOM, valueFilter = Int::class,
            content = CirJsonInclude.Include.CUSTOM, contentFilter = Long::class)
    private class Custom

    companion object {

        val EMPTY = CirJsonInclude.Value.EMPTY

    }

}