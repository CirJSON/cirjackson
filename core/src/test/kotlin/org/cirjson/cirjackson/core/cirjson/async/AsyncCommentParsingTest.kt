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

class AsyncCommentParsingTest : AsyncTestBase() {

    @Test
    fun testCommentsDisabled() {
        for (mode in ALL_ASYNC_MODES) {
            for (docString in DOCS) {
                val doc = docString.toByteArray()
                commentsDisabled(mode, 0, doc, 99)
                commentsDisabled(mode, 0, doc, 5)
                commentsDisabled(mode, 0, doc, 3)
                commentsDisabled(mode, 0, doc, 2)
                commentsDisabled(mode, 0, doc, 1)

                commentsDisabled(mode, 1, doc, 99)
                commentsDisabled(mode, 1, doc, 3)
                commentsDisabled(mode, 1, doc, 1)
            }
        }
    }

    private fun commentsDisabled(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createParser(mode, padding, doc, bytesPerFeed, false)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Expected exception for unrecognized comment")
        } catch (e: StreamReadException) {
            verifyException(e, "ALLOW_COMMENTS")
        }

        parser.close()
    }

    @Test
    fun testCommentsEnabled() {
        for (mode in ALL_ASYNC_MODES) {
            for (docString in DOCS) {
                val doc = docString.toByteArray()
                commentsEnabled(mode, 0, doc, 99)
                commentsEnabled(mode, 0, doc, 5)
                commentsEnabled(mode, 0, doc, 3)
                commentsEnabled(mode, 0, doc, 2)
                commentsEnabled(mode, 0, doc, 1)

                commentsEnabled(mode, 1, doc, 99)
                commentsEnabled(mode, 1, doc, 3)
                commentsEnabled(mode, 1, doc, 1)
            }
        }
    }

    private fun commentsEnabled(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createParser(mode, padding, doc, bytesPerFeed, true)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.intValue)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    @Test
    fun testCCommentsWithUTF8() {
        val doc = UTF8_DOC

        for (mode in ALL_ASYNC_MODES) {
            cCommentsWithUTF8(mode, 0, doc, 99)
            cCommentsWithUTF8(mode, 0, doc, 5)
            cCommentsWithUTF8(mode, 0, doc, 3)
            cCommentsWithUTF8(mode, 0, doc, 2)
            cCommentsWithUTF8(mode, 0, doc, 1)

            cCommentsWithUTF8(mode, 1, doc, 99)
            cCommentsWithUTF8(mode, 1, doc, 3)
            cCommentsWithUTF8(mode, 1, doc, 1)
        }
    }

    private fun cCommentsWithUTF8(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createParser(mode, padding, doc, bytesPerFeed, true)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testYamlCommentsEnabled() {
        val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_YAML_COMMENTS).build()
        val comment = "# foo\n"

        for (mode in ALL_ASYNC_MODES) {
            yamlCommentsEnabled(factory, mode, 0, 99)
            yamlCommentsEnabled(factory, mode, 0, 5)
            yamlCommentsEnabled(factory, mode, 0, 3)
            yamlCommentsEnabled(factory, mode, 0, 2)
            yamlCommentsEnabled(factory, mode, 0, 1)

            yamlCommentsEnabled(factory, mode, 1, 99)
            yamlCommentsEnabled(factory, mode, 1, 3)
            yamlCommentsEnabled(factory, mode, 1, 1)

            commentsBeforePropValue(factory, mode, 0, comment, 99)
            commentsBeforePropValue(factory, mode, 0, comment, 5)
            commentsBeforePropValue(factory, mode, 0, comment, 3)
            commentsBeforePropValue(factory, mode, 0, comment, 2)
            commentsBeforePropValue(factory, mode, 0, comment, 1)

            commentsBeforePropValue(factory, mode, 1, comment, 99)
            commentsBeforePropValue(factory, mode, 1, comment, 3)
            commentsBeforePropValue(factory, mode, 1, comment, 1)

            commentsBetweenArrayValues(factory, mode, 0, comment, 99)
            commentsBetweenArrayValues(factory, mode, 0, comment, 5)
            commentsBetweenArrayValues(factory, mode, 0, comment, 3)
            commentsBetweenArrayValues(factory, mode, 0, comment, 2)
            commentsBetweenArrayValues(factory, mode, 0, comment, 1)

            commentsBetweenArrayValues(factory, mode, 1, comment, 99)
            commentsBetweenArrayValues(factory, mode, 1, comment, 3)
            commentsBetweenArrayValues(factory, mode, 1, comment, 1)
        }
    }

    private fun yamlCommentsEnabled(factory: CirJsonFactory, mode: Int, padding: Int, bytesPerFeed: Int) {
        val docString = "# foo\n" +
                " {\"__cirJsonId__\":\"root\",\"a\" # xyz\n" +
                " : # foo\n" +
                " 1, # more\n" +
                "\"b\": [ \"array\", \n" +
                " #all!\n" +
                " 3 #yay!\n" +
                "] # foobar\n" +
                "} # x"
        val parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes(docString), padding)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("a", parser.currentText())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.intValue)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("b", parser.currentText())
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(3, parser.intValue)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        parser.close()
    }

    @Test
    fun testCCommentsEnabled() {
        val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_JAVA_COMMENTS).build()
        val comment = "/* foo */\n"

        for (mode in ALL_ASYNC_MODES) {
            commentsBeforePropValue(factory, mode, 0, comment, 99)
            commentsBeforePropValue(factory, mode, 0, comment, 5)
            commentsBeforePropValue(factory, mode, 0, comment, 3)
            commentsBeforePropValue(factory, mode, 0, comment, 2)
            commentsBeforePropValue(factory, mode, 0, comment, 1)

            commentsBeforePropValue(factory, mode, 1, comment, 99)
            commentsBeforePropValue(factory, mode, 1, comment, 3)
            commentsBeforePropValue(factory, mode, 1, comment, 1)

            commentsBetweenArrayValues(factory, mode, 0, comment, 99)
            commentsBetweenArrayValues(factory, mode, 0, comment, 5)
            commentsBetweenArrayValues(factory, mode, 0, comment, 3)
            commentsBetweenArrayValues(factory, mode, 0, comment, 2)
            commentsBetweenArrayValues(factory, mode, 0, comment, 1)

            commentsBetweenArrayValues(factory, mode, 1, comment, 99)
            commentsBetweenArrayValues(factory, mode, 1, comment, 3)
            commentsBetweenArrayValues(factory, mode, 1, comment, 1)
        }
    }

    @Test
    fun testCppCommentsEnabled() {
        val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_JAVA_COMMENTS).build()
        val comment = "// foo\n"

        for (mode in ALL_ASYNC_MODES) {
            commentsBeforePropValue(factory, mode, 0, comment, 99)
            commentsBeforePropValue(factory, mode, 0, comment, 5)
            commentsBeforePropValue(factory, mode, 0, comment, 3)
            commentsBeforePropValue(factory, mode, 0, comment, 2)
            commentsBeforePropValue(factory, mode, 0, comment, 1)

            commentsBeforePropValue(factory, mode, 1, comment, 99)
            commentsBeforePropValue(factory, mode, 1, comment, 3)
            commentsBeforePropValue(factory, mode, 1, comment, 1)

            commentsBetweenArrayValues(factory, mode, 0, comment, 99)
            commentsBetweenArrayValues(factory, mode, 0, comment, 5)
            commentsBetweenArrayValues(factory, mode, 0, comment, 3)
            commentsBetweenArrayValues(factory, mode, 0, comment, 2)
            commentsBetweenArrayValues(factory, mode, 0, comment, 1)

            commentsBetweenArrayValues(factory, mode, 1, comment, 99)
            commentsBetweenArrayValues(factory, mode, 1, comment, 3)
            commentsBetweenArrayValues(factory, mode, 1, comment, 1)
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    private fun commentsBeforePropValue(factory: CirJsonFactory, mode: Int, padding: Int, comment: String,
            bytesPerFeed: Int) {
        for (commented in arrayOf(":${comment}123", " :${comment}123", "\t:${comment}123", ": ${comment}123",
                ":\t${comment}123")) {
            val doc = "{\"__cirJsonId__\":\"root\",\"abc\"$commented}"
            val parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes(doc), padding)
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
            assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(123, parser.intValue)
            assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
            parser.close()
        }
    }

    private fun commentsBetweenArrayValues(factory: CirJsonFactory, mode: Int, padding: Int, comment: String,
            bytesPerFeed: Int) {
        for (commented in arrayOf("$comment,", " $comment,", "\t$comment,", "$comment ,", "$comment\t,", " $comment ,",
                "\t$comment\t,", "\n$comment,", "$comment\n,")) {
            val doc = "[\"root\"${commented}1${commented}2]"
            val parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes(doc), padding)
            assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
            var token = try {
                parser.nextToken()
            } catch (e: StreamReadException) {
                throw RuntimeException("Failed on '$doc' due to $e", e)
            }
            assertToken(CirJsonToken.VALUE_STRING, token)
            token = try {
                parser.nextToken()
            } catch (e: StreamReadException) {
                throw RuntimeException("Failed on '$doc' due to $e", e)
            }
            assertToken(CirJsonToken.VALUE_NUMBER_INT, token)
            assertEquals(1, parser.intValue)
            token = try {
                parser.nextToken()
            } catch (e: StreamReadException) {
                throw RuntimeException("Failed on '$doc' due to $e", e)
            }
            assertToken(CirJsonToken.VALUE_NUMBER_INT, token)
            assertEquals(2, parser.intValue)
            assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
            parser.close()
        }
    }

    private fun createParser(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int,
            enabled: Boolean): AsyncReaderWrapperBase {
        val factory = CirJsonFactory.builder().configure(CirJsonReadFeature.ALLOW_JAVA_COMMENTS, enabled).build()
        return createAsync(factory, mode, bytesPerFeed, doc, padding)
    }

    companion object {

        private const val DOC_WITH_C_COMMENT =
                "[ \"0\" /* comment:\n ends here */, 1 /* one more ok to have \"unquoted\"  */ ]"

        private const val DOC_WITH_CPP_COMMENT = "[ \"0\" // comment...\n, 1 \r  // one more, not array: []   \n ]"

        private val DOCS = arrayOf(DOC_WITH_C_COMMENT, DOC_WITH_CPP_COMMENT)

        private val UTF8_DOC = "/* \u00a9 2099 Yoyodyne Inc. */\n [ \"bar? \u00a9\" ]\n".toByteArray()

    }

}