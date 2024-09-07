package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AsyncStringObjectTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testBasicFieldsNames() {
        val doc = utf8Bytes(apostropheToQuote(
                "{'__cirJsonId__':'root','$UNICODE_SHORT_NAME':'$UNICODE_LONG_NAME','$UNICODE_LONG_NAME':'$UNICODE_SHORT_NAME','$ASCII_SHORT_NAME':'$ASCII_SHORT_NAME'}"))

        for (mode in ALL_ASYNC_MODES) {
            for (verifyContent in BOOLEAN_OPTIONS) {
                basicFieldsNames(doc, verifyContent, mode, 0, 99)
                basicFieldsNames(doc, verifyContent, mode, 0, 5)
                basicFieldsNames(doc, verifyContent, mode, 0, 3)
                basicFieldsNames(doc, verifyContent, mode, 0, 2)
                basicFieldsNames(doc, verifyContent, mode, 0, 1)

                basicFieldsNames(doc, verifyContent, mode, 1, 99)
                basicFieldsNames(doc, verifyContent, mode, 1, 3)
                basicFieldsNames(doc, verifyContent, mode, 1, 1)
            }
        }
    }

    private fun basicFieldsNames(doc: ByteArray, verifyContent: Boolean, mode: Int, padding: Int, bytesPerFeed: Int) {
        var parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())

        if (verifyContent) {
            assertEquals(UNICODE_SHORT_NAME, parser.currentName)
            assertEquals(UNICODE_SHORT_NAME, parser.currentText())
        }

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        if (verifyContent) {
            assertEquals(UNICODE_LONG_NAME, parser.currentText())
        }

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())

        if (verifyContent) {
            assertEquals(UNICODE_LONG_NAME, parser.currentName)
            assertEquals(UNICODE_LONG_NAME, parser.currentText())
        }

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        if (verifyContent) {
            assertEquals(UNICODE_SHORT_NAME, parser.currentText())
        }

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())

        if (verifyContent) {
            assertEquals(ASCII_SHORT_NAME, parser.currentName)
            assertEquals(ASCII_SHORT_NAME, parser.currentText())
        }

        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        if (verifyContent) {
            assertEquals(ASCII_SHORT_NAME, parser.currentText())
        }

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()

        if (!verifyContent) {
            return
        }

        parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(UNICODE_SHORT_NAME, parser.currentTextViaWriter())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(UNICODE_LONG_NAME, parser.currentTextViaWriter())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(UNICODE_LONG_NAME, parser.currentTextViaWriter())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(UNICODE_SHORT_NAME, parser.currentTextViaWriter())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(ASCII_SHORT_NAME, parser.currentTextViaWriter())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(ASCII_SHORT_NAME, parser.currentTextViaWriter())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    companion object {

        private const val STRING_0_TO_9 = "1234567890"

        private const val ASCII_SHORT_NAME = "a${STRING_0_TO_9}z"

        private const val UNICODE_SHORT_NAME = "Unicode${UNICODE_3_BYTES}RlzOk"

        private const val UNICODE_LONG_NAME =
                "Unicode-$UNICODE_3_BYTES-$STRING_0_TO_9-$STRING_0_TO_9-$STRING_0_TO_9-$UNICODE_2_BYTES-$STRING_0_TO_9-$STRING_0_TO_9-$STRING_0_TO_9-$UNICODE_3_BYTES-$STRING_0_TO_9-$STRING_0_TO_9-$STRING_0_TO_9"

    }

}