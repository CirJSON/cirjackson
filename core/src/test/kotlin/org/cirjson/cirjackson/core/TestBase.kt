package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonFactoryBuilder
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.support.MockDataInput
import org.cirjson.cirjackson.core.support.TestSupport
import org.cirjson.cirjackson.core.support.ThrottledInputStream
import org.cirjson.cirjackson.core.support.ThrottledReader
import java.io.*
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.fail

open class TestBase {

    /*
     *******************************************************************************************************************
     * Factory methods
     *******************************************************************************************************************
     */

    fun sharedStreamFactory(): CirJsonFactory {
        return CIRJSON_FACTORY
    }

    protected fun newStreamFactory(): CirJsonFactory {
        return CirJsonFactory()
    }

    protected fun streamFactoryBuilder(): CirJsonFactoryBuilder {
        return CirJsonFactory.builder()
    }

    /*
     *******************************************************************************************************************
     * Parser construction
     *******************************************************************************************************************
     */

    protected fun createParser(mode: Int, doc: String): CirJsonParser {
        return createParser(CIRJSON_FACTORY, mode, doc)
    }

    protected fun createParser(mode: Int, doc: ByteArray): CirJsonParser {
        return createParser(CIRJSON_FACTORY, mode, doc)
    }

    protected fun createParser(factory: TokenStreamFactory, mode: Int, doc: String): CirJsonParser {
        return when (mode) {
            MODE_INPUT_STREAM -> createParserUsingStream(factory, doc, "UTF-8")

            MODE_INPUT_STREAM_THROTTLED -> factory.createParser(testObjectReadContext(),
                    ThrottledInputStream(utf8Bytes(doc), 1))

            MODE_READER -> createParserUsingReader(factory, doc)

            MODE_READER_THROTTLED -> factory.createParser(testObjectReadContext(), ThrottledReader(doc, 1))

            MODE_DATA_INPUT -> createParserForDataInput(factory, MockDataInput(doc))

            else -> throw RuntimeException("internal error")
        }
    }

    protected fun createParser(factory: TokenStreamFactory, mode: Int, doc: ByteArray): CirJsonParser {
        return when (mode) {
            MODE_INPUT_STREAM -> factory.createParser(testObjectReadContext(), ByteArrayInputStream(doc))

            MODE_INPUT_STREAM_THROTTLED -> factory.createParser(testObjectReadContext(), ThrottledInputStream(doc, 1))

            MODE_READER -> factory.createParser(testObjectReadContext(),
                    StringReader(String(doc, StandardCharsets.UTF_8)))

            MODE_READER_THROTTLED -> factory.createParser(testObjectReadContext(),
                    ThrottledReader(String(doc, StandardCharsets.UTF_8), 1))

            MODE_DATA_INPUT -> createParserForDataInput(factory, MockDataInput(doc))

            else -> throw RuntimeException("internal error")
        }
    }

    protected fun createParserUsingReader(input: String): CirJsonParser {
        return createParserUsingReader(CirJsonFactory(), input)
    }

    protected fun createParserUsingReader(factory: TokenStreamFactory, input: String): CirJsonParser {
        return factory.createParser(testObjectReadContext(), StringReader(input))
    }

    protected fun createParserUsingStream(input: String, encoding: String): CirJsonParser {
        return createParserUsingStream(CirJsonFactory(), input, encoding)
    }

    protected fun createParserUsingStream(factory: TokenStreamFactory, input: String, encoding: String): CirJsonParser {
        val data = if (encoding == "UTF-32") {
            encodeInUTF32BE(input)
        } else {
            try {
                input.toByteArray(charset(encoding))
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }

        val inputStream = ByteArrayInputStream(data)
        return factory.createParser(testObjectReadContext(), inputStream)
    }

    protected fun createParserForDataInput(factory: TokenStreamFactory, input: DataInput): CirJsonParser {
        return factory.createParser(testObjectReadContext(), input)
    }

    /*
     *******************************************************************************************************************
     * Generator construction
     *******************************************************************************************************************
     */

    @Throws(IOException::class)
    fun createGenerator(mode: Int): CirJsonGenerator {
        return when (mode) {
            MODE_OUTPUT_STREAM -> createGenerator(ByteArrayOutputStream())

            MODE_WRITER -> createGenerator(StringWriter())

            else -> throw RuntimeException("internal error")
        }
    }

    @Throws(IOException::class)
    fun createGenerator(factory: TokenStreamFactory, mode: Int): CirJsonGenerator {
        return when (mode) {
            MODE_OUTPUT_STREAM -> createGenerator(factory, ByteArrayOutputStream())

            MODE_WRITER -> createGenerator(factory, StringWriter())

            else -> throw RuntimeException("internal error")
        }
    }

    @Throws(IOException::class)
    fun createGenerator(output: OutputStream): CirJsonGenerator {
        return createGenerator(CIRJSON_FACTORY, output)
    }

    @Throws(IOException::class)
    fun createGenerator(factory: TokenStreamFactory, output: OutputStream): CirJsonGenerator {
        return factory.createGenerator(ObjectWriteContext.empty(), output)
    }

    @Throws(IOException::class)
    fun createGenerator(writer: Writer): CirJsonGenerator {
        return createGenerator(CIRJSON_FACTORY, writer)
    }

    @Throws(IOException::class)
    fun createGenerator(factory: TokenStreamFactory, writer: Writer): CirJsonGenerator {
        return factory.createGenerator(ObjectWriteContext.empty(), writer)
    }

    /*
     *******************************************************************************************************************
     * Helper type construction
     *******************************************************************************************************************
     */

    fun testIOContext(): IOContext {
        return TestSupport.testIOContext()
    }

    protected fun writeJsonDoc(factory: CirJsonFactory, doc: String, generator: CirJsonGenerator) {
        factory.createParser(ObjectReadContext.empty(), apostropheToQuote(doc)).use { p ->
            while (p.nextToken() != null) {
                generator.copyCurrentStructure(p)
            }

            generator.close()
        }
    }

    /*
     *******************************************************************************************************************
     * Assertions
     *******************************************************************************************************************
     */

    protected fun assertToken(expectedToken: CirJsonToken, actualToken: CirJsonToken?) {
        if (actualToken != expectedToken) {
            fail("Expected token $expectedToken, current token $actualToken")
        }
    }

    protected fun assertToken(expectedToken: CirJsonToken, parser: CirJsonParser) {
        assertToken(expectedToken, parser.currentToken())
    }

    /**
     * @param e Exception to check
     *
     * @param anyMatches Array of Strings of which AT LEAST ONE ("any") has to be included in `e.message` -- using
     * case-INSENSITIVE comparison
     */
    protected fun verifyException(e: Throwable, vararg anyMatches: String) {
        val message = e.message
        val lowercaseMessage = message?.lowercase() ?: ""

        for (match in anyMatches) {
            val lowercaseMatch = match.lowercase()

            if (lowercaseMatch in lowercaseMessage) {
                return
            }
        }

        fail("Expected an exception with one of substrings (${
            anyMatches.joinToString(", ")
        })): got one with message \"$message\"")
    }

    /**
     * Method that gets textual contents of the current token using
     * available methods, and ensures results are consistent, before
     * returning them
     */
    fun getAndVerifyText(parser: CirJsonParser): String {
        val actualLength = parser.textLength
        val ch = parser.textCharacters!!
        val str2 = String(ch, parser.textOffset, actualLength)
        val str = parser.text!!

        if (str.length != actualLength) {
            fail("Internal problem (p.token == ${parser.currentToken()}): p.getText().length() ['$str'] == ${str.length}; p.getTextLength() == $actualLength")
        }

        assertEquals(str, str2, "String access via text, textXXX must be the same")
        return str
    }

    /*
     *******************************************************************************************************************
     * Escaping/quoting
     *******************************************************************************************************************
     */

    fun quote(str: String): String {
        return "\"$str\""
    }

    fun apostropheToQuote(str: String): String {
        return str.replace('\'', '"')
    }

    fun encodeInUTF32BE(input: String): ByteArray {
        val length = input.length
        val result = ByteArray(length * 4)
        var pointer = 0
        var i = 0

        while (i < length) {
            val c = input[i++]
            result[pointer] = 0
            result[pointer + 1] = 0
            result[pointer + 2] = (c.code shr 8).toByte()
            result[pointer + 3] = c.code.toByte()
            pointer += 4
        }

        return result
    }

    /*
     *******************************************************************************************************************
     * Misc other
     *******************************************************************************************************************
     */

    protected fun testObjectReadContext(): ObjectReadContext {
        return ObjectReadContext.empty()
    }

    protected fun utf8Bytes(str: String): ByteArray {
        return str.toByteArray(StandardCharsets.UTF_8)
    }

    protected fun utf8String(bytes: ByteArrayOutputStream): String {
        return String(bytes.toByteArray(), StandardCharsets.UTF_8)
    }

    fun fieldNameFor(index: Int): String {
        val stringBuilder = StringBuilder(16)
        fieldNameFor(stringBuilder, index)
        return stringBuilder.toString()
    }

    private fun fieldNameFor(stringBuilder: StringBuilder, index: Int) {
        stringBuilder.append(FIELD_BASENAME)
        stringBuilder.append(index)

        if (index <= 50) {
            return
        }

        stringBuilder.append('.')

        if (index > 200) {
            stringBuilder.append(index)

            if (index > 4000) {
                stringBuilder.append(".$index")
            }
        } else {
            stringBuilder.append(index shr 3)
        }
    }

    protected fun calcQuads(word: String): IntArray {
        return calcQuads(utf8Bytes(word))
    }

    protected fun calcQuads(wordBytes: ByteArray): IntArray {
        val length = wordBytes.size
        val result = IntArray((length + 3) / 4)
        var i = 0

        while (i < length) {
            var x = wordBytes[i].toInt() and 0xFF

            if (++i < length) {
                x = x shl 8 or (wordBytes[i].toInt() and 0xFF)

                if (++i < length) {
                    x = x shl 8 or (wordBytes[i].toInt() and 0xFF)

                    if (++i < length) {
                        x = x shl 8 or (wordBytes[i].toInt() and 0xFF)
                    }
                }
            }

            result[i shr 2] = x

            i++
        }

        return result
    }

    /*
     *******************************************************************************************************************
     * Content reading
     *******************************************************************************************************************
     */

    fun readResource(reference: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(4000)
        val inputStream: InputStream? = this::class.java.getResourceAsStream(reference)

        if (inputStream != null) {
            try {
                var length: Int

                while (inputStream.read(buffer).also { length = it } > 0) {
                    bytes.write(buffer, 0, length)
                }

                inputStream.close()
            } catch (e: IOException) {
                throw RuntimeException("Failed to read resource '$reference': $e")
            }
        }

        if (bytes.size() == 0) {
            throw IllegalArgumentException("Failed to read resource '$reference': empty resource?")
        }

        return bytes.toByteArray()
    }

    companion object {

        const val FIELD_BASENAME = "f"

        const val MODE_INPUT_STREAM: Int = 0

        const val MODE_INPUT_STREAM_THROTTLED: Int = 1

        const val MODE_READER: Int = 2

        const val MODE_READER_THROTTLED: Int = 3

        const val MODE_DATA_INPUT: Int = 4

        const val MODE_OUTPUT_STREAM: Int = 0

        const val MODE_WRITER = 1

        val ALL_PARSER_MODES = intArrayOf(MODE_INPUT_STREAM, MODE_INPUT_STREAM_THROTTLED, MODE_READER,
                MODE_READER_THROTTLED, MODE_DATA_INPUT)

        val ALL_NON_THROTTLED_PARSER_MODES = intArrayOf(MODE_INPUT_STREAM, MODE_READER, MODE_DATA_INPUT)

        val ALL_BINARY_PARSER_MODES = intArrayOf(MODE_INPUT_STREAM, MODE_INPUT_STREAM_THROTTLED, MODE_DATA_INPUT)

        val ALL_TEXT_PARSER_MODES = intArrayOf(MODE_READER, MODE_READER_THROTTLED)

        val ALL_STREAMING_PARSER_MODES =
                intArrayOf(MODE_INPUT_STREAM, MODE_INPUT_STREAM_THROTTLED, MODE_READER, MODE_READER_THROTTLED)

        val ALL_GENERATOR_MODES = intArrayOf(MODE_OUTPUT_STREAM, MODE_WRITER)

        const val SAMPLE_SPEC_VALUE_WIDTH: Int = 800

        const val SAMPLE_SPEC_VALUE_HEIGHT: Int = 600

        const val SAMPLE_SPEC_VALUE_TITLE: String = "View from 15th Floor"

        const val SAMPLE_SPEC_VALUE_TN_URL: String = "http://www.example.com/image/481989943"

        const val SAMPLE_SPEC_VALUE_TN_HEIGHT: Int = 125

        const val SAMPLE_SPEC_VALUE_TN_WIDTH: String = "100"

        const val SAMPLE_SPEC_VALUE_TN_ID1: Int = 116

        const val SAMPLE_SPEC_VALUE_TN_ID2: Int = 943

        const val SAMPLE_SPEC_VALUE_TN_ID3: Int = 234

        const val SAMPLE_SPEC_VALUE_TN_ID4: Int = 38793

        val SAMPLE_DOC_CIRJSON_SPEC = """
        {
          "__cirJsonId__": "root",
          "Image" : {
            "__cirJsonId__": "root/Image",
            "Width" : $SAMPLE_SPEC_VALUE_WIDTH,
            "Height" : $SAMPLE_SPEC_VALUE_HEIGHT,"Title" : "$SAMPLE_SPEC_VALUE_TITLE",
            "Thumbnail" : {
              "__cirJsonId__": "root/image/Thumbnail",
              "Url" : "$SAMPLE_SPEC_VALUE_TN_URL",
        "Height" : $SAMPLE_SPEC_VALUE_TN_HEIGHT,
              "Width" : "$SAMPLE_SPEC_VALUE_TN_WIDTH"
            },
            "IDs" : ["root/IDs",$SAMPLE_SPEC_VALUE_TN_ID1,$SAMPLE_SPEC_VALUE_TN_ID2,$SAMPLE_SPEC_VALUE_TN_ID3,$SAMPLE_SPEC_VALUE_TN_ID4]
          }
        }
        """.trimIndent()

        val CIRJSON_FACTORY = CirJsonFactory()

    }

}