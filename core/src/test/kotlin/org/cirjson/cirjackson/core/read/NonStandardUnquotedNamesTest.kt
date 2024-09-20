package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.AsyncTestBase.Companion.ALL_ASYNC_MODES
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class NonStandardUnquotedNamesTest : TestBase() {

    private val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES).build()

    @Test
    fun testUnfinishedUnquoted() {
        val chars = CharArray(4027)

        for (i in 0..<3998) {
            chars[i] = ' '
        }

        chars[3998] = '{'

        for ((i, c) in apostropheToQuote("'__cirJsonId__' : 'root', ").withIndex()) {
            chars[3999 + i] = c
        }

        chars[4025] = 'a'
        chars[4026] = 256.toChar()
        val doc = String(chars)
        val parser = createParser(factory, MODE_READER, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected end-of-input within/between Object entries")
        }

        parser.close()
    }

    @Test
    fun testLargeUnquotedNames() {
        val doc = generateLargeUnquotedNameDoc()

        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            largeUnquotedNames(mode, doc)
        }
    }

    private fun largeUnquotedNames(mode: Int, doc: String) {
        val parser = createParser(factory, mode, doc)
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
            simpleUnquotedNames(mode)
        }
    }

    private fun simpleUnquotedNames(mode: Int) {
        var doc = "{ \"__cirJsonId__\":\"root\", a : 1, _foo:true, $:\"money!\", \" \":null }"
        var parser = createParser(factory, mode, doc)
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
        assertEquals("money!", parser.text)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(" ", parser.currentName)
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        parser.close()

        doc = "{ \"__cirJsonId__\":\"root\", 123:true,4:false }"
        parser = createParser(factory, mode, doc)
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
    fun testNonStandardNameChars() {
        for (mode in ALL_ASYNC_MODES) {
            nonStandardNameChars(mode)
        }
    }

    private fun nonStandardNameChars(mode: Int) {
        val doc =
                "{ \"__cirJsonId__\":\"root\", @type : \"myType\", #color : 123, *error* : true,  hyphen-ated : \"yes\", me+my : null}"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("@type", parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("myType", parser.text)
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
        assertEquals("yes", parser.text)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("me+my", parser.currentName)
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        parser.close()
    }

    companion object {

        private const val REPS = 1050

    }

}