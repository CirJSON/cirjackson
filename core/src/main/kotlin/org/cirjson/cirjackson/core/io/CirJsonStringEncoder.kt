package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.util.ByteArrayBuilder
import org.cirjson.cirjackson.core.util.TextBuffer
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
        val inputLength = input.length
        var outputBuffer = CharArray(initialCharBufferSize(inputLength))
        val escapeCodes = CharTypes.sevenBitOutputEscapes
        val escapeCodeCount = escapeCodes.size
        var inputPointer = 0
        val textBufferDelegate = lazy { TextBuffer.fromInitial(outputBuffer) }
        val textBuffer by textBufferDelegate
        var outputPointer = 0
        val qBuffer by lazy { qBuffer() }

        outer@ while (inputPointer < inputLength) {
            while (true) {
                val c = input[inputPointer]

                if (c.code < escapeCodeCount && escapeCodes[c.code] != 0) {
                    break
                }

                if (outputPointer >= outputBuffer.size) {
                    outputBuffer = textBuffer.finishCurrentSegment()
                    outputPointer = 0
                }

                outputBuffer[outputPointer++] = c

                if (++inputPointer >= inputLength) {
                    break@outer
                }
            }

            val d = input[inputPointer++]
            val escapeCode = escapeCodes[d.code]
            val length = if (escapeCode < 0) appendNumeric(d.code, qBuffer) else appendNamed(escapeCode, qBuffer)

            if (outputPointer + length > outputBuffer.size) {
                val first = outputBuffer.size - outputPointer

                if (first > 0) {
                    qBuffer.copyInto(outputBuffer, outputPointer, 0, first)
                }

                outputBuffer = textBuffer.finishCurrentSegment()
                val second = length - first
                qBuffer.copyInto(outputBuffer, 0, first, second + first)
                outputPointer = second
            } else {
                qBuffer.copyInto(outputBuffer, outputPointer, 0, length)
                outputPointer += length
            }
        }

        if (!textBufferDelegate.isInitialized()) {
            return outputBuffer.copyOfRange(0, outputPointer)
        }

        textBuffer.currentSegmentSize = outputPointer
        return textBuffer.finishCurrentSegment()
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
        val escapeCodes = CharTypes.sevenBitOutputEscapes
        val escapeCodeCount = escapeCodes.size
        var inputPointer = 0
        val inputLength = input.length
        val qBuffer by lazy { qBuffer() }

        outer@ while (inputPointer < inputLength) {
            while (true) {
                val c = input[inputPointer]

                if (c.code < escapeCodeCount && escapeCodes[c.code] != 0) {
                    break
                }

                output.append(c)

                if (++inputPointer >= inputLength) {
                    break@outer
                }
            }

            val d = input[inputPointer++]
            val escapeCode = escapeCodes[d.code]
            val length = if (escapeCode < 0) appendNumeric(d.code, qBuffer) else appendNamed(escapeCode, qBuffer)
            output.appendRange(qBuffer, 0, length)
        }
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
        var inputPointer = 0
        val inputEnd = text.length
        var outputPointer = 0
        var outputBuffer = ByteArray(initialByteBufferSize(inputEnd))
        val byteBuilderDelegate = lazy { ByteArrayBuilder.fromInitial(outputBuffer, outputPointer) }
        val byteBuilder by byteBuilderDelegate

        main@ while (inputPointer < inputEnd) {
            val escapeCodes = CharTypes.sevenBitOutputEscapes

            while (true) {
                val ch = text[inputPointer]

                if (ch.code > 0x7F || escapeCodes[ch.code] != 0) {
                    break
                }

                if (outputPointer >= outputBuffer.size) {
                    outputBuffer = byteBuilder.finishCurrentSegment()
                    outputPointer = 0
                }

                outputBuffer[outputPointer++] = ch.code.toByte()

                if (++inputPointer >= inputEnd) {
                    break@main
                }
            }

            if (outputPointer >= outputBuffer.size) {
                outputBuffer = byteBuilder.finishCurrentSegment()
                outputPointer = 0
            }

            var ch = text[inputPointer++].code

            if (ch <= 0x7F) {
                val escapeCode = escapeCodes[ch]
                outputPointer = appendByte(ch, escapeCode, byteBuilder, outputPointer)
                outputBuffer = byteBuilder.currentSegment!!
                continue
            }

            if (ch <= 0x7FF) {
                outputBuffer[outputPointer++] = (0xC0 or (ch shr 6)).toByte()
            } else {
                if (ch !in UTF8Writer.SURR1_FIRST..UTF8Writer.SURR2_FIRST) {
                    outputBuffer[outputPointer++] = (0xE0 or (ch shr 12)).toByte()
                } else {
                    if (ch > UTF8Writer.SURR1_LAST) {
                        illegal(ch)
                    }

                    if (inputPointer >= inputEnd) {
                        illegal(ch)
                    }

                    ch = convert(ch, text[inputPointer++].code)

                    if (ch > 0x10FFFF) {
                        illegal(ch)
                    }

                    outputBuffer[outputPointer++] = (0xF0 or (ch shr 18)).toByte()

                    if (outputPointer >= outputBuffer.size) {
                        outputBuffer = byteBuilder.finishCurrentSegment()
                        outputPointer = 0
                    }

                    outputBuffer[outputPointer++] = (0x80 or ((ch shr 12) and 0x3F)).toByte()
                }

                if (outputPointer >= outputBuffer.size) {
                    outputBuffer = byteBuilder.finishCurrentSegment()
                    outputPointer = 0
                }

                outputBuffer[outputPointer++] = (0x80 or ((ch shr 6) and 0x3F)).toByte()
            }

            ch = 0x80 or (ch and 0x3F)

            if (outputPointer >= outputBuffer.size) {
                outputBuffer = byteBuilder.finishCurrentSegment()
                outputPointer = 0
            }

            outputBuffer[outputPointer++] = ch.toByte()
        }

        return if (!byteBuilderDelegate.isInitialized()) {
            outputBuffer.copyOfRange(0, outputPointer)
        } else {
            byteBuilder.completeAndCoalesce(outputPointer)
        }
    }

    /**
     * Will encode given String as UTF-8 (without any escaping) and return the resulting byte array.
     *
     * @param text Value String to process
     *
     * @return UTF-8 encoded bytes of `text` (without any escaping)
     */
    fun encodeAsUTF8(text: CharSequence): ByteArray {
        var inputPointer = 0
        val inputEnd = text.length
        var outputPointer = 0
        var outputBuffer = ByteArray(initialByteBufferSize(inputEnd))
        var outputEnd = outputBuffer.size
        val byteBuilderDelegate = lazy { ByteArrayBuilder.fromInitial(outputBuffer, outputPointer) }
        val byteBuilder by byteBuilderDelegate

        main@ while (inputPointer < inputEnd) {
            var c = text[inputPointer++].code

            while (c <= 0x7F) {
                if (outputPointer >= outputEnd) {
                    outputBuffer = byteBuilder.finishCurrentSegment()
                    outputEnd = outputBuffer.size
                    outputPointer = 0
                }

                outputBuffer[outputPointer++] = c.toByte()

                if (inputPointer >= inputEnd) {
                    break@main
                }

                c = text[inputPointer++].code
            }

            if (outputPointer >= outputEnd) {
                outputBuffer = byteBuilder.finishCurrentSegment()
                outputEnd = outputBuffer.size
                outputPointer = 0
            }

            if (c < 0x800) {
                outputBuffer[outputPointer++] = (0xC0 or (c shr 6)).toByte()
            } else {
                if (c !in UTF8Writer.SURR1_FIRST..UTF8Writer.SURR2_FIRST) {
                    outputBuffer[outputPointer++] = (0xE0 or (c shr 12)).toByte()
                } else {
                    if (c > UTF8Writer.SURR1_LAST) {
                        illegal(c)
                    }

                    if (inputPointer >= inputEnd) {
                        illegal(c)
                    }

                    c = convert(c, text[inputPointer++].code)

                    if (c > 0x10FFFF) {
                        illegal(c)
                    }

                    outputBuffer[outputPointer++] = (0xF0 or (c shr 18)).toByte()

                    if (outputPointer >= outputBuffer.size) {
                        outputBuffer = byteBuilder.finishCurrentSegment()
                        outputEnd = outputBuffer.size
                        outputPointer = 0
                    }

                    outputBuffer[outputPointer++] = (0x80 or ((c shr 12) and 0x3F)).toByte()
                }

                if (outputPointer >= outputBuffer.size) {
                    outputBuffer = byteBuilder.finishCurrentSegment()
                    outputEnd = outputBuffer.size
                    outputPointer = 0
                }

                outputBuffer[outputPointer++] = (0x80 or ((c shr 6) and 0x3F)).toByte()
            }

            if (outputPointer >= outputBuffer.size) {
                outputBuffer = byteBuilder.finishCurrentSegment()
                outputEnd = outputBuffer.size
                outputPointer = 0
            }

            outputBuffer[outputPointer++] = (0x80 or (c and 0x3F)).toByte()
        }

        return if (!byteBuilderDelegate.isInitialized()) {
            outputBuffer.copyOfRange(0, outputPointer)
        } else {
            byteBuilder.completeAndCoalesce(outputPointer)
        }
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