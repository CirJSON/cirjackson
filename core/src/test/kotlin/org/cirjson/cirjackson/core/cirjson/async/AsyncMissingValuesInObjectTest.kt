package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.support.AsyncReaderWrapperBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class AsyncMissingValuesInObjectTest : AsyncTestBase() {

    @Test
    fun testObjectBasic() {
        for (mode in ALL_ASYNC_MODES) {
            for (features in POSSIBLE_FEATURES) {
                objectBasic(mode, 0, features, 99)
                objectBasic(mode, 0, features, 5)
                objectBasic(mode, 0, features, 3)
                objectBasic(mode, 0, features, 2)
                objectBasic(mode, 0, features, 1)

                objectBasic(mode, 1, features, 99)
                objectBasic(mode, 1, features, 3)
                objectBasic(mode, 1, features, 1)
            }
        }
    }

    private fun objectBasic(mode: Int, padding: Int, features: Array<CirJsonReadFeature>, bytesPerFeed: Int) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "{\"__cirJsonId__\":\"root\", \"a\": true, \"b\": false}".toByteArray()

        createAsync(factory, mode, bytesPerFeed, doc, padding).use { parser ->
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
            assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals("a", parser.currentText())
            assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())

            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals("b", parser.currentText())
            assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())

            assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
            assertNull(parser.nextToken())
        }
    }

    @Test
    fun testObjectInnerComma() {
        for (mode in ALL_ASYNC_MODES) {
            for (features in POSSIBLE_FEATURES) {
                objectInnerComma(mode, 0, features, 99)
                objectInnerComma(mode, 0, features, 5)
                objectInnerComma(mode, 0, features, 3)
                objectInnerComma(mode, 0, features, 2)
                objectInnerComma(mode, 0, features, 1)

                objectInnerComma(mode, 1, features, 99)
                objectInnerComma(mode, 1, features, 3)
                objectInnerComma(mode, 1, features, 1)
            }
        }
    }

    private fun objectInnerComma(mode: Int, padding: Int, features: Array<CirJsonReadFeature>, bytesPerFeed: Int) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "{\"__cirJsonId__\":\"root\", \"a\": true,, \"b\": false}".toByteArray()

        createAsync(factory, mode, bytesPerFeed, doc, padding).use { parser ->
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
            assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals("a", parser.currentText())
            assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())

            assertUnexpected(parser, ',')
        }
    }

    @Test
    fun testObjectLeadingComma() {
        for (mode in ALL_ASYNC_MODES) {
            for (features in POSSIBLE_FEATURES) {
                objectLeadingComma(mode, 0, features, 99)
                objectLeadingComma(mode, 0, features, 5)
                objectLeadingComma(mode, 0, features, 3)
                objectLeadingComma(mode, 0, features, 2)
                objectLeadingComma(mode, 0, features, 1)

                objectLeadingComma(mode, 1, features, 99)
                objectLeadingComma(mode, 1, features, 3)
                objectLeadingComma(mode, 1, features, 1)
            }
        }
    }

    private fun objectLeadingComma(mode: Int, padding: Int, features: Array<CirJsonReadFeature>, bytesPerFeed: Int) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "{,\"__cirJsonId__\":\"root\", \"a\": true, \"b\": false}".toByteArray()

        createAsync(factory, mode, bytesPerFeed, doc, padding).use { parser ->
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())

            assertUnexpected(parser, ',')
        }
    }

    @Test
    fun testObjectTrailingComma() {
        for (mode in ALL_ASYNC_MODES) {
            for (features in POSSIBLE_FEATURES) {
                objectTrailingComma(mode, 0, features, 99)
                objectTrailingComma(mode, 0, features, 5)
                objectTrailingComma(mode, 0, features, 3)
                objectTrailingComma(mode, 0, features, 2)
                objectTrailingComma(mode, 0, features, 1)

                objectTrailingComma(mode, 1, features, 99)
                objectTrailingComma(mode, 1, features, 3)
                objectTrailingComma(mode, 1, features, 1)
            }
        }
    }

    private fun objectTrailingComma(mode: Int, padding: Int, features: Array<CirJsonReadFeature>, bytesPerFeed: Int) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "{\"__cirJsonId__\":\"root\", \"a\": true, \"b\": false,}".toByteArray()

        createAsync(factory, mode, bytesPerFeed, doc, padding).use { parser ->
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
            assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals("a", parser.currentText())
            assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())

            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals("b", parser.currentText())
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
        for (mode in ALL_ASYNC_MODES) {
            for (features in POSSIBLE_FEATURES) {
                objectTrailingCommas(mode, 0, features, 99)
                objectTrailingCommas(mode, 0, features, 5)
                objectTrailingCommas(mode, 0, features, 3)
                objectTrailingCommas(mode, 0, features, 2)
                objectTrailingCommas(mode, 0, features, 1)

                objectTrailingCommas(mode, 1, features, 99)
                objectTrailingCommas(mode, 1, features, 3)
                objectTrailingCommas(mode, 1, features, 1)
            }
        }
    }

    private fun objectTrailingCommas(mode: Int, padding: Int, features: Array<CirJsonReadFeature>, bytesPerFeed: Int) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "{\"__cirJsonId__\":\"root\", \"a\": true, \"b\": false,,}".toByteArray()

        createAsync(factory, mode, bytesPerFeed, doc, padding).use { parser ->
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
            assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals("a", parser.currentText())
            assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())

            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals("b", parser.currentText())
            assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())

            assertUnexpected(parser, ',')
        }
    }

    private fun assertUnexpected(parser: AsyncReaderWrapperBase, c: Char) {
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