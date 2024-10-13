package org.cirjson.cirjackson.core.write

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonGeneratorBase
import org.cirjson.cirjackson.core.cirjson.CirJsonWriteFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("HttpUrlsUsage")
class CirJsonWriteFeatureEscapeForwardSlashTest : TestBase() {

    @Test
    fun testDefaultSettings() {
        val factory = newStreamFactory()
        assertTrue(factory.isEnabled(CirJsonWriteFeature.ESCAPE_FORWARD_SLASHES))

        for (mode in ALL_GENERATOR_MODES) {
            defaultSettings(factory, mode)
        }
    }

    private fun defaultSettings(factory: CirJsonFactory, mode: Int) {
        val generator = createGenerator(factory, mode) as CirJsonGeneratorBase
        assertTrue(generator.isEnabled(CirJsonWriteFeature.ESCAPE_FORWARD_SLASHES))
        generator.close()
    }

    @Test
    fun testDontEscapeForwardSlash() {
        val factory = CirJsonFactory.builder().disable(CirJsonWriteFeature.ESCAPE_FORWARD_SLASHES).build()

        for (mode in ALL_GENERATOR_MODES) {
            dontEscapeForwardSlash(factory, mode)
        }
    }

    private fun dontEscapeForwardSlash(factory: CirJsonFactory, mode: Int) {
        val generator = createGenerator(factory, mode)
        forwardSlash(generator)
        assertEquals("{\"__cirJsonId__\":\"0\",\"url\":\"http://example.com\"}",
                generator.streamWriteOutputTarget!!.toString())
    }

    @Test
    fun testEscapeForwardSlash() {
        val factory = CirJsonFactory.builder().enable(CirJsonWriteFeature.ESCAPE_FORWARD_SLASHES).build()

        for (mode in ALL_GENERATOR_MODES) {
            escapeForwardSlash(factory, mode)
        }
    }

    private fun escapeForwardSlash(factory: CirJsonFactory, mode: Int) {
        val generator = createGenerator(factory, mode)
        forwardSlash(generator)
        assertEquals("{\"__cirJsonId__\":\"0\",\"url\":\"http:\\/\\/example.com\"}",
                generator.streamWriteOutputTarget!!.toString())
    }

    private fun forwardSlash(generator: CirJsonGenerator) {
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeStringProperty("url", "http://example.com")
        generator.writeEndObject()
        generator.close()
    }

}