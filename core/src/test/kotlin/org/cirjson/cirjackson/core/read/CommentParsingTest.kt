package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.*

class CommentParsingTest : TestBase() {

    @Test
    fun testDefaults() {
        assertFalse(sharedStreamFactory().isEnabled(CirJsonReadFeature.ALLOW_JAVA_COMMENTS))
    }

    @Test
    fun testCommentsDisabled() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (doc in DOCS) {
                commentsDisabled(mode, doc)
            }
        }
    }

    private fun commentsDisabled(mode: Int, doc: String) {
        val parser = createParser(mode, doc, false)
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
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (doc in DOCS) {
                commentsEnabled(mode, doc)
            }
        }
    }

    private fun commentsEnabled(mode: Int, doc: String) {
        val parser = createParser(mode, doc, true)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.intValue)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    @Test
    fun testCCommentsWithUTF8() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            cCommentsWithUTF8(mode)
        }
    }

    private fun cCommentsWithUTF8(mode: Int) {
        val parser = createParser(mode, UTF8_DOC, true)
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

        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            yamlCommentsEnabled(factory, mode)

            commentsBeforePropValue(factory, mode, comment)

            commentsBetweenArrayValues(factory, mode, comment)
        }
    }

    private fun yamlCommentsEnabled(factory: CirJsonFactory, mode: Int) {
        val docString = "# foo\n" +
                " {\"__cirJsonId__\":\"root\",\"a\" # xyz\n" +
                " : # foo\n" +
                " 1, # more\n" +
                "\"b\": [ \"array\", \n" +
                " #all!\n" +
                " 3 #yay!\n" +
                "] # foobar\n" +
                "} # x"
        val parser = createParser(factory, mode, docString)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("a", parser.text)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(1, parser.intValue)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("b", parser.text)
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

        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            commentsBeforePropValue(factory, mode, comment)

            commentsBetweenArrayValues(factory, mode, comment)
        }
    }

    @Test
    fun testCppCommentsEnabled() {
        val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_JAVA_COMMENTS).build()
        val comment = "// foo\n"

        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            commentsBeforePropValue(factory, mode, comment)

            commentsBetweenArrayValues(factory, mode, comment)
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    private fun commentsBeforePropValue(factory: CirJsonFactory, mode: Int, comment: String) {
        for (commented in arrayOf(":${comment}123", " :${comment}123", "\t:${comment}123", ": ${comment}123",
                ":\t${comment}123")) {
            val doc = "{\"__cirJsonId__\":\"root\",\"abc\"$commented}"
            val parser = createParser(factory, mode, doc)
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

    private fun commentsBetweenArrayValues(factory: CirJsonFactory, mode: Int, comment: String) {
        for (commented in arrayOf("$comment,", " $comment,", "\t$comment,", "$comment ,", "$comment\t,", " $comment ,",
                "\t$comment\t,", "\n$comment,", "$comment\n,")) {
            val doc = "[\"root\"${commented}1${commented}2]"
            val parser = createParser(factory, mode, doc)
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

    private fun createParser(mode: Int, doc: String, enabled: Boolean): CirJsonParser {
        val factory = CirJsonFactory.builder().configure(CirJsonReadFeature.ALLOW_JAVA_COMMENTS, enabled).build()
        return createParser(factory, mode, doc)
    }

    companion object {

        private const val DOC_WITH_C_COMMENT =
                "[ \"0\" /* comment:\n ends here */, 1 /* one more ok to have \"unquoted\"  */ ]"

        private const val DOC_WITH_CPP_COMMENT = "[ \"0\" // comment...\n, 1 \r  // one more, not array: []   \n ]"

        private val DOCS = arrayOf(DOC_WITH_C_COMMENT, DOC_WITH_CPP_COMMENT)

        private const val UTF8_DOC = "/* \u00a9 2099 Yoyodyne Inc. */\n [ \"bar? \u00a9\" ]\n"

    }

}