package org.cirjson.cirjackson.core.io

import java.io.CharConversionException
import java.io.IOException
import java.io.InputStream
import java.io.Reader

/**
 * Simple UTF-32/UCS-4 decoder.
 *
 * @property myIsAutoClosed Whether underlying [InputStream] (if any) should be closed when this [Reader] is closed or
 * not.
 */
open class UTF32Reader(protected val myIOContext: IOContext?, protected var myInput: InputStream?,
        private val myIsAutoClosed: Boolean, protected var myBuffer: ByteArray?, protected var myPointer: Int,
        protected var myLength: Int, protected val myIsBigEndian: Boolean) : Reader() {

    /**
     * Although input is fine with full Unicode set, Kotlin still uses 16-bit chars, so we may have to split high-order
     * chars into surrogate pairs.
     */
    protected var mySurrogate = NO_CHAR

    /**
     * Total read character count; used for error reporting purposes
     */
    protected var myCharCount = 0

    /**
     * Total read byte count; used for error reporting purposes
     */
    protected var myByteCount = 0

    protected val myIsManagedBuffers = myInput != null

    protected var myTempBuffer: CharArray? = null

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    @Throws(IOException::class)
    override fun close() {
        val input = myInput ?: return
        myInput = null

        if (myIsAutoClosed) {
            input.close()
        }

        freeBuffers()
    }

    /**
     * Although this method is implemented by the base class, AND it should never be called by main code, let's still
     * implement it a bit more efficiently just in case.
     */
    @Throws(IOException::class)
    override fun read(): Int {
        if (myTempBuffer == null) {
            myTempBuffer = CharArray(1)
        }

        if (read(myTempBuffer!!, 0, 1) < 1) {
            return -1
        }

        return myTempBuffer!![0].code
    }

    @Throws(IOException::class)
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        val buffer = myBuffer ?: return -1

        if (len < 1) {
            return len
        }

        if (off < 0 || off + len > cbuf.size) {
            reportBounds(cbuf, off, len)
        }

        var outputPointer = off
        val outputEnd = len + off

        if (mySurrogate != NO_CHAR) {
            cbuf[outputPointer++] = mySurrogate
            mySurrogate = NO_CHAR
        } else {
            val left = myLength - myPointer

            if (left < 4) {
                if (!loadMore(left)) {
                    if (left == 0) {
                        return -1
                    }

                    reportUnexpectedEOF(myLength + myPointer, 4)
                }
            }
        }

        val lastValidInputStart = myLength - 4

        while (outputPointer < outputEnd && myPointer <= lastValidInputStart) {
            val pointer = myPointer

            var (high, low) = if (myIsBigEndian) {
                buffer[pointer].toInt() shl 8 or (buffer[pointer + 1].toInt() and 0xFF) to
                        (buffer[pointer + 2].toInt() and 0xFF shl 8 or (buffer[pointer + 3].toInt() and 0xFF))
            } else {
                buffer[pointer].toInt() and 0xFF or (buffer[pointer + 1].toInt() and 0xFF shl 8) to
                        (buffer[pointer + 2].toInt() and 0xFF or (buffer[pointer + 3].toInt() shl 8))
            }

            myPointer += 4

            if (high != 0) {
                high = high and 0xFFFF
                val ch = high - 1 shl 16 or low

                if (high > 0x10) {
                    reportInvalid(ch, outputPointer - off,
                            "(above 0x${LAST_VALID_UNICODE_CHAR.toString(16).uppercase().padStart(8, '0')})")
                }

                cbuf[outputPointer++] = ((ch shr 10) + 0xD800).toChar()

                low = ch and 0x03FF or 0xDC00

                if (outputPointer >= outputEnd) {
                    mySurrogate = ch.toChar()
                    break
                }
            }

            cbuf[outputPointer++] = low.toChar()
        }

        val actualLength = outputEnd - off
        myCharCount += actualLength
        return actualLength
    }

    /*
     *******************************************************************************************************************
     * Internal methods
     *******************************************************************************************************************
     */

    /**
     * @param available Number of "unused" bytes in the input buffer
     *
     * @return `true`, if enough bytes were read to allow decoding of at least one full character; `false` if EOF was
     * encountered instead.
     */
    @Throws(IOException::class)
    private fun loadMore(available: Int): Boolean {
        val input = myInput ?: return false
        val buffer = myBuffer ?: return false

        myByteCount += myLength - available

        if (available > 0) {
            if (myPointer > 0) {
                buffer.copyInto(buffer, 0, myPointer, myPointer + available)
                myPointer = 0
            }

            myLength = available
        } else {
            myPointer = 0
            val count = input.read(buffer)

            if (count < 1) {
                myLength = 0

                if (count < 0) {
                    if (myIsManagedBuffers) {
                        freeBuffers()
                    }

                    return false
                }

                reportStrangeStream()
            }

            myLength = count
        }

        while (myLength < 4) {
            val count = input.read(buffer, myLength, buffer.size - myLength)

            if (count < 1) {
                if (count < 0) {
                    if (myIsManagedBuffers) {
                        freeBuffers()
                    }

                    reportUnexpectedEOF(myLength, 4)
                }

                reportStrangeStream()
            }

            myLength += count
        }

        return true
    }

    /**
     * This method should be called along with (or instead of) normal close. After calling this method, no further reads
     * should be tried. Method will try to recycle read buffers (if any).
     */
    private fun freeBuffers() {
        val buffer = myBuffer

        if (buffer != null) {
            myBuffer = null
            myIOContext?.releaseReadIOBuffer(buffer)
        }
    }

    /*
     *******************************************************************************************************************
     * Internal methods, problem reporting and exception handling
     *******************************************************************************************************************
     */

    @Throws(IOException::class)
    @Suppress("SameParameterValue")
    private fun reportUnexpectedEOF(gotBytes: Int, needed: Int) {
        val bytePosition = myByteCount + gotBytes
        val charPosition = myCharCount

        throw CharConversionException("Unexpected EOF in the middle of a 4-byte UTF-32 char: got $gotBytes, needed " +
                "$needed, at char #$charPosition, byte #$bytePosition)")
    }

    @Throws(IOException::class)
    private fun reportInvalid(value: Int, offset: Int, message: String) {
        val bytePosition = myByteCount + myPointer - 1
        val charPosition = myCharCount + offset

        throw CharConversionException("Invalid UTF-32 character 0x${value.toString(16)} $message at char " +
                "#$charPosition, byte #$bytePosition)")
    }

    private fun reportBounds(charBuffer: CharArray, start: Int, length: Int) {
        throw IndexOutOfBoundsException("read(buf,$start,$length), charBuffer[${charBuffer.size}]")
    }

    @Throws(IOException::class)
    private fun reportStrangeStream() {
        throw IOException("Strange I/O stream, returned 0 bytes on read")
    }

    companion object {

        /**
         * CirJSON actually limits available Unicode range in the high end to the same as xml (to basically limit UTF-8
         * max byte sequence length to 4)
         */
        const val LAST_VALID_UNICODE_CHAR = 0x10FFFF

        val NO_CHAR = '\u0000'

    }

}