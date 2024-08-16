package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.*

class DelegatesTest : TestBase() {

    @Test
    fun testParserDelegate() {
        val constraints = StreamReadConstraints.builder().maxNumberLength(MAX_NUMBER_LENGTH).build()
        val factory = CirJsonFactory.builder().streamReadConstraints(constraints).build()
        val content = "[ \"0\", 1, true, null, { \"__cirJsonId__\" : \"0/3\" , \"a\": \"foo\" }, \"AQI=\" ]"

        for (mode in ALL_NON_THROTTLED_PARSER_MODES) {
            parserDelegate(createParser(factory, mode, content))
        }
    }

    private fun parserDelegate(parser: CirJsonParser) {
        val delegate = CirJsonParserDelegate(parser)
        val token = "foo"

        assertFalse(delegate.isParsingAsyncPossible)
        assertFalse(delegate.isReadingTypeIdPossible)
        assertFalse(delegate.isParsingAsyncPossible)
        assertEquals(parser.version(), delegate.version())
        assertSame(parser.streamReadConstraints, delegate.streamReadConstraints)
        assertEquals(MAX_NUMBER_LENGTH, parser.streamReadConstraints.maxNumberLength)
        assertSame(parser.streamReadCapabilities, delegate.streamReadCapabilities)

        assertFalse(delegate.isEnabled(StreamReadFeature.IGNORE_UNDEFINED))
        assertSame(parser, delegate.delegate)
        assertNull(delegate.schema)

        assertNull(delegate.currentToken())
        assertFalse(delegate.isCurrentTokenNotNull)
        assertFalse(delegate.isTextCharactersAvailable)
        assertNull(delegate.currentValue())
        assertNull(delegate.currentName)

        assertToken(CirJsonToken.START_ARRAY, delegate.nextToken())
        assertEquals(CirJsonTokenId.ID_START_ARRAY, delegate.currentTokenId())
        assertTrue(delegate.hasToken(CirJsonToken.START_ARRAY))
        assertFalse(delegate.hasToken(CirJsonToken.START_OBJECT))
        assertTrue(delegate.hasTokenId(CirJsonTokenId.ID_START_ARRAY))
        assertFalse(delegate.hasTokenId(CirJsonTokenId.ID_START_OBJECT))
        assertTrue(delegate.isExpectedStartArrayToken)
        assertFalse(delegate.isExpectedStartObjectToken)
        assertFalse(delegate.isExpectedNumberIntToken)
        assertEquals("[", delegate.text)
        assertNotNull(delegate.streamReadContext)
        assertSame(parser.streamReadContext, delegate.streamReadContext)

        assertToken(CirJsonToken.VALUE_STRING, delegate.nextToken())

        assertToken(CirJsonToken.VALUE_NUMBER_INT, delegate.nextToken())
        assertEquals(1, delegate.intValue)
        assertEquals(1, delegate.valueAsInt)
        assertEquals(1, delegate.getValueAsInt(3))
        assertEquals(1L, delegate.valueAsLong)
        assertEquals(1L, delegate.getValueAsLong(3L))
        assertEquals(1L, delegate.longValue)
        assertEquals(1.0, delegate.valueAsDouble)
        assertEquals(1.0, delegate.getValueAsDouble(0.25))
        assertEquals(1.0, delegate.doubleValue)
        assertTrue(delegate.valueAsBoolean)
        assertTrue(delegate.getValueAsBoolean(false))
        assertEquals(1.toByte(), delegate.byteValue)
        assertEquals(1.toShort(), delegate.shortValue)
        assertEquals(1.0f, delegate.floatValue)
        assertFalse(delegate.isNaN)
        assertTrue(delegate.isExpectedNumberIntToken)
        assertEquals(CirJsonParser.NumberType.INT, delegate.numberType)
        assertEquals(CirJsonParser.NumberTypeFP.UNKNOWN, delegate.numberTypeFP)
        assertEquals(1, delegate.numberValue)
        assertNull(delegate.embeddedObject)

        assertToken(CirJsonToken.VALUE_TRUE, delegate.nextToken())
        assertTrue(delegate.booleanValue)
        assertTrue(delegate.valueAsBoolean)
        assertTrue(delegate.getValueAsBoolean(false))
        assertEquals(parser.currentLocation(), delegate.currentLocation())
        assertNull(delegate.typeId)
        assertNull(delegate.objectId)

        assertToken(CirJsonToken.VALUE_NULL, delegate.nextToken())
        assertNull(delegate.currentValue())
        delegate.assignCurrentValue(token)

        assertToken(CirJsonToken.START_OBJECT, delegate.nextToken())
        assertNull(delegate.currentValue())

        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, delegate.nextToken())
        assertEquals("__cirJsonId__", delegate.currentName)
        assertToken(CirJsonToken.VALUE_STRING, delegate.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, delegate.nextToken())
        assertEquals("a", delegate.currentName)

        assertToken(CirJsonToken.VALUE_STRING, delegate.nextToken())
        assertTrue(delegate.isTextCharactersAvailable)
        assertEquals("foo", delegate.text)

        assertToken(CirJsonToken.END_OBJECT, delegate.nextToken())
        assertEquals(token, delegate.currentValue())

        assertToken(CirJsonToken.VALUE_STRING, delegate.nextToken())
        assertContentEquals(byteArrayOf(1, 2), delegate.binaryValue)

        assertToken(CirJsonToken.END_ARRAY, delegate.nextToken())

        delegate.close()
        assertTrue(delegate.isClosed)
        assertTrue(parser.isClosed)

        parser.close()
    }

    @Test
    fun testGeneratorDelegate() {
        for (mode in ALL_GENERATOR_MODES) {
            generatorDelegate(createGenerator(mode))
        }
    }

    private fun generatorDelegate(generator: CirJsonGenerator) {
        val delegate = CirJsonGeneratorDelegate(generator)
        val token = "foo"

        assertTrue(delegate.isAbleOmitProperties)
        assertFalse(delegate.isAbleWriteTypeId)
        assertEquals(generator.version(), delegate.version())

        assertNull(delegate.schema)

        delegate.writeStartArray()

        assertEquals(1, delegate.streamWriteOutputBuffered)

        delegate.writeArrayId(intArrayOf())

        delegate.writeNumber(13)
        delegate.writeNumber(BigInteger.ONE)
        delegate.writeNumber(BigDecimal("0.5"))
        delegate.writeNumber("137")
        delegate.writeNull()
        delegate.writeBoolean(false)
        delegate.writeString("foo")

        assertNull(delegate.currentValue())
        delegate.assignCurrentValue(token)

        delegate.writeStartObject(null, 0)
        assertNull(delegate.currentValue())
        delegate.writeObjectId(Any())
        delegate.writeEndObject()
        assertEquals(token, delegate.currentValue())

        delegate.writeStartArray(0)
        delegate.writeArrayId(intArrayOf())
        delegate.writeEndArray()

        delegate.writeEndArray()

        delegate.flush()
        delegate.close()

        assertTrue(delegate.isClosed)
        assertTrue(generator.isClosed)

        val output = delegate.streamWriteOutputTarget!!
        assertEquals("[\"0\",13,1,0.5,137,null,false,\"foo\",{\"__cirJsonId__\":\"1\"},[\"2\"]]", output.toString())

        generator.close()
    }

    @Test
    fun testGeneratorDelegateArrays() {
        for (mode in ALL_GENERATOR_MODES) {
            generatorDelegateArrays(createGenerator(mode))
        }
    }

    private fun generatorDelegateArrays(generator: CirJsonGenerator) {
        val delegate = CirJsonGeneratorDelegate(generator)

        val marker = Any()
        delegate.writeStartArray(marker)
        assertSame(marker, delegate.currentValue())
        delegate.writeArrayId(marker)

        delegate.writeArray(intArrayOf(1, 2, 3), 0, 3)
        delegate.writeArray(longArrayOf(1L, 123456L, 2L), 1, 1)
        delegate.writeArray(doubleArrayOf(0.25, 0.5, 0.75), 0, 2)
        delegate.writeArray(arrayOf("Aa", "Bb", "Cc"), 1, 2)

        delegate.close()
        val output = delegate.streamWriteOutputTarget!!
        assertEquals("[\"0\",[\"1\",1,2,3],[\"2\",123456],[\"3\",0.25,0.5],[\"4\",\"Bb\",\"Cc\"]]", output.toString())

        generator.close()
    }

    @Test
    fun testGeneratorDelegateComments() {
        for (mode in ALL_GENERATOR_MODES) {
            generatorDelegateComments(createGenerator(mode))
        }
    }

    private fun generatorDelegateComments(generator: CirJsonGenerator) {
        val delegate = CirJsonGeneratorDelegate(generator)

        val marker = Any()
        delegate.writeStartArray(marker, 5)
        assertSame(marker, delegate.currentValue())
        delegate.writeArrayId(marker)

        delegate.writeNumber(1.toShort())
        delegate.writeNumber(12L)
        delegate.writeNumber(0.25)
        delegate.writeNumber(0.5f)

        delegate.writeRawValue("/*foo*/")
        delegate.writeRaw("  ")

        delegate.close()
        val output = delegate.streamWriteOutputTarget!!
        assertEquals("[\"0\",1,12,0.25,0.5,/*foo*/  ]", output.toString())

        generator.close()
    }

    @Test
    fun testNotDelegateCopyMethods() {
        val content =
                "[\"0\",{\"__cirJsonId__\":\"1\",\"a\":[\"2\",1,2,{\"__cirJsonId__\":\"3\",\"b\":3}],\"c\":\"d\"},{\"__cirJsonId__\":\"4\",\"e\":false},null]"
        for (parserMode in ALL_NON_THROTTLED_PARSER_MODES) {
            for (generatorMode in ALL_GENERATOR_MODES) {
                notDelegateCopyMethods(createParser(parserMode, content), createGenerator(generatorMode))
            }
        }
    }

    private fun notDelegateCopyMethods(parser: CirJsonParser, generator: CirJsonGenerator) {
        val delegate = object : CirJsonGeneratorDelegate(generator, false) {

            override fun writeName(name: String): CirJsonGenerator {
                super.writeName("$name-test")
                writeBoolean(true)
                super.writeName(name)
                return this
            }

        }

        parser.nextToken()
        delegate.copyCurrentStructure(parser)
        delegate.flush()
        val actual = delegate.streamWriteOutputTarget!!.toString()
        assertEquals(
                "[\"0\",{\"__cirJsonId__-test\":true,\"__cirJsonId__\":\"1\",\"a-test\":true,\"a\":[\"2\",1,2,{\"__cirJsonId__-test\":true,\"__cirJsonId__\":\"3\",\"b-test\":true,\"b\":3}],\"c-test\":true,\"c\":\"d\"},{\"__cirJsonId__-test\":true,\"__cirJsonId__\":\"4\",\"e-test\":true,\"e\":false},null]",
                actual)
        parser.close()
        delegate.close()
    }

    companion object {

        private const val MAX_NUMBER_LENGTH = 200

    }

}