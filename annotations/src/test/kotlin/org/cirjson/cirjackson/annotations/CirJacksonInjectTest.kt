package org.cirjson.cirjackson.annotations

import kotlin.reflect.full.memberProperties
import kotlin.test.*

class CirJacksonInjectTest {

    @Test
    fun testEmpty() {
        assertNull(EMPTY.id)
        assertNull(EMPTY.useInput)
        assertTrue(EMPTY.willUseInput(true))
        assertFalse(EMPTY.willUseInput(false))

        assertSame(EMPTY, CirJacksonInject.Value.construct(null, null))
        assertSame(EMPTY, CirJacksonInject.Value.construct("", null))
    }

    @Test
    fun testFromAnnotation() {
        assertSame(EMPTY, CirJacksonInject.Value.from(null))

        val annotation1 = Bogus::class.memberProperties.find { it.name == "field" }!!
                .annotations.find { it is CirJacksonInject } as CirJacksonInject
        var value = CirJacksonInject.Value.from(annotation1)
        assertEquals("inject", value.id)
        assertEquals(false, value.useInput)

        assertEquals("CirJacksonInject.Value(id=inject,useInput=false)", value.toString())
        assertNotEquals(value, EMPTY)
        assertNotEquals(EMPTY, value)

        val annotation2 = Bogus::class.memberProperties.find { it.name == "vanilla" }!!
                .annotations.find { it is CirJacksonInject } as CirJacksonInject
        value = CirJacksonInject.Value.from(annotation2)
        assertSame(EMPTY, value)
    }

    @Test
    fun testStandardOverrides() {
        assertEquals("CirJacksonInject.Value(id=null,useInput=null)", EMPTY.toString())
        assertNotEquals(0, EMPTY.hashCode())
        assertEquals(EMPTY, EMPTY)
        assertNotEquals(null, EMPTY as Any?)
        assertNotEquals("xyz", EMPTY as Any)
    }

    @Test
    fun testFactories() {
        val value1 = EMPTY.withId("name")
        assertNotSame(EMPTY, value1)
        assertEquals("name", value1.id)
        assertSame(value1, value1.withId("name"))

        val value2 = value1.withUseInput(true)
        assertNotSame(value1, value2)
        assertNotEquals(value1, value2)
        assertNotEquals(value2, value1)
        assertSame(value2, value2.withUseInput(true))
        assertNotEquals(0, value2.hashCode())
    }

    private class Bogus {

        @CirJacksonInject(value = "inject", useInput = OptionalBoolean.FALSE)
        var field = 0

        @CirJacksonInject
        var vanilla = 0

    }

    companion object {

        val EMPTY = CirJacksonInject.Value.EMPTY

    }

}