package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.symbols.CharsToNameCanonicalizer
import java.io.IOException
import java.io.OutputStream
import java.io.Reader
import java.io.Writer

/**
 * This is a concrete implementation of [CirJsonParser], which is based on a [Reader] to handle low-level character
 * conversion tasks.
 */
open class ReaderBasedCirJsonParser : CirJsonParserBase {

    /**
     * Reader that can be used for reading more content, if one buffer from input source, but in some cases preloaded
     * buffer is handed to the parser.
     */
    protected var myReader: Reader? = null

    /**
     * Current buffer from which data is read; generally data is read into buffer from input source.
     */
    protected var myInputBuffer: CharArray

    /**
     * Flag that indicates whether the input buffer is recyclable (and needs to be returned to recycler once we are
     * done) or not.
     *
     * If it is not, it also means that parser can NOT modify underlying buffer.
     */
    protected var myIsBufferRecyclable: Boolean

    protected val mySymbols: CharsToNameCanonicalizer

    protected val myHashSeed: Int

    /**
     * Flag that indicates that the current token has not yet been fully processed, and needs to be finished for some
     * access (or skipped to obtain the next token)
     */
    protected var myIsTokenIncomplete = false

    /**
     * Value of [myInputPointer] at the time when the first character of name token was read. Used for calculating token
     * location when requested; combined with [myCurrentInputProcessed], may be updated appropriately as needed.
     */
    protected var myNameStartOffset = 0

    protected var myNameStartRow = 0

    protected var myNameStartColumn = 0

    protected var myIsBufferReleased = false

    /**
     * Constructor called when caller wants to provide input buffer directly (or needs to, in case of bootstrapping
     * having read some of the contents) and it may or may not be recyclable use standard recycle context.
     *
     * @param objectReadContext Object read context to use
     *
     * @param ioContext I/O context to use
     *
     * @param streamReadFeatures Standard stream read features enabled
     *
     * @param formatReadFeatures Format-specific read features enabled
     *
     * @param reader Reader used for reading actual content, if any; `null` if none
     *
     * @param symbols Name canonicalizer to use
     *
     * @param inputBuffer Input buffer to read initial content from (before Reader)
     *
     * @param start Pointer in `inputBuffer` that has the first content character to decode
     *
     * @param end Pointer past the last content character in `inputBuffer`
     *
     * @param bufferRecyclable Whether `inputBuffer` passed is managed by CirJackson core (and thereby needs recycling)
     */
    constructor(objectReadContext: ObjectReadContext, ioContext: IOContext, streamReadFeatures: Int,
            formatReadFeatures: Int, reader: Reader?, symbols: CharsToNameCanonicalizer, inputBuffer: CharArray,
            start: Int, end: Int, bufferRecyclable: Boolean) : super(objectReadContext, ioContext, streamReadFeatures,
            formatReadFeatures) {
        myReader = reader
        myInputBuffer = inputBuffer
        myInputPointer = start
        myInputEnd = end
        myCurrentInputRowStart = start
        myCurrentInputProcessed = -start.toLong()
        mySymbols = symbols
        myHashSeed = symbols.hashSeed
        myIsBufferRecyclable = bufferRecyclable
    }

    /**
     * Constructor called when input comes as a [Reader], and buffer allocation can be done using default mechanism.
     *
     * @param objectReadContext Object read context to use
     *
     * @param ioContext I/O context to use
     *
     * @param streamReadFeatures Standard stream read features enabled
     *
     * @param formatReadFeatures Format-specific read features enabled
     *
     * @param reader Reader used for reading actual content, if any; `null` if none
     *
     * @param symbols Name canonicalizer to use
     */
    constructor(objectReadContext: ObjectReadContext, ioContext: IOContext, streamReadFeatures: Int,
            formatReadFeatures: Int, reader: Reader?, symbols: CharsToNameCanonicalizer) : super(objectReadContext,
            ioContext, streamReadFeatures, formatReadFeatures) {
        myReader = reader
        myInputBuffer = ioContext.allocateTokenBuffer()
        myInputPointer = 0
        myInputEnd = 0
        mySymbols = symbols
        myHashSeed = symbols.hashSeed
        myIsBufferRecyclable = true
    }

    /*
     *******************************************************************************************************************
     * Configuration access
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun releaseBuffered(writer: Writer): Int {
        val count = myInputEnd - myInputPointer

        if (count < 1) {
            return 0
        }

        val originalPointer = myInputPointer
        myInputPointer += count

        try {
            writer.write(myInputBuffer, originalPointer, count)
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }

        return count
    }

    override fun streamReadInputSource(): Any? {
        return myReader
    }

    @Throws(CirJacksonException::class)
    protected fun getNextChar(eofMessage: String, forToken: CirJsonToken): Char {
        if (myInputPointer >= myInputEnd) {
            if (!loadMore()) {
                return reportInvalidEOF(eofMessage, forToken)
            }
        }

        return myInputBuffer[myInputPointer++]
    }

    override fun closeInput() {
        if (myReader != null) {
            if (myIOContext!!.isResourceManaged || isEnabled(StreamReadFeature.AUTO_CLOSE_SOURCE)) {
                try {
                    myReader!!.close()
                } catch (e: IOException) {
                    throw wrapIOFailure(e)
                }
            }

            myReader = null
        }
    }

    /**
     * Method called to release internal buffers owned by the base reader. This may be called along with [closeInput]
     * (for example, when explicitly closing this reader instance), or separately (if need be).
     */
    override fun releaseBuffers() {
        super.releaseBuffers()

        mySymbols.release()

        if (myIsBufferRecyclable) {
            val buffer = myInputBuffer

            if (!myIsBufferReleased) {
                myInputBuffer = CharArray(0)
                myIOContext!!.releaseTokenBuffer(buffer)
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Low-level access, supporting
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun loadMoreGuaranteed() {
        if (!loadMore()) {
            return reportInvalidEOF()
        }
    }

    @Throws(CirJacksonException::class)
    protected fun loadMore(): Boolean {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, data access
     *******************************************************************************************************************
     */

    @get:Throws(CirJacksonException::class)
    final override val text: String?
        get() = TODO("Not yet implemented")

    @Throws(CirJacksonException::class)
    override fun getText(writer: Writer): Int {
        TODO()
    }

    @get:Throws(CirJacksonException::class)
    final override val valueAsString: String?
        get() = TODO("Not yet implemented")

    @Throws(CirJacksonException::class)
    final override fun getValueAsString(defaultValue: String?): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun getText(token: CirJsonToken?): String? {
        TODO("Not yet implemented")
    }

    @get:Throws(CirJacksonException::class)
    final override val textCharacters: CharArray?
        get() = TODO("Not yet implemented")

    @get:Throws(CirJacksonException::class)
    final override val textLength: Int
        get() = TODO("Not yet implemented")

    @get:Throws(CirJacksonException::class)
    final override val textOffset: Int
        get() = TODO("Not yet implemented")

    @Throws(CirJacksonException::class)
    override fun getBinaryValue(base64Variant: Base64Variant): ByteArray {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun readBinaryValue(base64Variant: Base64Variant, output: OutputStream): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun readBinary(base64Variant: Base64Variant, output: OutputStream, buffer: ByteArray): Int {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, traversal
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun nextToken(): CirJsonToken? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun nextAfterName(): CirJsonToken? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun finishToken() {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, nextXXX() overrides
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun nextName(string: SerializableString): Boolean {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun nextName(): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun isNextTokenNameYes(i: Int) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun isNextTokenNameMaybe(i: Int, nameToMatch: String): Boolean {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun nextTokenNotInObject(i: Int): CirJsonToken? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    final override fun nextTextValue(): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    final override fun nextIntValue(defaultValue: Int): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    final override fun nextLongValue(defaultValue: Long): Long {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    final override fun nextBooleanValue(): Boolean? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods, number parsing
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun parseFloatThatStartsWithPeriod(neg: Boolean): CirJsonToken? {
        TODO("Not yet implemented")
    }

    /**
     * Initial parsing method for number values. It needs to be able to parse enough input to be able to determine
     * whether the value is to be considered a simple integer value, or a more generic decimal value: latter of which
     * needs to be expressed as a floating point number. The basic rule is that if the number has no fractional or
     * exponential part, it is an integer; otherwise a floating point number.
     *
     * Because much of input has to be processed in any case, no partial parsing is done: all input text will be stored
     * for further processing. However, actual numeric value conversion will be deferred, since it is usually the most
     * complicated and costliest part of processing.
     *
     * @param code The first non-null digit character of the number to parse
     *
     * @return Type of token decoded, usually [CirJsonToken.VALUE_NUMBER_INT] or [CirJsonToken.VALUE_NUMBER_FLOAT]
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    protected fun parseUnsignedNumber(code: Int): CirJsonToken? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun parseFloat(code: Int, startPointer: Int, pointer: Int, neg: Boolean,
            integralLength: Int): CirJsonToken? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun parseSignedNumber(negative: Boolean): CirJsonToken? {
        TODO("Not yet implemented")
    }

    /**
     * Method called to parse a number, when the primary parse method has failed to parse it, due to it being split on
     * buffer boundary. As a result code is very similar, except that it has to explicitly copy contents to the text
     * buffer instead of just sharing the main input buffer.
     *
     * @param neg Whether number being decoded is negative or not
     *
     * @param startPointer Offset in input buffer for the next character of content
     *
     * @return Type of token decoded, usually [CirJsonToken.VALUE_NUMBER_INT] or [CirJsonToken.VALUE_NUMBER_FLOAT]
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    private fun parseNumber(neg: Boolean, startPointer: Int): CirJsonToken? {
        TODO("Not yet implemented")
    }

    /**
     * Method called when we have seen one zero, and want to ensure it is not followed by another
     */
    @Throws(CirJacksonException::class)
    private fun verifyNoLeadingZeroes(): Char {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun verifyNoLeadingZeroes2(): Char {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun handleInvalidNumberStart(code: Int, negative: Boolean): CirJsonToken? {
        TODO("Not yet implemented")
    }

    /**
     * Method called if expected numeric value (due to leading sign) does not look like a number
     */
    @Throws(CirJacksonException::class)
    protected fun handleInvalidNumberStart(code: Int, negative: Boolean, hasSign: Boolean): CirJsonToken? {
        TODO("Not yet implemented")
    }

    /**
     * Method called to ensure that a root-value is followed by a space token.
     *
     * NOTE: caller MUST ensure there is at least one character available; and that input pointer is AT given char (not
     * past)
     *
     * @param code First character of likely white space to skip
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws CirStreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    private fun verifyRootSpace(code: Int) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods, secondary parsing
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun parseName(): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun parseName(startPointer: Int, hash: Int, endChar: Int): String? {
        TODO("Not yet implemented")
    }

    /**
     * Method called when we see non-white space character other than double quote, when expecting an Object property
     * name. In standard mode will just throw an exception; but in non-standard modes may be able to parse name.
     *
     * @param i First not-yet-decoded character of possible "odd name" to decode
     *
     * @return Name decoded, if allowed and successful
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    protected fun handleOddName(i: Int): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun parseApostropheName(): String? {
        TODO("Not yet implemented")
    }

    /**
     * Method for handling cases where first non-space character of an expected value token is not legal for standard
     * CirJSON content.
     *
     * @param i First undecoded character of possible "odd value" to decode
     *
     * @return Type of value decoded, if allowed and successful
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    protected fun handleOddValue(i: Int): CirJsonToken? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun handleApostrophe(): CirJsonToken? {
        TODO("Not yet implemented")
    }

    private fun handleOddName(startPointer: Int, hash: Int, codes: IntArray): String? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun finishString() {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun finishString2() {
        TODO("Not yet implemented")
    }

    /**
     * Method called to skim through rest of unparsed String value, if it is not needed. This can be done a bit faster
     * if contents need not be stored for future access.
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    protected fun skipString() {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods, other parsing
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun skipCR() {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipColon(): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipColon(gotColon: Boolean): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipColonFast(pointer: Int): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipComma(i: Int): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipAfterComma(): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipWhitespaceOrEnd(): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipWhitespaceOrEnd2(): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipComment(): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipCComment(): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipYAMLComment(): Int {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun skipLine() {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun decodeEscaped(): Char {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun matchTrue() {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun matchFalse() {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun matchNull() {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun matchToken(matchString: String, i: Int) {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun matchToken2(matchString: String, i: Int) {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun checkMatchEnd(matchString: String, i: Int, c: Int) {
        TODO()
    }

    /*
     *******************************************************************************************************************
     * Binary access
     *******************************************************************************************************************
     */

    /**
     * Efficient handling for incremental parsing of base64-encoded textual content.
     *
     * @param base64Variant Type of base64 encoding expected in context
     *
     * @return Fully decoded value of base64 content
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    protected fun decodeBase64(base64Variant: Base64Variant): ByteArray {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods, location updating
     *******************************************************************************************************************
     */

    override fun currentTokenLocation(): CirJsonLocation {
        TODO("Not yet implemented")
    }

    override fun currentLocation(): CirJsonLocation {
        TODO("Not yet implemented")
    }

    override fun currentLocationMinusOne(): CirJsonLocation {
        TODO("Not yet implemented")
    }

    private fun updateLocation() {
        TODO("Not yet implemented")
    }

    private fun updateNameLocation() {
        TODO("Not yet implemented")
    }

    override val objectId: Any?
        get() = null

    override val typeId: Any?
        get() = null

    /*
     *******************************************************************************************************************
     * Internal methods, location updating
     *******************************************************************************************************************
     */

    @Throws(StreamReadException::class)
    private fun closeScope(i: Int) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods, error reporting
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun reportInvalidToken(matchedPart: String) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun reportInvalidToken(matchedPart: String, message: String) {
        TODO("Not yet implemented")
    }

}