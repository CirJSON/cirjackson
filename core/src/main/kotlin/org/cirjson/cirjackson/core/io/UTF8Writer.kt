package org.cirjson.cirjackson.core.io

import java.io.IOException
import java.io.OutputStream
import java.io.Writer

class UTF8Writer(private val myContext: IOContext, private var myOutput: OutputStream?) : Writer() {

    private var myOutputBuffer: ByteArray? = myContext.allocateWriteEncodingBuffer()

    private val myOutputBufferEnd = myOutputBuffer!!.size - 4

    private var myOutputPointer = 0

    private var mySurrogate = 0

    @Throws(IOException::class)
    override fun close() {
        if (myOutput != null) {
            if (myOutputPointer > 0) {
                myOutput!!.write(myOutputBuffer!!, 0, myOutputPointer)
            }

            val output = myOutput!!
            myOutput = null

            val buffer = myOutputBuffer

            if (buffer != null) {
                myOutputBuffer = null
                myContext.releaseWriteEncodingBuffer(buffer)
            }

            output.close()

            val code = mySurrogate
            mySurrogate = 0

            if (code > 0) {
                illegalSurrogate(code)
            }
        }

        myContext.close()
    }

    @Throws(IOException::class)
    override fun flush() {
        if (myOutput == null) {
            return
        }

        if (myOutputPointer > 0) {
            myOutput!!.write(myOutputBuffer!!, 0, myOutputPointer)
            myOutputPointer = 0
        }

        myOutput!!.flush()
    }

    @Throws(IOException::class)
    @Suppress("DuplicatedCode")
    override fun write(cbuf: CharArray, off: Int, len: Int) {
        var length = len
        var offset = off

        if (length < 2) {
            if (len == 1) {
                write(cbuf[offset].code)
            }

            return
        }

        if (mySurrogate > 0) {
            val second = cbuf[offset++]
            length--
            write(convertSurrogate(second.code))
        }

        var outputPointer = myOutputPointer
        val outputBuffer = myOutputBuffer!!
        val outputBufferLast = myOutputBufferEnd

        length += offset

        outputLoop@ while (offset < length) {
            if (outputPointer >= outputBufferLast) {
                myOutput!!.write(outputBuffer, 0, outputPointer)
                outputPointer = 0
            }

            var c = cbuf[offset++].code

            if (c < 0x80) {
                outputBuffer[outputPointer++] = c.toByte()
                var maxInCount = length - offset
                val maxOutCount = outputBufferLast - outputPointer

                if (maxInCount > maxOutCount) {
                    maxInCount = maxOutCount
                }

                maxInCount += offset

                while (true) {
                    if (offset >= maxInCount) {
                        continue@outputLoop
                    }

                    c = cbuf[offset++].code

                    if (c >= 0x80) {
                        break
                    }

                    outputBuffer[outputPointer++] = c.toByte()
                }
            }

            if (c < 0x800) {
                outputBuffer[outputPointer++] = (0xC0 or (c shr 6)).toByte()
            } else {
                if (c !in SURR1_FIRST..SURR2_LAST) {
                    outputBuffer[outputPointer++] = (0xE0 or (c shr 12)).toByte()
                    outputBuffer[outputPointer++] = (0x80 or ((c shr 6) and 0x3F)).toByte()
                    outputBuffer[outputPointer++] = (0x80 or (c and 0x3F)).toByte()
                    continue
                }

                if (c > SURR1_LAST) {
                    myOutputPointer = outputPointer
                    illegalSurrogate(c)
                }

                mySurrogate = c

                if (offset >= length) {
                    break
                }

                c = convertSurrogate(cbuf[offset++].code)

                if (c > 0x10FFFF) {
                    myOutputPointer = outputPointer
                    illegalSurrogate(c)
                }

                outputBuffer[outputPointer++] = (0xF0 or (c shr 18)).toByte()
                outputBuffer[outputPointer++] = (0x80 or ((c shr 12) and 0x3F)).toByte()
                outputBuffer[outputPointer++] = (0x80 or ((c shr 6) and 0x3F)).toByte()
            }

            outputBuffer[outputPointer++] = (0x80 or (c and 0x3F)).toByte()
        }

        myOutputPointer = outputPointer
    }

    @Throws(IOException::class)
    override fun write(c: Int) {
        var code = c

        if (mySurrogate > 0) {
            code = convertSurrogate(code)
        } else if (code in SURR1_FIRST..SURR2_LAST) {
            if (code >= SURR1_LAST) {
                illegalSurrogate(code)
            }

            mySurrogate = code
            return
        }

        if (myOutputPointer >= myOutputBufferEnd) {
            myOutput!!.write(myOutputBuffer!!, 0, myOutputPointer)
            myOutputPointer = 0
        }

        val outputBuffer = myOutputBuffer!!

        if (code < 0x80) {
            outputBuffer[myOutputPointer++] = code.toByte()
            return
        }

        var pointer = myOutputPointer

        if (code < 0x800) {
            outputBuffer[pointer++] = (0xC0 or (code shr 6)).toByte()
        } else if (code <= 0xFFFF) {
            outputBuffer[pointer++] = (0xE0 or (code shr 12)).toByte()
            outputBuffer[pointer++] = (0x80 or ((code shr 6) and 0x3F)).toByte()
        } else {
            if (code > 0x10FFFF) {
                illegalSurrogate(code)
            }

            outputBuffer[pointer++] = (0xF0 or (code shr 18)).toByte()
            outputBuffer[pointer++] = (0x80 or ((code shr 12) and 0x3F)).toByte()
            outputBuffer[pointer++] = (0x80 or ((code shr 6) and 0x3F)).toByte()
        }

        outputBuffer[pointer++] = (0x80 or (code and 0x3F)).toByte()
        myOutputPointer = pointer
    }

    @Throws(IOException::class)
    override fun write(cbuf: CharArray) {
        write(cbuf, 0, cbuf.size)
    }

    @Throws(IOException::class)
    override fun write(str: String) {
        write(str, 0, str.length)
    }

    @Throws(IOException::class)
    @Suppress("DuplicatedCode")
    override fun write(str: String, off: Int, len: Int) {
        var length = len
        var offset = off

        if (length < 2) {
            if (len == 1) {
                write(str[offset].code)
            }

            return
        }

        if (mySurrogate > 0) {
            val second = str[offset++]
            length--
            write(convertSurrogate(second.code))
        }

        var outputPointer = myOutputPointer
        val outputBuffer = myOutputBuffer!!
        val outputBufferLast = myOutputBufferEnd

        length += offset

        outputLoop@ while (offset < length) {
            if (outputPointer >= outputBufferLast) {
                myOutput!!.write(outputBuffer, 0, outputPointer)
                outputPointer = 0
            }

            var c = str[offset++].code

            if (c < 0x80) {
                outputBuffer[outputPointer++] = c.toByte()
                var maxInCount = length - offset
                val maxOutCount = outputBufferLast - outputPointer

                if (maxInCount > maxOutCount) {
                    maxInCount = maxOutCount
                }

                maxInCount += offset

                while (true) {
                    if (offset >= maxInCount) {
                        continue@outputLoop
                    }

                    c = str[offset++].code

                    if (c >= 0x80) {
                        break
                    }

                    outputBuffer[outputPointer++] = c.toByte()
                }
            }

            if (c < 0x800) {
                outputBuffer[outputPointer++] = (0xC0 or (c shr 6)).toByte()
            } else {
                if (c !in SURR1_FIRST..SURR2_LAST) {
                    outputBuffer[outputPointer++] = (0xE0 or (c shr 12)).toByte()
                    outputBuffer[outputPointer++] = (0x80 or ((c shr 6) and 0x3F)).toByte()
                    outputBuffer[outputPointer++] = (0x80 or (c and 0x3F)).toByte()
                    continue
                }

                if (c > SURR1_LAST) {
                    myOutputPointer = outputPointer
                    illegalSurrogate(c)
                }

                mySurrogate = c

                if (offset >= length) {
                    break
                }

                c = convertSurrogate(str[offset++].code)

                if (c > 0x10FFFF) {
                    myOutputPointer = outputPointer
                    illegalSurrogate(c)
                }

                outputBuffer[outputPointer++] = (0xF0 or (c shr 18)).toByte()
                outputBuffer[outputPointer++] = (0x80 or ((c shr 12) and 0x3F)).toByte()
                outputBuffer[outputPointer++] = (0x80 or ((c shr 6) and 0x3F)).toByte()
            }

            outputBuffer[outputPointer++] = (0x80 or (c and 0x3F)).toByte()
        }

        myOutputPointer = outputPointer
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

        const val SURR1_FIRST: Int = 0xD800

        const val SURR1_LAST: Int = 0xDBFF

        const val SURR2_FIRST: Int = 0xDC00

        const val SURR2_LAST: Int = 0xDFFF

        const val SURROGATE_BASE = 0x10000 - SURR2_FIRST - (SURR1_FIRST shl 10)

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