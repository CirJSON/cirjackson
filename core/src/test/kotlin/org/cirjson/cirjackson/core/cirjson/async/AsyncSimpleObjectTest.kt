package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.ObjectWriteContext
import org.cirjson.cirjackson.core.exception.InputCoercionException
import org.cirjson.cirjackson.core.exception.StreamReadException
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import kotlin.test.*

class AsyncSimpleObjectTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testBooleans() {
        val doc = utf8Bytes(apostropheToQuote(
                "{ '__cirJsonId__':'root', 'a':true, 'b':false, 'acdc':true, '$UNICODE_SHORT_NAME':true, 'a1234567':false,'$UNICODE_LONG_NAME':   true }"))

        for (mode in ALL_ASYNC_MODES) {
            booleans(mode, 0, doc, 99)
            booleans(mode, 0, doc, 5)
            booleans(mode, 0, doc, 3)
            booleans(mode, 0, doc, 2)
            booleans(mode, 0, doc, 1)

            booleans(mode, 1, doc, 99)
            booleans(mode, 1, doc, 3)
            booleans(mode, 1, doc, 1)
        }
    }

    private fun booleans(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("a", parser.currentText())
        assertFalse(parser.parser.isTextCharactersAvailable)
        val ch = parser.parser.textCharacters!!
        assertEquals(0, parser.parser.textOffset)
        assertEquals(1, parser.parser.textLength)
        assertEquals("a", String(ch, 0, 1))
        assertTrue(parser.parser.isTextCharactersAvailable)

        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("b", parser.currentText())
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("acdc", parser.currentText())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(UNICODE_SHORT_NAME, parser.currentText())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("a1234567", parser.currentText())
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(UNICODE_LONG_NAME, parser.currentText())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())

        try {
            parser.doubleValue
            fail("Should have thrown an exception")
        } catch (e: InputCoercionException) {
            verifyException(e, "Current token (VALUE_TRUE) not numeric")
        }

        try {
            parser.binaryValue
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Current token (VALUE_TRUE) not")
            verifyException(e, "can not access as binary")
        }

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        assertTrue(parser.isClosed)
    }

    @Test
    fun testNumbers() {
        val output = ByteArrayOutputStream()
        val generator = factory.createGenerator(ObjectWriteContext.empty(), output)
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeNumberProperty("i1", NUMBER_EXP_INTEGER)
        generator.writeNumberProperty("doubley", NUMBER_EXP_DOUBLE)
        generator.writeNumberProperty("biggieDecimal", NUMBER_EXP_BIG_DECIMAL)
        generator.writeEndObject()
        generator.close()
        val doc = output.toByteArray()

        for (mode in ALL_ASYNC_MODES) {
            numbers(mode, 0, doc, 99)
            numbers(mode, 0, doc, 5)
            numbers(mode, 0, doc, 3)
            numbers(mode, 0, doc, 2)
            numbers(mode, 0, doc, 1)

            numbers(mode, 1, doc, 99)
            numbers(mode, 1, doc, 3)
            numbers(mode, 1, doc, 1)
        }
    }

    private fun numbers(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("i1", parser.currentText())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.INT, parser.numberType)
        assertEquals(NUMBER_EXP_INTEGER, parser.intValue)
        assertEquals(NUMBER_EXP_INTEGER.toDouble(), parser.doubleValue)

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("doubley", parser.currentText())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.DOUBLE, parser.numberType)
        assertEquals(NUMBER_EXP_DOUBLE, parser.doubleValue)
        assertEquals(NUMBER_EXP_DOUBLE.toLong(), parser.longValue)

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("biggieDecimal", parser.currentText())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.DOUBLE, parser.numberType)
        assertEquals(NUMBER_EXP_BIG_DECIMAL, parser.bigDecimalValue)
        assertEquals(NUMBER_EXP_BIG_DECIMAL.toString(), parser.currentText())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        assertTrue(parser.isClosed)
    }

    companion object {

        private const val UNICODE_SHORT_NAME = "Unicode${UNICODE_3_BYTES}RlzOk"

        private const val UNICODE_LONG_NAME = "Unicode-with-$UNICODE_3_BYTES-much-longer"

        private const val NUMBER_EXP_INTEGER = -123456789

        private const val NUMBER_EXP_DOUBLE = 1024798.125

        private val NUMBER_EXP_BIG_DECIMAL = BigDecimal("1243565768679065.1247305834")

    }

}