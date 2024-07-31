package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.extensions.isNotFinite
import org.cirjson.cirjackson.core.io.CharTypes
import org.cirjson.cirjackson.core.io.CharacterEscapes
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.io.NumberOutput
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

    protected val myIDHolder = IDHolder()

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
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeName(name: SerializableString): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun writeUnquotedName(name: SerializableString) {
        TODO("Not yet implemented")
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
        if (!streamWriteContext.isInArray) {
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
        TODO("Not yet implemented")
    }

    /**
     * Specialized version of `writeName`, off-lined to keep the "fast path" as simple (and hopefully fast) as possible.
     */
    @Throws(CirJacksonException::class)
    protected fun writePrettyPrinterName(name: SerializableString) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Output method implementations, textual
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeString(value: String?): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeString(reader: Reader?, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeString(buffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeString(value: SerializableString): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeRawUTF8String(buffer: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeUTF8String(buffer: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Output method implementations, unprocessed ("raw")
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeRaw(text: String): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(text: String, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(raw: SerializableString): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeRawValue(raw: SerializableString): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(buffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(char: Char): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    /**
     * Helper method called when it is possible that output of raw section to output may cross buffer boundary
     */
    @Throws(CirJacksonException::class)
    private fun writeSegmentedRaw(buffer: CharArray, offset: Int, length: Int) {
        TODO("Not yet implemented")
    }

    /**
     * Helper method that is called for segmented write of raw content when explicitly outputting a segment of longer thing. Caller has to take care of ensuring there's no split surrogate pair at the end (that is, last char can not be first part of a surrogate char pair).
     */
    @Throws(CirJacksonException::class)
    private fun writeRawSegment(buffer: CharArray, offset: Int, end: Int) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Output method implementations, base64-encoded binary
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeBinary(variant: Base64Variant, data: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeBinary(variant: Base64Variant, data: InputStream, dataLength: Int): Int {
        TODO("Not yet implemented")
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

        if (buffer !== FLUSHED_OUTPUT_BUFFER) {
            myOutputBuffer = FLUSHED_OUTPUT_BUFFER
            ioContext.releaseWriteEncodingBuffer(buffer)
        }

        val charBuffer = myCharBuffer

        if (charBuffer != null) {
            myCharBuffer = null
            ioContext.releaseNameCopyBuffer(charBuffer)
        }
    }

    /*
     *******************************************************************************************************************
     * Internal methods, low-level writing; raw bytes
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun writeBytes(buffer: ByteArray) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun writeBytes(buffer: ByteArray, offset: Int, length: Int) {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    /**
     * Method called when character sequence to write is long enough that its maximum encoded and escaped form is not
     * guaranteed to fit in the output buffer. If so, we will need to choose smaller output chunks to write at a time.
     */
    @Throws(CirJacksonException::class)
    private fun writeStringSegments(buffer: CharArray, offset: Int, totalLength: Int) {
        TODO("Not yet implemented")
    }

    /**
     * Method called when character sequence to write is long enough that its maximum encoded and escaped form is not
     * guaranteed to fit in the output buffer. If so, we will need to choose smaller output chunks to write at a time.
     */
    @Throws(CirJacksonException::class)
    private fun writeStringSegments(text: String, offset: Int, totalLength: Int) {
        TODO("Not yet implemented")
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
    private fun writeStringSegment(buffer: CharArray, offset: Int, length: Int) {
        TODO("Not yet implemented")
    }

    /**
     * This method called when the string content is already in a string, and its maximum total encoded and escaped
     * length can not exceed size of the output buffer. Caller must ensure that there is enough space in output buffer,
     * assuming case of all non-escaped ASCII characters, as well as potentially enough space for other cases (but not
     * necessarily flushed)
     */
    @Throws(CirJacksonException::class)
    private fun writeStringSegment(text: String, offset: Int, length: Int) {
        TODO("Not yet implemented")
    }

    /**
     * Secondary method called when content contains characters to escape, and/or multibyte UTF-8 characters.
     */
    @Throws(CirJacksonException::class)
    private fun writeStringSegment(buffer: CharArray, offset: Int, length: Int, end: Int) {
        TODO("Not yet implemented")
    }

    /**
     * Secondary method called when content contains characters to escape, and/or multibyte UTF-8 characters.
     */
    @Throws(CirJacksonException::class)
    private fun writeStringSegment(text: String, offset: Int, length: Int, end: Int) {
        TODO("Not yet implemented")
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
    private fun writeStringSegmentASCII(buffer: CharArray, offset: Int, end: Int) {
        TODO("Not yet implemented")
    }

    /**
     * Same as `writeStringSegment`, but with additional escaping for high-range code points
     */
    @Throws(CirJacksonException::class)
    private fun writeStringSegmentASCII(text: String, offset: Int, end: Int) {
        TODO("Not yet implemented")
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
    private fun writeCustomStringSegment(buffer: CharArray, offset: Int, end: Int) {
        TODO("Not yet implemented")
    }

    /**
     * Same as `writeStringSegmentASCII`, but with additional escaping for high-range code points
     */
    @Throws(CirJacksonException::class)
    private fun writeCustomStringSegment(text: String, offset: Int, end: Int) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun writeCustomEscape(outputBuffer: ByteArray, outputPointer: Int, escape: SerializableString,
            remainingChars: Int): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun handleLongCustomEscape(outputBuffer: ByteArray, outputPointer: Int, outputEnd: Int, raw: ByteArray,
            remainingChars: Int): Int {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods, low-level writing; "raw UTF-8" segments
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun writeUTF8Segments(utf8: ByteArray, offset: Int, totalLength: Int) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun writeUTF8Segment(utf8: ByteArray, offset: Int, length: Int) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun writeUTF8Segment2(utf8: ByteArray, offset: Int, length: Int) {
        TODO("Not yet implemented")
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

        if (inputPointer < inputEnd) {
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
    private fun outputRawMultibyteChar(ch: Int, buffer: CharArray, inputOffset: Int, inputEnd: Int): Int {
        TODO("Not yet implemented")
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
    private fun outputMultibyteChar(ch: Int, outputPointer: Int): Int {
        TODO("Not yet implemented")
    }

    /**
     * Method called to write a generic Unicode escape for given character.
     *
     * @param charToEscape Character to escape using escape sequence (\\uXXXX)
     */
    @Throws(CirJacksonException::class)
    private fun writeGenericEscape(charToEscape: Int, outputPointer: Int): Int {
        TODO("Not yet implemented")
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