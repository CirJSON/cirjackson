package org.cirjson.cirjackson.annotations

import kotlin.test.*

class CirJsonTypeInfoTest {

    @Test
    fun testEmpty() {
        assertNull(CirJsonTypeInfo.Value.from(null))
    }

    @Test
    fun testFromAnnotation() {
        val annotation1 = Bogus1::class.annotations.find { it is CirJsonTypeInfo } as CirJsonTypeInfo
        val value1 = CirJsonTypeInfo.Value.from(annotation1)!!
        assertEquals(CirJsonTypeInfo.Id.CLASS, value1.idType)
        assertEquals(CirJsonTypeInfo.As.PROPERTY, value1.inclusionType)
        assertEquals("@class", value1.propertyName)
        assertTrue(value1.visible)
        assertNull(value1.defaultImplementation)
        assertEquals(true, value1.requireTypeIdForSubtypes)

        val annotation2 = Bogus2::class.annotations.find { it is CirJsonTypeInfo } as CirJsonTypeInfo
        val value2 = CirJsonTypeInfo.Value.from(annotation2)!!
        assertEquals(CirJsonTypeInfo.Id.NAME, value2.idType)
        assertEquals(CirJsonTypeInfo.As.EXTERNAL_PROPERTY, value2.inclusionType)
        assertEquals("ext", value2.propertyName)
        assertFalse(value2.visible)
        assertEquals(Nothing::class, value2.defaultImplementation)
        assertEquals(false, value2.requireTypeIdForSubtypes)

        assertEquals(value1, value1)
        assertEquals(value2, value2)

        assertNotEquals(value1, value2)
        assertNotEquals(value2, value1)

        assertEquals(
                "CirJsonTypeInfo.Value(idType=CLASS,inclusionType=PROPERTY,propertyName=@class,defaultImplementation=null,visible=true,requireTypeIdForSubtypes=true)",
                value1.toString())
        assertEquals(
                "CirJsonTypeInfo.Value(idType=NAME,inclusionType=EXTERNAL_PROPERTY,propertyName=ext,defaultImplementation=class java.lang.Void,visible=false,requireTypeIdForSubtypes=false)",
                value2.toString())
    }

    @Test
    fun testMutators() {
        val annotation1 = Bogus1::class.annotations.find { it is CirJsonTypeInfo } as CirJsonTypeInfo
        val value1 = CirJsonTypeInfo.Value.from(annotation1)!!
        assertEquals(CirJsonTypeInfo.Id.CLASS, value1.idType)
        assertSame(value1, value1.withIdType(CirJsonTypeInfo.Id.CLASS))

        var value2 = value1.withIdType(CirJsonTypeInfo.Id.MINIMAL_CLASS)
        assertEquals(CirJsonTypeInfo.Id.MINIMAL_CLASS, value2.idType)

        val value3 = value1.withIdType(CirJsonTypeInfo.Id.SIMPLE_NAME)
        assertEquals(CirJsonTypeInfo.Id.SIMPLE_NAME, value3.idType)

        assertEquals(CirJsonTypeInfo.As.PROPERTY, value1.inclusionType)
        assertSame(value1, value1.withInclusionType(CirJsonTypeInfo.As.PROPERTY))
        value2 = value1.withInclusionType(CirJsonTypeInfo.As.EXTERNAL_PROPERTY)
        assertEquals(CirJsonTypeInfo.As.EXTERNAL_PROPERTY, value2.inclusionType)

        assertSame(value1, value1.withDefaultImplementation(null))
        value2 = value1.withDefaultImplementation(String::class)
        assertEquals(String::class, value2.defaultImplementation)

        assertSame(value1, value1.withVisible(true))
        assertFalse(value1.withVisible(false).visible)

        assertEquals("foobar", value1.withPropertyName("foobar").propertyName)
    }

    @Test
    fun testWithRequireTypeIdForSubtypes() {
        val empty = CirJsonTypeInfo.Value.EMPTY
        assertNull(empty.requireTypeIdForSubtypes)

        val requireTypeIdTrue = empty.withRequireTypeIdForSubtypes(true)
        assertEquals(true, requireTypeIdTrue.requireTypeIdForSubtypes)

        val requireTypeIdFalse = empty.withRequireTypeIdForSubtypes(false)
        assertEquals(false, requireTypeIdFalse.requireTypeIdForSubtypes)

        val requireTypeIdDefault = requireTypeIdTrue.withRequireTypeIdForSubtypes(null)
        assertNull(requireTypeIdDefault.requireTypeIdForSubtypes)
    }

    @Test
    fun testDefaultValueForRequireTypeIdForSubtypes() {
        val annotation = Bogus3::class.annotations.find { it is CirJsonTypeInfo } as CirJsonTypeInfo
        val value = CirJsonTypeInfo.Value.from(annotation)!!
        assertNull(value.requireTypeIdForSubtypes)

        assertEquals(
                "CirJsonTypeInfo.Value(idType=NAME,inclusionType=EXTERNAL_PROPERTY,propertyName=ext,defaultImplementation=class java.lang.Void,visible=false,requireTypeIdForSubtypes=null)",
                value.toString())
    }

    @CirJsonTypeInfo(use = CirJsonTypeInfo.Id.CLASS, visible = true, defaultImplementation = CirJsonTypeInfo::class,
            requireTypeIdForSubtypes = OptionalBoolean.TRUE)
    private class Bogus1

    @CirJsonTypeInfo(use = CirJsonTypeInfo.Id.NAME, include = CirJsonTypeInfo.As.EXTERNAL_PROPERTY, property = "ext",
            defaultImplementation = Nothing::class, requireTypeIdForSubtypes = OptionalBoolean.FALSE)
    private class Bogus2

    @CirJsonTypeInfo(use = CirJsonTypeInfo.Id.NAME, include = CirJsonTypeInfo.As.EXTERNAL_PROPERTY, property = "ext",
            defaultImplementation = Nothing::class)
    private class Bogus3

}