package org.cirjson.cirjackson.core.constraints

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LargeNumberWriteTest : TestBase() {

    private val factory = newStreamFactory()

    private val noLimitFactory = CirJsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNumberLength(Int.MAX_VALUE).build()).build()

    @Test
    fun testWriteLargeInteger() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                createGenerator(factory, generatorMode).use { generator ->
                    writeLargeInteger(generator)

                    val doc = generator.streamWriteOutputTarget!!.toString()
                    createParser(noLimitFactory, parserMode, doc).use { parser ->
                        verifyLargeInteger(parser)
                    }
                }
            }
        }
    }

    @Test
    fun testWriteLargeDecimal() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                createGenerator(factory, generatorMode).use { generator ->
                    writeLargeDecimal(generator)

                    val doc = generator.streamWriteOutputTarget!!.toString()
                    createParser(noLimitFactory, parserMode, doc).use { parser ->
                        verifyLargeDecimal(parser)
                    }
                }
            }
        }
    }

    private fun writeLargeInteger(generator: CirJsonGenerator) {
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeName("field")
        generator.writeNumber(BIG_INTEGER)
        generator.writeEndObject()
        generator.close()
    }

    private fun verifyLargeInteger(parser: CirJsonParser) {
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("field", parser.currentName())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(BIG_INTEGER, parser.bigIntegerValue)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
    }

    private fun writeLargeDecimal(generator: CirJsonGenerator) {
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeName("field")
        generator.writeNumber(BIG_DECIMAL)
        generator.writeEndObject()
        generator.close()
    }

    private fun verifyLargeDecimal(parser: CirJsonParser) {
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("field", parser.currentName())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(BIG_DECIMAL, parser.bigDecimalValue)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
    }

    companion object {

        private val BIG_DECIMAL = BigDecimal(StringBuilder(2502).apply {
            append("0.")

            for (i in 1..2500) {
                append(i.mod(10))
            }
        }.toString())

        private val BIG_INTEGER = BigInteger(StringBuilder(2500).apply {
            for (i in 1..2500) {
                append(i.mod(10))
            }
        }.toString())

    }

}