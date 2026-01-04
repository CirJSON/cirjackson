package org.cirjson.cirjackson.core.symbols

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.TokenStreamFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestSymbolsWithMediaItem : TestBase() {

    @Test
    fun testSmallSymbolSetWithBytes() {
        for (mode in ALL_BINARY_PARSER_MODES) {
            smallSymbolSetWithBytes(mode)
        }
    }

    private fun smallSymbolSetWithBytes(mode: Int) {
        val symbolsRoot = ByteQuadsCanonicalizer.createRoot(SEED)
        val symbols = symbolsRoot.makeChild(TokenStreamFactory.Feature.collectDefaults())
        val factory = CirJsonFactory()
        val parser = createParser(factory, mode, DOC)

        var token: CirJsonToken?

        while (parser.nextToken().also { token = it } != null && token != CirJsonToken.NOT_AVAILABLE) {
            if (token != CirJsonToken.CIRJSON_ID_PROPERTY_NAME && token != CirJsonToken.PROPERTY_NAME) {
                continue
            }

            val name = parser.currentName()!!
            val quads = calcQuads(name.toByteArray(Charsets.UTF_8))

            if (symbols.findName(quads, quads.size) != null) {
                continue
            }

            symbols.addName(name, quads, quads.size)
        }

        parser.close()

        assertEquals(14, symbols.size)
        assertEquals(13, symbols.primaryCount)
        assertEquals(1, symbols.secondaryCount)
        assertEquals(0, symbols.tertiaryCount)
        assertEquals(0, symbols.spilloverCount)
    }

    @Test
    fun testSmallSymbolSetWithChars() {
        val factory = CirJsonFactory()
        val symbols = CharsToNameCanonicalizer.createRoot(factory, SEED).makeChild()
        val parser = createParser(factory, MODE_READER, DOC)

        var token: CirJsonToken?

        while (parser.nextToken().also { token = it } != null && token != CirJsonToken.NOT_AVAILABLE) {
            if (token != CirJsonToken.CIRJSON_ID_PROPERTY_NAME && token != CirJsonToken.PROPERTY_NAME) {
                continue
            }

            val name = parser.currentName()!!
            val ch = name.toCharArray()
            symbols.findSymbol(ch, 0, ch.size, symbols.calculateHash(name))
        }

        parser.close()

        assertEquals(14, symbols.size)
        assertEquals(64, symbols.bucketCount)
        assertEquals(0, symbols.collisionCount)
        assertEquals(0, symbols.maxCollisionLength)
        assertNotNull(symbols.toString())
    }

    companion object {

        private const val SEED = 33333

        private const val DOC = """
            {
                "__cirJsonId__": "root",
                "media" : {
                    "__cirJsonId__": "media",
                    "uri" : "https://foo.com",
                    "title" : "Test title 1",
                    "width" : 640, "height" : 480,
                    "format" : "video/mpeg4",
                    "duration" : 18000000,
                    "size" : 58982400,
                    "bitrate" : 262144,
                    "persons" : [ "media/persons" ],
                    "player" : "native",
                    "copyright" : "None"
                },
               "images" : [
                   "images",
                   {
                       "__cirJsonId__": "images/0",
                       "uri" : "https://bar.com",
                       "title" : "Test title 1",
                       "width" : 1024,"height" : 768,
                       "size" : "LARGE"
                   },
                   {
                       "__cirJsonId__": "images/1",
                       "uri" : "https://foobar.org",
                       "title" : "JavaOne Keynote",
                       "width" : 320, "height" : 240,
                       "size" : "SMALL"
                   }
               ]
            }
        """

    }

}