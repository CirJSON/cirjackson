package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.StreamReadConstraints
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.exception.InputCoercionException
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class NumberOverflowTest : TestBase() {

    private val factory = CirJsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNumberLength(1000000).build()).build()

    @Test
    fun testSimpleLongOverflow() {
        val below = BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE)
        val above = BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE)
        val belowDoc = "$below "
        val aboveDoc = "$above "

        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            simpleLongOverflow(mode, belowDoc)
            simpleLongOverflow(mode, aboveDoc)
        }
    }

    private fun simpleLongOverflow(mode: Int, doc: String) {
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())

        try {
            val long = parser.longValue
            fail("Expected an exception for underflow (input ${parser.text}): instead, got long value: $long")
        } catch (e: InputCoercionException) {
            verifyException(e, "out of range of `long`")
            assertToken(CirJsonToken.VALUE_NUMBER_INT, e.inputType)
            assertEquals(Long::class.java, e.targetType)
        }

        parser.close()
    }

    @Test
    fun testMaliciousLongOverflow() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (doc in DOCS) {
                maliciousLongOverflow(mode, doc)
            }
        }
    }

    private fun maliciousLongOverflow(mode: Int, doc: String) {
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())

        try {
            parser.longValue
            fail("Should have thrown an exception")
        } catch (e: InputCoercionException) {
            verifyException(e, "out of range of `long`")
            verifyException(e, "Integer with $BIG_NUM_LEN digits")
            assertToken(CirJsonToken.VALUE_NUMBER_INT, e.inputType)
            assertEquals(Long::class.java, e.targetType)
        }

        parser.close()
    }

    @Test
    fun testMaliciousIntOverflow() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (doc in DOCS) {
                maliciousIntOverflow(mode, doc)
            }
        }
    }

    private fun maliciousIntOverflow(mode: Int, doc: String) {
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())

        try {
            parser.intValue
            fail("Should have thrown an exception")
        } catch (e: InputCoercionException) {
            verifyException(e, "out of range of `int`")
            verifyException(e, "Integer with $BIG_NUM_LEN digits")
            assertToken(CirJsonToken.VALUE_NUMBER_INT, e.inputType)
            assertEquals(Int::class.java, e.targetType)
        }

        parser.close()
    }

    @Test
    fun testMaliciousBigIntToDouble() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (doc in DOCS) {
                maliciousBigIntToDouble(mode, doc)
            }
        }
    }

    private fun maliciousBigIntToDouble(mode: Int, doc: String) {
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        val double = parser.doubleValue
        val stringValue = if (doc === BIG_NEG_DOC) "-$BIG_POS_INTEGER" else BIG_POS_INTEGER
        assertEquals(stringValue.toDouble(), double)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    @Test
    fun testMaliciousBigIntToFloat() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (doc in DOCS) {
                maliciousBigIntToFloat(mode, doc)
            }
        }
    }

    private fun maliciousBigIntToFloat(mode: Int, doc: String) {
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        val float = parser.floatValue
        val stringValue = if (doc === BIG_NEG_DOC) "-$BIG_POS_INTEGER" else BIG_POS_INTEGER
        assertEquals(stringValue.toFloat(), float)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    companion object {

        private const val BIG_NUM_LEN = 199999

        private val BIG_POS_INTEGER = StringBuilder(BIG_NUM_LEN).apply {
            for (i in 1..BIG_NUM_LEN) {
                append('9')
            }
        }.toString()

        private val BIG_POS_DOC = "[\"root\", $BIG_POS_INTEGER]"

        private val BIG_NEG_DOC = "[\"root\", -$BIG_POS_INTEGER]"

        private val DOCS = arrayOf(BIG_POS_DOC, BIG_NEG_DOC)

    }

}