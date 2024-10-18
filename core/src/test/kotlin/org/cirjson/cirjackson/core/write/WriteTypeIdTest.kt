package org.cirjson.cirjackson.core.write

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.StreamWriteException
import org.cirjson.cirjackson.core.type.WritableTypeID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail

class WriteTypeIdTest : TestBase() {

    private val factory = sharedStreamFactory()

    @Test
    fun testNoNativeTypeIdForCirJson() {
        for (mode in ALL_GENERATOR_MODES) {
            noNativeTypeIdForCirJson(mode)
        }
    }

    private fun noNativeTypeIdForCirJson(mode: Int) {
        val generator = createGenerator(factory, mode)
        assertFalse(generator.isAbleWriteTypeId)

        try {
            generator.writeTypeId("whatever")
            fail("Should have thrown an exception")
        } catch (e: StreamWriteException) {
            verifyException(e, "No native support for writing Type Ids")
        }
    }

    @Test
    fun testBasicTypeIdWriteForObject() {
        for (mode in ALL_GENERATOR_MODES) {
            basicTypeIdWriteForObject(mode)
        }
    }

    private fun basicTypeIdWriteForObject(mode: Int) {
        val data = Any()

        var typeID = WritableTypeID(data, CirJsonToken.START_OBJECT, "typeId")
        typeID.inclusion = WritableTypeID.Inclusion.METADATA_PROPERTY
        typeID.asProperty = "type"

        var generator = createGenerator(factory, mode)
        var output = generator.streamWriteOutputTarget!!
        generator.writeTypePrefix(typeID)
        generator.writeNumberProperty("value", 13)
        generator.writeTypeSuffix(typeID)
        generator.close()
        assertEquals("{\"type\":\"typeId\",\"value\":13}", output.toString())

        typeID = WritableTypeID(data, CirJsonToken.START_OBJECT, "typeId")
        typeID.inclusion = WritableTypeID.Inclusion.WRAPPER_ARRAY
        generator = createGenerator(factory, mode)
        output = generator.streamWriteOutputTarget!!
        generator.writeTypePrefix(typeID)
        generator.writeNumberProperty("value", 13)
        generator.writeTypeSuffix(typeID)
        generator.close()
        assertEquals("[\"typeId\",{\"value\":13}]", output.toString())

        typeID = WritableTypeID(data, CirJsonToken.START_OBJECT, "typeId")
        typeID.inclusion = WritableTypeID.Inclusion.WRAPPER_OBJECT
        generator = createGenerator(factory, mode)
        output = generator.streamWriteOutputTarget!!
        generator.writeTypePrefix(typeID)
        generator.writeNumberProperty("value", 13)
        generator.writeTypeSuffix(typeID)
        generator.close()
        assertEquals("{\"typeId\":{\"value\":13}}", output.toString())

        typeID = WritableTypeID(data, CirJsonToken.START_OBJECT, "typeId")
        typeID.inclusion = WritableTypeID.Inclusion.PARENT_PROPERTY
        typeID.asProperty = "extId"
        generator = createGenerator(factory, mode)
        output = generator.streamWriteOutputTarget!!
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeName("value")
        generator.writeTypePrefix(typeID)
        generator.writeNumberProperty("number", 42)
        generator.writeTypeSuffix(typeID)
        generator.writeEndObject()
        generator.close()
        assertEquals("{\"__cirJsonId__\":\"0\",\"value\":{\"number\":42},\"extId\":\"typeId\"}", output.toString())
    }

    @Test
    fun testBasicTypeIdWriteForArray() {
        for (mode in ALL_GENERATOR_MODES) {
            basicTypeIdWriteForArray(mode)
        }
    }

    private fun basicTypeIdWriteForArray(mode: Int) {
        val data = Any()

        var typeID = WritableTypeID(data, CirJsonToken.START_ARRAY, "typeId")
        typeID.inclusion = WritableTypeID.Inclusion.WRAPPER_OBJECT
        var generator = createGenerator(factory, mode)
        var output = generator.streamWriteOutputTarget!!
        generator.writeTypePrefix(typeID)
        generator.writeNumber(13)
        generator.writeNumber(42)
        generator.writeTypeSuffix(typeID)
        generator.close()
        assertEquals("{\"typeId\":[13,42]}", output.toString())

        typeID = WritableTypeID(data, CirJsonToken.START_ARRAY, "typeId")
        typeID.inclusion = WritableTypeID.Inclusion.PAYLOAD_PROPERTY
        generator = createGenerator(factory, mode)
        output = generator.streamWriteOutputTarget!!
        generator.writeTypePrefix(typeID)
        generator.writeNumber(13)
        generator.writeNumber(42)
        generator.writeTypeSuffix(typeID)
        generator.close()
        assertEquals("[\"typeId\",[13,42]]", output.toString())
    }

}