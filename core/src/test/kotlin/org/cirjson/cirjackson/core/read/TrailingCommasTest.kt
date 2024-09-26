package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class TrailingCommasTest : TestBase() {

    @Test
    fun testArrayBasic() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (features in POSSIBLE_FEATURES) {
                arrayBasic(mode, features)
            }
        }
    }

    private fun arrayBasic(mode: Int, features: Array<CirJsonReadFeature>) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "[\"a\", \"b\"]"
        val parser = createParser(factory, mode, doc)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("a", parser.text)

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("b", parser.text)

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testArrayInnerComma() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (features in POSSIBLE_FEATURES) {
                arrayInnerComma(mode, features)
            }
        }
    }

    private fun arrayInnerComma(mode: Int, features: Array<CirJsonReadFeature>) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "[\"a\",, \"b\"]"
        val parser = createParser(factory, mode, doc)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("a", parser.text)

        if (CirJsonReadFeature.ALLOW_MISSING_VALUES !in features) {
            assertUnexpected(parser, ',')
            parser.close()
            return
        }

        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("b", parser.text)

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testArrayLeadingComma() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (features in POSSIBLE_FEATURES) {
                arrayLeadingComma(mode, features)
            }
        }
    }

    private fun arrayLeadingComma(mode: Int, features: Array<CirJsonReadFeature>) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "[, \"a\", \"b\"]"
        val parser = createParser(factory, mode, doc)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())

        assertUnexpected(parser, ',')
        parser.close()
    }

    @Test
    fun testArrayTrailingComma() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (features in POSSIBLE_FEATURES) {
                arrayTrailingComma(mode, features)
                arrayTrailingComma(mode, features)
                arrayTrailingComma(mode, features)
                arrayTrailingComma(mode, features)
                arrayTrailingComma(mode, features)

                arrayTrailingComma(mode, features)
                arrayTrailingComma(mode, features)
                arrayTrailingComma(mode, features)
            }
        }
    }

    private fun arrayTrailingComma(mode: Int, features: Array<CirJsonReadFeature>) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "[\"a\", \"b\",]"
        val parser = createParser(factory, mode, doc)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("a", parser.text)

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("b", parser.text)

        if (features.isEmpty()) {
            assertUnexpected(parser, ']')
            parser.close()
            return
        }

        if (CirJsonReadFeature.ALLOW_TRAILING_COMMA !in features) {
            assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testArrayTrailingCommas() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (features in POSSIBLE_FEATURES) {
                arrayTrailingCommas(mode, features)
            }
        }
    }

    private fun arrayTrailingCommas(mode: Int, features: Array<CirJsonReadFeature>) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "[\"a\", \"b\",,]"
        val parser = createParser(factory, mode, doc)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("a", parser.text)

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("b", parser.text)

        if (CirJsonReadFeature.ALLOW_MISSING_VALUES !in features) {
            assertUnexpected(parser, ',')
            parser.close()
            return
        }

        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())

        if (CirJsonReadFeature.ALLOW_TRAILING_COMMA !in features) {
            assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testArrayTripleTrailingCommas() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (features in POSSIBLE_FEATURES) {
                arrayTripleTrailingCommas(mode, features)
            }
        }
    }

    private fun arrayTripleTrailingCommas(mode: Int, features: Array<CirJsonReadFeature>) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "[\"a\", \"b\",,,]"
        val parser = createParser(factory, mode, doc)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("a", parser.text)

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("b", parser.text)

        if (CirJsonReadFeature.ALLOW_MISSING_VALUES !in features) {
            assertUnexpected(parser, ',')
            parser.close()
            return
        }

        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())

        if (CirJsonReadFeature.ALLOW_TRAILING_COMMA !in features) {
            assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testObjectBasic() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (features in POSSIBLE_FEATURES) {
                objectBasic(mode, features)
            }
        }
    }

    private fun objectBasic(mode: Int, features: Array<CirJsonReadFeature>) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "{\"__cirJsonId__\":\"root\", \"a\": true, \"b\": false}"

        createParser(factory, mode, doc).use { parser ->
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
            assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals("a", parser.text)
            assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())

            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals("b", parser.text)
            assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())

            assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
            assertNull(parser.nextToken())
        }
    }

    @Test
    fun testObjectInnerComma() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (features in POSSIBLE_FEATURES) {
                objectInnerComma(mode, features)
            }
        }
    }

    private fun objectInnerComma(mode: Int, features: Array<CirJsonReadFeature>) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "{\"__cirJsonId__\":\"root\", \"a\": true,, \"b\": false}"

        createParser(factory, mode, doc).use { parser ->
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
            assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals("a", parser.text)
            assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())

            assertUnexpected(parser, ',')
        }
    }

    @Test
    fun testObjectLeadingComma() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (features in POSSIBLE_FEATURES) {
                objectLeadingComma(mode, features)
            }
        }
    }

    private fun objectLeadingComma(mode: Int, features: Array<CirJsonReadFeature>) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "{,\"__cirJsonId__\":\"root\", \"a\": true, \"b\": false}"

        createParser(factory, mode, doc).use { parser ->
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())

            assertUnexpected(parser, ',')
        }
    }

    @Test
    fun testObjectTrailingComma() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (features in POSSIBLE_FEATURES) {
                objectTrailingComma(mode, features)
            }
        }
    }

    private fun objectTrailingComma(mode: Int, features: Array<CirJsonReadFeature>) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "{\"__cirJsonId__\":\"root\", \"a\": true, \"b\": false,}"

        createParser(factory, mode, doc).use { parser ->
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
            assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals("a", parser.text)
            assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())

            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals("b", parser.text)
            assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())

            if (CirJsonReadFeature.ALLOW_TRAILING_COMMA in features) {
                assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
                assertNull(parser.nextToken())
            } else {
                assertUnexpected(parser, '}')
            }
        }
    }

    @Test
    fun testObjectTrailingCommas() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (features in POSSIBLE_FEATURES) {
                objectTrailingCommas(mode, features)
            }
        }
    }

    private fun objectTrailingCommas(mode: Int, features: Array<CirJsonReadFeature>) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "{\"__cirJsonId__\":\"root\", \"a\": true, \"b\": false,,}"

        createParser(factory, mode, doc).use { parser ->
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
            assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals("a", parser.text)
            assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())

            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals("b", parser.text)
            assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())

            assertUnexpected(parser, ',')
        }
    }

    private fun assertUnexpected(parser: CirJsonParser, c: Char) {
        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('$c' (code ${c.code}))")
        }
    }

    companion object {

        private val POSSIBLE_FEATURES = listOf(emptyArray(), arrayOf(CirJsonReadFeature.ALLOW_MISSING_VALUES),
                arrayOf(CirJsonReadFeature.ALLOW_TRAILING_COMMA),
                arrayOf(CirJsonReadFeature.ALLOW_MISSING_VALUES, CirJsonReadFeature.ALLOW_TRAILING_COMMA))

    }

}