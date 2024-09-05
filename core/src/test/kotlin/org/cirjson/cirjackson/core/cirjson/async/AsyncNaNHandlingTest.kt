package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.*

class AsyncNaNHandlingTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testDefaultsForAsync() {
        assertFalse(factory.isEnabled(CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS))
    }

    @Test
    fun testDisallowNaN() {
        for (mode in ALL_ASYNC_MODES) {
            disallowNaN(mode, 0, 99)
            disallowNaN(mode, 0, 5)
            disallowNaN(mode, 0, 3)
            disallowNaN(mode, 0, 2)
            disallowNaN(mode, 0, 1)

            disallowNaN(mode, 1, 99)
            disallowNaN(mode, 1, 3)
            disallowNaN(mode, 1, 1)
        }
    }

    private fun disallowNaN(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[ \"0\", NaN]")
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "non-standard")
        }

        parser.close()
    }

    @Test
    fun testAllowNaN() {
        for (mode in ALL_ASYNC_MODES) {
            val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS).build()
            allowNaN(factory, mode, 0, 99)
            allowNaN(factory, mode, 0, 5)
            allowNaN(factory, mode, 0, 3)
            allowNaN(factory, mode, 0, 2)
            allowNaN(factory, mode, 0, 1)

            allowNaN(factory, mode, 1, 99)
            allowNaN(factory, mode, 1, 3)
            allowNaN(factory, mode, 1, 1)
        }
    }

    private fun allowNaN(factory: CirJsonFactory, mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[ \"0\", NaN]")
        var parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())

        val double = parser.doubleValue
        assertTrue(double.isNaN())
        assertEquals("NaN", parser.currentText())

        try {
            parser.bigDecimalValue
            fail("Should fail when trying to access NaN as BigDecimal")
        } catch (e: NumberFormatException) {
            verifyException(e, "can not be deserialized as `java.math.BigDecimal`")
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()

        val otherFactory = factory.rebuild().enable(CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS).build()
        parser = createAsync(otherFactory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    @Test
    fun testDisallowInfinity() {
        for (mode in ALL_ASYNC_MODES) {
            for (token in INFINITY_TOKENS) {
                disallowInfinity(mode, 0, token, 99)
                disallowInfinity(mode, 0, token, 5)
                disallowInfinity(mode, 0, token, 3)
                disallowInfinity(mode, 0, token, 2)
                disallowInfinity(mode, 0, token, 1)

                disallowInfinity(mode, 1, token, 99)
                disallowInfinity(mode, 1, token, 3)
                disallowInfinity(mode, 1, token, 1)
            }
        }
    }

    private fun disallowInfinity(mode: Int, padding: Int, token: String, bytesPerFeed: Int) {
        val doc = utf8Bytes("[ \"0\", $token]")
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            val cirJsonToken = parser.nextToken()
            fail("Expected exception; got $cirJsonToken (text [${parser.currentText()}])")
        } catch (e: StreamReadException) {
            verifyException(e, "Non-standard token '$token'")
        }

        parser.close()
    }

    @Test
    fun testAllowInfinity() {
        for (mode in ALL_ASYNC_MODES) {
            for (doc in INFINITY_DOCS) {
                val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS).build()
                allowInfinity(factory, mode, 0, doc, 99)
                allowInfinity(factory, mode, 0, doc, 5)
                allowInfinity(factory, mode, 0, doc, 3)
                allowInfinity(factory, mode, 0, doc, 2)
                allowInfinity(factory, mode, 0, doc, 1)

                allowInfinity(factory, mode, 1, doc, 99)
                allowInfinity(factory, mode, 1, doc, 3)
                allowInfinity(factory, mode, 1, doc, 1)
            }
        }
    }

    private fun allowInfinity(factory: CirJsonFactory, mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        var parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        var double = parser.doubleValue
        assertTrue(double.isInfinite())
        assertEquals("Infinity", parser.currentText())
        assertEquals(Double.POSITIVE_INFINITY, double)

        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        double = parser.doubleValue
        assertTrue(double.isInfinite())
        assertEquals("+Infinity", parser.currentText())
        assertEquals(Double.POSITIVE_INFINITY, double)

        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        double = parser.doubleValue
        assertTrue(double.isInfinite())
        assertEquals("-Infinity", parser.currentText())
        assertEquals(Double.NEGATIVE_INFINITY, double)

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()

        val otherFactory = factory.rebuild().enable(CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS).build()
        parser = createAsync(otherFactory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    companion object {

        private val INFINITY_TOKENS = arrayOf("Infinity", "-Infinity", "+Infinity")

        private val INFINITY_DOCS = arrayOf("[ \"0\", Infinity, +Infinity, -Infinity ]".toByteArray(),
                "[ \"0\", Infinity,+Infinity,-Infinity]".toByteArray(),
                "[ \"0\", Infinity  ,   +Infinity   ,   -Infinity]".toByteArray())

    }

}