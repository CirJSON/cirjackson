package org.cirjson.cirjackson.core.write

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.io.SerializedString
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SerializedStringWriteTest : TestBase() {

    private val factory = newStreamFactory()

    private val nameQuoted = SerializedString(NAME_WITH_QUOTES)

    private val nameLatin1 = SerializedString(NAME_WITH_LATIN1)

    @Test
    fun testSimpleNames() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                simpleNames(generatorMode, parserMode)
            }
        }
    }

    private fun simpleNames(generatorMode: Int, parserMode: Int) {
        val generator = createGenerator(factory, generatorMode)
        writeSimpleNames(generator)
        val doc = generator.streamWriteOutputTarget!!.toString()
        val parser = createParser(factory, parserMode, doc)
        verifySimpleNames(parser)
    }

    private fun writeSimpleNames(generator: CirJsonGenerator) {
        generator.writeStartArray()
        generator.writeArrayId(Any())

        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeName(nameQuoted)
        generator.writeString("a")
        generator.writeName(nameLatin1)
        generator.writeString("b")
        generator.writeEndObject()

        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeName(nameLatin1)
        generator.writeString("c")
        generator.writeName(nameQuoted)
        generator.writeString("d")
        generator.writeEndObject()

        generator.writeEndArray()
        generator.close()
    }

    private fun verifySimpleNames(parser: CirJsonParser) {
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(NAME_WITH_QUOTES, parser.text)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("a", parser.text)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(NAME_WITH_LATIN1, parser.text)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("b", parser.text)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(NAME_WITH_LATIN1, parser.text)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("c", parser.text)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(NAME_WITH_QUOTES, parser.text)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("d", parser.text)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testSimpleValues() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                simpleValues(generatorMode, parserMode)
            }
        }
    }

    private fun simpleValues(generatorMode: Int, parserMode: Int) {
        val generator = createGenerator(factory, generatorMode)
        writeSimpleValues(generator)
        val doc = generator.streamWriteOutputTarget!!.toString()
        val parser = createParser(factory, parserMode, doc)
        verifySimpleValues(parser)
    }

    private fun writeSimpleValues(generator: CirJsonGenerator) {
        generator.writeStartArray()
        generator.writeArrayId(Any())

        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeName(NAME_WITH_QUOTES)
        generator.writeString(SerializedString(VALUE_WITH_QUOTES))
        generator.writeName(NAME_WITH_LATIN1)
        generator.writeString(VALUE_LONG)
        generator.writeEndObject()

        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeName(NAME_WITH_LATIN1)
        generator.writeString(VALUE_WITH_QUOTES)
        generator.writeName(NAME_WITH_QUOTES)
        generator.writeString(SerializedString(VALUE_LONG))
        generator.writeEndObject()

        generator.writeEndArray()
        generator.close()
    }

    private fun verifySimpleValues(parser: CirJsonParser) {
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(NAME_WITH_QUOTES, parser.text)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(VALUE_WITH_QUOTES, parser.text)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(NAME_WITH_LATIN1, parser.text)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(VALUE_LONG, parser.text)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(NAME_WITH_LATIN1, parser.text)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(VALUE_WITH_QUOTES, parser.text)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(NAME_WITH_QUOTES, parser.text)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(VALUE_LONG, parser.text)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    companion object {

        private const val NAME_WITH_QUOTES = "\"name\""

        private const val NAME_WITH_LATIN1 = "P\u00f6ll\u00f6"

        private const val VALUE_WITH_QUOTES = "\"Value\""

        private val VALUE_LONG = generateLongValue()

        private fun generateLongValue(): String {
            val stringBuilder = StringBuilder()
            val random = Random(123L)

            while (stringBuilder.length < 9000) {
                val c = random.nextInt(96)

                if (c < 32) {
                    stringBuilder.append((48 + c).toChar())
                } else if (c < 64) {
                    stringBuilder.append((128 + c).toChar())
                } else {
                    stringBuilder.append((4000 + c).toChar())
                }
            }

            return stringBuilder.toString()
        }

    }

}