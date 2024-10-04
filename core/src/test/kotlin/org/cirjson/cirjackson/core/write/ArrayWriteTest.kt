package org.cirjson.cirjackson.core.write

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.StreamWriteException
import kotlin.test.*

class ArrayWriteTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testEmptyArrayWrite() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                emptyArrayWrite(generatorMode, parserMode)
            }
        }
    }

    private fun emptyArrayWrite(generatorMode: Int, parserMode: Int) {
        var generator = createGenerator(factory, generatorMode)

        var context = generator.streamWriteContext
        assertTrue(context.isInRoot)
        assertFalse(context.isInArray)
        assertFalse(context.isInObject)
        assertEquals(0, context.entryCount)
        assertEquals(0, context.currentIndex)

        generator.writeStartArray()
        context = generator.streamWriteContext
        assertFalse(context.isInRoot)
        assertTrue(context.isInArray)
        assertFalse(context.isInObject)
        assertEquals(0, context.entryCount)
        assertEquals(0, context.currentIndex)

        generator.writeArrayId(Any())

        generator.writeEndArray()
        context = generator.streamWriteContext
        assertTrue(context.isInRoot)
        assertFalse(context.isInArray)
        assertFalse(context.isInObject)
        assertEquals(1, context.entryCount)
        assertEquals(0, context.currentIndex)

        generator.close()
        var doc = generator.streamWriteOutputTarget!!.toString()
        var parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()

        generator = createGenerator(factory, generatorMode)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeEndArray()
        generator.writeEndArray()
        generator.close()
        doc = generator.streamWriteOutputTarget!!.toString()
        parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testInvalidArrayWrite() {
        for (mode in ALL_GENERATOR_MODES) {
            invalidArrayWrite(mode)
        }
    }

    private fun invalidArrayWrite(mode: Int) {
        val generator = createGenerator(factory, mode)
        generator.writeStartArray()
        generator.writeArrayId(Any())

        try {
            generator.writeEndObject()
            fail("Should have thrown an exception")
        } catch (e: StreamWriteException) {
            verifyException(e, "Current context not Object")
        }

        generator.close()
    }

    @Test
    fun testSimpleArrayWrite() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                simpleArrayWrite(generatorMode, parserMode)
            }
        }
    }

    private fun simpleArrayWrite(generatorMode: Int, parserMode: Int) {
        val generator = createGenerator(factory, generatorMode)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeNumber(13)
        generator.writeBoolean(true)
        generator.writeString("foobar")
        generator.writeEndArray()
        generator.close()
        val doc = generator.streamWriteOutputTarget!!.toString()
        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(13, parser.intValue)
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("foobar", parser.text)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

}