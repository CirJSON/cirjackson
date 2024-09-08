package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.extensions.isNotFinite
import org.cirjson.cirjackson.core.io.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.min

/**
 * @property myOutputStream Underlying output stream used for writing CirJSON content.
 *
 * @property myQuoteChar Character used for quoting CirJSON Object property names and String values.
 *
 * @property myOutputBuffer Intermediate buffer in which contents are buffered before being written using
 * [myOutputStream].
 *
 * @property myOutputTail Pointer to the position right beyond the last character to output (end marker; may be past the
 * buffer)
 *
 * @property myIsBufferRecyclable Flag that indicates whether the output buffer is recyclable (and needs to be returned
 * to recycler once we are done) or not.
 */
open class UTF8CirJsonGenerator(objectWriteContext: ObjectWriteContext, ioContext: IOContext, streamWriteFeatures: Int,
        formatWriteFeatures: Int, protected val myOutputStream: OutputStream?, rootValueSeparator: SerializableString?,
        characterEscapes: CharacterEscapes?, prettyPrinter: PrettyPrinter?, maxNonEscaped: Int,
        protected val myQuoteChar: Byte, protected var myOutputBuffer: ByteArray, protected var myOutputTail: Int,
        protected var myIsBufferRecyclable: Boolean) :
        CirJsonGeneratorBase(objectWriteContext, ioContext, streamWriteFeatures, formatWriteFeatures,
                rootValueSeparator, prettyPrinter, characterEscapes, maxNonEscaped) {

    private val hexBytes = if (myConfigurationWriteHexUppercase) HEX_BYTES_UPPER else HEX_BYTES_LOWER

    /**
     * End marker of the output buffer; one past the last valid position within the buffer.
     */
    protected var myOutputEnd = myOutputBuffer.size

    /**
     * Maximum number of `Char`s that we know will always fit in the output buffer after escaping
     */
    protected val myOutputMaxContiguous = myOutputEnd shr 3

    /**
     * Intermediate buffer in which characters of a String are copied
     * before being encoded.
     */
    protected var myCharBuffer: CharArray? = ioContext.allocConcatBuffer()

    /**
     * Length of `myCharBuffer`
     */
    protected val myCharBufferLength = myCharBuffer!!.size

    /**
     * 6 character temporary buffer allocated if needed, for constructing escape sequences. Unused in the base class.
     */
    @Suppress("unused")
    protected lateinit var myEntityBuffer: ByteArray

    constructor(objectWriteContext: ObjectWriteContext, ioContext: IOContext, streamWriteFeatures: Int,
            formatWriteFeatures: Int, outputStream: OutputStream?, rootValueSeparator: SerializableString?,
            characterEscapes: CharacterEscapes?, prettyPrinter: PrettyPrinter?, maxNonEscaped: Int,
            quoteChar: Byte) : this(objectWriteContext, ioContext, streamWriteFeatures, formatWriteFeatures,
            outputStream, rootValueSeparator, characterEscapes, prettyPrinter, maxNonEscaped, quoteChar,
            ioContext.allocateWriteEncodingBuffer(), 0, true)

    override var characterEscapes: CharacterEscapes?
        get() = super.characterEscapes
        set(value) {
            myCharacterEscapes = value
            myOutputEscapes = value?.escapeCodesForAscii ?: CharTypes.getSevenBitOutputEscapes(myQuoteChar.toInt(),
                    CirJsonWriteFeature.ESCAPE_FORWARD_SLASHES.isEnabledIn(formatWriteFeatures))
        }
    init {
        @Suppress("LeakingThis")
        this.characterEscapes = characterEscapes
    }

    /*
     *******************************************************************************************************************
     * Overridden configuration methods
     *******************************************************************************************************************
     */

    override val streamWriteOutputTarget: Any?
        get() = myOutputStream

    override val streamWriteOutputBuffered: Int
        get() = myOutputTail

    /*
     *******************************************************************************************************************
     * Overridden methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeName(name: String): CirJsonGenerator {
        if (myConfigurationPrettyPrinter != null) {
            writePrettyPrinterName(name)
            return this
        }

        val status = streamWriteContext.writeName(name)

        if (status == CirJsonWriteContext.STATUS_EXPECT_VALUE) {
            return reportError("Cannot write a property name, expecting a value")
        }

        if (status == CirJsonWriteContext.STATUS_OK_AFTER_COMMA) {
            if (myOutputTail >= myOutputEnd) {
                flushBuffer()
            }

            myOutputBuffer[myOutputTail++] = BYTE_COMMA
        }

        if (myConfigurationUnquoteNames) {
            writeStringSegments(name, false)
            return this
        }

        val length = name.length

        if (length >= myCharBufferLength) {
            writeStringSegments(name, true)
            return this
        }

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar

        if (length <= myOutputMaxContiguous) {
            if (myOutputTail + length >= myOutputEnd) {
                flushBuffer()
            }

            writeStringSegment(name, 0, length)
        } else {
            writeStringSegments(name, 0, length)
        }

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeName(name: SerializableString): CirJsonGenerator {
        if (myConfigurationPrettyPrinter != null) {
            writePrettyPrinterName(name)
            return this
        }

        val status = streamWriteContext.writeName(name.value)

        if (status == CirJsonWriteContext.STATUS_EXPECT_VALUE) {
            return reportError("Cannot write a property name, expecting a value")
        }

        if (status == CirJsonWriteContext.STATUS_OK_AFTER_COMMA) {
            if (myOutputTail >= myOutputEnd) {
                flushBuffer()
            }

            myOutputBuffer[myOutputTail++] = BYTE_COMMA
        }

        if (myConfigurationUnquoteNames) {
            writeUnquotedName(name)
            return this
        }

        val length = name.appendQuotedUTF8(myOutputBuffer, myOutputTail)

        if (length < 0) {
            writeBytes(name.asQuotedUTF8())
        } else {
            myOutputTail += length
        }

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        return this
    }

    @Throws(CirJacksonException::class)
    private fun writeUnquotedName(name: SerializableString) {
        val length = name.appendQuotedUTF8(myOutputBuffer, myOutputTail)

        if (length < 0) {
            writeBytes(name.asQuotedUTF8())
        } else {
            myOutputTail += length
        }
    }

    /*
     *******************************************************************************************************************
     * Output method implementations, structural
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun getID(target: Any, isArray: Boolean): String {
        return myIDHolder.getID(target, isArray)
    }

    @Throws(CirJacksonException::class)
    override fun writeStartArray(): CirJsonGenerator {
        verifyValueWrite(TYPE_MESSAGE_START_ARRAY)
        streamWriteContext = streamWriteContext.createChildArrayContext(null)
        streamWriteConstraints.validateNestingDepth(streamWriteContext.nestingDepth)

        if (myConfigurationPrettyPrinter != null) {
            myConfigurationPrettyPrinter.writeStartArray(this)
        } else {
            if (myOutputTail >= myOutputEnd) {
                flushBuffer()
            }

            myOutputBuffer[myOutputTail++] = BYTE_L_BRACKET
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeStartArray(currentValue: Any?): CirJsonGenerator {
        verifyValueWrite(TYPE_MESSAGE_START_ARRAY)
        streamWriteContext = streamWriteContext.createChildArrayContext(currentValue)
        streamWriteConstraints.validateNestingDepth(streamWriteContext.nestingDepth)

        if (myConfigurationPrettyPrinter != null) {
            myConfigurationPrettyPrinter.writeStartArray(this)
        } else {
            if (myOutputTail >= myOutputEnd) {
                flushBuffer()
            }

            myOutputBuffer[myOutputTail++] = BYTE_L_BRACKET
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeStartArray(currentValue: Any?, size: Int): CirJsonGenerator {
        verifyValueWrite(TYPE_MESSAGE_START_ARRAY)
        streamWriteContext = streamWriteContext.createChildArrayContext(currentValue)
        streamWriteConstraints.validateNestingDepth(streamWriteContext.nestingDepth)

        if (myConfigurationPrettyPrinter != null) {
            myConfigurationPrettyPrinter.writeStartArray(this)
        } else {
            if (myOutputTail >= myOutputEnd) {
                flushBuffer()
            }

            myOutputBuffer[myOutputTail++] = BYTE_L_BRACKET
        }

        return this
    }

    override fun writeArrayId(referenced: Any): CirJsonGenerator {
        val id = getArrayID(referenced)
        writeString(id)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeEndArray(): CirJsonGenerator {
        if (!streamWriteContext.isInArray) {
            return reportError("Current context not Array but ${streamWriteContext.typeDescription}")
        }

        if (myConfigurationPrettyPrinter != null) {
            myConfigurationPrettyPrinter.writeEndArray(this, streamWriteContext.entryCount)
        } else {
            if (myOutputTail >= myOutputEnd) {
                flushBuffer()
            }

            myOutputBuffer[myOutputTail++] = BYTE_R_BRACKET
        }

        streamWriteContext = streamWriteContext.clearAndGetParent()!!

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeStartObject(): CirJsonGenerator {
        verifyValueWrite(TYPE_MESSAGE_START_OBJECT)
        streamWriteContext = streamWriteContext.createChildObjectContext(null)
        streamWriteConstraints.validateNestingDepth(streamWriteContext.nestingDepth)

        if (myConfigurationPrettyPrinter != null) {
            myConfigurationPrettyPrinter.writeStartObject(this)
        } else {
            if (myOutputTail >= myOutputEnd) {
                flushBuffer()
            }

            myOutputBuffer[myOutputTail++] = BYTE_L_CURLY
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeStartObject(currentValue: Any?): CirJsonGenerator {
        verifyValueWrite(TYPE_MESSAGE_START_OBJECT)
        streamWriteContext = streamWriteContext.createChildObjectContext(currentValue)
        streamWriteConstraints.validateNestingDepth(streamWriteContext.nestingDepth)

        if (myConfigurationPrettyPrinter != null) {
            myConfigurationPrettyPrinter.writeStartObject(this)
        } else {
            if (myOutputTail >= myOutputEnd) {
                flushBuffer()
            }

            myOutputBuffer[myOutputTail++] = BYTE_L_CURLY
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeStartObject(currentValue: Any?, size: Int): CirJsonGenerator {
        verifyValueWrite(TYPE_MESSAGE_START_OBJECT)
        streamWriteContext = streamWriteContext.createChildObjectContext(currentValue)
        streamWriteConstraints.validateNestingDepth(streamWriteContext.nestingDepth)

        if (myConfigurationPrettyPrinter != null) {
            myConfigurationPrettyPrinter.writeStartObject(this)
        } else {
            if (myOutputTail >= myOutputEnd) {
                flushBuffer()
            }

            myOutputBuffer[myOutputTail++] = BYTE_L_CURLY
        }

        return this
    }

    override fun writeObjectId(referenced: Any): CirJsonGenerator {
        writeName(ID_NAME)
        val id = getObjectID(referenced)
        writeString(id)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeEndObject(): CirJsonGenerator {
        if (!streamWriteContext.isInObject) {
            return reportError("Current context not Object but ${streamWriteContext.typeDescription}")
        }

        if (myConfigurationPrettyPrinter != null) {
            myConfigurationPrettyPrinter.writeEndObject(this, streamWriteContext.entryCount)
        } else {
            if (myOutputTail >= myOutputEnd) {
                flushBuffer()
            }

            myOutputBuffer[myOutputTail++] = BYTE_R_CURLY
        }

        streamWriteContext = streamWriteContext.clearAndGetParent()!!

        return this
    }

    /**
     * Specialized version of `writeName`, off-lined to keep the "fast path" as simple (and hopefully fast) as possible.
     */
    @Throws(CirJacksonException::class)
    protected fun writePrettyPrinterName(name: String) {
        val status = streamWriteContext.writeName(name)

        if (status == CirJsonWriteContext.STATUS_EXPECT_VALUE) {
            return reportError("Cannot write a property name, expecting a value")
        }

        if (status == CirJsonWriteContext.STATUS_OK_AFTER_COMMA) {
            myConfigurationPrettyPrinter!!.writeObjectEntrySeparator(this)
        } else {
            myConfigurationPrettyPrinter!!.beforeObjectEntries(this)
        }

        if (myConfigurationUnquoteNames) {
            writeStringSegments(name, false)
            return
        }

        val length = name.length

        if (length >= myCharBufferLength) {
            writeStringSegments(name, true)
            return
        }

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        name.toCharArray(myCharBuffer!!, 0, 0, length)

        if (length <= myOutputMaxContiguous) {
            if (myOutputTail + length >= myOutputEnd) {
                flushBuffer()
            }

            writeStringSegment(myCharBuffer!!, 0, length)
        } else {
            writeStringSegments(myCharBuffer!!, 0, length)
        }

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
    }

    /**
     * Specialized version of `writeName`, off-lined to keep the "fast path" as simple (and hopefully fast) as possible.
     */
    @Throws(CirJacksonException::class)
    protected fun writePrettyPrinterName(name: SerializableString) {
        val status = streamWriteContext.writeName(name.value)

        if (status == CirJsonWriteContext.STATUS_EXPECT_VALUE) {
            return reportError("Cannot write a property name, expecting a value")
        }

        if (status == CirJsonWriteContext.STATUS_OK_AFTER_COMMA) {
            myConfigurationPrettyPrinter!!.writeObjectEntrySeparator(this)
        } else {
            myConfigurationPrettyPrinter!!.beforeObjectEntries(this)
        }

        val addQuotes = !myConfigurationUnquoteNames

        if (addQuotes) {
            if (myOutputTail >= myOutputEnd) {
                flushBuffer()
            }

            myOutputBuffer[myOutputTail++] = myQuoteChar
        }

        val length = name.appendQuotedUTF8(myOutputBuffer, myOutputTail)

        if (length < 0) {
            writeBytes(name.asQuotedUTF8())
        } else {
            myOutputTail += length
        }

        if (addQuotes) {
            if (myOutputTail >= myOutputEnd) {
                flushBuffer()
            }

            myOutputBuffer[myOutputTail++] = myQuoteChar
        }
    }

    /*
     *******************************************************************************************************************
     * Output method implementations, textual
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeString(value: String?): CirJsonGenerator {
        verifyValueWrite(WRITE_STRING)

        if (value == null) {
            writeNullInternal()
            return this
        }

        val length = value.length

        if (length > myOutputMaxContiguous) {
            writeStringSegments(value, true)
            return this
        }

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        writeStringSegment(value, 0, length)

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeString(reader: Reader?, length: Int): CirJsonGenerator {
        verifyValueWrite(WRITE_STRING)
        reader ?: return reportError("null reader")

        var toRead = if (length >= 0) length else Int.MAX_VALUE
        val buffer = myCharBuffer!!

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar

        while (toRead > 0) {
            val toReadNow = min(toRead, buffer.size)
            val read = try {
                reader.read(buffer, 0, toReadNow)
            } catch (e: IOException) {
                throw wrapIOFailure(e)
            }

            if (read <= 0) {
                break
            }

            if (myOutputTail + length >= myOutputEnd) {
                flushBuffer()
            }

            writeStringSegments(buffer, 0, read)
            toRead -= read
        }

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar

        if (length >= 0 && toRead > 0) {
            return reportError("Didn't read enough from reader")
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeString(buffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        verifyValueWrite(WRITE_STRING)

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar

        if (length <= myOutputMaxContiguous) {
            if (myOutputTail + length >= myOutputEnd) {
                flushBuffer()
            }

            writeStringSegment(buffer, offset, length)
        } else {
            writeStringSegments(buffer, offset, length)
        }

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeString(value: SerializableString): CirJsonGenerator {
        verifyValueWrite(WRITE_STRING)

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar

        val length = value.appendQuotedUTF8(myOutputBuffer, myOutputTail)

        if (length < 0) {
            writeBytes(value.asQuotedUTF8())
        } else {
            myOutputTail += length
        }

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRawUTF8String(buffer: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        checkRangeBoundsForByteArray(buffer, offset, length)
        verifyValueWrite(WRITE_STRING)

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        writeBytes(buffer, offset, length)

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeUTF8String(buffer: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        checkRangeBoundsForByteArray(buffer, offset, length)
        verifyValueWrite(WRITE_STRING)

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar

        if (length <= myOutputMaxContiguous) {
            writeUTF8Segment(buffer, offset, length)
        } else {
            writeUTF8Segments(buffer, offset, length)
        }

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        return this
    }

    /*
     *******************************************************************************************************************
     * Output method implementations, unprocessed ("raw")
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeRaw(text: String): CirJsonGenerator {
        val length = text.length
        val buffer = myCharBuffer!!

        if (length <= buffer.size) {
            text.toCharArray(buffer, 0, 0, length)
            writeRaw(buffer, 0, length)
        } else {
            writeRaw(text, 0, length)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    override fun writeRaw(text: String, offset: Int, length: Int): CirJsonGenerator {
        var offset = offset
        var length = length
        checkRangeBoundsForString(text, offset, length)
        val buffer = myCharBuffer!!
        val bufferLength = buffer.size

        if (length <= bufferLength) {
            text.toCharArray(buffer, 0, 0, length)
            writeRaw(buffer, 0, length)
            return this
        }

        val maxChunk = min(bufferLength, myOutputEnd shr 2 + myOutputEnd shr 4)
        val maxBytes = maxChunk * 3

        while (length > 0) {
            var length2 = min(maxChunk, length)
            text.toCharArray(buffer, 0, offset, offset + length2)

            if (myOutputTail + maxBytes >= myOutputEnd) {
                flushBuffer()
            }

            if (length2 > 1) {
                val ch = buffer[length2 - 1]

                if (ch in UTF8Writer.SURR1_FIRST.toChar()..UTF8Writer.SURR1_LAST.toChar()) {
                    --length2
                }
            }

            writeRawSegment(buffer, 0, length2)
            offset += length2
            length -= length2
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(raw: SerializableString): CirJsonGenerator {
        val length = raw.appendUnquotedUTF8(myOutputBuffer, myOutputTail)

        if (length < 0) {
            writeBytes(raw.asUnquotedUTF8())
        } else {
            myOutputTail += length
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRawValue(raw: SerializableString): CirJsonGenerator {
        verifyValueWrite(WRITE_RAW)
        val length = raw.appendUnquotedUTF8(myOutputBuffer, myOutputTail)

        if (length < 0) {
            writeBytes(raw.asUnquotedUTF8())
        } else {
            myOutputTail += length
        }

        return this
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    override fun writeRaw(buffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        var offset = offset
        var length = length
        checkRangeBoundsForCharArray(buffer, offset, length)
        val length3 = length * 3

        if (myOutputTail + length3 >= myOutputEnd) {
            if (myOutputEnd < length3) {
                writeSegmentedRaw(buffer, offset, length)
                return this
            }

            flushBuffer()
        }

        length += offset

        mainLoop@ while (offset < length) {
            while (true) {
                val ch = buffer[offset].code

                if (ch > 0x7F) {
                    break
                }

                myOutputBuffer[myOutputTail++] = ch.toByte()

                if (++offset >= length) {
                    break@mainLoop
                }
            }

            val ch = buffer[offset++].code

            if (ch < 0x800) {
                myOutputBuffer[myOutputTail++] = (ch shr 6 or 0xC0).toByte()
                myOutputBuffer[myOutputTail++] = (ch and 0x3F or 0x80).toByte()
            } else {
                offset = outputRawMultibyteChar(ch, buffer, offset, length)
            }
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(char: Char): CirJsonGenerator {
        if (myOutputTail + 3 >= myOutputEnd) {
            flushBuffer()
        }

        val ch = char.code
        val buffer = myOutputBuffer

        if (ch <= 0x7F) {
            buffer[myOutputTail++] = ch.toByte()
        } else if (ch < 0x800) {
            buffer[myOutputTail++] = (ch shr 6 or 0xC0).toByte()
            buffer[myOutputTail++] = (ch and 0x3F or 0x80).toByte()
        } else {
            outputRawMultibyteChar(ch, null, 0, 0)
        }

        return this
    }

    /**
     * Helper method called when it is possible that output of raw section to output may cross buffer boundary
     */
    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun writeSegmentedRaw(buffer: CharArray, offset: Int, length: Int) {
        var offset = offset
        val byteBuffer = myOutputBuffer
        val inputEnd = offset + length

        mainLoop@ while (offset < inputEnd) {
            while (true) {
                val ch = buffer[offset].code

                if (ch > 0x7F) {
                    break
                }

                if (myOutputTail >= myOutputEnd) {
                    flushBuffer()
                }

                byteBuffer[myOutputTail++] = ch.toByte()

                if (++offset >= inputEnd) {
                    break@mainLoop
                }
            }

            if (myOutputTail + 3 >= myOutputEnd) {
                flushBuffer()
            }

            val ch = buffer[offset++].code

            if (ch < 0x800) {
                byteBuffer[myOutputTail++] = (ch shr 6 or 0xC0).toByte()
                byteBuffer[myOutputTail++] = (ch and 0x3F or 0x80).toByte()
            } else {
                offset = outputRawMultibyteChar(ch, buffer, offset, inputEnd)
            }
        }
    }

    /**
     * Helper method that is called for segmented write of raw content when explicitly outputting a segment of longer
     * thing. Caller has to take care of ensuring there's no split surrogate pair at the end (that is, last char can not
     * be first part of a surrogate char pair).
     */
    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING", "SameParameterValue")
    private fun writeRawSegment(buffer: CharArray, offset: Int, end: Int) {
        var offset = offset

        mainLoop@ while (offset < end) {
            while (true) {
                val ch = buffer[offset].code

                if (ch > 0x7F) {
                    break
                }

                myOutputBuffer[myOutputTail++] = ch.toByte()

                if (++offset >= end) {
                    break@mainLoop
                }
            }

            val ch = buffer[offset++].code

            if (ch < 0x800) {
                myOutputBuffer[myOutputTail++] = (ch shr 6 or 0xC0).toByte()
                myOutputBuffer[myOutputTail++] = (ch and 0x3F or 0x80).toByte()
            } else {
                offset = outputRawMultibyteChar(ch, buffer, offset, end)
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Output method implementations, base64-encoded binary
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeBinary(variant: Base64Variant, data: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        checkRangeBoundsForByteArray(data, offset, length)

        verifyValueWrite(WRITE_BINARY)

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        writeBinaryInternal(variant, data, offset, offset + length)

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeBinary(variant: Base64Variant, data: InputStream, dataLength: Int): Int {
        verifyValueWrite(WRITE_BINARY)

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        val encodingBuffer = ioContext.allocateBase64Buffer()

        val bytes = try {
            if (dataLength < 0) {
                writeBinary(variant, data, encodingBuffer)
            } else {
                val missing = writeBinary(variant, data, encodingBuffer, dataLength)

                if (missing <= 0) {
                    dataLength
                } else {
                    reportError("Too few bytes available: missing $missing bytes (out of $dataLength)")
                }
            }
        } finally {
            ioContext.releaseBase64Buffer(encodingBuffer)
        }

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        return bytes
    }

    /*
     *******************************************************************************************************************
     * Output method implementations, primitive
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Short): CirJsonGenerator {
        verifyValueWrite(WRITE_NUMBER)

        if (myConfigurationNumbersAsStrings) {
            writeQuotedShort(value)
            return this
        }

        if (myOutputTail + 6 >= myOutputEnd) {
            flushBuffer()
        }

        myOutputTail = NumberOutput.outputInt(value.toInt(), myOutputBuffer, myOutputTail)
        return this
    }

    @Throws(CirJacksonException::class)
    private fun writeQuotedShort(value: Short) {
        if (myOutputTail + 8 >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        myOutputTail = NumberOutput.outputInt(value.toInt(), myOutputBuffer, myOutputTail)
        myOutputBuffer[myOutputTail++] = myQuoteChar
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Int): CirJsonGenerator {
        verifyValueWrite(WRITE_NUMBER)

        if (myConfigurationNumbersAsStrings) {
            writeQuotedInt(value)
            return this
        }

        if (myOutputTail + 11 >= myOutputEnd) {
            flushBuffer()
        }

        myOutputTail = NumberOutput.outputInt(value, myOutputBuffer, myOutputTail)
        return this
    }

    @Throws(CirJacksonException::class)
    private fun writeQuotedInt(value: Int) {
        if (myOutputTail + 13 >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        myOutputTail = NumberOutput.outputInt(value, myOutputBuffer, myOutputTail)
        myOutputBuffer[myOutputTail++] = myQuoteChar
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Long): CirJsonGenerator {
        verifyValueWrite(WRITE_NUMBER)

        if (myConfigurationNumbersAsStrings) {
            writeQuotedLong(value)
            return this
        }

        if (myOutputTail + 21 >= myOutputEnd) {
            flushBuffer()
        }

        myOutputTail = NumberOutput.outputLong(value, myOutputBuffer, myOutputTail)
        return this
    }

    @Throws(CirJacksonException::class)
    private fun writeQuotedLong(value: Long) {
        if (myOutputTail + 23 >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        myOutputTail = NumberOutput.outputLong(value, myOutputBuffer, myOutputTail)
        myOutputBuffer[myOutputTail++] = myQuoteChar
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: BigInteger?): CirJsonGenerator {
        verifyValueWrite(WRITE_NUMBER)

        if (value == null) {
            writeNullInternal()
        } else if (myConfigurationNumbersAsStrings) {
            writeQuotedRaw(value.toString())
        } else {
            writeRaw(value.toString())
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Double): CirJsonGenerator {
        val useFastWriter = isEnabled(StreamWriteFeature.USE_FAST_DOUBLE_WRITER)

        if (myConfigurationNumbersAsStrings ||
                value.isNotFinite() && CirJsonWriteFeature.WRITE_NAN_AS_STRINGS.isEnabledIn(formatWriteFeatures)) {
            writeString(NumberOutput.toString(value, useFastWriter))
        } else {
            verifyValueWrite(WRITE_NUMBER)
            writeRaw(NumberOutput.toString(value, useFastWriter))
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Float): CirJsonGenerator {
        val useFastWriter = isEnabled(StreamWriteFeature.USE_FAST_DOUBLE_WRITER)

        if (myConfigurationNumbersAsStrings ||
                value.isNotFinite() && CirJsonWriteFeature.WRITE_NAN_AS_STRINGS.isEnabledIn(formatWriteFeatures)) {
            writeString(NumberOutput.toString(value, useFastWriter))
        } else {
            verifyValueWrite(WRITE_NUMBER)
            writeRaw(NumberOutput.toString(value, useFastWriter))
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: BigDecimal?): CirJsonGenerator {
        verifyValueWrite(WRITE_NUMBER)

        if (value == null) {
            writeNullInternal()
        } else if (myConfigurationNumbersAsStrings) {
            writeQuotedRaw(asString(value))
        } else {
            writeRaw(asString(value))
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(encodedValue: String?): CirJsonGenerator {
        verifyValueWrite(WRITE_NUMBER)

        if (encodedValue == null) {
            writeNullInternal()
        } else if (myConfigurationNumbersAsStrings) {
            writeQuotedRaw(encodedValue)
        } else {
            writeRaw(encodedValue)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(encodedValueBuffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        verifyValueWrite(WRITE_NUMBER)

        if (myConfigurationNumbersAsStrings) {
            writeQuotedRaw(encodedValueBuffer, offset, length)
        } else {
            writeRaw(encodedValueBuffer, offset, length)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    private fun writeQuotedRaw(value: String) {
        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        writeRaw(value)

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
    }

    @Throws(CirJacksonException::class)
    private fun writeQuotedRaw(text: CharArray, offset: Int, length: Int) {
        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        writeRaw(text, offset, length)

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
    }

    @Throws(CirJacksonException::class)
    override fun writeBoolean(state: Boolean): CirJsonGenerator {
        verifyValueWrite(WRITE_BOOLEAN)

        if (myOutputTail + 5 >= myOutputEnd) {
            flushBuffer()
        }

        val keyword = if (state) TRUE_BYTES else FALSE_BYTES
        val length = keyword.size
        keyword.copyInto(myOutputBuffer, myOutputTail, 0, length)

        myOutputTail += length
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNull(): CirJsonGenerator {
        verifyValueWrite(WRITE_NULL)
        writeNullInternal()
        return this
    }

    /*
     *******************************************************************************************************************
     * Implementations for other methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    final override fun verifyValueWrite(typeMessage: String) {
        val status = streamWriteContext.writeValue()

        if (myConfigurationPrettyPrinter != null) {
            verifyPrettyValueWrite(typeMessage, status)
            return
        }

        val b = when (status) {
            CirJsonWriteContext.STATUS_OK_AS_IS -> return

            CirJsonWriteContext.STATUS_OK_AFTER_COMMA -> BYTE_COMMA

            CirJsonWriteContext.STATUS_OK_AFTER_COLON -> BYTE_COLON

            CirJsonWriteContext.STATUS_OK_AFTER_SPACE -> {
                if (myRootValueSeparator != null) {
                    val raw = myRootValueSeparator.asUnquotedUTF8()

                    if (raw.isNotEmpty()) {
                        writeBytes(raw)
                    }
                }

                return
            }

            CirJsonWriteContext.STATUS_EXPECT_NAME -> return reportCannotWriteValueExpectName(typeMessage)

            else -> return
        }

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = b
    }

    /*
     *******************************************************************************************************************
     * Low-level output handling
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun flush() {
        flushBuffer()

        if (myOutputStream == null || !isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)) {
            return
        }

        try {
            myOutputStream.flush()
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }
    }

    @Throws(CirJacksonException::class)
    override fun closeInput() {
        var flushFail: RuntimeException? = null

        try {
            if (myOutputBuffer !== FLUSHED_OUTPUT_BUFFER && isEnabled(StreamWriteFeature.AUTO_CLOSE_CONTENT)) {
                while (true) {
                    val context = streamWriteContext

                    if (context.isInArray) {
                        writeEndArray()
                    } else if (context.isInObject) {
                        writeEndObject()
                    } else {
                        break
                    }
                }
            }

            flushBuffer()
        } catch (e: RuntimeException) {
            flushFail = e
        }

        myOutputTail = 0

        if (myOutputStream != null) {
            try {
                if (ioContext.isResourceManaged || isEnabled(StreamWriteFeature.AUTO_CLOSE_TARGET)) {
                    myOutputStream.close()
                } else if (isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)) {
                    myOutputStream.flush()
                }
            } catch (e: IOException) {
                val cirJacksonException = wrapIOFailure(e)

                if (flushFail != null) {
                    cirJacksonException.addSuppressed(flushFail)
                }

                throw cirJacksonException
            }
        }

        if (flushFail != null) {
            throw flushFail
        }
    }

    override fun releaseBuffers() {
        val buffer = myOutputBuffer

        if (buffer !== FLUSHED_OUTPUT_BUFFER && myIsBufferRecyclable) {
            myOutputBuffer = FLUSHED_OUTPUT_BUFFER
            ioContext.releaseWriteEncodingBuffer(buffer)
        }

        val charBuffer = myCharBuffer

        if (charBuffer != null) {
            myCharBuffer = null
            ioContext.releaseConcatBuffer(charBuffer)
        }
    }

    /*
     *******************************************************************************************************************
     * Internal methods, low-level writing; raw bytes
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun writeBytes(buffer: ByteArray) {
        val length = buffer.size

        if (myOutputTail + length >= myOutputEnd) {
            flushBuffer()

            if (length > MAX_BYTES_TO_BUFFER) {
                try {
                    myOutputStream!!.write(buffer, 0, length)
                } catch (e: IOException) {
                    throw wrapIOFailure(e)
                }

                return
            }
        }

        buffer.copyInto(myOutputBuffer, myOutputTail, 0, length)
        myOutputTail += length
    }

    @Throws(CirJacksonException::class)
    private fun writeBytes(buffer: ByteArray, offset: Int, length: Int) {
        if (myOutputTail + length >= myOutputEnd) {
            flushBuffer()

            if (length > MAX_BYTES_TO_BUFFER) {
                try {
                    myOutputStream!!.write(buffer, offset, length)
                } catch (e: IOException) {
                    throw wrapIOFailure(e)
                }

                return
            }
        }

        buffer.copyInto(myOutputBuffer, myOutputTail, offset, offset + length)
        myOutputTail += length
    }

    /*
     *******************************************************************************************************************
     * Internal methods, mid-level writing, String segments
     *******************************************************************************************************************
     */

    /**
     * Method called when String to write is long enough not to fit completely in temporary copy buffer. If so, we will
     * actually copy it in small enough chunks, so it can be directly fed to single-segment writes (instead of maximum
     * slices that would fit in copy buffer)
     */
    @Throws(CirJacksonException::class)
    private fun writeStringSegments(text: String, addQuotes: Boolean) {
        if (addQuotes) {
            if (myOutputTail >= myOutputEnd) {
                flushBuffer()
            }

            myOutputBuffer[myOutputTail++] = myQuoteChar
        }

        var left = text.length
        var offset = 0

        while (left > 0) {
            val length = min(myOutputMaxContiguous, left)

            if (myOutputTail + length >= myOutputEnd) {
                flushBuffer()
            }

            writeStringSegment(text, offset, length)
            offset += length
            left -= length
        }

        if (addQuotes) {
            if (myOutputTail >= myOutputEnd) {
                flushBuffer()
            }

            myOutputBuffer[myOutputTail++] = myQuoteChar
        }
    }

    /**
     * Method called when character sequence to write is long enough that its maximum encoded and escaped form is not
     * guaranteed to fit in the output buffer. If so, we will need to choose smaller output chunks to write at a time.
     */
    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun writeStringSegments(buffer: CharArray, offset: Int, totalLength: Int) {
        var offset = offset
        var totalLength = totalLength

        do {
            val length = min(myOutputMaxContiguous, totalLength)

            if (myOutputTail + length >= myOutputEnd) {
                flushBuffer()
            }

            writeStringSegment(buffer, offset, length)
            offset += length
            totalLength -= length
        } while (totalLength > 0)
    }

    /**
     * Method called when character sequence to write is long enough that its maximum encoded and escaped form is not
     * guaranteed to fit in the output buffer. If so, we will need to choose smaller output chunks to write at a time.
     */
    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING", "SameParameterValue")
    private fun writeStringSegments(text: String, offset: Int, totalLength: Int) {
        var offset = offset
        var totalLength = totalLength

        do {
            val length = min(myOutputMaxContiguous, totalLength)

            if (myOutputTail + length >= myOutputEnd) {
                flushBuffer()
            }

            writeStringSegment(text, offset, length)
            offset += length
            totalLength -= length
        } while (totalLength > 0)
    }

    /*
     *******************************************************************************************************************
     * Internal methods, low-level writing; text segment with additional escaping (ASCII or such)
     *******************************************************************************************************************
     */

    /**
     * This method called when the string content is already in a char buffer, and its maximum total encoded and escaped
     * length can not exceed size of the output buffer. Caller must ensure that there is enough space in output buffer,
     * assuming case of all non-escaped ASCII characters, as well as potentially enough space for other cases (but not
     * necessarily flushed)
     */
    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun writeStringSegment(buffer: CharArray, offset: Int, length: Int) {
        var offset = offset
        val length = length + offset
        var outputPointer = myOutputTail
        val outputBuffer = myOutputBuffer
        val escapeCodes = myOutputEscapes

        while (offset < length) {
            val ch = buffer[offset].code

            if (ch > 0x7F || escapeCodes[ch] != 0) {
                break
            }

            outputBuffer[outputPointer++] = ch.toByte()
            ++offset
        }

        myOutputTail = outputPointer

        if (offset >= length) {
            return
        }

        if (myCharacterEscapes != null) {
            writeCustomStringSegment(buffer, offset, length)
        } else if (highestNonEscapedChar == 0) {
            writeStringSegment2(buffer, offset, length)
        } else {
            writeStringSegmentASCII(buffer, offset, length)
        }
    }

    /**
     * This method called when the string content is already in a string, and its maximum total encoded and escaped
     * length can not exceed size of the output buffer. Caller must ensure that there is enough space in output buffer,
     * assuming case of all non-escaped ASCII characters, as well as potentially enough space for other cases (but not
     * necessarily flushed)
     */
    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun writeStringSegment(text: String, offset: Int, length: Int) {
        var offset = offset
        val length = length + offset
        var outputPointer = myOutputTail
        val outputBuffer = myOutputBuffer
        val escapeCodes = myOutputEscapes

        while (offset < length) {
            val ch = text[offset].code

            if (ch > 0x7F || escapeCodes[ch] != 0) {
                break
            }

            outputBuffer[outputPointer++] = ch.toByte()
            ++offset
        }

        myOutputTail = outputPointer

        if (offset >= length) {
            return
        }

        if (myCharacterEscapes != null) {
            writeCustomStringSegment(text, offset, length)
        } else if (highestNonEscapedChar == 0) {
            writeStringSegment2(text, offset, length)
        } else {
            writeStringSegmentASCII(text, offset, length)
        }
    }

    /**
     * Secondary method called when content contains characters to escape, and/or multibyte UTF-8 characters.
     */
    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun writeStringSegment2(buffer: CharArray, offset: Int, end: Int) {
        var offset = offset

        if (myOutputTail + 6 * (end - offset) > myOutputEnd) {
            flushBuffer()
        }

        var outputPointer = myOutputTail
        val outputBuffer = myOutputBuffer
        val escapeCodes = myOutputEscapes

        while (offset < end) {
            val ch = buffer[offset++].code

            if (ch <= 0x7F) {
                if (escapeCodes[ch] == 0) {
                    outputBuffer[outputPointer++] = ch.toByte()
                    continue
                }

                val escape = escapeCodes[ch]

                if (escape > 0) {
                    outputBuffer[outputPointer++] = BYTE_BACKSLASH
                    outputBuffer[outputPointer++] = escape.toByte()
                } else {
                    outputPointer = writeGenericEscape(ch, outputPointer)
                }

                continue
            }

            if (ch <= 0x7FF) {
                outputBuffer[outputPointer++] = (ch shr 6 or 0xC0).toByte()
                outputBuffer[outputPointer++] = (ch and 0x3F or 0x80).toByte()
            } else {
                outputPointer = outputMultibyteChar(ch, outputPointer)
            }
        }

        myOutputTail = outputPointer
    }

    /**
     * Secondary method called when content contains characters to escape, and/or multibyte UTF-8 characters.
     */
    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun writeStringSegment2(text: String, offset: Int, end: Int) {
        var offset = offset

        if (myOutputTail + 6 * (end - offset) > myOutputEnd) {
            flushBuffer()
        }

        var outputPointer = myOutputTail
        val outputBuffer = myOutputBuffer
        val escapeCodes = myOutputEscapes

        while (offset < end) {
            val ch = text[offset++].code

            if (ch <= 0x7F) {
                if (escapeCodes[ch] == 0) {
                    outputBuffer[outputPointer++] = ch.toByte()
                    continue
                }

                val escape = escapeCodes[ch]

                if (escape > 0) {
                    outputBuffer[outputPointer++] = BYTE_BACKSLASH
                    outputBuffer[outputPointer++] = escape.toByte()
                } else {
                    outputPointer = writeGenericEscape(ch, outputPointer)
                }

                continue
            }

            if (ch <= 0x7FF) {
                outputBuffer[outputPointer++] = (ch shr 6 or 0xC0).toByte()
                outputBuffer[outputPointer++] = (ch and 0x3F or 0x80).toByte()
            } else {
                outputPointer = outputMultibyteChar(ch, outputPointer)
            }
        }

        myOutputTail = outputPointer
    }

    /*
     *******************************************************************************************************************
     * Internal methods, low-level writing; text segment with additional escaping (ASCII or such)
     *******************************************************************************************************************
     */

    /**
     * Same as `writeStringSegment`, but with additional escaping for high-range code points
     */
    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun writeStringSegmentASCII(buffer: CharArray, offset: Int, end: Int) {
        var offset = offset

        if (myOutputTail + 6 * (end - offset) > myOutputEnd) {
            flushBuffer()
        }

        var outputPointer = myOutputTail
        val outputBuffer = myOutputBuffer
        val escapeCodes = myOutputEscapes

        while (offset < end) {
            val ch = buffer[offset++].code

            if (ch <= 0x7F) {
                if (escapeCodes[ch] == 0) {
                    outputBuffer[outputPointer++] = ch.toByte()
                    continue
                }

                val escape = escapeCodes[ch]

                if (escape > 0) {
                    outputBuffer[outputPointer++] = BYTE_BACKSLASH
                    outputBuffer[outputPointer++] = escape.toByte()
                } else {
                    outputPointer = writeGenericEscape(ch, outputPointer)
                }

                continue
            }

            if (ch > highestNonEscapedChar) {
                outputPointer = writeGenericEscape(ch, outputPointer)
                continue
            }

            if (ch <= 0x7FF) {
                outputBuffer[outputPointer++] = (ch shr 6 or 0xC0).toByte()
                outputBuffer[outputPointer++] = (ch and 0x3F or 0x80).toByte()
            } else {
                outputPointer = outputMultibyteChar(ch, outputPointer)
            }
        }

        myOutputTail = outputPointer
    }

    /**
     * Same as `writeStringSegment`, but with additional escaping for high-range code points
     */
    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun writeStringSegmentASCII(text: String, offset: Int, end: Int) {
        var offset = offset

        if (myOutputTail + 6 * (end - offset) > myOutputEnd) {
            flushBuffer()
        }

        var outputPointer = myOutputTail
        val outputBuffer = myOutputBuffer
        val escapeCodes = myOutputEscapes

        while (offset < end) {
            val ch = text[offset++].code

            if (ch <= 0x7F) {
                if (escapeCodes[ch] == 0) {
                    outputBuffer[outputPointer++] = ch.toByte()
                    continue
                }

                val escape = escapeCodes[ch]

                if (escape > 0) {
                    outputBuffer[outputPointer++] = BYTE_BACKSLASH
                    outputBuffer[outputPointer++] = escape.toByte()
                } else {
                    outputPointer = writeGenericEscape(ch, outputPointer)
                }

                continue
            }

            if (ch > highestNonEscapedChar) {
                outputPointer = writeGenericEscape(ch, outputPointer)
                continue
            }

            if (ch <= 0x7FF) {
                outputBuffer[outputPointer++] = (ch shr 6 or 0xC0).toByte()
                outputBuffer[outputPointer++] = (ch and 0x3F or 0x80).toByte()
            } else {
                outputPointer = outputMultibyteChar(ch, outputPointer)
            }
        }

        myOutputTail = outputPointer
    }

    /*
     *******************************************************************************************************************
     * Internal methods, low-level writing; text segment with fully custom escaping (and possibly escaping of non-ASCII)
     *******************************************************************************************************************
     */

    /**
     * Same as `writeStringSegmentASCII`, but with additional escaping for high-range code points
     */
    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING", "DuplicatedCode")
    private fun writeCustomStringSegment(buffer: CharArray, offset: Int, end: Int) {
        var offset = offset

        if (myOutputTail + 6 * (end - offset) > myOutputEnd) {
            flushBuffer()
        }

        var outputPointer = myOutputTail
        val outputBuffer = myOutputBuffer
        val escapeCodes = myOutputEscapes
        val maxUnescaped = if (highestNonEscapedChar > 0) highestNonEscapedChar else 0xFFFF
        val customEscapes = myCharacterEscapes!!

        while (offset < end) {
            val ch = buffer[offset++].code

            if (ch <= 0x7F) {
                if (escapeCodes[ch] == 0) {
                    outputBuffer[outputPointer++] = ch.toByte()
                    continue
                }

                val escape = escapeCodes[ch]

                if (escape > 0) {
                    outputBuffer[outputPointer++] = BYTE_BACKSLASH
                    outputBuffer[outputPointer++] = escape.toByte()
                } else if (escape == CharacterEscapes.ESCAPE_CUSTOM) {
                    val escapedString = customEscapes.getEscapeSequence(ch) ?: return reportError(
                            "Invalid custom escape definitions; custom escape not found for character code 0x${
                                ch.toString(16).uppercase()
                            }, although was supposed to have one")
                    outputPointer = writeCustomEscape(outputBuffer, outputPointer, escapedString, end - offset)
                } else {
                    outputPointer = writeGenericEscape(ch, outputPointer)
                }

                continue
            }

            if (ch > maxUnescaped) {
                outputPointer = writeGenericEscape(ch, outputPointer)
                continue
            }

            val escape = customEscapes.getEscapeSequence(ch)

            if (escape != null) {
                outputPointer = writeCustomEscape(outputBuffer, outputPointer, escape, end - offset)
                continue
            }

            if (ch <= 0x7FF) {
                outputBuffer[outputPointer++] = (ch shr 6 or 0xC0).toByte()
                outputBuffer[outputPointer++] = (ch and 0x3F or 0x80).toByte()
            } else {
                outputPointer = outputMultibyteChar(ch, outputPointer)
            }
        }

        myOutputTail = outputPointer
    }

    /**
     * Same as `writeStringSegmentASCII`, but with additional escaping for high-range code points
     */
    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING", "DuplicatedCode")
    private fun writeCustomStringSegment(text: String, offset: Int, end: Int) {
        var offset = offset

        if (myOutputTail + 6 * (end - offset) > myOutputEnd) {
            flushBuffer()
        }

        var outputPointer = myOutputTail
        val outputBuffer = myOutputBuffer
        val escapeCodes = myOutputEscapes
        val maxUnescaped = if (highestNonEscapedChar > 0) highestNonEscapedChar else 0xFFFF
        val customEscapes = myCharacterEscapes!!

        while (offset < end) {
            val ch = text[offset++].code

            if (ch <= 0x7F) {
                if (escapeCodes[ch] == 0) {
                    outputBuffer[outputPointer++] = ch.toByte()
                    continue
                }

                val escape = escapeCodes[ch]

                if (escape > 0) {
                    outputBuffer[outputPointer++] = BYTE_BACKSLASH
                    outputBuffer[outputPointer++] = escape.toByte()
                } else if (escape == CharacterEscapes.ESCAPE_CUSTOM) {
                    val escapedString = customEscapes.getEscapeSequence(ch) ?: return reportError(
                            "Invalid custom escape definitions; custom escape not found for character code 0x${
                                ch.toString(16).uppercase()
                            }, although was supposed to have one")
                    outputPointer = writeCustomEscape(outputBuffer, outputPointer, escapedString, end - offset)
                } else {
                    outputPointer = writeGenericEscape(ch, outputPointer)
                }

                continue
            }

            if (ch > maxUnescaped) {
                outputPointer = writeGenericEscape(ch, outputPointer)
                continue
            }

            val escape = customEscapes.getEscapeSequence(ch)

            if (escape != null) {
                outputPointer = writeCustomEscape(outputBuffer, outputPointer, escape, end - offset)
                continue
            }

            if (ch <= 0x7FF) {
                outputBuffer[outputPointer++] = (ch shr 6 or 0xC0).toByte()
                outputBuffer[outputPointer++] = (ch and 0x3F or 0x80).toByte()
            } else {
                outputPointer = outputMultibyteChar(ch, outputPointer)
            }
        }

        myOutputTail = outputPointer
    }

    @Throws(CirJacksonException::class)
    private fun writeCustomEscape(outputBuffer: ByteArray, outputPointer: Int, escape: SerializableString,
            remainingChars: Int): Int {
        val raw = escape.asUnquotedUTF8()
        val length = raw.size

        if (length > 6) {
            handleLongCustomEscape(outputBuffer, outputPointer, myOutputEnd, raw, remainingChars)
        }

        raw.copyInto(outputBuffer, outputPointer, 0, length)
        return outputPointer + length
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun handleLongCustomEscape(outputBuffer: ByteArray, outputPointer: Int, outputEnd: Int, raw: ByteArray,
            remainingChars: Int): Int {
        var outputPointer = outputPointer
        val length = raw.size

        if (outputPointer + length > outputEnd) {
            myOutputTail = outputPointer
            flushBuffer()
            outputPointer = myOutputTail

            if (length > outputBuffer.size) {
                try {
                    myOutputStream!!.write(raw, 0, length)
                } catch (e: IOException) {
                    throw wrapIOFailure(e)
                }

                return outputPointer
            }
        }

        raw.copyInto(outputBuffer, outputPointer, 0, length)
        outputPointer += length

        return if (outputPointer + 6 * remainingChars > outputEnd) {
            myOutputTail = outputPointer
            flushBuffer()
            myOutputTail
        } else {
            outputPointer
        }
    }

    /*
     *******************************************************************************************************************
     * Internal methods, low-level writing; "raw UTF-8" segments
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun writeUTF8Segments(utf8: ByteArray, offset: Int, totalLength: Int) {
        var offset = offset
        var totalLength = totalLength

        do {
            val length = min(myOutputMaxContiguous, totalLength)
            writeUTF8Segment(utf8, offset, length)
            offset += length
            totalLength -= length
        } while (totalLength > 0)
    }

    @Throws(CirJacksonException::class)
    private fun writeUTF8Segment(utf8: ByteArray, offset: Int, length: Int) {
        val escapeCodes = myOutputEscapes
        var pointer = offset
        val end = offset + length

        while (pointer < end) {
            val ch = utf8[pointer++].toInt()

            if (ch >= 0 && escapeCodes[ch] != 0) {
                writeUTF8Segment2(utf8, offset, length)
                return
            }
        }

        if (myOutputTail + length > myOutputEnd) {
            flushBuffer()
        }

        utf8.copyInto(myOutputBuffer, myOutputTail, offset, offset + length)
        myOutputTail += length
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun writeUTF8Segment2(utf8: ByteArray, offset: Int, length: Int) {
        var offset = offset
        var length = length
        var outputPointer = myOutputTail

        if (outputPointer + length * 6 > myOutputEnd) {
            flushBuffer()
            outputPointer = myOutputTail
        }

        val outputBuffer = myOutputBuffer
        val escapeCodes = myOutputEscapes
        length += offset

        while (offset < length) {
            val ch = utf8[offset++].toInt()

            if (ch < 0 || escapeCodes[ch] == 0) {
                outputBuffer[outputPointer++] = ch.toByte()
                continue
            }

            val escape = escapeCodes[ch]

            if (escape > 0) {
                outputBuffer[outputPointer++] = BYTE_BACKSLASH
                outputBuffer[outputPointer++] = escape.toByte()
            } else {
                outputPointer = writeGenericEscape(ch, outputPointer)
            }
        }

        myOutputTail = outputPointer
    }

    /*
     *******************************************************************************************************************
     * Internal methods, low-level writing; binary
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    protected fun writeBinaryInternal(base64Variant: Base64Variant, input: ByteArray, inputPointer: Int,
            inputEnd: Int) {
        var inputPointer = inputPointer
        val safeInputEnd = inputEnd - 3
        val safeOutputEnd = myOutputEnd - 6
        var chunksBeforeLF = base64Variant.maxLineLength shr 2

        while (inputPointer <= safeInputEnd) {
            if (myOutputTail > safeOutputEnd) {
                flushBuffer()
            }

            var b24 = input[inputPointer++].toInt() shl 8
            b24 = input[inputPointer++].toInt() and 0xFF or b24
            b24 = b24 shl 8 or (input[inputPointer++].toInt() and 0xFF)
            myOutputTail = base64Variant.encodeBase64Chunk(b24, myOutputBuffer, myOutputTail)

            if (--chunksBeforeLF <= 0) {
                myOutputBuffer[myOutputTail++] = BYTE_BACKSLASH
                myOutputBuffer[myOutputTail++] = BYTE_N_LOWERCASE
                chunksBeforeLF = base64Variant.maxLineLength shr 2
            }
        }

        val inputLeft = inputEnd - inputPointer

        if (inputLeft > 0) {
            if (myOutputTail > safeOutputEnd) {
                flushBuffer()
            }

            var b24 = input[inputPointer++].toInt() shl 16

            if (inputLeft == 2) {
                b24 = input[inputPointer].toInt() and 0xFF shl 8 or b24
            }

            myOutputTail = base64Variant.encodeBase64Partial(b24, inputLeft, myOutputBuffer, myOutputTail)
        }
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    protected fun writeBinary(variant: Base64Variant, data: InputStream, readBuffer: ByteArray, bytesLeft: Int): Int {
        var bytesLeft = bytesLeft
        var inputPointer = 0
        var inputEnd = 0
        var lastFullOffset = -3

        val safeOutputEnd = myOutputEnd - 6
        var chunksBeforeLF = variant.maxLineLength shr 2

        while (bytesLeft > 2) {
            if (inputPointer > lastFullOffset) {
                inputEnd = readMore(data, readBuffer, inputPointer, inputEnd, bytesLeft)
                inputPointer = 0

                if (inputEnd < 3) {
                    break
                }

                lastFullOffset = inputEnd - 3
            }

            if (myOutputTail > safeOutputEnd) {
                flushBuffer()
            }

            var b24 = readBuffer[inputPointer++].toInt() shl 8
            b24 = readBuffer[inputPointer++].toInt() and 0xFF or b24
            b24 = b24 shl 8 or (readBuffer[inputPointer++].toInt() and 0xFF)
            bytesLeft -= 3
            myOutputTail = variant.encodeBase64Chunk(b24, myOutputBuffer, myOutputTail)

            if (--chunksBeforeLF <= 0) {
                myOutputBuffer[myOutputTail++] = BYTE_BACKSLASH
                myOutputBuffer[myOutputTail++] = BYTE_N_LOWERCASE
                chunksBeforeLF = variant.maxLineLength shr 2
            }
        }

        if (bytesLeft > 0) {
            inputEnd = readMore(data, readBuffer, inputPointer, inputEnd, bytesLeft)
            inputPointer = 0

            if (inputEnd > 0) {
                if (myOutputTail > safeOutputEnd) {
                    flushBuffer()
                }

                var b24 = readBuffer[inputPointer++].toInt() shl 16

                val amount = if (inputPointer < inputEnd) {
                    b24 = readBuffer[inputPointer].toInt() and 0xFF shl 8 or b24
                    2
                } else {
                    1
                }

                myOutputTail = variant.encodeBase64Partial(b24, amount, myOutputBuffer, myOutputTail)
                bytesLeft -= amount
            }
        }

        return bytesLeft
    }

    @Throws(CirJacksonException::class)
    protected fun writeBinary(variant: Base64Variant, data: InputStream, readBuffer: ByteArray): Int {
        var inputPointer = 0
        var inputEnd = 0
        var lastFullOffset = -3
        var bytesDone = 0

        val safeOutputEnd = myOutputEnd - 6
        var chunksBeforeLF = variant.maxLineLength shr 2

        while (true) {
            if (inputPointer > lastFullOffset) {
                inputEnd = readMore(data, readBuffer, inputPointer, inputEnd, readBuffer.size)
                inputPointer = 0

                if (inputEnd < 3) {
                    break
                }

                lastFullOffset = inputEnd - 3
            }

            if (myOutputTail > safeOutputEnd) {
                flushBuffer()
            }

            var b24 = readBuffer[inputPointer++].toInt() shl 8
            b24 = readBuffer[inputPointer++].toInt() and 0xFF or b24
            b24 = b24 shl 8 or (readBuffer[inputPointer++].toInt() and 0xFF)
            bytesDone += 3
            myOutputTail = variant.encodeBase64Chunk(b24, myOutputBuffer, myOutputTail)

            if (--chunksBeforeLF <= 0) {
                myOutputBuffer[myOutputTail++] = BYTE_BACKSLASH
                myOutputBuffer[myOutputTail++] = BYTE_N_LOWERCASE
                chunksBeforeLF = variant.maxLineLength shr 2
            }
        }

        if (inputEnd > 0) {
            if (myOutputTail > safeOutputEnd) {
                flushBuffer()
            }

            var b24 = readBuffer[inputPointer++].toInt() shl 16

            val amount = if (inputPointer < inputEnd) {
                b24 = readBuffer[inputPointer].toInt() and 0xFF shl 8 or b24
                2
            } else {
                1
            }

            myOutputTail = variant.encodeBase64Partial(b24, amount, myOutputBuffer, myOutputTail)
            bytesDone += amount
        }

        return bytesDone
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun readMore(input: InputStream, readBuffer: ByteArray, inputPointer: Int, inputEnd: Int,
            maxRead: Int): Int {
        var inputPointer = inputPointer
        var inputEnd = inputEnd
        var maxRead = maxRead
        var i = 0

        while (inputPointer < inputEnd) {
            readBuffer[i++] = readBuffer[inputPointer++]
        }

        inputEnd = i
        maxRead = min(maxRead, readBuffer.size)

        do {
            val length = maxRead - inputEnd

            if (length == 0) {
                break
            }

            val count = try {
                input.read(readBuffer, inputEnd, length)
            } catch (e: IOException) {
                throw wrapIOFailure(e)
            }

            if (count < 0) {
                return inputEnd
            }

            inputEnd += count
        } while (inputEnd < 3)

        return inputEnd
    }

    /*
     *******************************************************************************************************************
     * Internal methods, low-level writing; other
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun writeNullInternal() {
        if (myOutputTail + 5 >= myOutputEnd) {
            flushBuffer()
        }

        NULL_BYTES.copyInto(myOutputBuffer, myOutputTail, 0, 4)
        myOutputTail += 4
    }

    /*
     *******************************************************************************************************************
     * Internal methods, character escapes/encoding
     *******************************************************************************************************************
     */

    /**
     * Method called to output a character that is beyond range of 1- and 2-byte UTF-8 encodings, when outputting "raw"
     * text (meaning it is not to be escaped or quoted)
     */
    @Throws(CirJacksonException::class)
    private fun outputRawMultibyteChar(ch: Int, buffer: CharArray?, inputOffset: Int, inputEnd: Int): Int {
        if (ch in UTF8Writer.SURR1_FIRST..UTF8Writer.SURR2_LAST) {
            return if (inputOffset < inputEnd && buffer != null) {
                outputSurrogates(ch, buffer[inputOffset].code)
                inputOffset + 1
            } else {
                reportError("Split surrogate on writeRaw() input (last character): first character 0x${
                    ch.toString(16).padStart(4, '0')
                }")
            }
        }

        val byteBuffer = myOutputBuffer
        byteBuffer[myOutputTail++] = (ch shr 12 or 0xE0).toByte()
        byteBuffer[myOutputTail++] = (ch shr 6 and 0x3F or 0x80).toByte()
        byteBuffer[myOutputTail++] = (ch and 0x3F or 0x80).toByte()
        return inputOffset
    }

    @Throws(CirJacksonException::class)
    protected fun outputSurrogates(surrogate1: Int, surrogate2: Int) {
        val ch = decodeSurrogate(surrogate1, surrogate2)

        if (myOutputTail + 4 >= myOutputEnd) {
            flushBuffer()
        }

        val buffer = myOutputBuffer
        buffer[myOutputTail++] = (ch shr 18 or 0xF0).toByte()
        buffer[myOutputTail++] = (ch shr 12 and 0x3F or 0x80).toByte()
        buffer[myOutputTail++] = (ch shr 6 and 0x3F or 0x80).toByte()
        buffer[myOutputTail++] = (ch and 0x3F or 0x80).toByte()
    }

    /**
     * Method called to output a character that is beyond range of 1- and 2-byte UTF-8 encodings, when outputting text
     * (meaning it is not to be escaped or quoted)
     *
     * @param ch Multibyte to append
     *
     * @param outputPointer Position within output buffer to append multibyte in
     *
     * @return New output position after appending
     *
     * @throws CirJacksonException Encoding or write I/O exception
     */
    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun outputMultibyteChar(ch: Int, outputPointer: Int): Int {
        var outputPointer = outputPointer
        val hexBytes = hexBytes
        val buffer = myOutputBuffer

        if (ch in UTF8Writer.SURR1_FIRST..UTF8Writer.SURR2_LAST) {
            buffer[outputPointer++] = BYTE_BACKSLASH
            buffer[outputPointer++] = BYTE_U_LOWERCASE

            buffer[outputPointer++] = hexBytes[ch shr 12 and 0xF]
            buffer[outputPointer++] = hexBytes[ch shr 8 and 0xF]
            buffer[outputPointer++] = hexBytes[ch shr 4 and 0xF]
            buffer[outputPointer++] = hexBytes[ch and 0xF]
        } else {
            buffer[outputPointer++] = (ch shr 12 or 0xE0).toByte()
            buffer[outputPointer++] = (ch shr 6 and 0x3F or 0x80).toByte()
            buffer[outputPointer++] = (ch and 0x3F or 0x80).toByte()
        }

        return outputPointer
    }

    /**
     * Method called to write a generic Unicode escape for given character.
     *
     * @param charToEscape Character to escape using escape sequence (\\uXXXX)
     */
    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun writeGenericEscape(charToEscape: Int, outputPointer: Int): Int {
        var charToEscape = charToEscape
        var outputPointer = outputPointer
        val hexBytes = hexBytes
        val buffer = myOutputBuffer
        buffer[outputPointer++] = BYTE_BACKSLASH
        buffer[outputPointer++] = BYTE_U_LOWERCASE

        if (charToEscape > 0xFF) {
            val high = charToEscape shr 8 and 0xFF
            buffer[outputPointer++] = hexBytes[high shr 4]
            buffer[outputPointer++] = hexBytes[high and 0xF]
            charToEscape = charToEscape and 0xFF
        } else {
            buffer[outputPointer++] = BYTE_0
            buffer[outputPointer++] = BYTE_0
        }

        buffer[outputPointer++] = hexBytes[charToEscape shr 4]
        buffer[outputPointer++] = hexBytes[charToEscape and 0xF]
        return outputPointer
    }

    @Throws(CirJacksonException::class)
    protected open fun flushBuffer() {
        val length = myOutputTail

        if (length <= 0) {
            return
        }

        myOutputTail = 0

        try {
            myOutputStream!!.write(myOutputBuffer, 0, length)
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }
    }

    companion object {

        private const val TYPE_MESSAGE_START_ARRAY = "start an array"

        private const val TYPE_MESSAGE_START_OBJECT = "start an object"

        private const val BYTE_U_LOWERCASE = 'u'.code.toByte()

        private const val BYTE_N_LOWERCASE = 'n'.code.toByte()

        private const val BYTE_0 = '0'.code.toByte()

        private const val BYTE_L_BRACKET = '['.code.toByte()

        private const val BYTE_R_BRACKET = ']'.code.toByte()

        private const val BYTE_L_CURLY = '{'.code.toByte()

        private const val BYTE_R_CURLY = '}'.code.toByte()

        private const val BYTE_BACKSLASH = '\\'.code.toByte()

        private const val BYTE_COMMA = ','.code.toByte()

        private const val BYTE_COLON = ':'.code.toByte()

        private const val MAX_BYTES_TO_BUFFER = 512

        private val HEX_BYTES_UPPER = CharTypes.copyHexBytes(true)

        private val HEX_BYTES_LOWER = CharTypes.copyHexBytes(false)

        private val NULL_BYTES = byteArrayOf('n'.code.toByte(), 'u'.code.toByte(), 'l'.code.toByte(), 'l'.code.toByte())

        private val TRUE_BYTES = byteArrayOf('t'.code.toByte(), 'r'.code.toByte(), 'u'.code.toByte(), 'e'.code.toByte())

        private val FALSE_BYTES =
                byteArrayOf('f'.code.toByte(), 'a'.code.toByte(), 'l'.code.toByte(), 's'.code.toByte(),
                        'e'.code.toByte())

        private val FLUSHED_OUTPUT_BUFFER = ByteArray(0)

    }

}