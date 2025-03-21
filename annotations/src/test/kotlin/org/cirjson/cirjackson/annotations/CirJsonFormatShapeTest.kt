package org.cirjson.cirjackson.annotations

import kotlin.test.*

class CirJsonFormatShapeTest {

    @Test
    fun testEquality() {
        assertEquals(EMPTY, EMPTY)
        assertEquals(CirJsonFormat.Value(), CirJsonFormat.Value())

        val value1 = CirJsonFormat.Value.forShape(CirJsonFormat.Shape.BOOLEAN)
        val value2 = CirJsonFormat.Value.forShape(CirJsonFormat.Shape.BOOLEAN)
        val value3 = CirJsonFormat.Value.forShape(CirJsonFormat.Shape.SCALAR)

        assertEquals(value1, value2)
        assertEquals(value2, value1)

        assertNotEquals(value1, value3)
        assertNotEquals(value3, value1)
        assertNotEquals(value2, value3)
        assertNotEquals(value3, value2)

        assertNotEquals(value1.hashCode(), value3.hashCode())

        assertEquals(value1, value3.withShape(CirJsonFormat.Shape.BOOLEAN))
    }

    @Test
    fun testShape() {
        assertFalse(CirJsonFormat.Shape.STRING.isNumeric)
        assertFalse(CirJsonFormat.Shape.isNumeric(CirJsonFormat.Shape.STRING))
        assertFalse(CirJsonFormat.Shape.STRING.isStructured)
        assertFalse(CirJsonFormat.Shape.isStructured(CirJsonFormat.Shape.STRING))

        assertFalse(CirJsonFormat.Shape.BOOLEAN.isNumeric)
        assertFalse(CirJsonFormat.Shape.isNumeric(CirJsonFormat.Shape.BOOLEAN))
        assertFalse(CirJsonFormat.Shape.BOOLEAN.isStructured)
        assertFalse(CirJsonFormat.Shape.isStructured(CirJsonFormat.Shape.BOOLEAN))

        assertTrue(CirJsonFormat.Shape.NUMBER_INT.isNumeric)
        assertTrue(CirJsonFormat.Shape.isNumeric(CirJsonFormat.Shape.NUMBER_INT))
        assertTrue(CirJsonFormat.Shape.NUMBER_FLOAT.isNumeric)
        assertTrue(CirJsonFormat.Shape.isNumeric(CirJsonFormat.Shape.NUMBER_FLOAT))
        assertTrue(CirJsonFormat.Shape.NUMBER.isNumeric)
        assertTrue(CirJsonFormat.Shape.isNumeric(CirJsonFormat.Shape.NUMBER))

        assertFalse(CirJsonFormat.Shape.ARRAY.isNumeric)
        assertFalse(CirJsonFormat.Shape.isNumeric(CirJsonFormat.Shape.ARRAY))
        assertTrue(CirJsonFormat.Shape.ARRAY.isStructured)
        assertTrue(CirJsonFormat.Shape.isStructured(CirJsonFormat.Shape.ARRAY))

        assertFalse(CirJsonFormat.Shape.OBJECT.isNumeric)
        assertFalse(CirJsonFormat.Shape.isNumeric(CirJsonFormat.Shape.OBJECT))
        assertTrue(CirJsonFormat.Shape.OBJECT.isStructured)
        assertTrue(CirJsonFormat.Shape.isStructured(CirJsonFormat.Shape.OBJECT))

        assertFalse(CirJsonFormat.Shape.POJO.isNumeric)
        assertFalse(CirJsonFormat.Shape.isNumeric(CirJsonFormat.Shape.POJO))
        assertTrue(CirJsonFormat.Shape.POJO.isStructured)
        assertTrue(CirJsonFormat.Shape.isStructured(CirJsonFormat.Shape.POJO))

        assertFalse(CirJsonFormat.Shape.ANY.isNumeric)
        assertFalse(CirJsonFormat.Shape.isNumeric(CirJsonFormat.Shape.ANY))
        assertFalse(CirJsonFormat.Shape.ANY.isStructured)
        assertFalse(CirJsonFormat.Shape.isStructured(CirJsonFormat.Shape.ANY))

        assertFalse(CirJsonFormat.Shape.NATURAL.isNumeric)
        assertFalse(CirJsonFormat.Shape.isNumeric(CirJsonFormat.Shape.NATURAL))
        assertFalse(CirJsonFormat.Shape.NATURAL.isStructured)
        assertFalse(CirJsonFormat.Shape.isStructured(CirJsonFormat.Shape.NATURAL))
    }

    companion object {

        val EMPTY = CirJsonFormat.Value.EMPTY

    }

}