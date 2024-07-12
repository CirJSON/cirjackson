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
     * Second level decoding, primary field name decoding
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