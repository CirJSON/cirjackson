package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.async.ByteArrayFeeder
import org.cirjson.cirjackson.core.async.NonBlockingInputFeeder
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.symbols.ByteQuadsCanonicalizer
import java.io.IOException
import java.io.OutputStream

/**
 * Non-blocking parser implementation for JSON content that takes its input via `ByteArray` passed.
 *
 * NOTE: only supports parsing of UTF-8 encoded content (and 7-bit US-ASCII since it is strict subset of UTF-8): other
 * encodings are not supported.
 */
open class NonBlockingByteArrayCirJsonParser(objectReadContext: ObjectReadContext, ioContext: IOContext,
        streamReadFeatures: Int, formatReadFeatures: Int, symbols: ByteQuadsCanonicalizer) :
        NonBlockingUtf8CirJsonParserBase(objectReadContext, ioContext, streamReadFeatures, formatReadFeatures, symbols),
        ByteArrayFeeder {

    private var myInputBuffer = NO_BYTES

    override fun nonBlockingInputFeeder(): NonBlockingInputFeeder {
        return this
    }

    override val objectId: Any?
        get() = null

    override val nextSignedByteFromBuffer: Byte
        get() = myInputBuffer[myInputPointer++]

    override val nextUnsignedByteFromBuffer: Int
        get() = myInputBuffer[myInputPointer++].toInt() and 0xFF


    override fun getByteFromBuffer(pointer: Int): Byte {
        return myInputBuffer[pointer]
    }

    override fun releaseBuffered(output: OutputStream): Int {
        val avail = myInputEnd - myInputPointer

        if (avail > 0) {
            try {
                output.write(myInputBuffer, myInputPointer, avail)
            } catch (e: IOException) {
                throw wrapIOFailure(e)
            }
        }

        return avail
    }

    override val typeId: Any?
        get() = null

    override fun feedInput(data: ByteArray, offset: Int, end: Int) {
        if (myInputPointer < myInputEnd) {
            return reportError("Still have ${myInputEnd - myInputPointer} undecoded bytes, should not call 'feedInput'")
        }

        if (end < offset) {
            return reportError("Input end ($end) may not be before start ($offset)")
        }

        if (myEndOfInput) {
            return reportError("Already closed, can not feed more input")
        }

        myCurrentInputProcessed += myOriginalBufferLen

        streamReadConstraints().validateDocumentLength(myCurrentInputProcessed)

        myCurrentInputRowStart = offset - (myInputEnd - myCurrentInputRowStart)

        myCurrentBufferStart = offset
        myInputBuffer = data
        myInputPointer = offset
        myInputEnd = end
        myOriginalBufferLen = end - offset
    }

    @Throws(CirJacksonException::class)
    override fun <T> reportInvalidToken(matchedPart: String, message: String): T {
        val stringBuilder = StringBuilder(matchedPart)

        while (myInputPointer < myInputEnd) {
            val i = myInputBuffer[myInputPointer++]
            val c = decodeCharForError(i.toInt()).toChar()

            if (!c.isJavaIdentifierPart()) {
                break
            }

            stringBuilder.append(c)

            if (stringBuilder.length >= myIOContext!!.errorReportConfiguration.maxErrorTokenLength) {
                stringBuilder.append("...")
                break
            }
        }

        throw constructReadException("Unrecognized token '$stringBuilder': was expecting $message")
    }

}