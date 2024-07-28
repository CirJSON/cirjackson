package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.io.CharTypes
import org.cirjson.cirjackson.core.io.CharacterEscapes
import org.cirjson.cirjackson.core.io.IOContext
import java.io.InputStream
import java.io.Reader
import java.io.Writer
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.max

/**
 * [CirJsonGenerator] that outputs CirJSON content using a [Writer] which handles character encoding.
 *
 * @property myQuoteChar Character used for quoting CirJSON Object property names and String values.
 */
open class WriterBasedCirJsonGenerator(objectWriteContext: ObjectWriteContext, ioContext: IOContext,
        streamWriteFeatures: Int, formatWriteFeatures: Int, protected val myWriter: Writer,
        rootValueSeparator: SerializableString, prettyPrinter: PrettyPrinter?, characterEscapes: CharacterEscapes?,
        maxNonEscaped: Int, protected val myQuoteChar: Char) :
        CirJsonGeneratorBase(objectWriteContext, ioContext, streamWriteFeatures, formatWriteFeatures,
                rootValueSeparator, prettyPrinter, characterEscapes, maxNonEscaped) {

    /**
     * Intermediate buffer in which contents are buffered before being written using [myWriter].
     */
    protected var myOutputBuffer: CharArray = ioContext.allocConcatBuffer()

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

    init {
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
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeName(name: SerializableString): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun writeName(name: String, commaBefore: Boolean) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun writeName(name: SerializableString, commaBefore: Boolean) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun writeNameTail(string: SerializableString) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Output method implementations, structural
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun getID(target: Any, isArray: Boolean): String {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeStartArray(): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeStartArray(currentValue: Any?): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeStartArray(currentValue: Any?, size: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    override fun writeArrayId(referenced: Any): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeEndArray(): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeStartObject(): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeStartObject(currentValue: Any?): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeStartObject(currentValue: Any?, size: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    override fun writeObjectId(referenced: Any): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeEndObject(): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    /**
     * Specialized version of `writeName`, off-lined to keep the "fast path" as simple (and hopefully fast) as possible.
     */
    @Throws(CirJacksonException::class)
    protected fun writePrettyPrinterName(name: String, commaBefore: Boolean) {
        TODO("Not yet implemented")
    }

    /**
     * Specialized version of `writeName`, off-lined to keep the "fast path" as simple (and hopefully fast) as possible.
     */
    @Throws(CirJacksonException::class)
    protected fun writePrettyPrinterName(name: SerializableString, commaBefore: Boolean) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Output method implementations, textual
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeString(value: String): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeString(reader: Reader, length: Int): CirJsonGenerator {
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
    private fun writeStringInternal(string: SerializableString) {
        TODO("Not yet implemented")
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

    override fun writeRaw(buffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(char: Char): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun writeRawLong(text: String) {
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
    override fun writeBinary(variant: Base64Variant, data: InputStream, dataLength: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Output method implementations, primitive
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Short): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun writeQuotedShort(value: Short) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun writeQuotedInt(value: Int) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Long): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun writeQuotedLong(value: Long) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: BigInteger): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Double): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Float): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: BigDecimal): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(encodedValue: String): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(encodedValueBuffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun writeQuotedRaw(value: String) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun writeQuotedRaw(text: CharArray, offset: Int, length: Int) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeBoolean(state: Boolean): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeNull(): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Implementations for other methods
     *******************************************************************************************************************
     */

    final override fun verifyValueWrite(typeMessage: String) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Configuration access
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun flush() {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun closeInput() {
        TODO("Not yet implemented")
    }

    override fun releaseBuffers() {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods, low-level writing; text, default
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun writeStringInternal(text: String) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun writeString(length: Int) {
        TODO("Not yet implemented")
    }

    /**
     * Method called to write "long strings", strings whose length exceeds output buffer length.
     */
    @Throws(CirJacksonException::class)
    private fun writeLongString(text: String) {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    /**
     * This method called when the string content is already in a char buffer, and need not be copied for processing.
     */
    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun writeStringInternal(text: CharArray, offset: Int, length: Int) {
        var offset = offset
        var length = length
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun writeSegmentASCII(end: Int, maxNonEscaped: Int) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun writeStringASCII(text: CharArray, offset: Int, length: Int, maxNonEscaped: Int) {
        var offset = offset
        var length = length
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun writeSegmentCustom(end: Int) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun writeStringCustom(text: CharArray, offset: Int, length: Int) {
        var offset = offset
        var length = length
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods, low-level writing; binary
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun writeBinaryInternal(base64Variant: Base64Variant, input: ByteArray, inputPointer: Int,
            inputEnd: Int) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    protected fun writeBinary(variant: Base64Variant, data: InputStream, readBuffer: ByteArray, bytesLeft: Int): Int {
        var bytesLeft = bytesLeft
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun writeBinary(variant: Base64Variant, data: InputStream, readBuffer: ByteArray): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun readMore(input: InputStream, readBuffer: ByteArray, inputPointer: Int, inputEnd: Int,
            maxRead: Int): Int {
        var inputPointer = inputPointer
        var inputEnd = inputEnd
        var maxRead = maxRead
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods, low-level writing; other
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun writeNullInternal() {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    /**
     * Method called to append escape sequence for given character, at the
     * end of standard output buffer; or if not possible, write out directly.
     */
    @Throws(CirJacksonException::class)
    private fun appendCharacterEscape(char: Char, escapeCode: Int) {
        TODO("Not yet implemented")
    }

    protected fun allocateCopyBuffer(): CharArray {
        return myCopyBuffer ?: ioContext.allocateNameCopyBuffer(2000).also { myCopyBuffer = it }
    }

    @Throws(CirJacksonException::class)
    protected open fun flushBuffer() {
        TODO("Not yet implemented")
    }

}