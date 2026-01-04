package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.async.ByteBufferFeeder
import org.cirjson.cirjackson.core.async.NonBlockingInputFeeder
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.symbols.ByteQuadsCanonicalizer
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels

/**
 * Non-blocking parser implementation for JSON content that takes its input via [ByteBuffer] instance(s) passed.
 *
 * NOTE: only supports parsing of UTF-8 encoded content (and 7-bit US-ASCII since it is strict subset of UTF-8): other
 * encodings are not supported.
 */
open class NonBlockingByteBufferCirJsonParser(objectReadContext: ObjectReadContext, ioContext: IOContext,
        streamReadFeatures: Int, formatReadFeatures: Int, symbols: ByteQuadsCanonicalizer) :
        NonBlockingUtf8CirJsonParserBase(objectReadContext, ioContext, streamReadFeatures, formatReadFeatures, symbols),
        ByteBufferFeeder {

    private var myInputBuffer = ByteBuffer.wrap(NO_BYTES)

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
            val channel = Channels.newChannel(output)

            try {
                channel.write(myInputBuffer)
            } catch (e: IOException) {
                throw wrapIOFailure(e)
            }
        }

        return avail
    }

    override val typeId: Any?
        get() = null

    override fun feedInput(buffer: ByteBuffer) {
        if (myInputPointer < myInputEnd) {
            return reportError("Still have ${myInputEnd - myInputPointer} undecoded bytes, should not call 'feedInput'")
        }

        val start = buffer.position()
        val end = buffer.limit()

        if (end < start) {
            return reportError("Input end ($end) may not be before start ($start)")
        }

        if (myEndOfInput) {
            return reportError("Already closed, can not feed more input")
        }

        myCurrentInputProcessed += myOriginalBufferLen

        streamReadConstraints().validateDocumentLength(myCurrentInputProcessed)

        myCurrentInputRowStart = start - (myInputEnd - myCurrentInputRowStart)

        myCurrentBufferStart = start
        myInputBuffer = buffer
        myInputPointer = start
        myInputEnd = end
        myOriginalBufferLen = end - start
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