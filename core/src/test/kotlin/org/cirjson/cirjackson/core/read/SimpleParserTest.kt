package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.support.MockDataInput
import org.cirjson.cirjackson.core.util.CirJsonParserDelegate
import java.io.*
import java.nio.file.Files
import java.util.*
import kotlin.test.*

class SimpleParserTest : TestBase() {

    @Test
    fun testConfig() {
        var parser = createParser(MODE_READER, "[ \"root\" ]")
        var source = parser.streamReadInputSource()
        assertNotNull(source)
        assertIs<Reader>(source)
        parser.close()

        parser = createParser(MODE_INPUT_STREAM, "[ \"root\" ]")
        source = parser.streamReadInputSource()
        assertNotNull(source)
        assertIs<InputStream>(source)
        parser.close()

        parser = createParser(MODE_DATA_INPUT, "[ \"root\" ]")
        source = parser.streamReadInputSource()
        assertNotNull(source)
        assertIs<DataInput>(source)
        parser.close()
    }

    @Test
    fun testInterning() {
        val names = arrayOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j")

        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            interning(mode, true, names[mode * 2])
            interning(mode, false, names[mode * 2 + 1])
        }
    }

    private fun interning(mode: Int, enableIntern: Boolean, expectedName: String) {
        val factory = CirJsonFactory.builder().configure(TokenStreamFactory.Feature.INTERN_PROPERTY_NAMES, enableIntern)
                .build()
        assertEquals(enableIntern, factory.isEnabled(TokenStreamFactory.Feature.INTERN_PROPERTY_NAMES))
        val doc = "{ \"__cirJsonId__\":\"root\", \"$expectedName\" : 1}"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        val actualName = parser.currentName()!!
        assertEquals(expectedName, actualName)

        if (enableIntern) {
            assertSame(expectedName, actualName)
        } else {
            assertNotSame(expectedName, actualName)
        }

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testSpecExampleSkipping() {
        specExample(verifyContents = false, verifyNumbers = false)
    }

    @Test
    fun testSpecExampleFully() {
        specExample(verifyContents = true, verifyNumbers = false)
        specExample(verifyContents = true, verifyNumbers = true)
    }

    private fun specExample(verifyContents: Boolean, verifyNumbers: Boolean) {
        var parser = createParserUsingReader(CIRJSON_FACTORY, SAMPLE_DOC_CIRJSON_SPEC)
        specExample(parser, verifyContents, verifyNumbers)
        parser.close()

        parser = createParserUsingStream(CIRJSON_FACTORY, SAMPLE_DOC_CIRJSON_SPEC, "UTF-8")
        specExample(parser, verifyContents, verifyNumbers)
        parser.close()
        parser = createParserUsingStream(CIRJSON_FACTORY, SAMPLE_DOC_CIRJSON_SPEC, "UTF-16BE")
        specExample(parser, verifyContents, verifyNumbers)
        parser.close()
        parser = createParserUsingStream(CIRJSON_FACTORY, SAMPLE_DOC_CIRJSON_SPEC, "UTF-16LE")
        specExample(parser, verifyContents, verifyNumbers)
        parser.close()
        parser = createParserUsingStream(CIRJSON_FACTORY, SAMPLE_DOC_CIRJSON_SPEC, "UTF-32")
        specExample(parser, verifyContents, verifyNumbers)
        parser.close()

        parser = createParserForDataInput(CIRJSON_FACTORY, MockDataInput(SAMPLE_DOC_CIRJSON_SPEC))
        specExample(parser, verifyContents, verifyNumbers)
        parser.close()
    }

    private fun specExample(parser: CirJsonParser, verifyContents: Boolean, verifyNumbers: Boolean) {
        if (!parser.isCurrentTokenNotNull) {
            parser.nextToken()
        }

        assertNull(parser.typeId)
        assertNull(parser.objectId)

        assertToken(CirJsonToken.START_OBJECT, parser.currentToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())

        if (verifyContents) {
            verifyFieldName(parser, "Image")
        }

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())

        if (verifyContents) {
            verifyFieldName(parser, "Width")
        }

        verifyIntToken(parser.nextToken(), verifyNumbers)

        if (verifyContents) {
            verifyIntValue(parser, SAMPLE_SPEC_VALUE_WIDTH)
        }

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())

        if (verifyContents) {
            verifyFieldName(parser, "Height")
        }

        verifyIntToken(parser.nextToken(), verifyNumbers)

        if (verifyContents) {
            verifyIntValue(parser, SAMPLE_SPEC_VALUE_HEIGHT)
        }

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())

        if (verifyContents) {
            verifyFieldName(parser, "Title")
        }

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        if (verifyContents) {
            assertEquals(SAMPLE_SPEC_VALUE_TITLE, getAndVerifyText(parser))
        }

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())

        if (verifyContents) {
            verifyFieldName(parser, "Thumbnail")
        }

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())

        if (verifyContents) {
            verifyFieldName(parser, "Url")
        }

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        if (verifyContents) {
            assertEquals(SAMPLE_SPEC_VALUE_TN_URL, getAndVerifyText(parser))
        }

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())

        if (verifyContents) {
            verifyFieldName(parser, "Height")
        }

        verifyIntToken(parser.nextToken(), verifyNumbers)

        if (verifyContents) {
            verifyIntValue(parser, SAMPLE_SPEC_VALUE_TN_HEIGHT)
        }

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())

        if (verifyContents) {
            verifyFieldName(parser, "Width")
        }

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        if (verifyContents) {
            assertEquals(SAMPLE_SPEC_VALUE_TN_WIDTH, getAndVerifyText(parser))
        }

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())

        if (verifyContents) {
            verifyFieldName(parser, "IDs")
        }

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        verifyIntToken(parser.nextToken(), verifyNumbers)

        if (verifyContents) {
            verifyIntValue(parser, SAMPLE_SPEC_VALUE_TN_ID1)
        }

        verifyIntToken(parser.nextToken(), verifyNumbers)

        if (verifyContents) {
            verifyIntValue(parser, SAMPLE_SPEC_VALUE_TN_ID2)
        }

        verifyIntToken(parser.nextToken(), verifyNumbers)

        if (verifyContents) {
            verifyIntValue(parser, SAMPLE_SPEC_VALUE_TN_ID3)
        }

        verifyIntToken(parser.nextToken(), verifyNumbers)

        if (verifyContents) {
            verifyIntValue(parser, SAMPLE_SPEC_VALUE_TN_ID4)
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
    }

    private fun verifyIntToken(token: CirJsonToken?, requireNumbers: Boolean) {
        if (token == CirJsonToken.VALUE_NUMBER_INT) {
            return
        }

        if (requireNumbers) {
            assertToken(CirJsonToken.VALUE_NUMBER_INT, token)
        }

        if (token != CirJsonToken.VALUE_STRING) {
            fail("Expected INT or STRING value, got $token")
        }
    }

    private fun verifyIntValue(parser: CirJsonParser, expected: Int) {
        assertEquals(expected.toString(), parser.text)
    }

    @Test
    fun testKeywords() {
        val doc =
                "{\n\"__cirJsonId__\" : \"root\",\n\"key1\" : null,\n\"key2\" : true,\n\"key3\" : false,\n\"key4\" : [ \"key4\", false, null, true ]\n}"
        var parser = createParserUsingReader(CIRJSON_FACTORY, doc)
        keywords(parser, true)
        parser.close()

        parser = createParserUsingStream(CIRJSON_FACTORY, doc, "UTF-8")
        keywords(parser, true)
        parser.close()
        parser = createParserUsingStream(CIRJSON_FACTORY, doc, "UTF-16BE")
        keywords(parser, true)
        parser.close()
        parser = createParserUsingStream(CIRJSON_FACTORY, doc, "UTF-16LE")
        keywords(parser, true)
        parser.close()
        parser = createParserUsingStream(CIRJSON_FACTORY, doc, "UTF-32")
        keywords(parser, true)
        parser.close()

        parser = createParserForDataInput(CIRJSON_FACTORY, MockDataInput(doc))
        keywords(parser, false)
        parser.close()
    }

    private fun keywords(parser: CirJsonParser, checkColumn: Boolean) {
        var context = parser.streamReadContext()!!
        assertEquals("/", context.toString())
        assertTrue(context.isInRoot)
        assertFalse(context.isInArray)
        assertFalse(context.isInObject)
        assertEquals(0, context.entryCount)
        assertEquals(0, context.currentIndex)

        assertFalse(parser.isCurrentTokenNotNull)
        assertNull(parser.text)
        assertNull(parser.textCharacters)
        assertEquals(0, parser.textLength)
        assertEquals(0, parser.textOffset)

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertEquals("/", context.toString())

        assertTrue(parser.isCurrentTokenNotNull)
        val location = parser.currentTokenLocation()
        assertNotNull(location)
        assertEquals(1, location.lineNumber)

        if (checkColumn) {
            assertEquals(1, location.lineNumber)
        }

        context = parser.streamReadContext()!!
        assertFalse(context.isInRoot)
        assertFalse(context.isInArray)
        assertTrue(context.isInObject)
        assertEquals(0, context.entryCount)
        assertEquals(0, context.currentIndex)

        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        verifyFieldName(parser, "key1")
        assertEquals("{\"key1\"}", context.toString())
        assertEquals(3, parser.currentTokenLocation().lineNumber)

        context = parser.streamReadContext()!!
        assertFalse(context.isInRoot)
        assertFalse(context.isInArray)
        assertTrue(context.isInObject)
        assertEquals(2, context.entryCount)
        assertEquals(1, context.currentIndex)
        assertEquals("key1", context.currentName)

        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertEquals("key1", context.currentName)

        context = parser.streamReadContext()!!
        assertEquals(2, context.entryCount)
        assertEquals(1, context.currentIndex)

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        verifyFieldName(parser, "key2")
        context = parser.streamReadContext()!!
        assertEquals(3, context.entryCount)
        assertEquals(2, context.currentIndex)

        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertEquals("key2", context.currentName)

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        verifyFieldName(parser, "key3")
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        verifyFieldName(parser, "key4")

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        context = parser.streamReadContext()!!
        assertFalse(context.isInRoot)
        assertTrue(context.isInArray)
        assertFalse(context.isInObject)
        assertNull(context.currentName)
        assertEquals("key4", context.parent!!.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertEquals("[0]", context.toString())

        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())

        context = parser.streamReadContext()!!
        assertTrue(context.isInObject)

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        context = parser.streamReadContext()!!
        assertTrue(context.isInRoot)
        assertNull(context.currentName)
    }

    @Test
    fun testSkipping() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            skipping(mode)
        }
    }

    private fun skipping(mode: Int) {
        val doc = apostropheToQuote(
                "[ 'root', 1, 3, [ '2', true, null ], 3, { '__cirJsonId__':'4', 'a\\\\b':'quoted: \\'stuff\\'' }, [ '5', [ '5/0' ] ], { '__cirJsonId__':'4' } ]")
        var parser = createParser(mode, doc)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        parser.skipChildren()
        assertToken(CirJsonToken.END_ARRAY, parser.currentToken())
        assertNull(parser.nextToken())
        parser.close()

        parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        parser.skipChildren()
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.currentToken())
        assertEquals(1, parser.intValue)

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        parser.skipChildren()
        assertToken(CirJsonToken.END_ARRAY, parser.currentToken())

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        parser.skipChildren()
        assertToken(CirJsonToken.END_OBJECT, parser.currentToken())

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        parser.skipChildren()
        assertToken(CirJsonToken.END_ARRAY, parser.currentToken())

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        parser.skipChildren()
        assertToken(CirJsonToken.END_OBJECT, parser.currentToken())

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testNameEscaping() {
        val names = mapOf("" to "", "\\\"funny\\\"" to "\"funny\"", "\\\\" to "\\", "\\r" to "\r", "\\n" to "\n",
                "\\t" to "\t", "\\r\\n" to "\r\n", "\\\"\\\"" to "\"\"", "Line\\nfeed" to "Line\nfeed",
                "Yet even longer \\\"name\\\"!" to "Yet even longer \"name\"!")

        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for ((name, expected) in names) {
                nameEscaping(mode, name, expected)
            }
        }
    }

    private fun nameEscaping(mode: Int, name: String, expected: String) {
        val doc = "{ \"__cirJsonId__\":\"root\", \"$name\":null}"
        val parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        val actual = parser.currentName()
        assertEquals(actual, getAndVerifyText(parser))
        assertEquals(expected, actual)
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testLongText() {
        longText(310)
        longText(7700)
        longText(49000)
        longText(96000)
    }

    private fun longText(length: Int) {
        val value = createLongText(length)
        val stringWriter = StringWriter(length + (length shr 2))
        val generator = CIRJSON_FACTORY.createGenerator(ObjectWriteContext.empty(), stringWriter)
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeName("doc")
        generator.writeString(value)
        generator.writeEndObject()
        generator.close()
        val doc = stringWriter.toString()

        var parser = createParserUsingReader(CIRJSON_FACTORY, doc)
        longText(parser, value)
        parser.close()

        parser = createParserUsingStream(CIRJSON_FACTORY, doc, "UTF-8")
        longText(parser, value)
        parser.close()
        parser = createParserUsingStream(CIRJSON_FACTORY, doc, "UTF-16BE")
        longText(parser, value)
        parser.close()
        parser = createParserUsingStream(CIRJSON_FACTORY, doc, "UTF-16LE")
        longText(parser, value)
        parser.close()
        parser = createParserUsingStream(CIRJSON_FACTORY, doc, "UTF-32")
        longText(parser, value)
        parser.close()

        parser = createParserForDataInput(CIRJSON_FACTORY, MockDataInput(doc))
        longText(parser, value)
        parser.close()
    }

    private fun longText(parser: CirJsonParser, value: String) {
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("doc", parser.currentName())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        val actual = getAndVerifyText(parser)
        assertEquals(value, actual)
        assertEquals("doc", parser.currentName())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    private fun createLongText(length: Int): String {
        val stringBuilder = StringBuilder(length + 100)
        val random = Random(length.toLong())

        while (stringBuilder.length < length) {
            stringBuilder.append(random.nextInt())
            stringBuilder.append(" xyz foo")

            if (random.nextBoolean()) {
                stringBuilder.append(" and \"bar\"")
            } else if (random.nextBoolean()) {
                stringBuilder.append(" [whatever].... ")
            } else {
                stringBuilder.append(" UTF-8-fu: try this {\u00E2/\u0BF8/\uA123!} (look funny?)")
            }

            if (random.nextBoolean()) {
                if (random.nextBoolean()) {
                    stringBuilder.append('\r')
                } else if (random.nextBoolean()) {
                    stringBuilder.append('\n')
                } else {
                    stringBuilder.append("\r\n")
                }
            }
        }

        return stringBuilder.toString()
    }

    @Test
    fun testBytesAsSource() {
        val baseDoc = "[ \"root\", 1, 2, 3, 4 ]"
        val bytes = utf8Bytes(baseDoc)
        val offset = 50
        val length = bytes.size
        val data = ByteArray(100 + length)
        bytes.copyInto(data, offset, 0, length)

        val parser = CIRJSON_FACTORY.createParser(ObjectReadContext.empty(), data, offset, length)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.intValue)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(2, parser.intValue)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(3, parser.intValue)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(4, parser.intValue)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testUtf8BOMHandling() {
        val output = ByteArrayOutputStream()
        output.write(0xEF)
        output.write(0xBB)
        output.write(0xBF)
        output.write(utf8Bytes("[ \"root\", 1 ]"))
        val data = output.toByteArray()

        var parser = CIRJSON_FACTORY.createParser(ObjectReadContext.empty(), data)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        val location = parser.currentTokenLocation()
        assertEquals(3L, location.byteOffset)
        assertEquals(-1L, location.charOffset)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()

        parser = CIRJSON_FACTORY.createParser(ObjectReadContext.empty(), MockDataInput(data))
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertNotNull(parser.currentTokenLocation())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testSpacesInURL() {
        val file = File.createTempFile("pre fix&stuff", ".txt")
        val writer = BufferedWriter(OutputStreamWriter(Files.newOutputStream(file.toPath()), Charsets.UTF_8))
        writer.write("{ \"__cirJsonId__\" : \"root\" }")
        writer.close()
        val url = file.toURI().toURL()

        val parser = CIRJSON_FACTORY.createParser(ObjectReadContext.empty(), url)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testGetValueAsText() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            getValueAsText(mode, false)
            getValueAsText(mode, true)
        }
    }

    private fun getValueAsText(mode: Int, delegate: Boolean) {
        val doc = "{\"__cirJsonId__\":\"root\",\"a\":1,\"b\":true,\"c\":null,\"d\":\"foo\"}"
        val parser = createParser(mode, doc).let { if (delegate) CirJsonParserDelegate(it) else it }

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertNull(parser.valueAsString)
        assertEquals("foobar", parser.getValueAsString("foobar"))
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("a", parser.text)
        assertEquals("a", parser.valueAsString)
        assertEquals("a", parser.getValueAsString("foobar"))
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("1", parser.valueAsString)

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("b", parser.valueAsString)
        assertEquals("b", parser.getValueAsString("foobar"))
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertEquals("true", parser.valueAsString)
        assertEquals("true", parser.getValueAsString("foobar"))

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("c", parser.valueAsString)
        assertEquals("c", parser.getValueAsString("foobar"))
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertNull(parser.valueAsString)
        assertEquals("foobar", parser.getValueAsString("foobar"))

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("d", parser.getValueAsString("foobar"))
        assertEquals("d", parser.valueAsString)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("foo", parser.getValueAsString("foobar"))
        assertEquals("foo", parser.valueAsString)

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertEquals("foobar", parser.getValueAsString("foobar"))
        assertNull(parser.valueAsString)
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testGetTextViaWriter() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            getTextViaWriter(mode)
        }
    }

    private fun getTextViaWriter(mode: Int) {
        val longText = "this is a sample text for cirjson parsing using readText() method"
        val doc = "{\"__cirJsonId__\":\"root\",\"a\":\"$longText\",\"b\":true,\"c\":null,\"d\":\"foobar!\"}"
        val parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("a", parser.currentName())
        assertTextViaWriter(parser, "a")
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertTextViaWriter(parser, longText)
        assertEquals(longText, parser.text)

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertTextViaWriter(parser, "b")
        assertEquals("b", parser.currentName())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertTextViaWriter(parser, "true")

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertTextViaWriter(parser, "c")
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertTextViaWriter(parser, "null")

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertTextViaWriter(parser, "d")
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("foobar!", parser.text)
        assertTextViaWriter(parser, "foobar!")

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testLongerReadText() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            longerReadText(mode)
        }
    }

    private fun longerReadText(mode: Int) {
        val stringBuilder = StringBuilder(15 * 1000)

        for (i in 0..999) {
            stringBuilder.append("Sample Text").append(i)
        }

        val longText = stringBuilder.toString()
        val doc = "{\"__cirJsonId__\":\"root\",\"a\":\"$longText\",\"b\":true,\"c\":null,\"d\":\"foobar!\"}"
        val parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("a", parser.currentName())
        assertTextViaWriter(parser, "a")
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertTextViaWriter(parser, longText)
        assertEquals(longText, parser.text)

        parser.close()
    }

    /*
     *******************************************************************************************************************
     * Tests, Invalid input
     *******************************************************************************************************************
     */

    @Test
    fun testHandlingOfInvalidSpace() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            handlingOfInvalidSpace(mode)
        }
    }

    private fun handlingOfInvalidSpace(mode: Int) {
        val doc = "{ \u00A0 \"__cirJsonId__\":\"root\",\"a\":1}"
        val parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "unexpected character")
            verifyException(e, "code 160")
        }

        parser.close()
    }

    @Test
    fun testHandlingOfInvalidSpaceFromResource() {
        handlingOfInvalidSpaceFromResource(true)
        handlingOfInvalidSpaceFromResource(false)
    }

    private fun handlingOfInvalidSpaceFromResource(useStream: Boolean) {
        val input = this::class.java.getResourceAsStream("/test_0xA0.cirjson")!!
        val parser = if (useStream) {
            CIRJSON_FACTORY.createParser(ObjectReadContext.empty(), input)
        } else {
            CIRJSON_FACTORY.createParser(ObjectReadContext.empty(), InputStreamReader(input, Charsets.UTF_8))
        }

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals("request", parser.currentName())
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
            assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals("mac", parser.currentName())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            assertNotNull(parser.text)
            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals("data", parser.currentName())
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())

            @Suppress("ControlFlowWithEmptyBody")
            while (parser.nextToken() != null) {
            }

            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "unexpected character")
            verifyException(e, "code 160")
        }

        parser.close()
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    private fun verifyFieldName(parser: CirJsonParser, expectedName: String) {
        assertEquals(expectedName, parser.text)
        assertEquals(expectedName, parser.currentName())
    }

    private fun assertTextViaWriter(parser: CirJsonParser, expected: String) {
        val writer = StringWriter()
        val length = parser.getText(writer)
        val actual = writer.toString()
        assertEquals(length, actual.length)
        assertEquals(expected, actual)
    }

}