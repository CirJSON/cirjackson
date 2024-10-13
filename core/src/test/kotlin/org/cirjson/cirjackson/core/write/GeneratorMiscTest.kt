package org.cirjson.cirjackson.core.write

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import java.io.ByteArrayOutputStream
import java.io.DataOutput
import java.io.DataOutputStream
import java.io.OutputStreamWriter
import kotlin.test.*

class GeneratorMiscTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testIsClosed() {
        for (mode in ALL_GENERATOR_MODES) {
            isClosed(mode)
        }
    }

    private fun isClosed(mode: Int) {
        val generator = createGenerator(factory, mode)
        assertFalse(generator.isClosed)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeNumber(-1)
        generator.writeEndArray()
        assertFalse(generator.isClosed)
        generator.close()
        assertTrue(generator.isClosed)
        generator.close()
        assertTrue(generator.isClosed)
    }

    @Test
    fun testWriteRaw() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                writeRaw(generatorMode, parserMode)
            }
        }
    }

    private fun writeRaw(generatorMode: Int, parserMode: Int) {
        val generator = createGenerator(factory, generatorMode)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeRaw(',')
        generator.writeRaw("-123, true")
        generator.writeRaw(", \"x\"  ")
        generator.writeEndArray()
        generator.close()

        val doc = generator.streamWriteOutputTarget!!.toString()
        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(-123, parser.intValue)
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("x", parser.text)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testWriteRawValue() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                writeRawValue(generatorMode, parserMode)
            }
        }
    }

    private fun writeRawValue(generatorMode: Int, parserMode: Int) {
        val generator = createGenerator(factory, generatorMode)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeRawValue("7")
        generator.writeRawValue("[ \"1\", null ]")
        generator.writeRawValue("false")
        generator.writeEndArray()
        generator.close()

        val doc = generator.streamWriteOutputTarget!!.toString()
        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(7, parser.intValue)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testLongerObjects() {
        for (generatorMode in EXTENDED_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                longerObjects(generatorMode, parserMode)
            }
        }
    }

    private fun longerObjects(generatorMode: Int, parserMode: Int) {
        val output = ByteArrayOutputStream(200)
        val generator = createGenerator(factory, generatorMode, output)

        generator.writeStartObject()
        generator.writeObjectId(Any())

        for (round in 1..1500) {
            for (letter in 'a'..'z') {
                for (i in 0..<20) {
                    val name = if (letter > 'p') {
                        "X$letter$i"
                    } else if (letter > 'f') {
                        "$letter$i"
                    } else {
                        "__$i$letter"
                    }

                    generator.writeName(name)
                    generator.writeNumber(i - 1)
                }

                generator.writeRaw('\n')
            }
        }

        generator.writeEndObject()
        generator.close()

        val doc = output.toByteArray()
        val parser = createParser(factory, parserMode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (round in 1..1500) {
            for (letter in 'a'..'z') {
                for (i in 0..<20) {
                    assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())

                    val name = if (letter > 'p') {
                        "X$letter$i"
                    } else if (letter > 'f') {
                        "$letter$i"
                    } else {
                        "__$i$letter"
                    }

                    assertEquals(name, parser.currentName)
                    assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
                    assertEquals(i - 1, parser.intValue)
                }
            }
        }

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    private fun createGenerator(factory: CirJsonFactory, generatorMode: Int,
            output: ByteArrayOutputStream): CirJsonGenerator {
        return when (generatorMode) {
            MODE_OUTPUT_STREAM -> {
                factory.createGenerator(ObjectWriteContext.empty(), output, CirJsonEncoding.UTF8)
            }

            MODE_WRITER -> {
                factory.createGenerator(ObjectWriteContext.empty(), OutputStreamWriter(output, Charsets.UTF_8))
            }

            MODE_DATA_OUTPUT -> {
                factory.createGenerator(ObjectWriteContext.empty(), DataOutputStream(output) as DataOutput)
            }

            else -> throw RuntimeException("internal error")
        }
    }

    companion object {

        private const val MODE_DATA_OUTPUT = 2

        private val EXTENDED_GENERATOR_MODES = arrayOf(MODE_OUTPUT_STREAM, MODE_WRITER, MODE_DATA_OUTPUT)

    }

}