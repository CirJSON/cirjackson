package org.cirjson.cirjackson.core.base64

import org.cirjson.cirjackson.core.Base64Variant
import org.cirjson.cirjackson.core.Base64Variants
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class Base64PaddingTest : TestBase() {

    @Test
    fun testPaddingUsingInputStream() {
        testPadding(MODE_INPUT_STREAM)
        testPadding(MODE_INPUT_STREAM_THROTTLED)
    }

    @Test
    fun testPaddingUsingReader() {
        testPadding(MODE_READER)
        testPadding(MODE_READER_THROTTLED)
    }

    @Test
    fun testPaddingUsingDataInput() {
        testPadding(MODE_DATA_INPUT)
    }

    private fun testPadding(mode: Int) {
        val base64Variant =
                Base64Variants.MIME_NO_LINEFEEDS.withReadBehaviour(Base64Variant.PaddingReadBehaviour.PADDING_ALLOWED)
        val doc =
                "{\"__cirJsonId__\":\"root\" , \"diff\" : \"1sPEAASBOGM6XGFwYWNoZV9yb290X2Rldlx0bXBcX3N0YXBsZXJcNHEydHJhY3ZcYXZhc3RfZnJlZV9hbnRpdmlydXNfc2V0dXBfb25saW5lLmV4ZS8vYzpcYXBhY2hlX3Jvb3RfZGV2XHN0b3JhZ2VcY1w3XDFcYzcxZmViMTA2NDA5MTE4NzIwOGI4MGNkM2Q0NWE0YThcYXZhc3RfZnJlZV9hbnRpdmlydXNfc2V0dXBfb25saW5lLmV4ZS8FkK0pAKA2kLFgAJsXgyyBZfkKWXg6OZiYBgBYCQCASAAAgAMAAAC4AACABgEAgAoAAABYCACADgAAAJgIAIAQAAAA2AgAgBgAAAAYCWgAAIDJAAAAkHgJAwAqDwCoAAAAqBgDAIwOAAUAAQAAAPAAAIACAUABAIAEAQCABQEIAQAAOCcDAEAhADABAAB4SAMAKFgBAACgigMAqCUAAQAASLADAKgBAADwwAMAaAQAFQA\"}"

        createParser(CIRJSON_FACTORY, mode, doc).use { parser ->
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
            assertEquals("__cirJsonId__", parser.nextName())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            assertEquals("diff", parser.nextName())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            val bytes = parser.getBinaryValue(base64Variant)
            assertNotNull(bytes)
            assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        }
    }

}