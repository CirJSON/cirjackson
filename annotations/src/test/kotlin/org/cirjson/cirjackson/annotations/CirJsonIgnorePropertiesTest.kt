package org.cirjson.cirjackson.annotations

import kotlin.test.*

class CirJsonIgnorePropertiesTest {

    @Test
    fun testEmpty() {
        assertSame(EMPTY, CirJsonIgnoreProperties.Value.from(null))
        assertEquals(0, EMPTY.ignored.size)
        assertFalse(EMPTY.allowGetters)
        assertFalse(EMPTY.allowSetters)
    }

    @Test
    fun testEquality() {
        assertEquals(EMPTY, EMPTY)

        assertSame(EMPTY, EMPTY.withMerge())

        val value = EMPTY.withoutMerge()
        assertEquals(value, value)
        assertNotEquals(EMPTY, value)
        assertNotEquals(value, EMPTY)
    }

    @Test
    fun testFromAnnotation() {
        val annotation = Bogus::class.annotations.find { it is CirJsonIgnoreProperties }!! as CirJsonIgnoreProperties
        val value = CirJsonIgnoreProperties.Value.from(annotation)
        assertNotNull(value)
        assertFalse(value.merge)
        assertFalse(value.allowGetters)
        assertFalse(value.allowSetters)
        val ignored = value.ignored
        assertEquals(2, ignored.size)
        assertEquals(setOf("foo", "bar"), ignored)
    }

    @Test
    fun testFactories() {
        assertSame(EMPTY, CirJsonIgnoreProperties.Value.forIgnoreUnknown(false))
        assertSame(EMPTY, CirJsonIgnoreProperties.Value.forIgnoredProperties())
        assertSame(EMPTY, CirJsonIgnoreProperties.Value.forIgnoredProperties(emptySet()))

        val value = CirJsonIgnoreProperties.Value.forIgnoredProperties(setOf("a", "b"))
        assertEquals(setOf("a", "b"), value.ignored)

        val valueSerialization = value.withAllowGetters()
        assertTrue(valueSerialization.allowGetters)
        assertFalse(valueSerialization.allowSetters)
        assertEquals(setOf("a", "b"), valueSerialization.ignored)
        assertEquals(setOf("a", "b"), valueSerialization.findIgnoredForDeserialization())
        assertEquals(emptySet(), valueSerialization.findIgnoredForSerialization())

        val valueDeserialization = value.withAllowSetters()
        assertFalse(valueDeserialization.allowGetters)
        assertTrue(valueDeserialization.allowSetters)
        assertEquals(setOf("a", "b"), valueDeserialization.ignored)
        assertEquals(emptySet(), valueDeserialization.findIgnoredForDeserialization())
        assertEquals(setOf("a", "b"), valueDeserialization.findIgnoredForSerialization())
    }

    @Test
    fun testMutantFactories() {
        assertEquals(2, EMPTY.withIgnored("a", "b").ignored.size)
        assertEquals(1, EMPTY.withIgnored(setOf("a")).ignored.size)
        assertEquals(0, EMPTY.withIgnored(null).ignored.size)

        assertTrue(EMPTY.withIgnoreUnknown().ignoreUnknown)
        assertFalse(EMPTY.withoutIgnoreUnknown().ignoreUnknown)

        assertTrue(EMPTY.withAllowGetters().allowGetters)
        assertFalse(EMPTY.withoutAllowGetters().allowGetters)
        assertTrue(EMPTY.withAllowSetters().allowSetters)
        assertFalse(EMPTY.withoutAllowSetters().allowSetters)

        assertTrue(EMPTY.withMerge().merge)
        assertFalse(EMPTY.withoutMerge().merge)
    }

    @Test
    fun testSimpleMerge() {
        val value1 = EMPTY.withIgnoreUnknown().withAllowGetters()
        val value2a = EMPTY.withMerge().withIgnored("a")
        val value2b = EMPTY.withoutMerge()

        val value3a = value1.withOverrides(value2a)
        assertEquals(setOf("a"), value3a.ignored)
        assertTrue(value3a.ignoreUnknown)
        assertTrue(value3a.allowGetters)
        assertFalse(value3a.allowSetters)

        val value3b = CirJsonIgnoreProperties.Value.merge(value1, value2b)!!
        assertEquals(emptySet(), value3b.ignored)
        assertFalse(value3b.ignoreUnknown)
        assertFalse(value3b.allowGetters)
        assertFalse(value3b.allowSetters)

        assertEquals(value2b, value3b)

        assertSame(value2b, value2b.withOverrides(null))
        assertSame(value2b, value2b.withOverrides(EMPTY))
    }

    @Test
    fun testMergeIgnoreProperties() {
        val value1 = EMPTY.withIgnored("a")
        val value2 = EMPTY.withIgnored("b")
        val value3 = EMPTY.withIgnored("c")

        val merged = CirJsonIgnoreProperties.Value.mergeAll(value1, value2, value3)!!
        val ignored = merged.ignored
        assertTrue("a" in ignored)
        assertTrue("b" in ignored)
        assertTrue("c" in ignored)
    }

    @Test
    fun testToString() {
        assertEquals(
                "CirJsonIgnoreProperties.Value(ignored=[],ignoreUnknown=false,allowGetters=false,allowSetters=true,merge=true)",
                EMPTY.withAllowSetters().withMerge().toString())
        assertNotEquals(0, EMPTY.hashCode())
    }

    @CirJsonIgnoreProperties(value = ["foo", "bar"], ignoreUnknown = true)
    private class Bogus

    companion object {

        val EMPTY = CirJsonIgnoreProperties.Value.EMPTY

    }

}