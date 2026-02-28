package org.cirjson.cirjackson.annotations

import kotlin.test.*

class CirJsonFormatTest {

    @Test
    fun testEmptyInstanceDefaults() {
        for (feature in CirJsonFormat.Feature.entries) {
            assertNull(EMPTY.getFeature(feature))
        }

        assertFalse(EMPTY.hasLocale())
        assertFalse(EMPTY.hasPattern())
        assertFalse(EMPTY.hasShape())
        assertFalse(EMPTY.hasTimeZone())
        assertFalse(EMPTY.hasLenient())

        assertFalse(EMPTY.isLenient)
    }

    @Test
    fun testEquality() {
        val value = CirJsonFormat.Value.forShape(CirJsonFormat.Shape.BOOLEAN)

        assertNotEquals(value, value.withPattern("ZBC"))
        assertNotEquals(value, value.withFeature(CirJsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY))
        assertNotEquals(value, value.withoutFeature(CirJsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY))
    }

    @Test
    fun testToString() {
        assertEquals("CirJsonFormat.Value(pattern=,shape=STRING,lenient=null,locale=null,timezone=null,features=EMPTY)",
                CirJsonFormat.Value.forShape(CirJsonFormat.Shape.STRING).toString())
        assertEquals("CirJsonFormat.Value(pattern=[.],shape=ANY,lenient=null,locale=null,timezone=null,features=EMPTY)",
                CirJsonFormat.Value.forPattern("[.]").toString())
    }

    @Test
    fun testFromAnnotation() {
        val annotation = Bogus::class.annotations.find { it is CirJsonFormat } as CirJsonFormat
        val value = CirJsonFormat.Value.from(annotation)
        assertEquals("xyz", value.pattern)
        assertEquals(CirJsonFormat.Shape.BOOLEAN, value.shape)
        assertEquals("bogus", value.timeZoneAsString())

        assertSame(EMPTY, CirJsonFormat.Value.from(null))
    }

    @Test
    fun testSimpleMerge() {
        assertFalse(EMPTY.hasLocale())
        assertFalse(EMPTY.hasPattern())
        assertFalse(EMPTY.hasShape())
        assertFalse(EMPTY.hasTimeZone())

        assertNull(EMPTY.locale)

        val value = CirJsonFormat.Value.forPattern(TEST_PATTERN)
        assertTrue(value.hasPattern())
        assertEquals(TEST_PATTERN, value.pattern)
        assertFalse(value.hasLocale())
        assertFalse(value.hasShape())
        assertFalse(value.hasTimeZone())

        var merged = value.withOverrides(EMPTY)
        assertEquals(TEST_PATTERN, merged.pattern)
        assertFalse(merged.hasLocale())
        assertFalse(merged.hasShape())
        assertFalse(merged.hasTimeZone())

        assertSame(merged, merged.withOverrides(merged))

        merged = CirJsonFormat.Value.merge(EMPTY, value)!!
        assertEquals(TEST_PATTERN, merged.pattern)
        assertFalse(merged.hasLocale())
        assertFalse(merged.hasShape())
        assertFalse(merged.hasTimeZone())

        assertSame(merged, merged.withOverrides(null))

        val value2 = CirJsonFormat.Value.forShape(TEST_SHAPE)

        merged = value.withOverrides(value2)
        assertEquals(TEST_PATTERN, merged.pattern)
        assertFalse(merged.hasLocale())
        assertEquals(TEST_SHAPE, merged.shape)
        assertFalse(merged.hasTimeZone())

        merged = value2.withOverrides(value)
        assertEquals(TEST_PATTERN, merged.pattern)
        assertFalse(merged.hasLocale())
        assertEquals(TEST_SHAPE, merged.shape)
        assertFalse(merged.hasTimeZone())
    }

    @Test
    fun testMultiMerge() {
        val value = CirJsonFormat.Value.forPattern(TEST_PATTERN)
        val value2 = CirJsonFormat.Value.forLeniency(false)

        val merged = CirJsonFormat.Value.mergeAll(EMPTY, value, value2)!!
        assertEquals(TEST_PATTERN, merged.pattern)
        assertEquals(false, merged.lenient)
    }

    @Test
    fun testLeniency() {
        assertFalse(EMPTY.hasLenient())
        assertFalse(EMPTY.isLenient)
        assertNull(EMPTY.lenient)

        val lenient = EMPTY.withLenient(true)
        assertTrue(lenient.hasLenient())
        assertTrue(lenient.isLenient)
        assertEquals(true, lenient.lenient)
        assertEquals(lenient, lenient)
        assertNotEquals(EMPTY, lenient)
        assertNotEquals(lenient, EMPTY)

        assertSame(lenient, lenient.withLenient(true))

        val strict = lenient.withLenient(false)
        assertTrue(strict.hasLenient())
        assertFalse(strict.isLenient)
        assertEquals(false, strict.lenient)
        assertEquals(strict, strict)
        assertNotEquals(EMPTY, strict)
        assertNotEquals(strict, EMPTY)
        assertNotEquals(lenient, strict)
        assertNotEquals(strict, lenient)

        val dunno = lenient.withLenient(null)
        assertFalse(dunno.hasLenient())
        assertFalse(dunno.isLenient)
        assertNull(dunno.lenient)
        assertEquals(dunno, dunno)
        assertEquals(EMPTY, dunno)
        assertEquals(dunno, EMPTY)
        assertNotEquals(lenient, dunno)
        assertNotEquals(dunno, lenient)
        assertNotEquals(strict, dunno)
        assertNotEquals(dunno, strict)
    }

    @Test
    fun testCaseInsensitiveValues() {
        assertNull(EMPTY.getFeature(CirJsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES))

        val insensitive = EMPTY.withFeature(CirJsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES)
        assertTrue(insensitive.getFeature(CirJsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES)!!)

        val sensitive = EMPTY.withoutFeature(CirJsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES)
        assertFalse(sensitive.getFeature(CirJsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES)!!)
    }

    @Test
    fun testShape() {
        assertFalse(CirJsonFormat.Shape.STRING.isNumeric)
        assertFalse(CirJsonFormat.Shape.STRING.isStructured)

        assertTrue(CirJsonFormat.Shape.NUMBER_INT.isNumeric)
        assertTrue(CirJsonFormat.Shape.NUMBER_FLOAT.isNumeric)
        assertTrue(CirJsonFormat.Shape.NUMBER.isNumeric)

        assertTrue(CirJsonFormat.Shape.ARRAY.isStructured)
        assertTrue(CirJsonFormat.Shape.OBJECT.isStructured)
    }

    @Test
    fun testFeatures() {
        val features1 = CirJsonFormat.Features.EMPTY
        val features2 = features1.with(CirJsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .without(CirJsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
        assertEquals(features1, features1)
        assertNotEquals(features1, features2)
        assertNotEquals(null, features1 as CirJsonFormat.Features?)
        assertNotEquals("foo", features1 as Any)

        assertNull(features1.get(CirJsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY))
        assertEquals(true, features2.get(CirJsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY))

        assertNull(features1.get(CirJsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS))
        assertEquals(false, features2.get(CirJsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS))

        val features3 = features1.withOverrides(features2)
        assertEquals(true, features3.get(CirJsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY))
        assertEquals(false, features3.get(CirJsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS))

        val features4 =
                CirJsonFormat.Features.construct(arrayOf(CirJsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS),
                        arrayOf(CirJsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY))
        assertEquals(false, features4.get(CirJsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY))
        assertEquals(true, features4.get(CirJsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS))
    }

    @CirJsonFormat(shape = CirJsonFormat.Shape.BOOLEAN, pattern = "xyz", timezone = "bogus")
    private class Bogus

    companion object {

        val EMPTY = CirJsonFormat.Value.EMPTY

        const val TEST_PATTERN = "format-string"

        val TEST_SHAPE = CirJsonFormat.Shape.NUMBER

    }

}