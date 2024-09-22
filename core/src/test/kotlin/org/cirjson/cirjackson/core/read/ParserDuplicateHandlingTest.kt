package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.StreamReadFeature
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.*

class ParserDuplicateHandlingTest : TestBase() {

    @Test
    fun testSimpleDuplicateCheckDisabled() {
        val factory = newStreamFactory()
        assertFalse(factory.isEnabled(StreamReadFeature.STRICT_DUPLICATE_DETECTION))

        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (doc in DOCS) {
                simpleDuplicateCheckDisabled(factory, mode, doc)
            }
        }
    }

    private fun simpleDuplicateCheckDisabled(factory: CirJsonFactory, mode: Int, doc: String) {
        val parser = createParser(factory, mode, doc)
        val token = parser.nextToken()
        assertNotNull(token)
        assertTrue(token.isStructStart)

        var depth = 1

        while (depth > 0) {
            when (parser.nextToken()) {
                CirJsonToken.START_ARRAY, CirJsonToken.START_OBJECT -> ++depth

                CirJsonToken.END_ARRAY, CirJsonToken.END_OBJECT -> --depth

                else -> {}
            }
        }

        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testSimpleDuplicateCheckEnabled() {
        val factory = CirJsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()

        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (doc in DOCS) {
                simpleDuplicateCheckEnabled(factory, mode, doc)
            }
        }
    }

    private fun simpleDuplicateCheckEnabled(factory: CirJsonFactory, mode: Int, doc: String) {
        val parser = createParser(factory, mode, doc)
        val token = parser.nextToken()
        assertNotNull(token)
        assertTrue(token.isStructStart)

        var depth = 1

        try {
            while (depth > 0) {
                when (parser.nextToken()) {
                    CirJsonToken.START_ARRAY, CirJsonToken.START_OBJECT -> ++depth

                    CirJsonToken.END_ARRAY, CirJsonToken.END_OBJECT -> --depth

                    else -> {}
                }
            }

            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "duplicate Object Property \"a\"")
        }

        parser.close()
    }

    companion object {

        private val DOCS =
                arrayOf("{ '__cirJsonId__':'root', 'a':1, 'a':2 }", "['root', { '__cirJsonId__':'0', 'a':1, 'a':2 }]",
                        "{ '__cirJsonId__':'root', 'a':1, 'b':2, 'c':3,'a':true,'e':false }",
                        "{ '__cirJsonId__':'root', 'foo': { '__cirJsonId__':'foo', 'bar': [ 'foo/bar', [ 'foo/bar/0', { '__cirJsonId__':'foo/bar/0/0', 'x':3, 'a':1 } ]], 'x':0, 'a':'y', 'b':3,'a':13 } }",
                        "['root', {'__cirJsonId__':'0', 'b':1},{'__cirJsonId__':'1', 'b\":3},['2', {'__cirJsonId__':'2/0', 'a':3}], {'__cirJsonId__':'3', 'a':1,'a':2}]",
                        "{'__cirJsonId__':'root', 'b':1,'array':['array', {'__cirJsonId__':'array/0', 'b':3}],'ob':{'__cirJsonId__':'ob', 'b':4,'x':0,'y':3,'a':true,'a':false }}").map {
                    apostropheToQuote(it)
                }

    }

}