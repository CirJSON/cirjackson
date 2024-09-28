package org.cirjson.cirjackson.core.read.location

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.async.ByteArrayFeeder
import org.cirjson.cirjackson.core.async.ByteBufferFeeder
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.async.NonBlockingByteArrayCirJsonParser
import org.cirjson.cirjackson.core.cirjson.async.NonBlockingByteBufferCirJsonParser
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.read.location.LocationOfErrorTest.ParserGenerator
import org.cirjson.cirjackson.core.support.MockDataInput
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Suppress("ControlFlowWithEmptyBody")
class LocationOfErrorTest : TestBase() {

    @Test
    fun testLocationOfError() {
        val cases = generateTestCases()

        for ((parserVariant, invalidCirJson) in cases) {
            locationOfError(parserVariant, invalidCirJson)
        }
    }

    private fun locationOfError(parserVariant: ParserVariant, invalidCirJson: InvalidCirJson) {
        val parser = parserVariant.createParser(invalidCirJson.doc)

        val e = assertFailsWith<StreamReadException>("parserVariant: $parserVariant, invalidCirJson: $invalidCirJson") {
            while (parser.nextToken() != null) {
            }
        }

        val location = e.location!!

        assertEquals(invalidCirJson.lineNumber, location.lineNumber,
                "parserVariant: $parserVariant, invalidCirJson: $invalidCirJson")

        if (parserVariant.isSupportingByteOffset) {
            assertEquals(invalidCirJson.offset, location.byteOffset,
                    "parserVariant: $parserVariant, invalidCirJson: $invalidCirJson")
        }

        if (parserVariant.isSupportingCharOffset) {
            assertEquals(invalidCirJson.offset, location.charOffset,
                    "parserVariant: $parserVariant, invalidCirJson: $invalidCirJson")
        }

        if (parserVariant.isSupportingColumnNumber) {
            assertEquals(invalidCirJson.columnNumber, location.columnNumber,
                    "parserVariant: $parserVariant, invalidCirJson: $invalidCirJson")
        }
    }

    private fun generateTestCases(): List<Pair<ParserVariant, InvalidCirJson>> {
        return ParserVariant.entries.flatMap { parserVariant -> INVALID_CIRJSON_CASES.map { parserVariant to it } }
    }

    private enum class ParserVariant(val isSupportingByteOffset: Boolean, val isSupportingCharOffset: Boolean,
            val isSupportingColumnNumber: Boolean, val parserGenerator: ParserGenerator) {
        BYTE_ARRAY(true, false, true,
                ParserGenerator { FACTORY.createParser(ObjectReadContext.empty(), it.toByteArray()) }),

        CHAR_ARRAY(false, true, true,
                ParserGenerator { FACTORY.createParser(ObjectReadContext.empty(), it.toCharArray()) }),

        DATA_INPUT(false, false, false,
                ParserGenerator { FACTORY.createParser(ObjectReadContext.empty(), MockDataInput(it)) }),

        ASYNC_BYTE_ARRAY(true, false, true, ParserGenerator {
            FACTORY.createNonBlockingByteArrayParser<NonBlockingByteArrayCirJsonParser>(ObjectReadContext.empty())
                    .apply {
                        val feeder = nonBlockingInputFeeder() as ByteArrayFeeder
                        assertTrue(feeder.isNeedingMoreInput)
                        val data = it.toByteArray()
                        feeder.feedInput(data, 0, data.size)
                        feeder.endOfInput()
                    }
        }),

        ASYNC_BYTE_BUFFER(true, false, true, ParserGenerator {
            FACTORY.createNonBlockingByteBufferParser<NonBlockingByteBufferCirJsonParser>(ObjectReadContext.empty())
                    .apply {
                        val feeder = nonBlockingInputFeeder() as ByteBufferFeeder
                        assertTrue(feeder.isNeedingMoreInput)
                        val buffer = ByteBuffer.wrap(it.toByteArray())
                        feeder.feedInput(buffer)
                        feeder.endOfInput()
                    }
        });

        fun createParser(doc: String): CirJsonParser {
            return parserGenerator.createParser(doc)
        }

    }

    fun interface ParserGenerator {

        fun createParser(doc: String): CirJsonParser

    }

    private data class InvalidCirJson(val name: String, val doc: String, val offset: Long, val lineNumber: Int,
            val columnNumber: Int) {

        override fun toString(): String = name

    }

    companion object {

        private val FACTORY = CirJsonFactory()

        private val INVALID_CIRJSON_CASES = arrayOf(InvalidCirJson("Object property missing colon",
                apostropheToQuote("{\"__cirJsonId__\":\"root\",'invalid' 'cirjson'}"), 34L, 1, 35),
                InvalidCirJson("Comma after key in object property",
                        apostropheToQuote("{\"__cirJsonId__\":\"root\",'invalid', 'cirjson'}"), 33L, 1, 34),
                InvalidCirJson("Missing comma between object properties",
                        apostropheToQuote("{\"__cirJsonId__\":\"root\",'key1':'value1' 'key2':'value2'}"), 40L, 1, 41),
                InvalidCirJson("Number as a property key", "{\"__cirJsonId__\":\"root\",1234: 5678}", 24L, 1, 25),
                InvalidCirJson("false literal as property key", "{\"__cirJsonId__\":\"root\",false: true}", 24L, 1, 25),
                InvalidCirJson("true literal as property key", "{\"__cirJsonId__\":\"root\",true: false}", 24L, 1, 25),
                InvalidCirJson("null literal as property key", "{\"__cirJsonId__\":\"root\",null: \"content\"}", 24L, 1,
                        25),
                InvalidCirJson("Missing comma between list elements", "[\"root\",\"no\" \"commas\"]", 13L, 1, 14),
                InvalidCirJson("Property key/value delimiter in list", "[\"root\",\"still\":\"invalid\"]", 15L, 1, 16),
                InvalidCirJson("Unexpected EOF", "{", 1L, 1, 2),
                InvalidCirJson("Close marker without matching open marker", "}", 0L, 1, 1),
                InvalidCirJson("Mismatched open/close tokens", "{\"__cirJsonId__\":\"root\",\"open\":\"close\"]", 38L,
                        1, 39),
                InvalidCirJson("Bare strings in CirJSON", "{\"__cirJsonId__\":\"root\",missing: quotes}", 24L, 1, 25),
                InvalidCirJson("Error in middle of line for multiline input",
                        "{\"__cirJsonId__\":\"root\",\n  \"one\": 1,\n  \"two\": 2\n  \"three\": 3\n}", 50L, 4, 3),
                InvalidCirJson("Error at end of line for multiline input",
                        "{\"__cirJsonId__\":\"root\",\n\"key1\":\"value1\",,\n\"key2\":\"value2\"\n}", 41L, 2, 17))

    }

}