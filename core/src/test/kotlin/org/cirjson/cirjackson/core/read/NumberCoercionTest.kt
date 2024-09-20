package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.InputCoercionException
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class NumberCoercionTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testToIntCoercion() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            toIntCoercion(mode)
        }
    }

    private fun toIntCoercion(mode: Int) {
        var parser = createParser(factory, mode, "1 ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1L, parser.longValue)
        assertEquals(1, parser.intValue)
        parser.close()

        parser = createParser(factory, mode, "10 ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(BigInteger.TEN, parser.bigIntegerValue)
        assertEquals(10, parser.intValue)
        parser.close()

        parser = createParser(factory, mode, "2 ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(2.0, parser.doubleValue)
        assertEquals(2, parser.intValue)
        parser.close()

        parser = createParser(factory, mode, "2 ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(2.0f, parser.floatValue)
        assertEquals(2, parser.intValue)
        parser.close()

        parser = createParser(factory, mode, "0.1 ")
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(0.1, parser.doubleValue)
        assertEquals(0, parser.intValue)
        parser.close()

        parser = createParser(factory, mode, "10 ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(BigDecimal.TEN, parser.bigDecimalValue)
        assertEquals(10, parser.intValue)
        parser.close()
    }

    @Test
    fun testToIntFailing() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            toIntFailing(mode)
        }
    }

    private fun toIntFailing(mode: Int) {
        val big = 1L + Int.MAX_VALUE
        createParser(factory, mode, "$big ").use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(big, parser.longValue)

            try {
                parser.intValue
                fail("Should have thrown an exception")
            } catch (e: InputCoercionException) {
                verifyException(e, "out of range of `int`")
                assertEquals(CirJsonToken.VALUE_NUMBER_INT, e.inputType)
                assertEquals(Int::class.java, e.targetType)
            }
        }

        val small = Int.MIN_VALUE - 1L
        createParser(factory, mode, "$small ").use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(small, parser.numberValue)
            assertEquals(small, parser.longValue)

            try {
                parser.intValue
                fail("Should have thrown an exception")
            } catch (e: InputCoercionException) {
                verifyException(e, "out of range of `int`")
                assertEquals(CirJsonToken.VALUE_NUMBER_INT, e.inputType)
                assertEquals(Int::class.java, e.targetType)
            }
        }

        createParser(factory, mode, "$big.0 ").use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
            assertEquals(big.toDouble(), parser.doubleValue)

            try {
                parser.intValue
                fail("Should have thrown an exception")
            } catch (e: InputCoercionException) {
                verifyException(e, "out of range of `int`")
                assertEquals(CirJsonToken.VALUE_NUMBER_FLOAT, e.inputType)
                assertEquals(Int::class.java, e.targetType)
            }
        }

        createParser(factory, mode, "$small.0 ").use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
            assertEquals(small.toDouble(), parser.doubleValue)

            try {
                parser.intValue
                fail("Should have thrown an exception")
            } catch (e: InputCoercionException) {
                verifyException(e, "out of range of `int`")
                assertEquals(CirJsonToken.VALUE_NUMBER_FLOAT, e.inputType)
                assertEquals(Int::class.java, e.targetType)
            }
        }

        createParser(factory, mode, "$big ").use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(BigInteger.valueOf(big), parser.bigIntegerValue)

            try {
                parser.intValue
                fail("Should have thrown an exception")
            } catch (e: InputCoercionException) {
                verifyException(e, "out of range of `int`")
                assertEquals(CirJsonToken.VALUE_NUMBER_INT, e.inputType)
                assertEquals(Int::class.java, e.targetType)
            }
        }

        createParser(factory, mode, "$small ").use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(BigInteger.valueOf(small), parser.bigIntegerValue)

            try {
                parser.intValue
                fail("Should have thrown an exception")
            } catch (e: InputCoercionException) {
                verifyException(e, "out of range of `int`")
                assertEquals(CirJsonToken.VALUE_NUMBER_INT, e.inputType)
                assertEquals(Int::class.java, e.targetType)
            }
        }
    }

    @Test
    fun testToLongCoercion() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            toLongCoercion(mode)
        }
    }

    private fun toLongCoercion(mode: Int) {
        var parser = createParser(factory, mode, "1 ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.intValue)
        assertEquals(1L, parser.longValue)
        parser.close()

        val biggish = 12345678901L
        parser = createParser(factory, mode, "$biggish ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(BigInteger.valueOf(biggish), parser.bigIntegerValue)
        assertEquals(biggish, parser.longValue)
        parser.close()

        parser = createParser(factory, mode, "2 ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(2.0, parser.doubleValue)
        assertEquals(2L, parser.longValue)
        parser.close()

        parser = createParser(factory, mode, "2 ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(2.0f, parser.floatValue)
        assertEquals(2L, parser.longValue)
        parser.close()

        parser = createParser(factory, mode, "0.1 ")
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(0.1, parser.doubleValue)
        assertEquals(0L, parser.longValue)
        parser.close()

        parser = createParser(factory, mode, "10 ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(BigDecimal.TEN, parser.bigDecimalValue)
        assertEquals(10L, parser.longValue)
        parser.close()
    }

    @Test
    fun testToLongFailing() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            toLongFailing(mode)
        }
    }

    private fun toLongFailing(mode: Int) {
        val big = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN)
        createParser(factory, mode, "$big ").use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(big, parser.bigIntegerValue)
            assertEquals(big, parser.numberValue)

            try {
                parser.longValue
                fail("Should have thrown an exception")
            } catch (e: InputCoercionException) {
                verifyException(e, "out of range of `Long`")
                assertEquals(CirJsonToken.VALUE_NUMBER_INT, e.inputType)
                assertEquals(Long::class.java, e.targetType)
            }
        }

        val small = BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.TEN)
        createParser(factory, mode, "$small ").use { parser ->
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(small, parser.bigIntegerValue)

            try {
                parser.longValue
                fail("Should have thrown an exception")
            } catch (e: InputCoercionException) {
                verifyException(e, "out of range of `Long`")
                assertEquals(CirJsonToken.VALUE_NUMBER_INT, e.inputType)
                assertEquals(Long::class.java, e.targetType)
            }
        }
    }

    @Test
    fun testToBigIntegerCoercion() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            toBigIntegerCoercion(mode)
        }
    }

    private fun toBigIntegerCoercion(mode: Int) {
        var parser = createParser(factory, mode, "1 ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.intValue)
        assertEquals(BigInteger.ONE, parser.bigIntegerValue)
        parser.close()

        parser = createParser(factory, mode, "1 ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1L, parser.longValue)
        assertEquals(BigInteger.ONE, parser.bigIntegerValue)
        parser.close()

        parser = createParser(factory, mode, "2 ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(2.0, parser.doubleValue)
        assertEquals(BigInteger.TWO, parser.bigIntegerValue)
        parser.close()

        parser = createParser(factory, mode, "2 ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(2.0f, parser.floatValue)
        assertEquals(BigInteger.TWO, parser.bigIntegerValue)
        parser.close()

        parser = createParser(factory, mode, "0.1 ")
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(0.1, parser.doubleValue)
        assertEquals(BigInteger.ZERO, parser.bigIntegerValue)
        parser.close()

        parser = createParser(factory, mode, "10 ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(BigDecimal.TEN, parser.bigDecimalValue)
        assertEquals(BigInteger.TEN, parser.bigIntegerValue)
        parser.close()
    }

    @Test
    fun testToFloatCoercion() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            toFloatCoercion(mode)
        }
    }

    private fun toFloatCoercion(mode: Int) {
        var parser = createParser(factory, mode, "100.5 ")
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(BigDecimal("100.5"), parser.bigDecimalValue)
        assertEquals(100.5f, parser.floatValue)
        parser.close()

        parser = createParser(factory, mode, "10 ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(BigInteger.TEN, parser.bigIntegerValue)
        assertEquals(10.0f, parser.floatValue)
        parser.close()

        parser = createParser(factory, mode, "0.5 ")
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(0.5, parser.doubleValue)
        assertEquals(0.5f, parser.floatValue)
        parser.close()
    }

    @Test
    fun testToDoubleCoercion() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            toDoubleCoercion(mode)
        }
    }

    private fun toDoubleCoercion(mode: Int) {
        var parser = createParser(factory, mode, "100.5 ")
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(BigDecimal("100.5"), parser.bigDecimalValue)
        assertEquals(100.5, parser.doubleValue)
        parser.close()

        parser = createParser(factory, mode, "10 ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(BigInteger.TEN, parser.bigIntegerValue)
        assertEquals(10.0, parser.doubleValue)
        parser.close()

        parser = createParser(factory, mode, "0.5 ")
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(0.5f, parser.floatValue)
        assertEquals(0.5, parser.doubleValue)
        parser.close()
    }

    @Test
    fun testToBigDecimalCoercion() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            toBigDecimalCoercion(mode)
        }
    }

    private fun toBigDecimalCoercion(mode: Int) {
        var parser = createParser(factory, mode, "1 ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.intValue)
        assertEquals(BigDecimal.ONE, parser.bigDecimalValue)
        parser.close()

        parser = createParser(factory, mode, "${Long.MAX_VALUE} ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(Long.MAX_VALUE, parser.longValue)
        assertEquals(BigDecimal.valueOf(Long.MAX_VALUE), parser.bigDecimalValue)
        parser.close()

        val biggie = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN)
        parser = createParser(factory, mode, "$biggie ")
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(biggie, parser.bigIntegerValue)
        assertEquals(BigDecimal(biggie), parser.bigDecimalValue)
        parser.close()
    }

}