package org.cirjson.cirjackson.core.io

import java.io.IOException
import java.io.OutputStream
import java.io.Writer

class UTF8Writer(private val myContext: IOContext, private val myOutput: OutputStream) : Writer() {

    private var myOutputBuffer: ByteArray? = myContext.allocateWriteEncodingBuffer()

    private val myOutputBufferEnd = myOutputBuffer!!.size - 4

    private var myOutputPointer = 0

    private var mySurrogate = 0

    @Throws(IOException::class)
    override fun close() {
        TODO("Not yet implemented")
    }

    @Throws(IOException::class)
    override fun flush() {
        TODO("Not yet implemented")
    }

    @Throws(IOException::class)
    override fun write(cbuf: CharArray, off: Int, len: Int) {
        TODO("Not yet implemented")
    }

    @Throws(IOException::class)
    override fun write(c: Int) {
        TODO("Not yet implemented")
    }

    @Throws(IOException::class)
    override fun write(cbuf: CharArray) {
        TODO("Not yet implemented")
    }

    @Throws(IOException::class)
    override fun write(str: String) {
        TODO("Not yet implemented")
    }

    @Throws(IOException::class)
    override fun write(str: String, off: Int, len: Int) {
        TODO("Not yet implemented")
    }

    @Throws(IOException::class)
    override fun append(c: Char): Writer {
        write(c.code)
        return this
    }

    /*
     *******************************************************************************************************************
     * Internal methods
     *******************************************************************************************************************
     */

    /**
     * Method called to calculate Unicode code-point, from a surrogate pair.
     *
     * @param secondPart Second UTF-16 unit of surrogate (first part stored in `mySurrogate`)
     *
     * @return Decoded Unicode point
     *
     * @throws IOException If surrogate pair is invalid
     */
    @Throws(IOException::class)
    private fun convertSurrogate(secondPart: Int): Int {
        val firstPart = mySurrogate
        mySurrogate = 0

        if (secondPart !in SURR2_FIRST..SURR2_LAST) {
            throw IOException("Broken surrogate pair: first char 0x${firstPart.toString(16)}), second 0x${
                secondPart.toString(16)
            }); illegal combination")
        }

        return (firstPart shl 10) + secondPart + SURROGATE_BASE
    }

    companion object {

        internal const val SURR1_FIRST: Int = 0xD800

        internal const val SURR1_LAST: Int = 0xDBFF

        internal const val SURR2_FIRST: Int = 0xDC00

        internal const val SURR2_LAST: Int = 0xDFFF

        val SURROGATE_BASE = 0x10000 - SURR2_FIRST - (SURR1_FIRST shl 10)

        @Throws(IOException::class)
        private fun illegalSurrogate(code: Int) {
            throw IOException(illegalSurrogateDesc(code))
        }

        internal fun illegalSurrogateDesc(code: Int): String {
            return if (code > 0x10FFFF) {
                "Illegal character point (0x${code.toString(16)}) to output; max is 0x10FFFF as per CirJSON standards"
            } else if (code >= SURR1_FIRST) {
                if (code <= SURR1_LAST) {
                    "Unmatched first part of surrogate pair (0x${code.toString(16)})"
                } else {
                    "Unmatched second part of surrogate pair (0x${code.toString(16)})"
                }
            } else {
                "Illegal character point (0x${code.toString(16)}) to output"
            }
        }

    }

}