package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonEncoding
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.util.Other
import java.io.DataInput
import java.io.IOException
import java.io.InputStream
import java.io.Reader

/**
 * This class is used to determine the encoding of byte stream that is to contain CirJSON content. Rules are fairly
 * simple, and defined in CirJSON specification, except for BOM handling, which is a property of underlying streams.
 */
class ByteSourceCirJsonBootstrapper {

    private val myContext: IOContext

    private val myInput: InputStream?

    private val myInputBuffer: ByteArray

    private var myInputPointer = 0

    private var myInputEnd = 0

    /**
     * Flag that indicates whether buffer is to be recycled after being used or not.
     */
    private val myIsBufferRecyclable: Boolean

    /**
     * Whether input has been detected to be in Big-Endian encoding or not.
     */
    private var myIsBigEndian = true

    private var myBytesPerChar = 0

    constructor(ioContext: IOContext, input: InputStream) {
        myContext = ioContext
        myInput = input
        myInputBuffer = ioContext.allocateReadIOBuffer()
        myIsBufferRecyclable = true
    }

    constructor(ioContext: IOContext, inputBuffer: ByteArray, inputStart: Int, inputLength: Int) {
        myContext = ioContext
        myInput = null
        myInputBuffer = inputBuffer
        myInputPointer = inputStart
        myInputEnd = inputLength + inputStart
        myIsBufferRecyclable = false
    }

    /*
     *******************************************************************************************************************
     * Encoding detection during bootstrapping
     *******************************************************************************************************************
     */

    /**
     * Method that should be called after constructing an instance. It will figure out encoding that content uses, to
     * allow for instantiating a proper scanner object.
     *
     * @return [CirJsonEncoding] detected, if any; [CirJsonEncoding.UTF8] otherwise
     *
     * @throws CirJacksonException If read from underlying input source fails
     */
    @Throws(CirJacksonException::class)
    fun detectEncoding(): CirJsonEncoding {
        var foundEncoding = false

        if (ensureLoaded(4)) {
            val quad = myInputBuffer[myInputPointer].toInt() shl 24 or
                    (myInputBuffer[myInputPointer + 1].toInt() and 0xFF shl 16) or
                    (myInputBuffer[myInputPointer + 2].toInt() and 0xFF shl 8) or
                    (myInputBuffer[myInputPointer + 3].toInt() and 0xFF)

            if (handleBOM(quad)) {
                foundEncoding = true
            } else {
                if (checkUTF32(quad)) {
                    foundEncoding = true
                } else if (checkUTF16(quad ushr 16)) {
                    foundEncoding = true
                }
            }
        } else if (ensureLoaded(2)) {
            val i16 = myInputBuffer[myInputPointer].toInt() and 0xFF shl 8 or
                    (myInputBuffer[myInputPointer + 1].toInt() and 0xFF)

            if (checkUTF16(i16)) {
                foundEncoding = true
            }
        }

        val encoding = if (foundEncoding) {
            when (myBytesPerChar) {
                1 -> CirJsonEncoding.UTF8
                2 -> if (myIsBigEndian) CirJsonEncoding.UTF16_BE else CirJsonEncoding.UTF16_LE
                4 -> if (myIsBigEndian) CirJsonEncoding.UTF32_BE else CirJsonEncoding.UTF32_LE
                else -> Other.throwInternalReturnAny()
            }
        } else {
            CirJsonEncoding.UTF8
        }

        myContext.encoding = encoding
        return encoding
    }

    /*
     *******************************************************************************************************************
     * Constructing a Reader
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    fun constructReader(): Reader {
        val encoding = myContext.encoding!!

        TODO()
    }

    /*
     *******************************************************************************************************************
     * Internal methods, parsing
     *******************************************************************************************************************
     */

    /**
     * Method that verifies if a BOM was successfully found, and encoding thereby recognized.
     *
     * @return `true` if a BOM was found, `false` otherwise
     *
     * @throws CirJacksonException If a weird UCS-4 was found
     */
    @Throws(CirJacksonException::class)
    private fun handleBOM(quad: Int): Boolean {
        when (quad) {
            0x0000FEFF -> {
                myIsBigEndian = true
                myInputPointer += 4
                myBytesPerChar = 4
                return true
            }

            0xFFFE0000.toInt() -> {
                myIsBigEndian = false
                myInputPointer += 4
                myBytesPerChar = 4
                return true
            }

            0x0000FFFE -> {
                return reportWeirdUCS4("2143")
            }

            0xFEFF0000.toInt() -> {
                return reportWeirdUCS4("3412")
            }
        }

        val msw = quad ushr 16

        return when {
            msw == 0xFEFF -> {
                myIsBigEndian = true
                myInputPointer += 2
                myBytesPerChar = 2
                true
            }

            msw == 0xFFFE -> {
                myIsBigEndian = false
                myInputPointer += 2
                myBytesPerChar = 2
                true
            }

            quad ushr 8 == 0xEFBBBF -> {
                myIsBigEndian = true
                myInputPointer += 3
                myBytesPerChar = 1
                true
            }

            else -> false
        }
    }

    @Throws(CirJacksonException::class)
    private fun checkUTF32(quad: Int): Boolean {
        return when {
            quad shr 8 == 0 -> {
                myIsBigEndian = true
                myBytesPerChar = 4
                true
            }

            quad and 0x00FFFFFF == 0 -> {
                myIsBigEndian = false
                myBytesPerChar = 4
                true
            }

            quad and 0x00FF0000.inv() == 0 -> {
                reportWeirdUCS4("3412")
            }

            quad and 0x0000FF00.inv() == 0 -> {
                reportWeirdUCS4("2143")
            }

            else -> false
        }
    }

    private fun checkUTF16(i16: Int): Boolean {
        return if (i16 and 0xFF00 == 0) {
            myIsBigEndian = true
            myBytesPerChar = 2
            true
        } else if (i16 and 0x00FF == 0) {
            myIsBigEndian = false
            myBytesPerChar = 2
            true
        } else {
            false
        }
    }

    /*
     *******************************************************************************************************************
     * Internal methods, raw input access
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun ensureLoaded(minimum: Int): Boolean {
        var gotten = myInputEnd - myInputPointer

        while (gotten < minimum) {
            val count = try {
                myInput?.read(myInputBuffer, myInputEnd, myInputBuffer.size - myInputEnd) ?: -1
            } catch (e: IOException) {
                throw wrapIOFailure(e)
            }

            if (count < 1) {
                return false
            }

            myInputEnd += count
            gotten += count
        }

        return true
    }

    /*
     *******************************************************************************************************************
     * Internal methods, problem reporting and exception handling
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun <T> reportWeirdUCS4(type: String): T {
        throw createIOFailure("Unsupported UCS-4 endianness ($type) detected")
    }

    @Throws(CirJacksonException::class)
    private fun createIOFailure(message: String): CirJacksonException {
        return wrapIOFailure(IOException(message))
    }

    companion object {

        /**
         * Limit in bytes for input byte array length to use StringReader instead of InputStreamReader
         */
        const val STRING_READER_BYTE_ARRAY_LENGTH_LIMIT = 8192

        /**
         * Helper method that may be called to see if given [DataInput] has BOM marker, and if so, to skip it.
         *
         * @param input DataInput to read content from
         *
         * @return Byte (as unsigned `Int`) read after possible UTF-8 BOM
         *
         * @throws CirJacksonException If read from underlying input source fails
         */
        @Throws(CirJacksonException::class)
        fun skipUTF8BOM(input: DataInput): Int {
            try {
                var b = input.readUnsignedByte()

                if (b != 0xEF) {
                    return b
                }

                b = input.readUnsignedByte()

                if (b != 0xBB) {
                    throw IOException(
                            "Unexpected byte 0x${b.toString(16)} following 0xEF; should get 0xBB as part of UTF-8 BOM")
                }

                b = input.readUnsignedByte()

                if (b != 0xBF) {
                    throw IOException("Unexpected byte 0x${
                        b.toString(16)
                    } following 0xEF 0xBB; should get 0xBF as part of UTF-8 BOM")
                }

                return input.readUnsignedByte()
            } catch (e: IOException) {
                throw wrapIOFailure(e)
            }
        }

        @Throws(CirJacksonException::class)
        private fun wrapIOFailure(e: IOException): CirJacksonException {
            return CirJacksonIOException.construct(e, null)
        }

    }

}