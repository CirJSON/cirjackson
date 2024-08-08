package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.extensions.isNotFinite
import org.cirjson.cirjackson.core.io.CharTypes
import org.cirjson.cirjackson.core.io.CharacterEscapes
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.io.NumberOutput
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.io.Writer
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.max
import kotlin.math.min

/**
 * [CirJsonGenerator] that outputs CirJSON content using a [Writer] which handles character encoding.
 *
 * @property myQuoteChar Character used for quoting CirJSON Object property names and String values.
 */
open class WriterBasedCirJsonGenerator(objectWriteContext: ObjectWriteContext, ioContext: IOContext,
        streamWriteFeatures: Int, formatWriteFeatures: Int, protected val myWriter: Writer?,
        rootValueSeparator: SerializableString?, prettyPrinter: PrettyPrinter?, characterEscapes: CharacterEscapes?,
        maxNonEscaped: Int, protected val myQuoteChar: Char) :
        CirJsonGeneratorBase(objectWriteContext, ioContext, streamWriteFeatures, formatWriteFeatures,
                rootValueSeparator, prettyPrinter, characterEscapes, maxNonEscaped) {

    /**
     * Intermediate buffer in which contents are buffered before being written using [myWriter].
     */
    protected var myOutputBuffer: CharArray = ioContext.allocConcatBuffer()

    private val hexChars = if (myConfigurationWriteHexUppercase) HEX_CHARS_UPPER else HEX_CHARS_LOWER

    /**
     * Pointer to the first buffered character to output
     */
    protected var myOutputHead = 0

    /**
     * Pointer to the position right beyond the last character to output (end marker; may point to position right beyond
     * the end of the buffer)
     */
    protected var myOutputTail = 0

    /**
     * End marker of the output buffer; one past the last valid position within the buffer.
     */
    protected var myOutputEnd = myOutputBuffer.size

    /**
     * Short (14 char) temporary buffer allocated if needed, for constructing escape sequences
     */
    protected val myEntityBuffer by lazy {
        val buffer = CharArray(14)
        buffer[0] = '\\'
        buffer[2] = '\\'
        buffer[3] = 'u'
        buffer[4] = '0'
        buffer[5] = '0'
        buffer[8] = '\\'
        buffer[9] = 'u'
        buffer
    }

    /**
     * When custom escapes are used, this member variable is used internally to hold a reference to currently used
     * escape
     */
    protected var myCurrentEscape: SerializableString? = null

    protected var myCopyBuffer: CharArray? = null

    override var characterEscapes: CharacterEscapes?
        get() = super.characterEscapes
        set(value) {
            myCharacterEscapes = value
            myOutputEscapes = value?.escapeCodesForAscii ?: CharTypes.getSevenBitOutputEscapes(myQuoteChar.code,
                    CirJsonWriteFeature.ESCAPE_FORWARD_SLASHES.isEnabledIn(formatWriteFeatures))
        }

    protected val myIDHolder = IDHolder()

    init {
        @Suppress("LeakingThis")
        this.characterEscapes = characterEscapes
    }

    /*
     *******************************************************************************************************************
     * Overridden configuration, introspection methods
     *******************************************************************************************************************
     */

    override val streamWriteOutputTarget: Any?
        get() = myWriter

    override val streamWriteOutputBuffered: Int
        get() = max(myOutputTail - myOutputHead, 0)

    /*
     *******************************************************************************************************************
     * Overridden methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeName(name: String): CirJsonGenerator {
        val status = streamWriteContext.writeName(name)

        if (status == CirJsonWriteContext.STATUS_EXPECT_VALUE) {
            return reportError("Cannot write a property name, expecting a value")
        }

        writeName(name, status == CirJsonWriteContext.STATUS_OK_AFTER_COMMA)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeName(name: SerializableString): CirJsonGenerator {
        val status = streamWriteContext.writeName(name.value)

        if (status == CirJsonWriteContext.STATUS_EXPECT_VALUE) {
            return reportError("Cannot write a property name, expecting a value")
        }

        writeName(name, status == CirJsonWriteContext.STATUS_OK_AFTER_COMMA)
        return this
    }

    @Throws(CirJacksonException::class)
    protected fun writeName(name: String, commaBefore: Boolean) {
        if (myConfigurationPrettyPrinter != null) {
            writePrettyPrinterName(name, commaBefore)
            return
        }

        if (myOutputTail + 1 >= myOutputEnd) {
            flushBuffer()
        }

        if (commaBefore) {
            myOutputBuffer[myOutputTail++] = ','
        }

        if (myConfigurationUnquoteNames) {
            writeStringInternal(name)
            return
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        writeStringInternal(name)

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
    }

    @Throws(CirJacksonException::class)
    protected fun writeName(name: SerializableString, commaBefore: Boolean) {
        if (myConfigurationPrettyPrinter != null) {
            writePrettyPrinterName(name, commaBefore)
            return
        }

        if (myOutputTail + 1 >= myOutputEnd) {
            flushBuffer()
        }

        if (commaBefore) {
            myOutputBuffer[myOutputTail++] = ','
        }

        if (myConfigurationUnquoteNames) {
            val ch = name.asQuotedChars()
            writeRaw(ch, 0, ch.size)
            return
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar

        val length = name.appendQuoted(myOutputBuffer, myOutputTail)

        if (length < 0) {
            writeNameTail(name)
            return
        }

        myOutputTail += length

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
    }

    @Throws(CirJacksonException::class)
    private fun writeNameTail(name: SerializableString) {
        val quoted = name.asQuotedChars()
        writeRaw(quoted, 0, quoted.size)

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
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

            myOutputBuffer[myOutputTail++] = '['
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

            myOutputBuffer[myOutputTail++] = '['
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

            myOutputBuffer[myOutputTail++] = '['
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

            myOutputBuffer[myOutputTail++] = ']'
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

            myOutputBuffer[myOutputTail++] = '{'
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

            myOutputBuffer[myOutputTail++] = '{'
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

            myOutputBuffer[myOutputTail++] = '{'
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

            myOutputBuffer[myOutputTail++] = '}'
        }

        streamWriteContext = streamWriteContext.clearAndGetParent()!!

        return this
    }

    /**
     * Specialized version of `writeName`, off-lined to keep the "fast path" as simple (and hopefully fast) as possible.
     */
    @Throws(CirJacksonException::class)
    protected fun writePrettyPrinterName(name: String, commaBefore: Boolean) {
        if (commaBefore) {
            myConfigurationPrettyPrinter!!.writeObjectEntrySeparator(this)
        } else {
            myConfigurationPrettyPrinter!!.beforeObjectEntries(this)
        }

        if (myConfigurationUnquoteNames) {
            writeStringInternal(name)
            return
        }

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        writeStringInternal(name)

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
    }

    /**
     * Specialized version of `writeName`, off-lined to keep the "fast path" as simple (and hopefully fast) as possible.
     */
    @Throws(CirJacksonException::class)
    protected fun writePrettyPrinterName(name: SerializableString, commaBefore: Boolean) {
        if (commaBefore) {
            myConfigurationPrettyPrinter!!.writeObjectEntrySeparator(this)
        } else {
            myConfigurationPrettyPrinter!!.beforeObjectEntries(this)
        }

        val quoted = name.asQuotedChars()

        if (myConfigurationUnquoteNames) {
            writeRaw(quoted, 0, quoted.size)
            return
        }

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        writeRaw(quoted, 0, quoted.size)

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
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

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        writeStringInternal(value)

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

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        val buffer = allocateCopyBuffer()

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

            writeStringInternal(buffer, 0, read)
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
        writeStringInternal(buffer, offset, length)

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
        val length = value.appendQuoted(myOutputBuffer, myOutputTail)

        if (length < 0) {
            writeStringInternal(value)
            return this
        }

        myOutputTail += length

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
        return this
    }

    @Throws(CirJacksonException::class)
    private fun writeStringInternal(string: SerializableString) {
        val text = string.asQuotedChars()
        val length = text.size

        if (length < SHORT_WRITE) {
            val room = myOutputEnd - myOutputTail

            if (length > room) {
                flushBuffer()
            }

            text.copyInto(myOutputBuffer, myOutputTail, 0, length)
            myOutputTail += length
        } else {
            flushBuffer()

            try {
                myWriter!!.write(text, 0, length)
            } catch (e: IOException) {
                throw wrapIOFailure(e)
            }
        }

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = myQuoteChar
    }

    @Throws(CirJacksonException::class)
    override fun writeRawUTF8String(buffer: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    @Throws(CirJacksonException::class)
    override fun writeUTF8String(buffer: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    /*
     *******************************************************************************************************************
     * Output method implementations, unprocessed ("raw")
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeRaw(text: String): CirJsonGenerator {
        val length = text.length
        var room = myOutputEnd - myOutputTail

        if (room == 0) {
            flushBuffer()
            room = myOutputEnd - myOutputTail
        }

        if (room >= length) {
            text.toCharArray(myOutputBuffer, myOutputTail, 0, length)
            myOutputTail += length
        } else {
            writeRawLong(text)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(text: String, offset: Int, length: Int): CirJsonGenerator {
        checkRangeBoundsForString(text, offset, length)

        var room = myOutputEnd - myOutputTail

        if (room < length) {
            flushBuffer()
            room = myOutputEnd - myOutputTail
        }

        if (room >= length) {
            text.toCharArray(myOutputBuffer, myOutputTail, offset, offset + length)
            myOutputTail += length
        } else {
            writeRawLong(text.substring(offset, offset + length))
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(raw: SerializableString): CirJsonGenerator {
        val length = raw.appendUnquoted(myOutputBuffer, myOutputTail)

        if (length < 0) {
            writeRaw(raw.value)
            return this
        }

        myOutputTail += length
        return this
    }

    override fun writeRaw(buffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        checkRangeBoundsForCharArray(buffer, offset, length)

        if (length < SHORT_WRITE) {
            val room = myOutputEnd - myOutputTail

            if (length > room) {
                flushBuffer()
            }

            buffer.copyInto(myOutputBuffer, myOutputTail, offset, offset + length)
            myOutputTail += length
            return this
        }

        flushBuffer()

        try {
            myWriter!!.write(buffer, offset, length)
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(char: Char): CirJsonGenerator {
        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = char
        return this
    }

    @Throws(CirJacksonException::class)
    private fun writeRawLong(text: String) {
        val room = myOutputEnd - myOutputTail
        text.toCharArray(myOutputBuffer, myOutputTail, 0, room)
        myOutputTail += room
        var offset = room
        var length = text.length - room

        while (length > myOutputEnd) {
            val amount = myOutputEnd
            text.toCharArray(myOutputBuffer, 0, offset, offset + amount)
            myOutputHead = 0
            myOutputTail = amount
            flushBuffer()
            offset += amount
            length -= amount
        }

        text.toCharArray(myOutputBuffer, 0, offset, offset + length)
        myOutputHead = 0
        myOutputTail = length
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
        writeBinaryInternal(variant, data, offset, length)

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

                if (missing > 0) {
                    reportError<Unit>("Too few bytes available: missing $missing bytes (out of $dataLength)")
                }

                dataLength
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

        var pointer = myOutputTail
        val buffer = myOutputBuffer

        if (state) {
            buffer[pointer] = 't'
            buffer[++pointer] = 'r'
            buffer[++pointer] = 'u'
        } else {
            buffer[pointer] = 'f'
            buffer[++pointer] = 'a'
            buffer[++pointer] = 'l'
            buffer[++pointer] = 's'
        }

        buffer[++pointer] = 'e'
        myOutputTail = pointer + 1
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

        val c = when (status) {
            CirJsonWriteContext.STATUS_OK_AS_IS -> return

            CirJsonWriteContext.STATUS_OK_AFTER_COMMA -> ','

            CirJsonWriteContext.STATUS_OK_AFTER_COLON -> ':'

            CirJsonWriteContext.STATUS_OK_AFTER_SPACE -> {
                if (myRootValueSeparator != null) {
                    writeRaw(myRootValueSeparator.value)
                }

                return
            }

            CirJsonWriteContext.STATUS_EXPECT_NAME -> return reportCannotWriteValueExpectName(typeMessage)

            else -> return
        }

        if (myOutputTail >= myOutputEnd) {
            flushBuffer()
        }

        myOutputBuffer[myOutputTail++] = c
    }

    /*
     *******************************************************************************************************************
     * Low-level output handling
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun flush() {
        flushBuffer()

        if (myWriter == null || !isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)) {
            return
        }

        try {
            myWriter.flush()
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

        myOutputHead = 0
        myOutputTail = 0

        if (myWriter != null) {
            try {
                if (ioContext.isResourceManaged || isEnabled(StreamWriteFeature.AUTO_CLOSE_TARGET)) {
                    myWriter.close()
                } else if (isEnabled(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)) {
                    myWriter.flush()
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
        var buffer: CharArray? = myOutputBuffer

        if (buffer !== FLUSHED_OUTPUT_BUFFER) {
            myOutputBuffer = FLUSHED_OUTPUT_BUFFER
            ioContext.releaseConcatBuffer(buffer)
        }

        buffer = myCopyBuffer

        if (buffer != null) {
            myCopyBuffer = null
            ioContext.releaseNameCopyBuffer(buffer)
        }
    }

    /*
     *******************************************************************************************************************
     * Internal methods, low-level writing; text, default
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun writeStringInternal(text: String) {
        val length = text.length

        if (length > myOutputEnd) {
            writeLongString(text)
            return
        }

        if (myOutputTail + length >= myOutputEnd) {
            flushBuffer()
        }

        text.toCharArray(myOutputBuffer, myOutputTail, 0, length)

        if (myCharacterEscapes != null) {
            writeStringCustom(length)
        } else if (highestNonEscapedChar != 0) {
            writeStringASCII(length, highestNonEscapedChar)
        } else {
            writeString(length)
        }
    }

    @Throws(CirJacksonException::class)
    private fun writeString(length: Int) {
        val end = myOutputTail + length
        val escapeCodes = myOutputEscapes
        val escapeLength = escapeCodes.size

        outputLoop@ while (myOutputTail < end) {
            while (true) {
                val c = myOutputBuffer[myOutputTail].code

                if (c < escapeLength && escapeCodes[c] != 0) {
                    break
                }

                if (++myOutputTail >= end) {
                    break@outputLoop
                }
            }

            val flushLength = myOutputTail - myOutputHead

            if (flushLength > 0) {
                try {
                    myWriter!!.write(myOutputBuffer, myOutputHead, flushLength)
                } catch (e: IOException) {
                    throw wrapIOFailure(e)
                }
            }

            val c = myOutputBuffer[myOutputTail++]
            prependOrWriteCharacterEscape(c, escapeCodes[c.code])
        }
    }

    /**
     * Method called to write "long strings", strings whose length exceeds output buffer length.
     */
    @Throws(CirJacksonException::class)
    private fun writeLongString(text: String) {
        flushBuffer()

        val textLength = text.length
        var offset = 0

        do {
            val max = myOutputEnd
            val segmentLength = if (offset + max > textLength) textLength - offset else max
            text.toCharArray(myOutputBuffer, 0, offset, offset + segmentLength)

            if (myCharacterEscapes != null) {
                writeSegmentCustom(segmentLength)
            } else if (highestNonEscapedChar != 0) {
                writeSegmentASCII(segmentLength, highestNonEscapedChar)
            } else {
                writeSegment(segmentLength)
            }

            offset += segmentLength
        } while (offset < textLength)
    }

    /**
     * Method called to output textual context which has been copied to the output buffer prior to call. If any escaping
     * is needed, it will also be handled by the method.
     *
     * Note: when called, textual content to write is within output buffer, right after buffered content (if any).
     * That's why only length of that text is passed, as buffer and offset are implied.
     */
    @Throws(CirJacksonException::class)
    private fun writeSegment(end: Int) {
        val escapeCodes = myOutputEscapes
        val escapeLength = escapeCodes.size

        var pointer = 0
        var start = 0

        while (pointer < end) {
            var c: Char

            while (true) {
                c = myOutputBuffer[pointer]

                if (c.code < escapeLength && escapeCodes[c.code] != 0) {
                    break
                }

                if (++pointer >= end) {
                    break
                }
            }

            val flushLength = pointer - start

            if (flushLength > 0) {
                try {
                    myWriter!!.write(myOutputBuffer, start, flushLength)
                } catch (e: IOException) {
                    throw wrapIOFailure(e)
                }

                if (pointer >= end) {
                    break
                }
            }

            ++pointer
            start = prependOrWriteCharacterEscape(myOutputBuffer, pointer, end, c, escapeCodes[c.code])
        }
    }

    /**
     * This method called when the string content is already in a char buffer, and need not be copied for processing.
     */
    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun writeStringInternal(text: CharArray, offset: Int, length: Int) {
        var offset = offset
        var length = length

        if (myCharacterEscapes != null) {
            writeStringCustom(text, offset, length)
            return
        } else if (highestNonEscapedChar != 0) {
            writeStringASCII(text, offset, length, highestNonEscapedChar)
            return
        }

        length += offset
        val escapeCodes = myOutputEscapes
        val escapeLength = escapeCodes.size

        while (offset < length) {
            val start = offset

            while (true) {
                val c = text[offset].code

                if (c < escapeLength && escapeCodes[c] != 0) {
                    break
                }

                if (++offset >= length) {
                    break
                }
            }

            val newAmount = offset - start

            if (newAmount < SHORT_WRITE) {
                if (myOutputTail + newAmount >= myOutputEnd) {
                    flushBuffer()
                }

                if (newAmount > 0) {
                    text.copyInto(myOutputBuffer, myOutputTail, start, start + newAmount)
                    myOutputTail += newAmount
                }
            } else {
                flushBuffer()

                try {
                    myWriter!!.write(text, start, newAmount)
                } catch (e: IOException) {
                    throw wrapIOFailure(e)
                }
            }

            if (offset >= length) {
                break
            }

            val c = text[offset++]
            appendCharacterEscape(c, escapeCodes[c.code])
        }
    }

    /*
     *******************************************************************************************************************
     * Internal methods, low-level writing; text segment with additional escaping (ASCII or such)
     *******************************************************************************************************************
     */

    /**
     * Same as "writeString(Int)", except needs additional escaping for subset of characters
     */
    @Throws(CirJacksonException::class)
    private fun writeStringASCII(length: Int, maxNonEscaped: Int) {
        val end = myOutputTail + length
        val escapeCodes = myOutputEscapes
        val escapeLimit = min(escapeCodes.size, maxNonEscaped + 1)
        var escapeCode: Int

        outputLoop@ while (myOutputTail < end) {
            var ch: Char

            while (true) {
                ch = myOutputBuffer[myOutputTail]

                if (ch.code < escapeLimit) {
                    escapeCode = escapeCodes[ch.code]

                    if (escapeCode != 0) {
                        break
                    }
                } else if (ch.code > maxNonEscaped) {
                    escapeCode = CharacterEscapes.ESCAPE_STANDARD
                    break
                }

                if (++myOutputTail >= end) {
                    break@outputLoop
                }
            }

            val flushLength = myOutputTail - myOutputHead

            if (flushLength > 0) {
                try {
                    myWriter!!.write(myOutputBuffer, myOutputHead, flushLength)
                } catch (e: IOException) {
                    throw wrapIOFailure(e)
                }
            }

            ++myOutputTail
            prependOrWriteCharacterEscape(ch, escapeCode)
        }
    }

    @Throws(CirJacksonException::class)
    private fun writeSegmentASCII(end: Int, maxNonEscaped: Int) {
        val escapeCodes = myOutputEscapes
        val escapeLimit = min(escapeCodes.size, maxNonEscaped + 1)

        var pointer = 0
        var escapeCode = 0
        var start = 0

        while (pointer < end) {
            var ch: Char
            var c: Int

            while (true) {
                ch = myOutputBuffer[pointer]
                c = ch.code

                if (c < escapeLimit) {
                    escapeCode = escapeCodes[c]

                    if (escapeCode != 0) {
                        break
                    }
                } else if (c > maxNonEscaped) {
                    escapeCode = CharacterEscapes.ESCAPE_STANDARD
                    break
                }

                if (++pointer >= end) {
                    break
                }
            }

            val flushLength = pointer - start

            if (flushLength > 0) {
                try {
                    myWriter!!.write(myOutputBuffer, start, flushLength)
                } catch (e: IOException) {
                    throw wrapIOFailure(e)
                }

                if (pointer >= end) {
                    break
                }
            }

            ++pointer
            start = prependOrWriteCharacterEscape(myOutputBuffer, pointer, end, ch, escapeCode)
        }
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun writeStringASCII(text: CharArray, offset: Int, length: Int, maxNonEscaped: Int) {
        var offset = offset
        val length = length + offset
        val escapeCodes = myOutputEscapes
        val escapeLimit = min(escapeCodes.size, maxNonEscaped + 1)
        var escapeCode = 0

        while (offset < length) {
            val start = offset
            var ch: Char
            var c: Int

            while (true) {
                ch = text[offset]
                c = ch.code

                if (c < escapeLimit) {
                    escapeCode = escapeCodes[c]

                    if (escapeCode != 0) {
                        break
                    }
                } else if (c > maxNonEscaped) {
                    escapeCode = CharacterEscapes.ESCAPE_STANDARD
                    break
                }

                if (++offset >= length) {
                    break
                }
            }

            val newAmount = offset - start

            if (newAmount < SHORT_WRITE) {
                if (myOutputTail + newAmount >= myOutputEnd) {
                    flushBuffer()
                }

                if (newAmount > 0) {
                    text.copyInto(myOutputBuffer, myOutputTail, start, start + newAmount)
                    myOutputTail += newAmount
                }
            } else {
                flushBuffer()

                try {
                    myWriter!!.write(text, start, newAmount)
                } catch (e: IOException) {
                    throw wrapIOFailure(e)
                }
            }

            if (offset >= length) {
                break
            }

            ++offset
            appendCharacterEscape(ch, escapeCode)
        }
    }

    /*
     *******************************************************************************************************************
     * Internal methods, low-level writing; text segment with custom escaping (possibly coupling with ASCII limits)
     *******************************************************************************************************************
     */

    /**
     * Same as "writeString(Int)", except needs additional escaping for subset of characters
     */
    @Throws(CirJacksonException::class)
    private fun writeStringCustom(length: Int) {
        val end = myOutputTail + length
        val escapeCodes = myOutputEscapes
        val maxNonEscaped = if (highestNonEscapedChar >= 1) highestNonEscapedChar else 0xFFFF
        val escapeLimit = min(escapeCodes.size, maxNonEscaped + 1)
        var escapeCode: Int
        val customEscapes = myCharacterEscapes!!

        outputLoop@ while (myOutputTail < end) {
            var ch: Char
            var c: Int

            while (true) {
                ch = myOutputBuffer[myOutputTail]
                c = ch.code

                if (c < escapeLimit) {
                    escapeCode = escapeCodes[c]

                    if (escapeCode != 0) {
                        break
                    }
                } else if (c > maxNonEscaped) {
                    escapeCode = CharacterEscapes.ESCAPE_STANDARD
                    break
                } else if (customEscapes.getEscapeSequence(c).also { myCurrentEscape = it } != null) {
                    escapeCode = CharacterEscapes.ESCAPE_CUSTOM
                    break
                }

                if (++myOutputTail >= end) {
                    break@outputLoop
                }
            }

            val flushLength = myOutputTail - myOutputHead

            if (flushLength > 0) {
                try {
                    myWriter!!.write(myOutputBuffer, myOutputHead, flushLength)
                } catch (e: IOException) {
                    throw wrapIOFailure(e)
                }
            }

            ++myOutputTail
            prependOrWriteCharacterEscape(ch, escapeCode)
        }
    }

    @Throws(CirJacksonException::class)
    private fun writeSegmentCustom(end: Int) {
        val escapeCodes = myOutputEscapes
        val maxNonEscaped = if (highestNonEscapedChar >= 1) highestNonEscapedChar else 0xFFFF
        val escapeLimit = min(escapeCodes.size, maxNonEscaped + 1)
        val customEscapes = myCharacterEscapes!!

        var pointer = 0
        var escapeCode = 0
        var start = 0

        while (pointer < end) {
            var ch: Char
            var c: Int

            while (true) {
                ch = myOutputBuffer[pointer]
                c = ch.code

                if (c < escapeLimit) {
                    escapeCode = escapeCodes[c]

                    if (escapeCode != 0) {
                        break
                    }
                } else if (c > maxNonEscaped) {
                    escapeCode = CharacterEscapes.ESCAPE_STANDARD
                    break
                } else if (customEscapes.getEscapeSequence(c).also { myCurrentEscape = it } != null) {
                    escapeCode = CharacterEscapes.ESCAPE_CUSTOM
                    break
                }

                if (++pointer >= end) {
                    break
                }
            }

            val flushLength = pointer - start

            if (flushLength > 0) {
                try {
                    myWriter!!.write(myOutputBuffer, start, flushLength)
                } catch (e: IOException) {
                    throw wrapIOFailure(e)
                }

                if (pointer >= end) {
                    break
                }
            }

            ++pointer
            start = prependOrWriteCharacterEscape(myOutputBuffer, pointer, end, ch, escapeCode)
        }
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun writeStringCustom(text: CharArray, offset: Int, length: Int) {
        var offset = offset
        val length = length + offset
        val escapeCodes = myOutputEscapes
        val maxNonEscaped = if (highestNonEscapedChar >= 1) highestNonEscapedChar else 0xFFFF
        val escapeLimit = min(escapeCodes.size, maxNonEscaped + 1)
        val customEscapes = myCharacterEscapes!!

        var escapeCode = 0

        while (offset < length) {
            val start = offset
            var ch: Char
            var c: Int

            while (true) {
                ch = text[offset]
                c = ch.code

                if (c < escapeLimit) {
                    escapeCode = escapeCodes[c]

                    if (escapeCode != 0) {
                        break
                    }
                } else if (c > maxNonEscaped) {
                    escapeCode = CharacterEscapes.ESCAPE_STANDARD
                    break
                } else if (customEscapes.getEscapeSequence(c).also { myCurrentEscape = it } != null) {
                    escapeCode = CharacterEscapes.ESCAPE_CUSTOM
                    break
                }

                if (++offset >= length) {
                    break
                }
            }

            val newAmount = offset - start

            if (newAmount < SHORT_WRITE) {
                if (myOutputTail + newAmount >= myOutputEnd) {
                    flushBuffer()
                }

                if (newAmount > 0) {
                    text.copyInto(myOutputBuffer, myOutputTail, start, start + newAmount)
                    myOutputTail += newAmount
                }
            } else {
                flushBuffer()

                try {
                    myWriter!!.write(text, start, newAmount)
                } catch (e: IOException) {
                    throw wrapIOFailure(e)
                }
            }

            if (offset >= length) {
                break
            }

            ++offset
            appendCharacterEscape(ch, escapeCode)
        }
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
                myOutputBuffer[myOutputTail++] = '\\'
                myOutputBuffer[myOutputTail++] = 'n'
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
                myOutputBuffer[myOutputTail++] = '\\'
                myOutputBuffer[myOutputTail++] = 'n'
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
                myOutputBuffer[myOutputTail++] = '\\'
                myOutputBuffer[myOutputTail++] = 'n'
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
        if (myOutputTail + 4 >= myOutputEnd) {
            flushBuffer()
        }

        var pointer = myOutputTail
        val buffer = myOutputBuffer
        buffer[pointer] = 'n'
        buffer[++pointer] = 'u'
        buffer[++pointer] = 'l'
        buffer[++pointer] = 'l'
        myOutputTail = pointer + 1
    }

    /*
     *******************************************************************************************************************
     * Internal methods, low-level writing; escapes
     *******************************************************************************************************************
     */

    /**
     * Method called to try to either prepend character escape at front of given buffer; or if not possible, to write it
     * out directly. Uses head and tail pointers (and updates as necessary)
     */
    @Throws(CirJacksonException::class)
    private fun prependOrWriteCharacterEscape(char: Char, escapeCode: Int) {
        var ch = char.code

        if (escapeCode >= 0) {
            if (myOutputTail >= 2) {
                var pointer = myOutputTail - 2
                myOutputHead = pointer
                myOutputBuffer[pointer++] = '\\'
                myOutputBuffer[pointer] = escapeCode.toChar()
                return
            }

            val buffer = myEntityBuffer
            myOutputHead = myOutputTail
            buffer[1] = escapeCode.toChar()

            try {
                myWriter!!.write(buffer, 0, 2)
            } catch (e: IOException) {
                throw wrapIOFailure(e)
            }

            return
        }

        if (escapeCode != CharacterEscapes.ESCAPE_CUSTOM) {
            val hexChars = hexChars

            if (myOutputTail >= 6) {
                val buffer = myOutputBuffer
                var pointer = myOutputTail - 6
                myOutputHead = pointer
                buffer[pointer] = '\\'
                buffer[++pointer] = 'u'

                if (ch > 0xFF) {
                    val high = ch shr 8 and 0xFF
                    buffer[++pointer] = hexChars[high shr 4]
                    buffer[++pointer] = hexChars[high and 0xF]
                    ch = ch and 0xFF
                } else {
                    buffer[++pointer] = '0'
                    buffer[++pointer] = '0'
                }

                buffer[++pointer] = hexChars[ch shr 4]
                buffer[++pointer] = hexChars[ch and 0xF]
                return
            }

            val buffer = myEntityBuffer
            myOutputHead = myOutputTail

            try {
                if (ch > 0xFF) {
                    val high = ch shr 8 and 0xFF
                    val low = ch and 0xFF
                    buffer[10] = hexChars[high shr 4]
                    buffer[11] = hexChars[high and 0xF]
                    buffer[12] = hexChars[low shr 4]
                    buffer[13] = hexChars[low and 0xF]
                    myWriter!!.write(buffer, 8, 6)
                } else {
                    buffer[6] = hexChars[ch shr 4]
                    buffer[7] = hexChars[ch and 0xF]
                    myWriter!!.write(buffer, 2, 6)
                }
            } catch (e: IOException) {
                throw wrapIOFailure(e)
            }

            return
        }

        val escape = myCurrentEscape?.value?.also { myCurrentEscape = null } ?: myCharacterEscapes!!.getEscapeSequence(
                ch)!!.value
        val length = escape.length

        if (myOutputTail >= length) {
            val pointer = myOutputTail - length
            myOutputHead = pointer
            escape.toCharArray(myOutputBuffer, pointer, 0, length)
            return
        }

        myOutputHead = myOutputTail

        try {
            myWriter!!.write(escape)
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }
    }

    /**
     * Method called to try to either prepend character escape at front of given buffer; or if not possible, to write it
     * out directly.
     *
     * @return Pointer to start of prepended entity (if prepended); or 'pointer' if not.
     */
    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun prependOrWriteCharacterEscape(buffer: CharArray, pointer: Int, end: Int, char: Char,
            escapeCode: Int): Int {
        var pointer = pointer
        var ch = char.code

        if (escapeCode >= 0) {
            if (pointer in 2..<end) {
                pointer -= 2
                buffer[pointer] = '\\'
                buffer[pointer + 1] = escapeCode.toChar()
            } else {
                val buffer = myEntityBuffer
                myOutputHead = myOutputTail
                buffer[1] = escapeCode.toChar()

                try {
                    myWriter!!.write(buffer, 0, 2)
                } catch (e: IOException) {
                    throw wrapIOFailure(e)
                }
            }

            return pointer
        }

        if (escapeCode != CharacterEscapes.ESCAPE_CUSTOM) {
            val hexChars = hexChars

            if (myOutputTail >= 6) {
                pointer -= 6
                buffer[pointer++] = '\\'
                buffer[pointer++] = 'u'

                if (ch > 0xFF) {
                    val high = ch shr 8 and 0xFF
                    buffer[pointer++] = hexChars[high shr 4]
                    buffer[pointer++] = hexChars[high and 0xF]
                    ch = ch and 0xFF
                } else {
                    buffer[pointer++] = '0'
                    buffer[pointer++] = '0'
                }

                buffer[pointer++] = hexChars[ch shr 4]
                buffer[pointer] = hexChars[ch and 0xF]
                pointer -= 5
            } else {
                val buffer = myEntityBuffer
                myOutputHead = myOutputTail

                try {
                    if (ch > 0xFF) {
                        val high = ch shr 8 and 0xFF
                        val low = ch and 0xFF
                        buffer[10] = hexChars[high shr 4]
                        buffer[11] = hexChars[high and 0xF]
                        buffer[12] = hexChars[low shr 4]
                        buffer[13] = hexChars[low and 0xF]
                        myWriter!!.write(buffer, 8, 6)
                    } else {
                        buffer[6] = hexChars[ch shr 4]
                        buffer[7] = hexChars[ch and 0xF]
                        myWriter!!.write(buffer, 2, 6)
                    }
                } catch (e: IOException) {
                    throw wrapIOFailure(e)
                }
            }

            return pointer
        }

        val escape = myCurrentEscape?.value?.also { myCurrentEscape = null } ?: myCharacterEscapes!!.getEscapeSequence(
                ch)!!.value
        val length = escape.length

        if (pointer in length..<end) {
            pointer -= length
            escape.toCharArray(myOutputBuffer, pointer, 0, length)
        } else {
            try {
                myWriter!!.write(escape)
            } catch (e: IOException) {
                throw wrapIOFailure(e)
            }
        }

        return pointer
    }

    /**
     * Method called to append escape sequence for given character, at the end of standard output buffer; or if not
     * possible, write out directly.
     */
    @Throws(CirJacksonException::class)
    private fun appendCharacterEscape(char: Char, escapeCode: Int) {
        var ch = char.code

        if (escapeCode >= 0) {
            if (myOutputTail + 2 >= myOutputEnd) {
                flushBuffer()
            }

            myOutputBuffer[myOutputTail++] = '\\'
            myOutputBuffer[myOutputTail++] = escapeCode.toChar()
            return
        }

        if (escapeCode != CharacterEscapes.ESCAPE_CUSTOM) {
            if (myOutputTail + 2 >= myOutputEnd) {
                flushBuffer()
            }

            var pointer = myOutputTail
            val buffer = myOutputBuffer
            val hexChars = hexChars
            buffer[pointer++] = '\\'
            buffer[pointer++] = 'u'

            if (ch > 0xFF) {
                val high = ch shr 8 and 0xFF
                buffer[pointer++] = hexChars[high shr 4]
                buffer[pointer++] = hexChars[high and 0xF]
                ch = ch and 0xFF
            } else {
                buffer[pointer++] = '0'
                buffer[pointer++] = '0'
            }

            buffer[pointer++] = hexChars[ch shr 4]
            buffer[pointer++] = hexChars[ch and 0xF]
            myOutputTail = pointer
            return
        }

        val escape = myCurrentEscape?.value?.also { myCurrentEscape = null } ?: myCharacterEscapes!!.getEscapeSequence(
                ch)!!.value
        val length = escape.length

        if (myOutputTail + length > myOutputEnd) {
            flushBuffer()

            if (length > myOutputEnd) {
                try {
                    myWriter!!.write(escape)
                } catch (e: IOException) {
                    throw wrapIOFailure(e)
                }
            }
        }

        escape.toCharArray(myOutputBuffer, myOutputTail, 0, length)
        myOutputTail += length
    }

    protected fun allocateCopyBuffer(): CharArray {
        return myCopyBuffer ?: ioContext.allocateNameCopyBuffer(2000).also { myCopyBuffer = it }
    }

    @Throws(CirJacksonException::class)
    protected open fun flushBuffer() {
        val length = myOutputTail - myOutputHead

        if (length <= 0) {
            return
        }

        val offset = myOutputHead
        myOutputTail = 0
        myOutputHead = 0

        try {
            myWriter!!.write(myOutputBuffer, offset, length)
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }
    }

    companion object {

        const val TYPE_MESSAGE_START_ARRAY = "start an array"

        const val TYPE_MESSAGE_START_OBJECT = "start an object"

        const val SHORT_WRITE = 32

        val HEX_CHARS_UPPER = CharTypes.copyHexChars(true)

        val HEX_CHARS_LOWER = CharTypes.copyHexChars(false)

        /**
         * CharArray used to represent a flushed [myOutputBuffer] in [releaseBuffers], instead of setting it to `null`
         * and needing to add `!!` everywhere
         */
        val FLUSHED_OUTPUT_BUFFER = CharArray(0)

    }

}