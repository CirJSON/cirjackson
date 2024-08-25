package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class CustomQuoteCharTest : TestBase() {

    private val factory = streamFactoryBuilder().quoteChar('\'').build()

    @Test
    fun testInvalidQuote() {
        try {
            streamFactoryBuilder().quoteChar('\u00A0')
            fail("Should not allow quote character outside ASCII range")
        } catch (e: IllegalArgumentException) {
            verifyException(e, "Can only use Unicode characters up to 0x7F")
        }
    }

    @Test
    fun testBasicApostrophe() {
        for (mode in ALL_GENERATOR_MODES) {
            basicApostrophe(mode)
        }
    }

    private fun basicApostrophe(mode: Int) {
        var generator = createGenerator(factory, mode)
        var output = generator.streamWriteOutputTarget!!
        writeObject(generator, "question", "answer")
        assertEquals("{'__cirJsonId__':'0','question':'answer'}", output.toString())

        generator = createGenerator(factory, mode)
        output = generator.streamWriteOutputTarget!!
        writeArray(generator, "hello world")
        assertEquals("['0','hello world']", output.toString())
    }

    @Test
    fun testApostropheQuoting() {
        for (mode in ALL_GENERATOR_MODES) {
            apostropheQuoting(mode)
        }
    }

    private fun apostropheQuoting(mode: Int) {
        var generator = createGenerator(factory, mode)
        var output = generator.streamWriteOutputTarget!!
        writeObject(generator, "object's key", "It's \"fun\"")
        assertEquals("{'__cirJsonId__':'0','object\\'s key':'It\\'s \\\"fun\\\"'}", output.toString())

        generator = createGenerator(factory, mode)
        output = generator.streamWriteOutputTarget!!
        writeArray(generator, "It's a sin")
        assertEquals("['0','It\\'s a sin']", output.toString())
    }

    private fun writeObject(generator: CirJsonGenerator, key: String, value: String) {
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeStringProperty(key, value)
        generator.writeEndObject()
        generator.close()
    }

    private fun writeArray(generator: CirJsonGenerator, value: String) {
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeString(value)
        generator.writeEndArray()
        generator.close()
    }

}