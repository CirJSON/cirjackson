package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.core.exception.InputCoercionException
import org.cirjson.cirjackson.core.exception.StreamReadException
import java.io.ByteArrayInputStream
import java.io.CharArrayReader
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.*

class NumberParsingTest : TestBase() {

    private val slowFactory = CirJsonFactory.builder().build()

    private val fastFactory = CirJsonFactory.builder().enable(StreamReadFeature.USE_FAST_DOUBLE_PARSER)
            .enable(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER).build()

    private val factories = arrayOf(slowFactory, fastFactory)

    /*
     *******************************************************************************************************************
     * Tests, Int
     *******************************************************************************************************************
     */

    @Test
    fun testSimpleInt() {
        val expectedArray = intArrayOf(1234, -999, 0, 1, -2, 123456789)

        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                for (expected in expectedArray) {
                    simpleInt(factory, mode, expected)
                }
            }
        }
    }

    private fun simpleInt(factory: CirJsonFactory, mode: Int, expected: Int) {
        var doc = "[ \"root\", $expected ]"
        var parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.INT, parser.numberType)
        assertEquals(CirJsonParser.NumberTypeFP.UNKNOWN, parser.numberTypeFP)
        assertTrue(parser.isExpectedNumberIntToken)
        assertEquals("$expected", parser.text)

        if (expected.toShort().toInt() == expected) {
            assertEquals(expected.toShort(), parser.shortValue)

            if (expected.toByte().toInt() == expected) {
                assertEquals(expected.toByte(), parser.byteValue)
            } else {
                try {
                    parser.byteValue
                    fail("Should get exception for non-byte value $expected")
                } catch (e: InputCoercionException) {
                    verifyException(e, "Numeric value")
                    verifyException(e, "out of range")
                }
            }
        } else {
            try {
                parser.shortValue
                fail("Should get exception for non-short value $expected")
            } catch (e: InputCoercionException) {
                verifyException(e, "Numeric value")
                verifyException(e, "out of range")
            }
        }

        assertEquals(expected, parser.intValue)
        assertEquals(expected, parser.getValueAsInt(expected + 3))
        assertEquals(expected, parser.valueAsInt)
        assertEquals(expected.toLong(), parser.longValue)
        assertEquals(expected.toDouble(), parser.doubleValue)
        assertEquals(BigDecimal.valueOf(expected.toLong()), parser.bigDecimalValue)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()

        doc = "$expected "
        parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertTrue(parser.isExpectedNumberIntToken)
        assertEquals("$expected", parser.text)

        assertEquals(expected, parser.intValue)
        assertEquals(expected.toLong(), parser.longValue)
        assertEquals(expected.toDouble(), parser.doubleValue)
        assertEquals(BigDecimal.valueOf(expected.toLong()), parser.bigDecimalValue)
        parser.close()

        doc = "$expected.0 "
        parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertFalse(parser.isExpectedNumberIntToken)
        assertEquals(expected, parser.valueAsInt)
        parser.close()

        parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(expected, parser.getValueAsInt(0))
        parser.close()
    }

    @Test
    fun testIntRange() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                intRange(factory, mode)
            }
        }
    }

    private fun intRange(factory: CirJsonFactory, mode: Int) {
        val doc = "[ \"root\", ${Int.MAX_VALUE},${Int.MIN_VALUE} ]"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.INT, parser.numberType)
        assertEquals(Int.MAX_VALUE, parser.intValue)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.INT, parser.numberType)
        assertEquals(Int.MIN_VALUE, parser.intValue)
        parser.close()
    }

    @Test
    fun testVeryLongIntRootValue() {
        val stringBuilder = StringBuilder(250)
        stringBuilder.append("-2")

        for (i in 1..220) {
            stringBuilder.append('0')
        }

        stringBuilder.append(' ')
        val doc = stringBuilder.toString()
        val parser = createParser(newStreamFactory(), MODE_DATA_INPUT, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertNotNull(parser.bigIntegerValue)
        parser.close()
    }

    /*
     *******************************************************************************************************************
     * Tests, Long
     *******************************************************************************************************************
     */

    @Test
    fun testSimpleLong() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                simpleLong(factory, mode)
            }
        }
    }

    private fun simpleLong(factory: CirJsonFactory, mode: Int) {
        val expected = 12345678907L
        val doc = "[ \"root\", $expected ]"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.LONG, parser.numberType)
        assertEquals(expected, parser.longValue)
        assertEquals("$expected", parser.text)

        try {
            parser.intValue
            fail("Should have thrown an exception")
        } catch (e: InputCoercionException) {
            verifyException(e, "out of range")
            assertToken(CirJsonToken.VALUE_NUMBER_INT, e.inputType)
            assertEquals(Int::class.java, e.targetType)
        }

        assertEquals(expected.toDouble(), parser.doubleValue)
        assertEquals(BigDecimal.valueOf(expected), parser.bigDecimalValue)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    @Test
    fun testLongRange() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                longRange(factory, mode)
            }
        }
    }

    private fun longRange(factory: CirJsonFactory, mode: Int) {
        val belowMinInt = Int.MIN_VALUE - 1L
        val aboveMaxInt = Int.MAX_VALUE + 1L
        val belowMaxLong = Long.MAX_VALUE - 1L
        val aboveMinLong = Long.MIN_VALUE + 1L
        val doc =
                "[ \"root\", ${Long.MAX_VALUE},${Long.MIN_VALUE}, $belowMinInt, $aboveMaxInt, $belowMaxLong, $aboveMinLong ]"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.LONG, parser.numberType)
        assertEquals(Long.MAX_VALUE, parser.longValue)

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.LONG, parser.numberType)
        assertEquals(Long.MIN_VALUE, parser.longValue)

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.LONG, parser.numberType)
        assertEquals(belowMinInt, parser.longValue)

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.LONG, parser.numberType)
        assertEquals(aboveMaxInt, parser.longValue)

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.LONG, parser.numberType)
        assertEquals(belowMaxLong, parser.longValue)

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.LONG, parser.numberType)
        assertEquals(aboveMinLong, parser.longValue)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    /*
     *******************************************************************************************************************
     * Tests, BigNumber
     *******************************************************************************************************************
     */

    @Test
    fun testBigIntegerRange() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                bigIntegerRange(factory, mode)
            }
        }
    }

    private fun bigIntegerRange(factory: CirJsonFactory, mode: Int) {
        val small = BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE)
        val big = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)
        val doc = "[ \"root\", $small  ,  $big]"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.BIG_INTEGER, parser.numberType)
        assertEquals(small, parser.bigIntegerValue)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.BIG_INTEGER, parser.numberType)
        assertEquals(big, parser.bigIntegerValue)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    @Test
    fun testBigNumbers() {
        val stringBuilder = StringBuilder(520)

        for (i in 1..520) {
            stringBuilder.append('1')
        }

        val value = BigInteger(stringBuilder.toString())

        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                bigNumbers(factory, mode, value)
            }
        }
    }

    private fun bigNumbers(factory: CirJsonFactory, mode: Int, value: BigInteger) {
        val doc = "$value "
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(CirJsonParser.NumberType.BIG_INTEGER, parser.numberType)
        assertEquals(value, parser.bigIntegerValue)
        parser.close()
    }

    @Test
    fun testBiggerThanFloatHandling() {
        var value = BigDecimal.valueOf(Double.MAX_VALUE)
        value = value.add(value).add(BigDecimal.valueOf(0.25))
        val valueString = value.toString()

        assertTrue(valueString.toDouble().isInfinite())

        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                biggerThanFloatHandling(factory, mode, value, valueString)
            }
        }
    }

    private fun biggerThanFloatHandling(factory: CirJsonFactory, mode: Int, value: BigDecimal, valueString: String) {
        val doc = "$valueString "
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertFalse(parser.isNaN)
        assertEquals(valueString, parser.text)
        assertEquals(value, parser.bigDecimalValue)
        assertFalse(parser.isNaN)
    }

    /*
     *******************************************************************************************************************
     * Tests, int/long/BigInteger via E-notation (engineering)
     *******************************************************************************************************************
     */

    @Test
    fun testIWithENotation() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                intWithENotation(factory, mode)
            }
        }
    }

    private fun intWithENotation(factory: CirJsonFactory, mode: Int) {
        val doc = "1e5 "
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(100000, parser.intValue)
        parser.close()
    }

    @Test
    fun testLongWithENotation() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                longWithENotation(factory, mode)
            }
        }
    }

    private fun longWithENotation(factory: CirJsonFactory, mode: Int) {
        val doc = "1e5 "
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(100000L, parser.longValue)
        parser.close()
    }

    @Test
    fun testBigIntegerWithENotation() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                bigIntegerWithENotation(factory, mode)
            }
        }
    }

    private fun bigIntegerWithENotation(factory: CirJsonFactory, mode: Int) {
        val doc = "1e5 "
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(BigInteger.valueOf(100000L), parser.bigIntegerValue)
        parser.close()
    }

    @Test
    fun testLargeBigIntegerWithENotation() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                largeBigIntegerWithENotation(factory, mode)
            }
        }
    }

    private fun largeBigIntegerWithENotation(factory: CirJsonFactory, mode: Int) {
        val doc = "2e308 "
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(BigDecimal("2e308").toBigInteger(), parser.bigIntegerValue)
        parser.close()
    }

    /*
     *******************************************************************************************************************
     * Tests, floating point (basic)
     *******************************************************************************************************************
     */

    @Test
    fun testSimpleDouble() {
        val values =
                arrayOf("1234.00", "2.1101567E-16", "1.0e5", "0.0", "1.0", "-1.0", "-0.5", "-12.9", "-999.0", "2.5e+5",
                        "9e4", "-12e-3", "0.25").map { it.toDouble() to it }

        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                for ((value, valueString) in values) {
                    simpleDouble(factory, mode, value, valueString)
                }
            }
        }
    }

    private fun simpleDouble(factory: CirJsonFactory, mode: Int, value: Double, valueString: String) {
        var doc = "[ \"root\", $valueString ]"
        var parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(valueString, parser.text)
        assertEquals(value, parser.doubleValue)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()

        doc = "$valueString "
        parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(valueString, parser.text)
        assertEquals(value, parser.doubleValue)
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testFloatBoundaryChars() {
        for (factory in factories) {
            val chars = CharArray(50005)

            for (i in 500..<9000) {
                chars.fill(' ', 0, i)
                chars[i] = '-'
                chars[i + 1] = '1'
                chars[i + 2] = 'e'
                chars[i + 3] = '-'
                chars[i + 4] = '1'
                floatBoundaryChars(factory, chars, i)
            }
        }
    }

    private fun floatBoundaryChars(factory: CirJsonFactory, chars: CharArray, i: Int) {
        val reader = CharArrayReader(chars, 0, i + 5)
        val parser = factory.createParser(ObjectReadContext.empty(), reader)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        parser.close()
    }

    @Test
    fun testFloatBoundaryBytes() {
        for (factory in factories) {
            val bytes = ByteArray(50005)

            for (i in 500..<9000) {
                bytes.fill(' '.code.toByte(), 0, i)
                bytes[i] = '-'.code.toByte()
                bytes[i + 1] = '1'.code.toByte()
                bytes[i + 2] = 'e'.code.toByte()
                bytes[i + 3] = '-'.code.toByte()
                bytes[i + 4] = '1'.code.toByte()
                floatBoundaryBytes(factory, bytes, i)
            }
        }
    }

    private fun floatBoundaryBytes(factory: CirJsonFactory, bytes: ByteArray, i: Int) {
        val reader = ByteArrayInputStream(bytes, 0, i + 5)
        val parser = factory.createParser(ObjectReadContext.empty(), reader)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        parser.close()
    }

    /*
     *******************************************************************************************************************
     * Tests, misc other
     *******************************************************************************************************************
     */

    @Test
    fun testNumbers() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                numbers(factory, mode)
            }
        }
    }

    private fun numbers(factory: CirJsonFactory, mode: Int) {
        val doc = "[ \"root\", -13, 8100200300, 13.5, 0.00010, -2.033 ]"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(-13, parser.intValue)
        assertEquals(-13L, parser.longValue)
        assertEquals(-13.0, parser.doubleValue)
        assertEquals("-13", parser.text)

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(8100200300L, parser.longValue)

        try {
            parser.intValue
            fail("Should have thrown an exception")
        } catch (e: InputCoercionException) {
            verifyException(e, "out of range of `int`")
            assertToken(CirJsonToken.VALUE_NUMBER_INT, e.inputType)
            assertEquals(Int::class.java, e.targetType)
        }

        assertEquals(8100200300.0, parser.doubleValue)
        assertEquals("8100200300", parser.text)

        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(13, parser.intValue)
        assertEquals(13L, parser.longValue)
        assertEquals(13.5, parser.doubleValue)
        assertEquals("13.5", parser.text)

        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(0, parser.intValue)
        assertEquals(0L, parser.longValue)
        assertEquals(0.00010, parser.doubleValue)
        assertEquals("0.00010", parser.text)

        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(-2, parser.intValue)
        assertEquals(-2L, parser.longValue)
        assertEquals(-2.033, parser.doubleValue)
        assertEquals("-2.033", parser.text)

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    @Test
    fun testParsingOfLongerSequences() {
        val doc = createParsingOfLongerSequencesDoc()

        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                parsingOfLongerSequences(factory, mode, doc)
            }
        }
    }

    private fun parsingOfLongerSequences(factory: CirJsonFactory, mode: Int, doc: String) {
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (i in 1..LONGER_SEQUENCES_COUNT) {
            for (expected in LONGER_SEQUENCES_VALUES) {
                assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
                assertEquals(expected, parser.doubleValue)
            }
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    private fun createParsingOfLongerSequencesDoc(): String {
        val segment = createParsingOfLongerSequencesSegment()
        val segmentLength = segment.length
        val stringBuilder = StringBuilder(LONGER_SEQUENCES_COUNT * segmentLength + 40)
        stringBuilder.append("[\"root\"")

        for (i in 0..<LONGER_SEQUENCES_COUNT) {
            stringBuilder.append(',')
            stringBuilder.append(segment)
            stringBuilder.append('\n')

            var x = i and 3

            if (i > 300) {
                x += i.mod(5)
            }

            while (--x > 0) {
                stringBuilder.append(' ')
            }
        }

        stringBuilder.append("]")
        return stringBuilder.toString()
    }

    private fun createParsingOfLongerSequencesSegment(): String {
        val stringBuilder = StringBuilder()

        for (i in LONGER_SEQUENCES_VALUES.indices) {
            if (i > 0) {
                stringBuilder.append(',')
            }
            stringBuilder.append(LONGER_SEQUENCES_VALUES[i])
        }

        return stringBuilder.toString()
    }

    @Test
    fun testLongNumbers1() {
        val stringBuilder = StringBuilder(901)

        for (i in 1..900) {
            stringBuilder.append('9')
        }

        val value = stringBuilder.toString()

        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                longNumbers1(factory, mode, value)
            }
        }
    }

    private fun longNumbers1(factory: CirJsonFactory, mode: Int, value: String) {
        val doc = "[ \"root\", $value ]"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(value, parser.text)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    @Test
    fun testLongNumbers2() {
        val stringBuilder = StringBuilder(2101)
        stringBuilder.append('-')

        for (i in 1..2100) {
            stringBuilder.append('1')
        }

        val value = stringBuilder.toString()

        for (baseFactory in factories) {
            val factory = baseFactory.rebuild()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNumberLength(10000).build()).build()

            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                longNumbers2(factory, mode, value)
            }
        }
    }

    private fun longNumbers2(factory: CirJsonFactory, mode: Int, value: String) {
        val doc = "$value "
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(value, parser.bigIntegerValue.toString())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testParsingOfLongerSequencesWithNonNumeric() {
        for (value in NON_NUMERIC_VALUES) {
            val doc = createParsingOfLongerSequencesWithNonNumericDoc(value)

            for (baseFactory in factories) {
                val factory = baseFactory.rebuild().enable(CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS).build()

                for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                    parsingOfLongerSequencesWithNonNumeric(factory, mode, doc, value)
                }
            }
        }
    }

    private fun parsingOfLongerSequencesWithNonNumeric(factory: CirJsonFactory, mode: Int, doc: String, value: Double) {
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (i in 0..<NON_NUMERIC_VALUES_COUNT) {
            assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
            val actual = parser.doubleValue

            if (value.compareTo(actual) != 0) {
                fail("Expected at #$i value $value, instead got $actual")
            } else if (value.isNaN() || value.isInfinite()) {
                assertTrue(parser.isNaN)
            } else {
                assertFalse(parser.isNaN)
            }
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    private fun createParsingOfLongerSequencesWithNonNumericDoc(value: Double): String {
        val array = createParsingOfLongerSequencesWithNonNumericArray(value)
        val stringBuilder = StringBuilder(NON_NUMERIC_COUNT + array.length + 20)

        for (i in 1..NON_NUMERIC_COUNT) {
            stringBuilder.append(' ')
        }

        stringBuilder.append(array)
        return stringBuilder.toString()
    }

    private fun createParsingOfLongerSequencesWithNonNumericArray(value: Double): String {
        val stringBuilder = StringBuilder(NON_NUMERIC_VALUES_COUNT * 30)
        stringBuilder.append("[\"root\",").append(value)

        for (i in 1..<NON_NUMERIC_VALUES_COUNT) {
            stringBuilder.append(',').append(value)
        }

        stringBuilder.append(']')
        return stringBuilder.toString()
    }

    /*
     *******************************************************************************************************************
     * Tests, invalid access
     *******************************************************************************************************************
     */

    @Test
    fun testInvalidIntAccess() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                invalidIntAccess(factory, mode)
            }
        }
    }

    private fun invalidIntAccess(factory: CirJsonFactory, mode: Int) {
        val doc = "[ \"abc\" ]"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.intValue
            fail("Should have thrown an exception")
        } catch (e: InputCoercionException) {
            verifyException(e, "can not use numeric value accessors")
        }

        parser.close()
    }

    @Test
    fun testInvalidLongAccess() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                invalidLongAccess(factory, mode)
            }
        }
    }

    private fun invalidLongAccess(factory: CirJsonFactory, mode: Int) {
        val doc = "[ \"root\", false ]"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())

        try {
            parser.longValue
            fail("Should have thrown an exception")
        } catch (e: InputCoercionException) {
            verifyException(e, "can not use numeric value accessors")
        }

        parser.close()
    }

    @Test
    fun testLongerFloatingPoint() {
        val stringBuilder = StringBuilder(202)

        for (i in 1..200) {
            stringBuilder.append('1')
        }

        stringBuilder.append(".0")
        val value = stringBuilder.toString()

        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                longerFloatingPoint(factory, mode, value)
            }
        }
    }

    private fun longerFloatingPoint(factory: CirJsonFactory, mode: Int, value: String) {
        val doc = "$value "
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(value, parser.text)
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testInvalidNumber() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                invalidNumber(factory, mode)
            }
        }
    }

    private fun invalidNumber(factory: CirJsonFactory, mode: Int) {
        val doc = " -foo "
        val parser = createParser(factory, mode, doc)

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('f'")
        }

        parser.close()
    }

    @Test
    fun testNegativeMaxNumberLength() {
        try {
            StreamReadConstraints.builder().maxNumberLength(-1).build()
            fail("Should have thrown an exception")
        } catch (e: IllegalArgumentException) {
            verifyException(e, "Cannot set maxNumberLength to a negative value")
        }
    }

    companion object {

        private val LONGER_SEQUENCES_VALUES = doubleArrayOf(0.01, -10.5, 2.1e9, 4.0e-8)

        private const val LONGER_SEQUENCES_COUNT = 1000

        private val NON_NUMERIC_VALUES = doubleArrayOf(0.01, -10.5, 2.1e9, 4.0e-8, Double.NaN, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY)

        private const val NON_NUMERIC_COUNT = 4096

        private const val NON_NUMERIC_VALUES_COUNT = NON_NUMERIC_COUNT * 2

    }

}