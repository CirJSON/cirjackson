package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.util.ByteArrayBuilder
import kotlin.math.max
import kotlin.math.min

/**
 * Helper class used for efficient encoding of CirJSON String values (including CirJSON property names) into Strings or
 * UTF-8 byte arrays.
 *
 * Note that methods in here are somewhat optimized, but not ridiculously so. Reason is that conversion method results
 * are expected to be cached so that these methods will not be hot spots during normal operation.
 */
object CirJsonStringEncoder {

    private val HEX_CHARS = CharTypes.copyHexChars(true)

    private val HEX_BYTES = CharTypes.copyHexBytes(true)

    private const val MIN_CHAR_BUFFER_SIZE = 16

    private const val MIN_BYTE_BUFFER_SIZE = 24

    private const val MAX_BUFFER_SIZE = 32000

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    /**
     * Method that will escape text contents using CirJSON standard escaping, and return results as a character array.
     *
     * @param input Value String to process
     *
     * @return CirJSON-escaped String matching `input`
     */
    fun quoteAsCharArray(input: CharSequence): CharArray {
        TODO()
    }

    /**
     * Method that will quote text contents using CirJSON standard quoting, and append results to a supplied
     * [StringBuilder]. Use this variant if you have e.g. a [StringBuilder] and want to avoid superfluous copying of it.
     *
     * @param input Value [CharSequence] to process
     *
     * @param output [StringBuilder] to append escaped contents to
     */
    fun quoteAsString(input: CharSequence, output: StringBuilder) {
        TODO()
    }

    /**
     * Method that will escape text contents using CirJSON standard escaping, encode resulting String as UTF-8 bytes and
     * return results as a  byte array.
     *
     * @param text Value String to process
     *
     * @return UTF-8 encoded bytes of CirJSON-escaped `text`
     */
    fun quoteAsUTF8(text: CharSequence): ByteArray {
        TODO()
    }

    /**
     * Will encode given String as UTF-8 (without any escaping) and return the resulting byte array.
     *
     * @param text Value String to process
     *
     * @return UTF-8 encoded bytes of `text` (without any escaping)
     */
    fun encodeAsUTF8(text: CharSequence): ByteArray {
        TODO()
    }

    /*
     *******************************************************************************************************************
     * Internal methods
     *******************************************************************************************************************
     */

    private fun qBuffer(): CharArray {
        val buf = CharArray(6)
        buf[0] = '\\'
        buf[1] = '0'
        buf[2] = '0'
        return buf
    }

    private fun appendNumeric(value: Int, qBuffer: CharArray): Int {
        qBuffer[1] = 'u'
        qBuffer[4] = HEX_CHARS[value shr 4]
        qBuffer[5] = HEX_CHARS[value and 0xF]
        return 6
    }

    private fun appendNamed(esc: Int, qBuffer: CharArray): Int {
        qBuffer[1] = esc.toChar()
        return 2
    }

    private fun appendByte(ch: Int, esc: Int, byteBuilder: ByteArrayBuilder, pointer: Int): Int {
        var char = ch

        byteBuilder.currentSegmentLength = pointer
        byteBuilder.append('\\'.code)

        if (esc < 0) {
            byteBuilder.append('u'.code)

            if (char > 0xFF) {
                val hi = char shr 8
                byteBuilder.append(HEX_BYTES[hi shr 4].toInt())
                byteBuilder.append(HEX_BYTES[hi and 0xF].toInt())
                char = char and 0xFF
            } else {
                byteBuilder.append('0'.code)
                byteBuilder.append('0'.code)
            }

            byteBuilder.append(HEX_BYTES[char shr 4].toInt())
            byteBuilder.append(HEX_BYTES[char and 0xF].toInt())
        } else {
            byteBuilder.append(esc.toByte().toInt())
        }

        return byteBuilder.currentSegmentLength
    }

    private fun convert(p1: Int, p2: Int): Int {
        if (p2 !in UTF8Writer.SURR2_FIRST..UTF8Writer.SURR2_LAST) {
            throw IllegalArgumentException("Broken surrogate pair: first char 0x${p1.toString(16)}, second 0x${
                p2.toString(16)
            }; illegal combination")
        }

        return (p1 shl 10) + p2 + UTF8Writer.SURROGATE_BASE
    }

    private fun illegal(code: Int) {
        throw IllegalArgumentException(UTF8Writer.illegalSurrogateDesc(code))
    }

    internal fun initialCharBufferSize(stringLength: Int): Int {
        val estimated = max(MIN_CHAR_BUFFER_SIZE, stringLength + min(6 + (stringLength shr 3), 1000))
        return min(estimated, MAX_BUFFER_SIZE)
    }

    internal fun initialByteBufferSize(stringLength: Int): Int {
        val doubled = max(MIN_BYTE_BUFFER_SIZE, stringLength + 6 + (stringLength shr 1))
        return min(doubled, MAX_BUFFER_SIZE)
    }

}