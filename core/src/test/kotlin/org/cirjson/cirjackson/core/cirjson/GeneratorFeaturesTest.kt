package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.StreamWriteFeature
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.StreamWriteException
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.*

class GeneratorFeaturesTest : TestBase() {

    @Test
    fun testConfigDefaults() {
        val generator = createGenerator(MODE_WRITER)
        assertFalse((generator as CirJsonGeneratorBase).isEnabled(CirJsonWriteFeature.WRITE_NUMBERS_AS_STRINGS))
        assertFalse(generator.isEnabled(StreamWriteFeature.WRITE_BIG_DECIMAL_AS_PLAIN))

        assertTrue(generator.isAbleOmitProperties)
        assertFalse(generator.isAbleWriteTypeId)

        generator.close()
    }

    @Test
    fun testFieldNameQuoting() {
        for (mode in ALL_GENERATOR_MODES) {
            var factory = CirJsonFactory()
            fieldNameQuoting(factory, mode, true)
            factory = factory.rebuild().configure(CirJsonWriteFeature.QUOTE_PROPERTY_NAMES, false).build()
            fieldNameQuoting(factory, mode, false)
            factory = factory.rebuild().configure(CirJsonWriteFeature.QUOTE_PROPERTY_NAMES, true).build()
            fieldNameQuoting(factory, mode, true)
        }
    }

    private fun fieldNameQuoting(factory: CirJsonFactory, mode: Int, quoted: Boolean) {
        val generator = createGenerator(factory, mode)
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeName("foo")
        generator.writeNumber(1)
        generator.writeEndObject()
        generator.close()

        val expected = if (quoted) "{\"__cirJsonId__\":\"0\",\"foo\":1}" else "{__cirJsonId__:\"0\",foo:1}"
        val actual = generator.streamWriteOutputTarget!!.toString()
        assertEquals(expected, actual)
    }

    @Test
    fun testNonNumericQuoting() {
        for (mode in ALL_GENERATOR_MODES) {
            var factory = CirJsonFactory()
            assertTrue(factory.isEnabled(CirJsonWriteFeature.WRITE_NAN_AS_STRINGS))
            nonNumericQuoting(factory, mode, true)
            factory = factory.rebuild().configure(CirJsonWriteFeature.WRITE_NAN_AS_STRINGS, false).build()
            nonNumericQuoting(factory, mode, false)
            factory = factory.rebuild().configure(CirJsonWriteFeature.WRITE_NAN_AS_STRINGS, true).build()
            nonNumericQuoting(factory, mode, true)
        }
    }

    private fun nonNumericQuoting(factory: CirJsonFactory, mode: Int, quoted: Boolean) {
        val generator = createGenerator(factory, mode)
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeName("double")
        generator.writeNumber(Double.NaN)
        generator.writeName("float")
        generator.writeNumber(Float.NaN)
        generator.writeEndObject()
        generator.close()

        val expected = if (quoted) {
            "{\"__cirJsonId__\":\"0\",\"double\":\"NaN\",\"float\":\"NaN\"}"
        } else {
            "{\"__cirJsonId__\":\"0\",\"double\":NaN,\"float\":NaN}"
        }
        val actual = generator.streamWriteOutputTarget!!.toString()
        assertEquals(expected, actual)
    }

    @Test
    fun testNumbersAsCirJSONStrings() {
        for (mode in ALL_GENERATOR_MODES) {
            var factory = CirJsonFactory()
            assertEquals("[\"0\",1,2,3,3001,1.25,2.25,0.5,-1,12.3,null,null,null]", writeNumbers(factory, mode))
            factory = factory.rebuild().configure(CirJsonWriteFeature.WRITE_NUMBERS_AS_STRINGS, true).build()
            assertEquals("[\"0\",\"1\",\"2\",\"3\",\"3001\",\"1.25\",\"2.25\",\"0.5\",\"-1\",\"12.3\",null,null,null]",
                    writeNumbers(factory, mode))
        }
    }

    private fun writeNumbers(factory: CirJsonFactory, mode: Int): String {
        val generator = createGenerator(factory, mode)

        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeNumber(1.toShort())
        generator.writeNumber(2)
        generator.writeNumber(3L)
        generator.writeNumber(BigInteger.valueOf(3001L))
        generator.writeNumber(1.25)
        generator.writeNumber(2.25f)
        generator.writeNumber(BigDecimal.valueOf(0.5))
        generator.writeNumber("-1")
        generator.writeNumber(charArrayOf('1', '2', '.', '3', '-'), 0, 4)
        generator.writeNumber(null as String?)
        generator.writeNumber(null as BigInteger?)
        generator.writeNumber(null as BigDecimal?)
        generator.writeEndArray()
        generator.close()

        return generator.streamWriteOutputTarget!!.toString()
    }

    @Test
    fun testBigDecimalAsPlain() {
        for (mode in ALL_GENERATOR_MODES) {
            bigDecimalAsPlain(mode)
        }
    }

    private fun bigDecimalAsPlain(mode: Int) {
        var factory = CirJsonFactory()
        var generator = createGenerator(factory, mode)
        var output = generator.streamWriteOutputTarget!!

        generator.writeNumber(BIG_DECIMAL)
        generator.close()
        assertEquals("1E+2", output.toString())

        factory = factory.rebuild().enable(StreamWriteFeature.WRITE_BIG_DECIMAL_AS_PLAIN).build()
        generator = createGenerator(factory, mode)
        output = generator.streamWriteOutputTarget!!

        generator.writeNumber(BIG_DECIMAL)
        generator.close()
        assertEquals("100", output.toString())
    }

    @Test
    fun testBigDecimalAsPlainString() {
        for (mode in ALL_GENERATOR_MODES) {
            bigDecimalAsPlainString(mode)
        }
    }

    private fun bigDecimalAsPlainString(mode: Int) {
        val factory = CirJsonFactory.builder().enable(StreamWriteFeature.WRITE_BIG_DECIMAL_AS_PLAIN)
                .enable(CirJsonWriteFeature.WRITE_NUMBERS_AS_STRINGS).build()
        val generator = createGenerator(factory, mode)
        generator.writeNumber(BIG_DECIMAL)
        generator.close()
        assertEquals(quote("100"), generator.streamWriteOutputTarget!!.toString())
    }

    @Test
    fun testTooBigBigDecimal() {
        for (mode in ALL_GENERATOR_MODES) {
            tooBigBigDecimal(mode)
        }
    }

    private fun tooBigBigDecimal(mode: Int) {
        for (asString in booleanArrayOf(false, true)) {
            val factory = CirJsonFactory.builder().enable(StreamWriteFeature.WRITE_BIG_DECIMAL_AS_PLAIN)
                    .configure(CirJsonWriteFeature.WRITE_NUMBERS_AS_STRINGS, asString).build()
            var generator = createGenerator(factory, mode)

            generator.writeStartArray()
            generator.writeArrayId(Any())
            generator.writeNumber(BIG)
            generator.writeNumber(SMALL)
            generator.writeEndArray()
            generator.close()

            for (invalidBigDecimal in INVALID_BIG_DECIMALS) {
                generator = createGenerator(factory, mode)

                try {
                    generator.writeNumber(invalidBigDecimal)
                    fail("Should not have written without exception: $invalidBigDecimal")
                } catch (e: StreamWriteException) {
                    verifyException(e, "Attempt to write plain `BigDecimal`")
                    verifyException(e, "illegal scale")
                }
            }
        }
    }

    companion object {

        private val BIG_DECIMAL = BigDecimal("1E+2")

        private val BIG = BigDecimal("1E+9999")

        private val SMALL = BigDecimal("1E-9999")

        private val INVALID_BIG_DECIMALS = arrayOf(BigDecimal("1E+10000"), BigDecimal("1E-10000"))

    }

}