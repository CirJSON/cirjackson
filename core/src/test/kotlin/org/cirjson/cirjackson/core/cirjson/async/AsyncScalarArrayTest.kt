package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import java.io.StringWriter
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("FloatingPointLiteralPrecision")
class AsyncScalarArrayTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testTokens() {
        val doc = utf8Bytes("  [ \"root\", true, false  ,true   , null,false , null]")

        for (mode in ALL_ASYNC_MODES) {
            tokens(mode, 0, doc, 99)
            tokens(mode, 0, doc, 5)
            tokens(mode, 0, doc, 3)
            tokens(mode, 0, doc, 2)
            tokens(mode, 0, doc, 1)

            tokens(mode, 1, doc, 99)
            tokens(mode, 1, doc, 3)
            tokens(mode, 1, doc, 1)
        }
    }

    private fun tokens(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertEquals("false", parser.currentText())
        assertEquals("false", parser.currentTextViaCharacters())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertEquals("null", parser.currentTextViaWriter())
        assertEquals("null", parser.currentText())
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        assertTrue(parser.isClosed)
    }

    @Test
    fun testIntegers() {
        val doc = utf8Bytes(createDocString(INTEGERS))

        for (mode in ALL_ASYNC_MODES) {
            integers(mode, 0, doc, 99)
            integers(mode, 0, doc, 5)
            integers(mode, 0, doc, 3)
            integers(mode, 0, doc, 2)
            integers(mode, 0, doc, 1)

            integers(mode, 1, doc, 99)
            integers(mode, 1, doc, 3)
            integers(mode, 1, doc, 1)
        }
    }

    private fun integers(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (value in INTEGERS) {
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(value, parser.intValue)
            assertEquals(CirJsonParser.NumberType.INT, parser.numberType)

            val asString = value.toString()
            assertEquals(asString, parser.currentText())
            val stringWriter = StringWriter()
            assertEquals(asString.length, parser.parser.getText(stringWriter))
            assertEquals(asString, stringWriter.toString())
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        assertTrue(parser.isClosed)
    }

    @Test
    fun testLongs() {
        val doc = utf8Bytes(createDocString(LONGS))

        for (mode in ALL_ASYNC_MODES) {
            longs(mode, 0, doc, 99)
            longs(mode, 0, doc, 5)
            longs(mode, 0, doc, 3)
            longs(mode, 0, doc, 2)
            longs(mode, 0, doc, 1)

            longs(mode, 1, doc, 99)
            longs(mode, 1, doc, 3)
            longs(mode, 1, doc, 1)
        }
    }

    private fun longs(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (value in LONGS) {
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(value, parser.longValue)
            assertEquals(CirJsonParser.NumberType.LONG, parser.numberType)

            val asString = value.toString()
            assertEquals(asString, parser.currentText())
            val stringWriter = StringWriter()
            assertEquals(asString.length, parser.parser.getText(stringWriter))
            assertEquals(asString, stringWriter.toString())
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        assertTrue(parser.isClosed)
    }

    @Test
    fun testFloats() {
        val doc = utf8Bytes(createDocString(FLOATS))

        for (mode in ALL_ASYNC_MODES) {
            floats(mode, 0, doc, 99)
            floats(mode, 0, doc, 5)
            floats(mode, 0, doc, 3)
            floats(mode, 0, doc, 2)
            floats(mode, 0, doc, 1)

            floats(mode, 1, doc, 99)
            floats(mode, 1, doc, 3)
            floats(mode, 1, doc, 1)
        }
    }

    private fun floats(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (value in FLOATS) {
            assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
            assertEquals(value, parser.floatValue)
            assertEquals(CirJsonParser.NumberType.FLOAT, parser.numberType)

            val asString = value.toString()
            assertEquals(asString, parser.currentText())
            val stringWriter = StringWriter()
            assertEquals(asString.length, parser.parser.getText(stringWriter))
            assertEquals(asString, stringWriter.toString())
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        assertTrue(parser.isClosed)
    }

    @Test
    fun testDoubles() {
        val doc = utf8Bytes(createDocString(DOUBLES))

        for (mode in ALL_ASYNC_MODES) {
            doubles(mode, 0, doc, 99)
            doubles(mode, 0, doc, 5)
            doubles(mode, 0, doc, 3)
            doubles(mode, 0, doc, 2)
            doubles(mode, 0, doc, 1)

            doubles(mode, 1, doc, 99)
            doubles(mode, 1, doc, 3)
            doubles(mode, 1, doc, 1)
        }
    }

    private fun doubles(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (value in DOUBLES) {
            assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
            assertEquals(value, parser.doubleValue)
            assertEquals(CirJsonParser.NumberType.DOUBLE, parser.numberType)

            val asString = value.toString()
            assertEquals(asString, parser.currentText())
            val stringWriter = StringWriter()
            assertEquals(asString.length, parser.parser.getText(stringWriter))
            assertEquals(asString, stringWriter.toString())
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        assertTrue(parser.isClosed)
    }

    @Test
    fun testBigIntegers() {
        val doc = utf8Bytes(createDocString(BIG_INTEGERS))

        for (mode in ALL_ASYNC_MODES) {
            bigIntegers(mode, 0, doc, 99)
            bigIntegers(mode, 0, doc, 5)
            bigIntegers(mode, 0, doc, 3)
            bigIntegers(mode, 0, doc, 2)
            bigIntegers(mode, 0, doc, 1)

            bigIntegers(mode, 1, doc, 99)
            bigIntegers(mode, 1, doc, 3)
            bigIntegers(mode, 1, doc, 1)
        }
    }

    private fun bigIntegers(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (value in BIG_INTEGERS) {
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(value, parser.bigIntegerValue)
            assertEquals(CirJsonParser.NumberType.BIG_INTEGER, parser.numberType)

            val asString = value.toString()
            assertEquals(asString, parser.currentText())
            val stringWriter = StringWriter()
            assertEquals(asString.length, parser.parser.getText(stringWriter))
            assertEquals(asString, stringWriter.toString())
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        assertTrue(parser.isClosed)
    }

    @Test
    fun testBigDecimals() {
        val doc = utf8Bytes(createDocString(BIG_DECIMALS))

        for (mode in ALL_ASYNC_MODES) {
            bigDecimals(mode, 0, doc, 99)
            bigDecimals(mode, 0, doc, 5)
            bigDecimals(mode, 0, doc, 3)
            bigDecimals(mode, 0, doc, 2)
            bigDecimals(mode, 0, doc, 1)

            bigDecimals(mode, 1, doc, 99)
            bigDecimals(mode, 1, doc, 3)
            bigDecimals(mode, 1, doc, 1)
        }
    }

    private fun bigDecimals(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (value in BIG_DECIMALS) {
            assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
            assertEquals(value, parser.bigDecimalValue)
            assertEquals(CirJsonParser.NumberType.BIG_DECIMAL, parser.numberType)

            val asString = value.toString()
            assertEquals(asString, parser.currentText())
            val stringWriter = StringWriter()
            assertEquals(asString.length, parser.parser.getText(stringWriter))
            assertEquals(asString, stringWriter.toString())
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        assertTrue(parser.isClosed)
    }

    private fun createDocString(elements: Array<*>): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append("[\"root\"")

        for (element in elements) {
            stringBuilder.append(',').append(element.toString())
        }

        stringBuilder.append("]")
        return stringBuilder.toString()
    }

    companion object {

        private val INTEGERS =
                arrayOf(1, -1, 16, -17, 0, 131, -0, -155, 1000, -3000, 0xFFFF, -99999, Int.MAX_VALUE, 0, Int.MIN_VALUE)

        private val LONGS = arrayOf(-1L + Int.MIN_VALUE, 1L + Int.MAX_VALUE, 19L * Int.MIN_VALUE, 27L * Int.MAX_VALUE,
                Long.MIN_VALUE, Long.MAX_VALUE)

        private val FLOATS = arrayOf(0.0f, 0.25f, -0.5f, 10000.125f, -99999.075f)

        private val DOUBLES = arrayOf(0.0, 0.25, -0.5, 10000.125, -99999.075)

        private val BIG_INTEGERS = BigInteger.valueOf(Long.MAX_VALUE).let {
            arrayOf(it.shiftLeft(100).add(BigInteger.valueOf(123456789L)), it.add(it),
                    it.multiply(BigInteger.valueOf(17)), it.negate().subtract(BigInteger.TEN))
        }

        private val BIG_DECIMALS = BigDecimal("1234567890344656736.125").let {
            arrayOf(BigDecimal.valueOf(-999.25), it, it.divide(BigDecimal("5")), it.add(it),
                    it.multiply(BigDecimal("1.23")), it.negate())
        }

    }

}