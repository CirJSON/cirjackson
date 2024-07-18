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

    @Throws(IOException::class)
    override fun read(): Int {
        TODO()
    }

    @Throws(IOException::class)
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        TODO("Not yet implemented")
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

        throw CharConversionException(
                "Invalid UTF-32 character 0x${value.toString(16)}$message at char #$charPosition, byte #$bytePosition)")
    }

    private fun reportBounds(charBuffer: CharArray, start: Int, length: Int) {
        throw IndexOutOfBoundsException("read(buf,$start,$length), charBuffer[${charBuffer.size}]")
    }

    @Throws(IOException::class)
    private fun reportStrangeStream() {
        throw IOException("Strange I/O stream, returned 0 bytes on read")
    }

    companion object {

        val NO_CHAR = '\u0000'

    }

}