package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class AsyncNonStandardParsingTest : AsyncTestBase() {

    @Test
    fun testLargeUnquotedNames() {
        val doc = utf8Bytes(generateLargeUnquotedNameDoc())

        for (mode in ALL_ASYNC_MODES) {
            largeUnquotedNames(mode, 0, doc, 99)
            largeUnquotedNames(mode, 0, doc, 5)
            largeUnquotedNames(mode, 0, doc, 3)
            largeUnquotedNames(mode, 0, doc, 2)
            largeUnquotedNames(mode, 0, doc, 1)

            largeUnquotedNames(mode, 1, doc, 99)
            largeUnquotedNames(mode, 1, doc, 3)
            largeUnquotedNames(mode, 1, doc, 1)
        }
    }

    private fun largeUnquotedNames(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES).build()
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        for (i in 1..REPS) {
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
            assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals("abc${i and 127}", parser.currentName)
            assertToken(if (i and 1 != 0) CirJsonToken.VALUE_TRUE else CirJsonToken.VALUE_FALSE, parser.nextToken())
            assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    private fun generateLargeUnquotedNameDoc(): String {
        val stringBuilder = StringBuilder(7500)
        stringBuilder.append("[\n\"root\"")

        for (i in 1..REPS) {
            stringBuilder.append(',')

            if (i and 7 == 0) {
                stringBuilder.append('\n')
            }

            stringBuilder.append("{\"__cirJsonId__\":\"$i\",")
            stringBuilder.append("abc").append(i and 127).append(':')
            stringBuilder.append(i and 1 != 0)
            stringBuilder.append("}\n")
        }

        stringBuilder.append(']')
        return stringBuilder.toString()
    }

    @Test
    fun testSimpleUnquotedNames() {
        for (mode in ALL_ASYNC_MODES) {
            simpleUnquotedNames(mode, 0, 99)
            simpleUnquotedNames(mode, 0, 5)
            simpleUnquotedNames(mode, 0, 3)
            simpleUnquotedNames(mode, 0, 2)
            simpleUnquotedNames(mode, 0, 1)

            simpleUnquotedNames(mode, 1, 99)
            simpleUnquotedNames(mode, 1, 3)
            simpleUnquotedNames(mode, 1, 1)
        }
    }

    private fun simpleUnquotedNames(mode: Int, padding: Int, bytesPerFeed: Int) {
        var doc = utf8Bytes("{ \"__cirJsonId__\":\"root\", a : 1, _foo:true, $:\"money!\", \" \":null }")
        val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES).build()
        var parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("a", parser.currentName)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("_foo", parser.currentName)
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("$", parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("money!", parser.currentText())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(" ", parser.currentName)
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        parser.close()

        doc = utf8Bytes("{ \"__cirJsonId__\":\"root\", 123:true,4:false }")
        parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("123", parser.currentName)
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("4", parser.currentName)
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        parser.close()
    }

    @Test
    fun testApostropheQuotingDisabled() {
        for (mode in ALL_ASYNC_MODES) {
            apostropheQuotingDisabled(mode, 0, 99)
            apostropheQuotingDisabled(mode, 0, 5)
            apostropheQuotingDisabled(mode, 0, 3)
            apostropheQuotingDisabled(mode, 0, 2)
            apostropheQuotingDisabled(mode, 0, 1)

            apostropheQuotingDisabled(mode, 1, 99)
            apostropheQuotingDisabled(mode, 1, 3)
            apostropheQuotingDisabled(mode, 1, 1)
        }
    }

    private fun apostropheQuotingDisabled(mode: Int, padding: Int, bytesPerFeed: Int) {
        val factory = CirJsonFactory()

        var doc = utf8Bytes("[\"root\", 'text' ]")
        var parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('''")
        }

        parser.close()

        doc = utf8Bytes("{ \"__cirJsonId__\":\"root\", 'a':1 }")
        parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('''")
        }

        parser.close()
    }

    @Test
    fun testApostropheQuotingEnabled() {
        for (mode in ALL_ASYNC_MODES) {
            apostropheQuotingEnabled(mode, 0, 99)
            apostropheQuotingEnabled(mode, 0, 5)
            apostropheQuotingEnabled(mode, 0, 3)
            apostropheQuotingEnabled(mode, 0, 2)
            apostropheQuotingEnabled(mode, 0, 1)

            apostropheQuotingEnabled(mode, 1, 99)
            apostropheQuotingEnabled(mode, 1, 3)
            apostropheQuotingEnabled(mode, 1, 1)
        }
    }

    private fun apostropheQuotingEnabled(mode: Int, padding: Int, bytesPerFeed: Int) {
        val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_SINGLE_QUOTES).build()

        var doc = utf8Bytes(
                "{ \"__cirJsonId__\":\"root\", 'a' : 1, \"foobar\": 'b', '_abcde1234':'d', '\"' : '\"\"', '':'', '$UNICODE_NAME':'$UNICODE_VALUE' }")
        var parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("a", parser.currentName)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("1", parser.currentText())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("foobar", parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("b", parser.currentText())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("_abcde1234", parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("d", parser.currentText())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("\"", parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("\"\"", parser.currentText())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("", parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("", parser.currentText())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(UNICODE_NAME, parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(UNICODE_VALUE, parser.currentText())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        parser.close()

        doc = utf8Bytes(
                "{ \"__cirJsonId__\":\"root\", 'b':1,'array':[\"array\",{\"__cirJsonId__\":\"array/0\",'b':3}],'ob':{\"__cirJsonId__\":\"ob\",'b':4,'x':0,'y':'$UNICODE_SEGMENT','a':false } }")
        parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("b", parser.currentName)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("b", parser.currentName)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(3, parser.intValue)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("b", parser.currentName)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(4, parser.intValue)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("x", parser.currentName)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(0, parser.intValue)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("y", parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(UNICODE_SEGMENT, parser.currentText())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("a", parser.currentName)
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        parser.close()
    }

    @Test
    fun testSingleQuotesEscaped() {
        for (mode in ALL_ASYNC_MODES) {
            singleQuotesEscaped(mode, 0, 99)
            singleQuotesEscaped(mode, 0, 5)
            singleQuotesEscaped(mode, 0, 3)
            singleQuotesEscaped(mode, 0, 2)
            singleQuotesEscaped(mode, 0, 1)

            singleQuotesEscaped(mode, 1, 99)
            singleQuotesEscaped(mode, 1, 3)
            singleQuotesEscaped(mode, 1, 1)
        }
    }

    private fun singleQuotesEscaped(mode: Int, padding: Int, bytesPerFeed: Int) {
        val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_SINGLE_QUOTES).build()
        val doc = utf8Bytes("[ '16\\'' ]")
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("16'", parser.currentText())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    @Test
    fun testNonStandardNameChars() {
        for (mode in ALL_ASYNC_MODES) {
            nonStandardNameChars(mode, 0, 99)
            nonStandardNameChars(mode, 0, 5)
            nonStandardNameChars(mode, 0, 3)
            nonStandardNameChars(mode, 0, 2)
            nonStandardNameChars(mode, 0, 1)

            nonStandardNameChars(mode, 1, 99)
            nonStandardNameChars(mode, 1, 3)
            nonStandardNameChars(mode, 1, 1)
        }
    }

    private fun nonStandardNameChars(mode: Int, padding: Int, bytesPerFeed: Int) {
        val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES).build()
        val doc = utf8Bytes(
                "{ \"__cirJsonId__\":\"root\", @type : \"myType\", #color : 123, *error* : true,  hyphen-ated : \"yes\", me+my : null}")
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("@type", parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("myType", parser.currentText())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("#color", parser.currentName)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(123, parser.intValue)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("*error*", parser.currentName)
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("hyphen-ated", parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("yes", parser.currentText())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("me+my", parser.currentName)
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        parser.close()
    }

    @Test
    fun testNonStandardBackslashQuotingForValues() {
        for (mode in ALL_ASYNC_MODES) {
            nonStandardBackslashQuotingForValues(mode, 0, 99)
            nonStandardBackslashQuotingForValues(mode, 0, 5)
            nonStandardBackslashQuotingForValues(mode, 0, 3)
            nonStandardBackslashQuotingForValues(mode, 0, 2)
            nonStandardBackslashQuotingForValues(mode, 0, 1)

            nonStandardBackslashQuotingForValues(mode, 1, 99)
            nonStandardBackslashQuotingForValues(mode, 1, 3)
            nonStandardBackslashQuotingForValues(mode, 1, 1)
        }
    }

    private fun nonStandardBackslashQuotingForValues(mode: Int, padding: Int, bytesPerFeed: Int) {
        val docString = quote("\\'")
        val doc = utf8Bytes(docString)

        var factory = CirJsonFactory()
        var parser = createAsync(factory, mode, bytesPerFeed, doc, padding)

        try {
            parser.nextToken()
            parser.currentText()
            fail("Should have thrown an exception for doc <$docString>")
        } catch (e: StreamReadException) {
            verifyException(e, "unrecognized character escape")
        }

        parser.close()

        factory = factory.rebuild().enable(CirJsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER).build()
        parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("'", parser.currentText())
        parser.close()
    }

    companion object {

        private const val REPS = 1050

        private const val UNICODE_NAME = "Uni$UNICODE_2_BYTES-key-$UNICODE_3_BYTES"

        private const val UNICODE_VALUE = "Uni$UNICODE_3_BYTES-value-$UNICODE_2_BYTES"

    }

}