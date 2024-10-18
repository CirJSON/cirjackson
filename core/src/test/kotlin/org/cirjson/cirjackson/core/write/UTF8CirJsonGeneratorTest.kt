package org.cirjson.cirjackson.core.write

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.ObjectWriteContext
import org.cirjson.cirjackson.core.StreamWriteConstraints
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.UTF8CirJsonGenerator
import org.cirjson.cirjackson.core.exception.StreamConstraintsException
import org.cirjson.cirjackson.core.util.DefaultPrettyPrinter
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class UTF8CirJsonGeneratorTest : TestBase() {

    private val factory = newStreamFactory()

    private val factoryMaxNesting1 =
            CirJsonFactory.builder().streamWriteConstraints(StreamWriteConstraints.builder().maxNestingDepth(1).build())
                    .build()

    @Test
    fun testSimple() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            simple(mode)
        }
    }

    private fun simple(mode: Int) {
        val outputStream = ByteArrayOutputStream()
        val context = testIOContext()
        val generator = UTF8CirJsonGenerator(ObjectWriteContext.empty(), context, 0, 0, outputStream,
                DefaultPrettyPrinter.DEFAULT_ROOT_VALUE_SEPARATOR, null, null, 0, '"'.code.toByte())
        val string = "Natuurlijk is alles gelukt en weer een tevreden klant\uD83D\uDE04"
        val length = 4000 - 38

        for (i in 1..length) {
            generator.writeNumber(1)
        }

        generator.writeString(string)
        generator.flush()
        generator.close()

        val doc = outputStream.toByteArray()
        val parser = createParser(factory, mode, doc)

        for (i in 1..length) {
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(1, parser.intValue)
        }

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(string, parser.text)
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testNestingDepthWithSmallLimitNestedArray() {
        val output = ByteArrayOutputStream()
        val generator = factoryMaxNesting1.createGenerator(ObjectWriteContext.empty(), output)

        try {
            generator.writeStartObject()
            generator.writeObjectId(Any())
            generator.writeName("array")
            generator.writeStartArray()
            fail("Should have thrown an exception")
        } catch (e: StreamConstraintsException) {
            verifyException(e,
                    "Document nesting depth (2) exceeds the maximum allowed (1, from `StreamWriteConstraints.maxNestingDepth`)")
        }

        generator.close()
    }

    @Test
    fun testNestingDepthWithSmallLimitNestedObject() {
        val output = ByteArrayOutputStream()
        val generator = factoryMaxNesting1.createGenerator(ObjectWriteContext.empty(), output)

        try {
            generator.writeStartObject()
            generator.writeObjectId(Any())
            generator.writeName("object")
            generator.writeStartObject()
            fail("Should have thrown an exception")
        } catch (e: StreamConstraintsException) {
            verifyException(e,
                    "Document nesting depth (2) exceeds the maximum allowed (1, from `StreamWriteConstraints.maxNestingDepth`)")
        }

        generator.close()
    }

    @Test
    fun testSurrogatesWithRaw() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            surrogatesWithRaw(mode)
        }
    }

    private fun surrogatesWithRaw(mode: Int) {
        val value = quote("\uD83D\uDE0C")
        val output = ByteArrayOutputStream()
        val generator = factoryMaxNesting1.createGenerator(ObjectWriteContext.empty(), output)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeRaw(',')
        generator.writeRaw(value)
        generator.writeEndArray()
        generator.close()

        val doc = output.toByteArray()
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        val string = parser.text!!
        assertEquals(2, string.length)
        assertEquals(0xD83D.toChar(), string[0])
        assertEquals(0xDE0C.toChar(), string[1])
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    // TODO: testFilteringWithEscapedChars

}