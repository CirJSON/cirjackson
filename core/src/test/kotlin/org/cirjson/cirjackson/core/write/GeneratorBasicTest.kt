package org.cirjson.cirjackson.core.write

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.ObjectWriteContext
import org.cirjson.cirjackson.core.TestBase
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import kotlin.test.*

class GeneratorBasicTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testStringWrite() {
        val inputs = arrayOf("", "X", "1234567890")

        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                for (useStringWrite in BOOLEAN_OPTIONS) {
                    for ((index, input) in inputs.withIndex()) {
                        stringWrite(generatorMode, parserMode, useStringWrite, index, input)
                    }
                }
            }
        }
    }

    private fun stringWrite(generatorMode: Int, parserMode: Int, useStringWrite: Boolean, index: Int, input: String) {
        val generator = createGenerator(factory, generatorMode)

        if (useStringWrite) {
            generator.writeString(input)
        } else {
            val length = input.length
            val buffer = CharArray(length + 20)
            input.toCharArray(buffer, index, 0, length)
            generator.writeString(buffer, index, length)
        }

        generator.flush()
        generator.close()
        val doc = generator.streamWriteOutputTarget!!.toString()
        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(input, parser.text)
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testIntValueWrite() {
        val values = intArrayOf(0, 1, -9, 32, -32, 57, 189, 2017, -9999, 13240, 123456, 1111111, 22222222, 123456789,
                7300999, -7300999, 99300999, -99300999, 999300999, -999300999, 1000300999, 2000500126, -1000300999,
                -2000500126, Int.MIN_VALUE, Int.MAX_VALUE)

        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                for (pad in BOOLEAN_OPTIONS) {
                    for (value in values) {
                        intValueWrite(generatorMode, parserMode, pad, value)
                    }
                }
            }
        }
    }

    private fun intValueWrite(generatorMode: Int, parserMode: Int, pad: Boolean, value: Int) {
        val generator = createGenerator(factory, generatorMode)
        generator.writeNumber(value)

        if (pad) {
            generator.writeRaw(" ")
        }

        generator.close()
        var doc = generator.streamWriteOutputTarget!!.toString()

        if (parserMode == MODE_DATA_INPUT && !pad) {
            doc += ' '
        }

        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(value, parser.intValue)
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testLongValueWrite() {
        val values = longArrayOf(0L, 1L, -1L, 2000100345, -12005002294L, 5111222333L, -5111222333L, 65111222333L,
                -65111222333L, 123456789012L, -123456789012L, 123456789012345L, -123456789012345L, 123456789012345789L,
                -123456789012345789L, Long.MIN_VALUE, Long.MAX_VALUE)

        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                for (pad in BOOLEAN_OPTIONS) {
                    for (value in values) {
                        longValueWrite(generatorMode, parserMode, pad, value)
                    }
                }
            }
        }
    }

    private fun longValueWrite(generatorMode: Int, parserMode: Int, pad: Boolean, value: Long) {
        val generator = createGenerator(factory, generatorMode)
        generator.writeNumber(value)

        if (pad) {
            generator.writeRaw(" ")
        }

        generator.close()
        var doc = generator.streamWriteOutputTarget!!.toString()

        if (parserMode == MODE_DATA_INPUT && !pad) {
            doc += ' '
        }

        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(value, parser.longValue)
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testBooleanWrite() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                for (pad in BOOLEAN_OPTIONS) {
                    for (value in BOOLEAN_OPTIONS) {
                        booleanWrite(generatorMode, parserMode, pad, value)
                    }
                }
            }
        }
    }

    private fun booleanWrite(generatorMode: Int, parserMode: Int, pad: Boolean, value: Boolean) {
        val generator = createGenerator(factory, generatorMode)
        generator.writeBoolean(value)

        if (pad) {
            generator.writeRaw(" ")
        }

        generator.close()
        var doc = generator.streamWriteOutputTarget!!.toString()

        if (parserMode == MODE_DATA_INPUT && !pad) {
            doc += ' '
        }

        val parser = createParser(factory, parserMode, doc)
        assertToken(if (value) CirJsonToken.VALUE_TRUE else CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertEquals(value.toString(), parser.text)
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testNullWrite() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                for (pad in BOOLEAN_OPTIONS) {
                    nullWrite(generatorMode, parserMode, pad)
                }
            }
        }
    }

    private fun nullWrite(generatorMode: Int, parserMode: Int, pad: Boolean) {
        val generator = createGenerator(factory, generatorMode)
        generator.writeNull()

        if (pad) {
            generator.writeRaw(" ")
        }

        generator.close()
        var doc = generator.streamWriteOutputTarget!!.toString()

        if (parserMode == MODE_DATA_INPUT && !pad) {
            doc += ' '
        }

        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertEquals("null", parser.text)
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testRootIntegersWrite() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                rootIntegersWrite(generatorMode, parserMode)
            }
        }
    }

    private fun rootIntegersWrite(generatorMode: Int, parserMode: Int) {
        val generator = createGenerator(factory, generatorMode)
        generator.writeNumber(1)
        generator.writeNumber(2.toShort())
        generator.writeNumber(-13L)

        generator.close()
        var doc = generator.streamWriteOutputTarget!!.toString()

        if (parserMode == MODE_DATA_INPUT) {
            doc += ' '
        }

        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.intValue)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(2, parser.intValue)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(-13, parser.intValue)
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testOutputContext() {
        for (mode in ALL_GENERATOR_MODES) {
            outputContext(mode)
        }
    }

    private fun outputContext(mode: Int) {
        val generator = createGenerator(factory, mode)
        assertTrue(generator.streamWriteContext.isInRoot)

        generator.writeStartObject()
        assertTrue(generator.streamWriteContext.isInObject)
        generator.writeObjectId(Any())

        generator.writeName("a")
        assertEquals("a", generator.streamWriteContext.currentName)

        generator.writeStartArray()
        assertTrue(generator.streamWriteContext.isInArray)
        generator.writeArrayId(Any())

        generator.writeStartObject()
        assertTrue(generator.streamWriteContext.isInObject)
        generator.writeObjectId(Any())

        generator.writeName("b")
        assertEquals("b", generator.streamWriteContext.currentName)
        generator.writeNumber(123)
        assertEquals("b", generator.streamWriteContext.currentName)

        generator.writeName("c")
        assertEquals("c", generator.streamWriteContext.currentName)
        generator.writeNumber(5)
        assertEquals("c", generator.streamWriteContext.currentName)

        generator.writeName("d")
        assertEquals("d", generator.streamWriteContext.currentName)

        generator.writeStartArray()
        var context = generator.streamWriteContext
        assertTrue(context.isInArray)
        assertEquals(0, context.currentIndex)
        assertEquals(0, context.entryCount)
        generator.writeArrayId(Any())

        generator.writeBoolean(true)
        context = generator.streamWriteContext
        assertTrue(context.isInArray)
        assertEquals(1, context.currentIndex)
        assertEquals(2, context.entryCount)
        generator.writeArrayId(Any())

        generator.writeNumber(3)
        context = generator.streamWriteContext
        assertTrue(context.isInArray)
        assertEquals(3, context.currentIndex)
        assertEquals(4, context.entryCount)

        generator.writeEndArray()
        assertTrue(generator.streamWriteContext.isInObject)

        generator.writeEndObject()
        assertTrue(generator.streamWriteContext.isInArray)

        generator.writeEndArray()
        assertTrue(generator.streamWriteContext.isInObject)

        generator.writeEndObject()
        assertTrue(generator.streamWriteContext.isInRoot)
        generator.close()
    }

    @Test
    fun testOutputTarget() {
        val output = ByteArrayOutputStream()
        var generator = factory.createGenerator(ObjectWriteContext.empty(), output)
        assertSame(output, generator.streamWriteOutputTarget)
        generator.close()

        val writer = StringWriter()
        generator = factory.createGenerator(ObjectWriteContext.empty(), writer)
        assertSame(writer, generator.streamWriteOutputTarget)
        generator.close()
    }

    @Test
    fun testStreamWriteOutputBuffered() {
        for (mode in ALL_GENERATOR_MODES) {
            streamWriteOutputBuffered(mode)
        }
    }

    private fun streamWriteOutputBuffered(mode: Int) {
        val generator = createGenerator(factory, mode)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeNumber(1234)
        assertEquals(9, generator.streamWriteOutputBuffered)
        generator.flush()
        assertEquals(0, generator.streamWriteOutputBuffered)
        generator.writeEndArray()
        assertEquals(1, generator.streamWriteOutputBuffered)
        generator.close()
        assertEquals(0, generator.streamWriteOutputBuffered)
    }

}