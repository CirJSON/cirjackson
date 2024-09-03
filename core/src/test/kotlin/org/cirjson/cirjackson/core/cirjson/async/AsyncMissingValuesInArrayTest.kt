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

class AsyncMissingValuesInArrayTest : AsyncTestBase() {

    @Test
    fun testArrayInnerComma() {
        for (mode in ALL_ASYNC_MODES) {
            for (features in POSSIBLE_FEATURES) {
                arrayInnerComma(mode, 0, features, 99)
                arrayInnerComma(mode, 0, features, 5)
                arrayInnerComma(mode, 0, features, 3)
                arrayInnerComma(mode, 0, features, 2)
                arrayInnerComma(mode, 0, features, 1)

                arrayInnerComma(mode, 1, features, 99)
                arrayInnerComma(mode, 1, features, 3)
                arrayInnerComma(mode, 1, features, 1)
            }
        }
    }

    private fun arrayInnerComma(mode: Int, padding: Int, features: Array<CirJsonReadFeature>, bytesPerFeed: Int) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "[\"a\",, \"b\"]".toByteArray()
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("a", parser.currentText())

        if (CirJsonReadFeature.ALLOW_MISSING_VALUES !in features) {
            assertUnexpected(parser, ',')
            parser.close()
            return
        }

        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("b", parser.currentText())

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testArrayLeadingComma() {
        for (mode in ALL_ASYNC_MODES) {
            for (features in POSSIBLE_FEATURES) {
                arrayLeadingComma(mode, 0, features, 99)
                arrayLeadingComma(mode, 0, features, 5)
                arrayLeadingComma(mode, 0, features, 3)
                arrayLeadingComma(mode, 0, features, 2)
                arrayLeadingComma(mode, 0, features, 1)

                arrayLeadingComma(mode, 1, features, 99)
                arrayLeadingComma(mode, 1, features, 3)
                arrayLeadingComma(mode, 1, features, 1)
            }
        }
    }

    private fun arrayLeadingComma(mode: Int, padding: Int, features: Array<CirJsonReadFeature>, bytesPerFeed: Int) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "[, \"a\", \"b\"]".toByteArray()
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())

        assertUnexpected(parser, ',')
        parser.close()
    }

    @Test
    fun testArrayTrailingComma() {
        for (mode in ALL_ASYNC_MODES) {
            for (features in POSSIBLE_FEATURES) {
                arrayTrailingComma(mode, 0, features, 99)
                arrayTrailingComma(mode, 0, features, 5)
                arrayTrailingComma(mode, 0, features, 3)
                arrayTrailingComma(mode, 0, features, 2)
                arrayTrailingComma(mode, 0, features, 1)

                arrayTrailingComma(mode, 1, features, 99)
                arrayTrailingComma(mode, 1, features, 3)
                arrayTrailingComma(mode, 1, features, 1)
            }
        }
    }

    private fun arrayTrailingComma(mode: Int, padding: Int, features: Array<CirJsonReadFeature>, bytesPerFeed: Int) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "[\"a\", \"b\",]".toByteArray()
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("a", parser.currentText())

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("b", parser.currentText())

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
        for (mode in ALL_ASYNC_MODES) {
            for (features in POSSIBLE_FEATURES) {
                arrayTrailingCommas(mode, 0, features, 99)
                arrayTrailingCommas(mode, 0, features, 5)
                arrayTrailingCommas(mode, 0, features, 3)
                arrayTrailingCommas(mode, 0, features, 2)
                arrayTrailingCommas(mode, 0, features, 1)

                arrayTrailingCommas(mode, 1, features, 99)
                arrayTrailingCommas(mode, 1, features, 3)
                arrayTrailingCommas(mode, 1, features, 1)
            }
        }
    }

    private fun arrayTrailingCommas(mode: Int, padding: Int, features: Array<CirJsonReadFeature>, bytesPerFeed: Int) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "[\"a\", \"b\",,]".toByteArray()
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("a", parser.currentText())

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("b", parser.currentText())

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
        for (mode in ALL_ASYNC_MODES) {
            for (features in POSSIBLE_FEATURES) {
                arrayTripleTrailingCommas(mode, 0, features, 99)
                arrayTripleTrailingCommas(mode, 0, features, 5)
                arrayTripleTrailingCommas(mode, 0, features, 3)
                arrayTripleTrailingCommas(mode, 0, features, 2)
                arrayTripleTrailingCommas(mode, 0, features, 1)

                arrayTripleTrailingCommas(mode, 1, features, 99)
                arrayTripleTrailingCommas(mode, 1, features, 3)
                arrayTripleTrailingCommas(mode, 1, features, 1)
            }
        }
    }

    private fun arrayTripleTrailingCommas(mode: Int, padding: Int, features: Array<CirJsonReadFeature>,
            bytesPerFeed: Int) {
        val factory = CirJsonFactory.builder().enable(*features).build()
        val doc = "[\"a\", \"b\",,,]".toByteArray()
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("a", parser.currentText())

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("b", parser.currentText())

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