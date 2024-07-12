package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.core.io.CharTypes
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.symbols.ByteQuadsCanonicalizer
import org.cirjson.cirjackson.core.util.Other

abstract class NonBlockingUtf8CirJsonParserBase(objectReadContext: ObjectReadContext, ioContext: IOContext,
        streamReadFeatures: Int, formatReadFeatures: Int, symbols: ByteQuadsCanonicalizer) :
        NonBlockingCirJsonParserBase(objectReadContext, ioContext, streamReadFeatures, formatReadFeatures, symbols) {

    /**
     * In addition to current buffer pointer, and end pointer, we will also need to know number of bytes originally
     * contained. This is needed to correctly update location information when the block has been completed.
     */
    protected var myOriginalBufferLen = 0

    val isNeedingMoreInput: Boolean
        get() = myInputPointer >= myInputEnd && !myEndOfInput

    fun endOfInput() {
        myEndOfInput = true
    }

    override fun decodeEscaped(): Char {
        return Other.throwInternalReturnAny()
    }

    /*
     *******************************************************************************************************************
     * Main-level decoding
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun nextToken(): CirJsonToken? {
        if (myInputPointer >= myInputEnd) {
            return if (isClosed) {
                null
            } else if (myEndOfInput) {
                if (myCurrentToken == CirJsonToken.NOT_AVAILABLE) {
                    finishTokenWithEOF()
                } else {
                    eofAsNextToken()
                }
            } else {
                CirJsonToken.NOT_AVAILABLE
            }
        }

        if (myCurrentToken == CirJsonToken.NOT_AVAILABLE) {
            finishCurrentToken()
        }

        myNumberTypesValid = NUMBER_UNKNOWN
        tokenCharacterOffset = myCurrentInputProcessed + myInputPointer
        myBinaryValue = null
        val code = nextUnsignedByteFromBuffer

        return when (myMajorState) {
            MAJOR_INITIAL -> startDocument(code)
            MAJOR_ROOT -> startValue(code)
            MAJOR_OBJECT_PROPERTY_FIRST -> startIdName(code)
            MAJOR_OBJECT_PROPERTY_NEXT -> startNameAfterComma(code)
            MAJOR_OBJECT_VALUE -> startValueExpectColon(code)
            MAJOR_ARRAY_ELEMENT_FIRST -> startArrayId(code)
            MAJOR_ARRAY_ELEMENT_NEXT -> startValueExpectComma(code)
            else -> Other.throwInternalReturnAny()
        }
    }

    protected abstract val nextSignedByteFromBuffer: Byte

    protected abstract val nextUnsignedByteFromBuffer: Int

    /**
     * Method to get the byte from the buffer at the given pointer.
     *
     * @param pointer pointer to byte that is required
     *
     * @return byte from the buffer at the given pointer
     */
    protected abstract fun getByteFromBuffer(pointer: Int): Byte

    protected fun finishCurrentToken(): CirJsonToken? {
        TODO()
    }

    protected fun finishTokenWithEOF(): CirJsonToken? {
        TODO()
    }

    /*
     *******************************************************************************************************************
     * Second level decoding, root level
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun startDocument(code: Int): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun finishBOM(): CirJsonToken {
        TODO()
    }

    /*
     *******************************************************************************************************************
     * Second level decoding, primary field name decoding
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun startIdName(code: Int): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun startNameAfterComma(code: Int): CirJsonToken? {
        TODO()
    }

    /*
     *******************************************************************************************************************
     * Second level decoding, value decoding
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun startArrayId(code: Int): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun startValue(code: Int): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun startValueExpectComma(code: Int): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun startValueExpectColon(code: Int): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun startUnexpectedValue(code: Int, leadingComma: Boolean): CirJsonToken? {
        TODO()
    }

    /*
     *******************************************************************************************************************
     * Second level decoding, skipping white-space, comments
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun skipWhitespace(code: Int): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun startSlashComment(fromMinorState: Int): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun finishHashComment(fromMinorState: Int): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun finishCppComment(fromMinorState: Int): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun finishCComment(fromMinorState: Int): CirJsonToken? {
        TODO()
    }

    /*
     *******************************************************************************************************************
     * Tertiary level decoding, simple tokens decoding
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun startFalseToken(): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun startTrueToken(): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun startNullToken(): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishKeywordToken(expectedToken: String, matched: Int, result: CirJsonToken): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishKeywordTokenWithEOF(expectedToken: String, matched: Int,
            result: CirJsonToken): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNonStdToken(expectedToken: String, matched: Int, result: CirJsonToken): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNonStdTokenWithEOF(expectedToken: String, matched: Int,
            result: CirJsonToken): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishErrorToken(): CirJsonToken {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishErrorTokenWithEOF(): CirJsonToken {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun reportErrorToken(actualToken: String): CirJsonToken {
        return reportError("Unrecognized token '$actualToken': was expecting ${validCirJsonTokenList()}")
    }

    /*
     *******************************************************************************************************************
     * Second level decoding, numbers decoding
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun startFloatThatStartsWithPeriod(): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun startPositiveNumber(code: Int): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun startNegativeNumber(): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun startPositiveNumber(): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun startNumberLeadingZero(): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNumberPlus(code: Int): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNumberMinus(code: Int): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNumberPlusMinus(code: Int, negative: Boolean): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNumberLeadingZero(): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNumberPosLeadingZero(): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNumberNegLeadingZero(): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNumberPosNegLeadingZero(negative: Boolean): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNumberIntegralPart(outputBuffer: CharArray, outputPointer: Int): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun startFloat(outputBuffer: CharArray, outputPointer: Int, code: Int): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishFloatFraction(): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishFloatExponent(code: Int, checkSign: Boolean): CirJsonToken? {
        TODO()
    }

    /*
     *******************************************************************************************************************
     * Tertiary level decoding, name decoding
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun fastParseName(): String? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun parseMediumName(pointer: Int, quad2: Int): String? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun parseMediumName(pointer: Int, quad3: Int, q2: Int): String? {
        TODO()
    }

    /**
     * Slower parsing method which is generally branched to when an escape sequence is detected (or alternatively for
     * long names, one crossing input buffer boundary). Needs to be able to handle more exceptional cases, gets slower,
     * and hence is offlined to a separate method.
     */
    @Throws(CirJacksonException::class)
    private fun parseEscapedName(quadLength: Int, currentQuad: Int, currentQuadBytes: Int): String? {
        TODO()
    }

    /**
     * Method called when we see non-white space character other than double quote, when expecting a field name. In
     * standard mode will just throw an exception; but in non-standard modes may be able to parse name.
     */
    @Throws(CirJacksonException::class)
    private fun handleOddName(code: Int): CirJsonToken? {
        TODO()
    }

    /**
     * Parsing of optionally supported non-standard "unquoted" names: names without either double-quotes or apostrophes
     * surrounding them. Unlike other
     */
    @Throws(CirJacksonException::class)
    private fun finishUnquotedName(quadLength: Int, currentQuad: Int, currentQuadBytes: Int): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun finishApostropheName(quadLength: Int, currentQuad: Int, currentQuadBytes: Int): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun finishPropertyWithEscape(): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun decodeSplitEscaped(value: Int, bytesRead: Int): Int {
        TODO()
    }

    /*
     *******************************************************************************************************************
     * Second level decoding, String decoding
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun startString(): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun finishRegularString(): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun startApostropheString(): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun finishApostropheString(): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun decodeSplitMultiByte(code: Int, type: Int, gotNext: Boolean): Boolean {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun decodeSplitUTF8V3(previous: Int, previousCount: Int, next: Int): Boolean {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun decodeSplitUTF8V4(previous: Int, previousCount: Int, next: Int): Boolean {
        TODO()
    }

    /*
     *******************************************************************************************************************
     * Internal methods, UTF8 decoding
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun decodeCharEscape(): Int {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun decodeFastCharEscape(): Int {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun decodeUTF8V2(c: Int, d: Int): Int {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun decodeUTF8V3(c: Int, d: Int, e: Int): Int {
        TODO()
    }

    @Throws(CirJacksonException::class)
    private fun decodeUTF8V4(c: Int, d: Int, e: Int, f: Int): Int {
        TODO()
    }

    companion object {

        private val FEAT_MASK_TRAILING_COMMA = CirJsonReadFeature.ALLOW_TRAILING_COMMA.mask

        private val FEAT_MASK_LEADING_ZEROS = CirJsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS.mask

        private val FEAT_MASK_ALLOW_MISSING = CirJsonReadFeature.ALLOW_MISSING_VALUES.mask

        private val FEAT_MASK_ALLOW_SINGLE_QUOTES = CirJsonReadFeature.ALLOW_SINGLE_QUOTES.mask

        private val FEAT_MASK_ALLOW_UNQUOTED_NAMES = CirJsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES.mask

        private val FEAT_MASK_ALLOW_JAVA_COMMENTS = CirJsonReadFeature.ALLOW_JAVA_COMMENTS.mask

        private val FEAT_MASK_ALLOW_YAML_COMMENTS = CirJsonReadFeature.ALLOW_YAML_COMMENTS.mask

        /**
         * This is the main input-code lookup table, fetched eagerly
         */
        private val INPUT_CODE_UTF8 = CharTypes.inputCodeUtf8

        /**
         * Latin1 encoding is not supported, but we do use 8-bit subset for pre-processing task, to simplify first pass,
         * keep it fast.
         */
        private val INPUT_CODE_LATIN1 = CharTypes.inputCodeLatin1

    }

}