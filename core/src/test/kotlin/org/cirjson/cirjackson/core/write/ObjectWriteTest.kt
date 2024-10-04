package org.cirjson.cirjackson.core.write

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.StreamWriteFeature
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.exception.StreamWriteException
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.*

class ObjectWriteTest : TestBase() {

    private val slowFactory = newStreamFactory()

    private val fastFactory = CirJsonFactory.builder().enable(StreamWriteFeature.USE_FAST_DOUBLE_WRITER).build()

    private val factories = arrayOf(slowFactory, fastFactory)

    @Test
    fun testEmptyObjectWrite() {
        for (factory in factories) {
            for (generatorMode in ALL_GENERATOR_MODES) {
                for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                    emptyObjectWrite(factory, generatorMode, parserMode)
                }
            }
        }
    }

    private fun emptyObjectWrite(factory: CirJsonFactory, generatorMode: Int, parserMode: Int) {
        val generator = createGenerator(factory, generatorMode)

        var context = generator.streamWriteContext
        assertTrue(context.isInRoot)
        assertFalse(context.isInArray)
        assertFalse(context.isInObject)
        assertEquals(0, context.entryCount)
        assertEquals(0, context.currentIndex)

        generator.writeStartObject()
        context = generator.streamWriteContext
        assertFalse(context.isInRoot)
        assertFalse(context.isInArray)
        assertTrue(context.isInObject)
        assertEquals(0, context.entryCount)
        assertEquals(0, context.currentIndex)

        generator.writeObjectId(Any())
        context = generator.streamWriteContext
        assertFalse(context.isInRoot)
        assertFalse(context.isInArray)
        assertTrue(context.isInObject)
        assertEquals(1, context.entryCount)
        assertEquals(0, context.currentIndex)

        generator.writeEndObject()
        context = generator.streamWriteContext
        assertTrue(context.isInRoot)
        assertFalse(context.isInArray)
        assertFalse(context.isInObject)
        assertEquals(1, context.entryCount)
        assertEquals(0, context.currentIndex)

        generator.close()

        val doc = generator.streamWriteOutputTarget!!.toString()
        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testInvalidObjectWrite() {
        for (factory in factories) {
            for (mode in ALL_GENERATOR_MODES) {
                invalidObjectWrite(factory, mode)
            }
        }
    }

    private fun invalidObjectWrite(factory: CirJsonFactory, mode: Int) {
        val generator = createGenerator(factory, mode)
        generator.writeStartObject()
        generator.writeObjectId(Any())

        try {
            generator.writeEndArray()
            fail("Should have thrown an exception")
        } catch (e: StreamWriteException) {
            verifyException(e, "Current context not Array")
        }

        generator.close()
    }

    @Test
    fun testSimpleObjectWrite() {
        for (factory in factories) {
            for (generatorMode in ALL_GENERATOR_MODES) {
                for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                    simpleObjectWrite(factory, generatorMode, parserMode)
                }
            }
        }
    }

    private fun simpleObjectWrite(factory: CirJsonFactory, generatorMode: Int, parserMode: Int) {
        val generator = createGenerator(factory, generatorMode)
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeName("first")
        generator.writeNumber(-901)
        generator.writeName("sec")
        generator.writeBoolean(false)
        generator.writeName("3rd!")
        generator.writeString("yee-haw")
        generator.writeEndObject()
        generator.close()
        val doc = generator.streamWriteOutputTarget!!.toString()
        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("first", parser.text)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(-901, parser.intValue)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("sec", parser.text)
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("3rd!", parser.text)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("yee-haw", parser.text)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testConvenienceMethods() {
        for (factory in factories) {
            for (generatorMode in ALL_GENERATOR_MODES) {
                for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                    convenienceMethods(factory, generatorMode, parserMode)
                }
            }
        }
    }

    private fun convenienceMethods(factory: CirJsonFactory, generatorMode: Int, parserMode: Int) {
        val generator = createGenerator(factory, generatorMode)
        generator.writeStartObject()
        generator.writeObjectId(Any())
        val text = "\"some\nString!\""

        generator.writeNullProperty("null")
        generator.writeBooleanProperty("booleanTrue", true)
        generator.writeBooleanProperty("booleanFalse", false)
        generator.writeNumberProperty("short", (-12345).toShort())
        generator.writeNumberProperty("int", Int.MIN_VALUE + 1707)
        generator.writeNumberProperty("long", Int.MIN_VALUE - 1707L)
        generator.writeNumberProperty("bigInteger",
                BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.valueOf(1707L)))
        generator.writeNumberProperty("float", 17.07f)
        generator.writeNumberProperty("double", 17.07)
        generator.writeNumberProperty("bigDecimal", BigDecimal("0.1"))

        generator.writeObjectPropertyStart("ob")
        generator.writeObjectId(Any())
        generator.writeStringProperty("str", text)
        generator.writeEndObject()

        generator.writeArrayPropertyStart("arr")
        generator.writeArrayId(Any())
        generator.writeEndArray()

        generator.writeBinaryProperty("bin", byteArrayOf(1, 2))

        generator.writeEndObject()
        generator.close()

        val doc = generator.streamWriteOutputTarget!!.toString()
        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("null", parser.text)
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("booleanTrue", parser.text)
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("booleanFalse", parser.text)
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("short", parser.text)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.INT, parser.numberType)

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("int", parser.text)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.INT, parser.numberType)

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("long", parser.text)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.LONG, parser.numberType)

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("bigInteger", parser.text)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.BIG_INTEGER, parser.numberType)

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("float", parser.text)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.DOUBLE, parser.numberType)

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("double", parser.text)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.DOUBLE, parser.numberType)

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("bigDecimal", parser.text)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.DOUBLE, parser.numberType)

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("ob", parser.text)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("str", parser.text)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(text, getAndVerifyText(parser))

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("arr", parser.text)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("bin", parser.text)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("AQI=", parser.text)

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testConvenienceMethodsWithNulls() {
        for (factory in factories) {
            for (generatorMode in ALL_GENERATOR_MODES) {
                for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                    convenienceMethodsWithNulls(factory, generatorMode, parserMode)
                }
            }
        }
    }

    private fun convenienceMethodsWithNulls(factory: CirJsonFactory, generatorMode: Int, parserMode: Int) {
        val generator = createGenerator(factory, generatorMode)
        generator.writeStartObject()
        generator.writeObjectId(Any())

        generator.writeStringProperty("str", null)
        generator.writeNumberProperty("bigInteger", null as BigInteger?)
        generator.writeNumberProperty("bigDecimal", null as BigDecimal?)
        generator.writePOJOProperty("obj", null)

        generator.writeEndObject()
        generator.close()

        val doc = generator.streamWriteOutputTarget!!.toString()
        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("str", parser.text)
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("bigInteger", parser.text)
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("bigDecimal", parser.text)
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("obj", parser.text)
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

}