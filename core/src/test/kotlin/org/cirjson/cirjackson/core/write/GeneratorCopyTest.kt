package org.cirjson.cirjackson.core.write

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class GeneratorCopyTest : TestBase() {

    private val factory = sharedStreamFactory()

    @Test
    fun testCopyRootTokens() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                copyRootTokens(generatorMode, parserMode)
            }
        }
    }

    private fun copyRootTokens(generatorMode: Int, parserMode: Int) {
        val doc = "\"text\\non two lines\" true false 2.0 null 1234567890123 "
        val parser = createParser(factory, parserMode, doc)
        val generator = createGenerator(factory, generatorMode)

        var token: CirJsonToken?

        while (parser.nextToken().also { token = it } != null) {
            generator.copyCurrentEvent(parser)
            assertToken(token!!, parser.currentToken())
        }

        parser.close()
        generator.close()

        assertEquals("\"text\\non two lines\" true false 2.0 null 1234567890123",
                generator.streamWriteOutputTarget!!.toString())
    }

    @Test
    fun testCopyArrayTokens() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                copyArrayTokens(generatorMode, parserMode)
            }
        }
    }

    private fun copyArrayTokens(generatorMode: Int, parserMode: Int) {
        val doc = "123 [\"root\", 1, null, [\"2\", false, 1234567890124 ] ]"
        val parser = createParser(factory, parserMode, doc)
        val generator = createGenerator(factory, generatorMode)

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        generator.copyCurrentEvent(parser)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.currentToken())
        assertEquals(123, parser.intValue)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        generator.copyCurrentStructure(parser)
        assertToken(CirJsonToken.END_ARRAY, parser.currentToken())

        parser.close()
        generator.close()

        assertEquals("123 [\"root\",1,null,[\"2\",false,1234567890124]]",
                generator.streamWriteOutputTarget!!.toString())
    }

    @Test
    fun testCopyObjectTokens() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                copyObjectTokens(generatorMode, parserMode)
            }
        }
    }

    private fun copyObjectTokens(generatorMode: Int, parserMode: Int) {
        var doc =
                "{ \"__cirJsonId__\":\"root\", \"a\":1, \"b\":[\"b\",{ \"__cirJsonId__\":\"b/0\", \"c\" : null, \"d\" : 0.25 }] }"
        var parser = createParser(factory, parserMode, doc)
        var generator = createGenerator(factory, generatorMode)

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        generator.copyCurrentStructure(parser)
        assertToken(CirJsonToken.END_OBJECT, parser.currentToken())
        parser.close()
        generator.close()
        assertEquals(
                "{\"__cirJsonId__\":\"root\",\"a\":1,\"b\":[\"b\",{\"__cirJsonId__\":\"b\\/0\",\"c\":null,\"d\":0.25}]}",
                generator.streamWriteOutputTarget!!.toString())

        doc = "{\"__cirJsonId__\":\"root\",\"a\":1,\"b\":null}"
        parser = createParser(factory, parserMode, doc)
        generator = createGenerator(factory, generatorMode)
        generator.writeStartObject()
        generator.writeObjectId(Any())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        generator.copyCurrentStructure(parser)
        generator.writeEndObject()

        parser.close()
        generator.close()
        assertEquals("{\"__cirJsonId__\":\"0\",\"a\":1}", generator.streamWriteOutputTarget!!.toString())
    }

}