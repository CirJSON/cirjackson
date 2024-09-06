package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AsyncPropertyNamesTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    private val factoryApostrophe = streamFactoryBuilder().enable(CirJsonReadFeature.ALLOW_SINGLE_QUOTES).build()

    @Test
    fun testSimpleFieldNames() {
        for (mode in ALL_ASYNC_MODES) {
            for (name in SIMPLE_NAMES) {
                simpleFieldNames(mode, 0, name, 99)
                simpleFieldNames(mode, 0, name, 5)
                simpleFieldNames(mode, 0, name, 3)
                simpleFieldNames(mode, 0, name, 2)
                simpleFieldNames(mode, 0, name, 1)

                simpleFieldNames(mode, 1, name, 99)
                simpleFieldNames(mode, 1, name, 3)
                simpleFieldNames(mode, 1, name, 1)
            }
        }
    }

    private fun simpleFieldNames(mode: Int, padding: Int, name: String, bytesPerFeed: Int) {
        val doc = utf8Bytes("{\"__cirJsonId__\":\"root\", \"$name\":true}                     \r")
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(name, parser.currentName)
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        val location = parser.parser.currentLocation()
        assertEquals(2, location.lineNumber, "name: $name")
        assertEquals(1, location.columnNumber, "name: $name")
        parser.close()
    }

    @Test
    fun testEscapedFieldNames() {
        val names =
                arrayOf("\\'foo\\'" to "'foo'", "\\'foobar\\'" to "'foobar'", "\\'foo \\u0026 bar\\'" to "'foo & bar'",
                        "Something \\'longer\\'?" to "Something 'longer'?", "\\u00A7" to "\u00A7",
                        "\\u4567" to "\u4567",
                        "Unicode: \\u00A7 and \\u4567?" to "Unicode: \u00A7 and \u4567?").map { (nameEncoded, nameExpected) ->
                    apostropheToQuote(nameEncoded) to apostropheToQuote(nameExpected)
                }

        for (mode in ALL_ASYNC_MODES) {
            for ((nameEncoded, nameExpected) in names) {
                escapedFieldNames(mode, 0, nameEncoded, nameExpected, 99)
                escapedFieldNames(mode, 0, nameEncoded, nameExpected, 5)
                escapedFieldNames(mode, 0, nameEncoded, nameExpected, 3)
                escapedFieldNames(mode, 0, nameEncoded, nameExpected, 2)
                escapedFieldNames(mode, 0, nameEncoded, nameExpected, 1)

                escapedFieldNames(mode, 1, nameEncoded, nameExpected, 99)
                escapedFieldNames(mode, 1, nameEncoded, nameExpected, 3)
                escapedFieldNames(mode, 1, nameEncoded, nameExpected, 1)
            }
        }
    }

    private fun escapedFieldNames(mode: Int, padding: Int, nameEncoded: String, nameExpected: String,
            bytesPerFeed: Int) {
        var doc = utf8Bytes("{\"__cirJsonId__\":\"root\", \"$nameEncoded\":true}")
        escapedFieldNamesQuote(mode, padding, doc, nameExpected, bytesPerFeed)
        doc = utf8Bytes("{'__cirJsonId__':'root', '$nameEncoded':true}")
        escapedFieldNamesApostrophe(mode, padding, doc, nameExpected, bytesPerFeed)
    }

    private fun escapedFieldNamesQuote(mode: Int, padding: Int, doc: ByteArray, nameExpected: String,
            bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(nameExpected, parser.currentName)
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    private fun escapedFieldNamesApostrophe(mode: Int, padding: Int, doc: ByteArray, nameExpected: String,
            bytesPerFeed: Int) {
        val parser = createAsync(factoryApostrophe, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(nameExpected, parser.currentName)
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    companion object {

        private val SIMPLE_NAMES =
                arrayOf("", "a", "ab", "abc", "abcd", "abcd1", "abcd12", "abcd123", "abcd1234", "abcd1234a",
                        "abcd1234ab", "abcd1234abc", "abcd1234abcd", "abcd1234abcd1")

    }

}