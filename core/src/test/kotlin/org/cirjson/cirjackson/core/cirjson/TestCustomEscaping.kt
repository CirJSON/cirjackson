package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.SerializableString
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.io.CharacterEscapes
import org.cirjson.cirjackson.core.io.SerializedString
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCustomEscaping : TestBase() {

    @Test
    fun testAboveAsciiEscape() {
        for (mode in ALL_GENERATOR_MODES) {
            for (stringAsChars in STRING_AS_CHARS_OPTIONS) {
                aboveAsciiEscape(mode, stringAsChars)
            }
        }
    }

    private fun aboveAsciiEscape(mode: Int, stringAsChars: Boolean) {
        var factory = CirJsonFactory()
        val value = "chars: [\u00A0]-[\u1234]"
        val key = "fun:\u0088:\u3456"

        var generator = createGenerator(factory, mode)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        writeString(generator, value, stringAsChars)
        generator.writeEndArray()
        generator.close()
        var actual = generator.streamWriteOutputTarget!!.toString()
        assertEquals("[\"0\",${quote(value)}]", actual)

        factory = factory.rebuild().enable(CirJsonWriteFeature.ESCAPE_NON_ASCII).build()
        generator = createGenerator(factory, mode)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        writeString(generator, "$value\\", stringAsChars)
        generator.writeEndArray()
        generator.close()
        actual = generator.streamWriteOutputTarget!!.toString()
        assertEquals("[\"0\",${quote("chars: [\\u00A0]-[\\u1234]\\\\")}]", actual)

        factory = factory.rebuild().enable(CirJsonWriteFeature.ESCAPE_NON_ASCII).build()
        generator = createGenerator(factory, mode)
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeName("$key\\")
        generator.writeBoolean(true)
        generator.writeEndObject()
        generator.close()
        actual = generator.streamWriteOutputTarget!!.toString()
        assertEquals("{\"__cirJsonId__\":\"0\",${quote("fun:\\u0088:\\u3456\\\\")}:true}", actual)
    }

    @Test
    fun testEscapeCustom() {
        for (mode in ALL_GENERATOR_MODES) {
            for (customString in CUSTOM_STRINGS) {
                for (stringAsChars in STRING_AS_CHARS_OPTIONS) {
                    escapeCustom(mode, customString, stringAsChars)
                }
            }
        }
    }

    private fun escapeCustom(mode: Int, customString: String, stringAsChars: Boolean) {
        val factory = CirJsonFactory.builder().characterEscapes(MyEscapes(customString)).build()
        val stringOut = "[\\A\\u0062c$customString-$TWO_BYTE_ESCAPED_STRING-$THREE_BYTE_ESCAPED_STRING]"
        val generator = createGenerator(factory, mode)
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeName(STRING_IN)
        writeString(generator, STRING_IN, stringAsChars)
        generator.writeEndObject()
        generator.close()
        val expected = "{\"__cirJsonI${customString}__\":\"0\",${quote(stringOut)}:${quote(stringOut)}}"
        val actual = generator.streamWriteOutputTarget!!.toString()
        assertEquals(expected, actual)
    }

    private fun writeString(generator: CirJsonGenerator, string: String, stringAsChars: Boolean) {
        if (stringAsChars) {
            generator.writeString(string)
        } else {
            val ch = string.toCharArray()
            generator.writeString(ch, 0, ch.size)
        }
    }

    @Test
    fun testCirJsonpEscapes() {
        // TODO: create CirJSONP
    }

    private class MyEscapes(customString: String) : CharacterEscapes() {

        private val myCustomSerializableString = SerializedString(customString)

        override val escapeCodesForAscii: IntArray = standardAsciiEscapesForCirJSON.apply {
            this['a'.code] = 'A'.code
            this['b'.code] = ESCAPE_STANDARD
            this['d'.code] = ESCAPE_CUSTOM
        }

        override fun getEscapeSequence(ch: Int): SerializableString? {
            return when (ch) {
                'd'.code -> myCustomSerializableString
                TWO_BYTE_ESCAPED -> TWO_BYTE_ESCAPED_STRING
                THREE_BYTE_ESCAPED -> THREE_BYTE_ESCAPED_STRING
                else -> null
            }
        }

    }

    companion object {

        private const val TWO_BYTE_ESCAPED = 0x111

        private const val THREE_BYTE_ESCAPED = 0x1111

        private val TWO_BYTE_ESCAPED_STRING = SerializedString("&111")

        private val THREE_BYTE_ESCAPED_STRING = SerializedString("&1111")

        private const val STRING_IN = "[abcd-${TWO_BYTE_ESCAPED.toChar()}-${THREE_BYTE_ESCAPED.toChar()}]"

        private val CUSTOM_STRINGS = arrayOf("[x]", "[abcde]", "[12345]", "[xxyyzz4321]", "[zzyyxx1234]")

        private val STRING_AS_CHARS_OPTIONS = booleanArrayOf(true, false)

    }

}