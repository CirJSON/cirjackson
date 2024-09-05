package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.core.io.CharTypes
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.symbols.ByteQuadsCanonicalizer
import org.cirjson.cirjackson.core.util.Other
import kotlin.math.min

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
            return finishCurrentToken()
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

    /**
     * Method called when decoding of a token has been started, but not yet completed due to missing input; method is to
     * continue decoding due to at least one more byte being made available to decode.
     *
     * @return Token decoded, if complete; [CirJsonToken.NOT_AVAILABLE] if not
     *
     * @throws CirJacksonException (generally [org.cirjson.cirjackson.core.exception.StreamReadException]) for decoding
     * problems
     */
    @Throws(CirJacksonException::class)
    protected fun finishCurrentToken(): CirJsonToken? {
        return when (myMinorState) {
            MINOR_ROOT_BOM -> finishBOM(myPending32)

            MINOR_PROPERTY_LEADING_WS -> startIdName(nextUnsignedByteFromBuffer)

            MINOR_PROPERTY_LEADING_COMMA -> startNameAfterComma(nextUnsignedByteFromBuffer)

            MINOR_PROPERTY_LEADING_WS_AFTER_COMMA -> startNameAfterCommaAndWhitespace(nextUnsignedByteFromBuffer)

            // Property name states

            MINOR_PROPERTY_NAME -> parseEscapedName(myQuadLength, myPending32, myPendingBytes)

            MINOR_PROPERTY_NAME_ESCAPE -> finishPropertyWithEscape()

            MINOR_PROPERTY_APOS_NAME -> finishApostropheName(myQuadLength, myPending32, myPendingBytes)

            MINOR_PROPERTY_UNQUOTED_NAME -> finishUnquotedName(myQuadLength, myPending32, myPendingBytes)

            // Value states: base

            MINOR_VALUE_LEADING_WS -> startValue(nextUnsignedByteFromBuffer)

            MINOR_VALUE_WS_AFTER_COMMA -> startValueAfterComma(nextUnsignedByteFromBuffer)

            MINOR_VALUE_EXPECTING_COMMA -> startValueExpectComma(nextUnsignedByteFromBuffer)

            MINOR_VALUE_EXPECTING_COLON -> startValueExpectColon(nextUnsignedByteFromBuffer)

            // Value states: tokens

            MINOR_VALUE_TOKEN_NULL -> finishKeywordToken("null", myPending32, CirJsonToken.VALUE_NULL)

            MINOR_VALUE_TOKEN_TRUE -> finishKeywordToken("true", myPending32, CirJsonToken.VALUE_TRUE)

            MINOR_VALUE_TOKEN_FALSE -> finishKeywordToken("false", myPending32, CirJsonToken.VALUE_FALSE)

            MINOR_VALUE_TOKEN_NON_STD -> finishNonStdToken(myNonStdTokenType, myPending32)

            // Value states: numbers

            MINOR_NUMBER_PLUS -> finishNumberPlus(nextUnsignedByteFromBuffer)

            MINOR_NUMBER_MINUS -> finishNumberMinus(nextUnsignedByteFromBuffer)

            MINOR_NUMBER_ZERO -> finishNumberLeadingZero()

            MINOR_NUMBER_PLUS_ZERO -> finishNumberLeadingPosZero()

            MINOR_NUMBER_MINUS_ZERO -> finishNumberLeadingNegZero()

            MINOR_NUMBER_INTEGER_DIGITS -> {
                finishNumberIntegralPart(myTextBuffer.bufferWithoutReset!!, myTextBuffer.currentSegmentSize)
            }

            MINOR_NUMBER_FRACTION_DIGITS -> finishFloatFraction()

            MINOR_NUMBER_EXPONENT_MARKER -> finishFloatExponent(nextUnsignedByteFromBuffer, true)

            MINOR_NUMBER_EXPONENT_DIGITS -> finishFloatExponent(nextUnsignedByteFromBuffer, false)

            // Value states: strings

            MINOR_VALUE_STRING -> finishRegularString()

            MINOR_VALUE_STRING_UTF8_2 -> {
                myTextBuffer.append(decodeUTF8V2(myPending32, nextUnsignedByteFromBuffer).toChar())

                if (myMinorStateAfterSplit == MINOR_VALUE_APOS_STRING) {
                    finishApostropheString()
                } else {
                    finishRegularString()
                }
            }

            MINOR_VALUE_STRING_UTF8_3 -> {
                if (!decodeSplitUTF8V3(myPending32, myPendingBytes, nextUnsignedByteFromBuffer)) {
                    CirJsonToken.NOT_AVAILABLE
                } else if (myMinorStateAfterSplit == MINOR_VALUE_APOS_STRING) {
                    finishApostropheString()
                } else {
                    finishRegularString()
                }
            }

            MINOR_VALUE_STRING_UTF8_4 -> {
                if (!decodeSplitUTF8V4(myPending32, myPendingBytes, nextSignedByteFromBuffer.toInt())) {
                    CirJsonToken.NOT_AVAILABLE
                } else if (myMinorStateAfterSplit == MINOR_VALUE_APOS_STRING) {
                    finishApostropheString()
                } else {
                    finishRegularString()
                }
            }

            MINOR_VALUE_STRING_ESCAPE -> {
                val c = decodeSplitEscaped(myQuoted32, myQuotedDigits)

                if (c < 0) {
                    CirJsonToken.NOT_AVAILABLE
                } else {
                    myTextBuffer.append(c.toChar())

                    if (myMinorStateAfterSplit == MINOR_VALUE_APOS_STRING) {
                        finishApostropheString()
                    } else {
                        finishRegularString()
                    }
                }
            }

            MINOR_VALUE_APOS_STRING -> finishApostropheString()

            // Error

            MINOR_VALUE_TOKEN_ERROR -> finishErrorToken()

            // Comments

            MINOR_COMMENT_LEADING_SLASH -> startSlashComment(myPending32)

            MINOR_COMMENT_CLOSING_ASTERISK -> finishCComment(myPending32, true)

            MINOR_COMMENT_C -> finishCComment(myPending32, false)

            MINOR_COMMENT_CPP -> finishCppComment(myPending32)

            MINOR_COMMENT_YAML -> finishHashComment(myPending32)

            else -> Other.throwInternalReturnAny()
        }
    }

    /**
     * Method similar to [finishCurrentToken], but called when no more input is available, and end-of-input has been
     * detected. This is usually problem case, but not always: root-level values may be properly terminated by this, and
     * similarly trailing white-space may have been skipped.
     *
     * @return Token decoded, if complete; [CirJsonToken.NOT_AVAILABLE] if not
     *
     * @throws CirJacksonException (generally [org.cirjson.cirjackson.core.exception.StreamReadException]) for decoding
     * problems
     */
    @Throws(CirJacksonException::class)
    protected fun finishTokenWithEOF(): CirJsonToken? {
        return when (myMinorState) {
            MINOR_ROOT_GOT_SEPARATOR -> eofAsNextToken()

            MINOR_PROPERTY_LEADING_COMMA -> {
                reportInvalidEOF(": expected an Object property name or END_ARRAY", CirJsonToken.NOT_AVAILABLE)
            }

            MINOR_VALUE_LEADING_WS -> eofAsNextToken()

            MINOR_VALUE_EXPECTING_COMMA -> {
                reportInvalidEOF(": expected a value token", CirJsonToken.NOT_AVAILABLE)
            }

            MINOR_VALUE_TOKEN_NULL -> finishKeywordTokenWithEOF("null", myPending32, CirJsonToken.VALUE_NULL)

            MINOR_VALUE_TOKEN_TRUE -> finishKeywordTokenWithEOF("true", myPending32, CirJsonToken.VALUE_TRUE)

            MINOR_VALUE_TOKEN_FALSE -> finishKeywordTokenWithEOF("false", myPending32, CirJsonToken.VALUE_FALSE)

            MINOR_VALUE_TOKEN_NON_STD -> finishNonStdTokenWithEOF(myNonStdTokenType, myPending32)

            MINOR_VALUE_TOKEN_ERROR -> finishErrorTokenWithEOF()

            MINOR_NUMBER_ZERO, MINOR_NUMBER_PLUS_ZERO, MINOR_NUMBER_MINUS_ZERO -> valueCompleteInt(0, "0")

            MINOR_NUMBER_INTEGER_DIGITS -> {
                var length = myTextBuffer.currentSegmentSize

                if (myNumberNegative) {
                    --length
                }

                myIntegralLength = length
                valueComplete(CirJsonToken.VALUE_NUMBER_INT)
            }

            MINOR_NUMBER_FRACTION_DIGITS -> {
                myExponentLength = 0
                valueComplete(CirJsonToken.VALUE_NUMBER_FLOAT)
            }

            MINOR_NUMBER_EXPONENT_DIGITS -> valueComplete(CirJsonToken.VALUE_NUMBER_FLOAT)

            MINOR_NUMBER_EXPONENT_MARKER -> {
                reportInvalidEOF(": was expecting fraction after exponent marker", CirJsonToken.VALUE_NUMBER_FLOAT)
            }

            MINOR_COMMENT_CLOSING_ASTERISK, MINOR_COMMENT_C -> {
                reportInvalidEOF(": was expecting closing '*/' for comment", CirJsonToken.NOT_AVAILABLE)
            }

            MINOR_COMMENT_CPP, MINOR_COMMENT_YAML -> eofAsNextToken()

            else -> reportInvalidEOF(": was expecting rest of token (internal state: $myMinorState)", myCurrentToken)
        }
    }

    /*
     *******************************************************************************************************************
     * Second level decoding, root level
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun startDocument(code: Int): CirJsonToken? {
        var ch = code and 0xFF

        if (ch == 0xEF && myMinorState != MINOR_ROOT_BOM) {
            return finishBOM(1)
        }

        while (ch <= 0x020) {
            if (ch != CODE_SPACE) {
                if (ch == CODE_LF) {
                    ++myCurrentInputRow
                    myCurrentInputRowStart = myInputPointer
                } else if (ch == CODE_CR) {
                    ++myCurrentInputRowAlt
                    myCurrentInputRowStart = myInputPointer
                } else if (ch != CODE_TAB) {
                    return reportInvalidSpace(ch)
                }
            }

            if (myInputPointer >= myInputEnd) {
                myMinorState = MINOR_ROOT_GOT_SEPARATOR

                return if (isClosed) {
                    null
                } else if (myEndOfInput) {
                    eofAsNextToken()
                } else {
                    CirJsonToken.NOT_AVAILABLE
                }
            }

            ch = nextUnsignedByteFromBuffer
        }

        return startValue(ch)
    }

    @Throws(CirJacksonException::class)
    private fun finishBOM(bytesHandles: Int): CirJsonToken? {
        var realBytesHandles = bytesHandles

        while (myInputPointer < myInputEnd) {
            val ch = nextUnsignedByteFromBuffer

            when (realBytesHandles) {
                3 -> {
                    myCurrentInputProcessed -= 3
                    return startDocument(ch)
                }

                2 -> if (ch != 0xBF) {
                    return reportError("Unexpected byte 0x${
                        ch.toString(16).padStart(2, '0')
                    } following 0xEF 0xBB; should get 0xBF as third byte of UTF-8 BOM")
                }

                1 -> if (ch != 0xBB) {
                    return reportError("Unexpected byte 0x${
                        ch.toString(16).padStart(2, '0')
                    } following 0xEF; should get 0xBB as second byte UTF-8 BOM")
                }
            }

            ++realBytesHandles
        }

        myPending32 = realBytesHandles
        myMinorState = MINOR_ROOT_BOM
        return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
    }

    /*
     *******************************************************************************************************************
     * Second level decoding, primary field name decoding
     *******************************************************************************************************************
     */

    /**
     * Method that handles initial token type recognition for token that has to be CIRJSON_ID_PROPERTY_NAME.
     */
    @Throws(CirJacksonException::class)
    private fun startIdName(code: Int): CirJsonToken? {
        var ch = code

        if (ch <= 0x0020) {
            ch = skipWhitespace(ch)

            if (ch <= 0) {
                myMinorState = MINOR_PROPERTY_LEADING_WS
                return myCurrentToken
            }
        }

        updateTokenLocation()

        if (ch != CODE_QUOTE) {
            return if (ch == CODE_R_CURLY) {
                closeObjectScope()
            } else {
                handleOddName(ch)
            }
        }

        if (myInputPointer + 13 <= myInputEnd) {
            val n = fastParseName()

            if (n != null) {
                return fieldComplete(n)
            }
        }

        return parseEscapedName(0, 0, 0)
    }

    @Throws(CirJacksonException::class)
    private fun startNameAfterComma(code: Int): CirJsonToken? {
        var ch = code

        if (ch <= 0x0020) {
            ch = skipWhitespace(ch)

            if (ch <= 0) {
                myMinorState = MINOR_PROPERTY_LEADING_COMMA
                return myCurrentToken
            }
        }

        if (ch != CODE_COMMA) {
            return when (ch) {
                CODE_R_CURLY -> closeObjectScope()

                CODE_HASH -> finishHashComment(MINOR_PROPERTY_LEADING_COMMA)

                CODE_SLASH -> startSlashComment(MINOR_PROPERTY_LEADING_COMMA)

                else -> reportUnexpectedChar(ch.toChar(),
                        "was expecting comma to separate ${streamReadContext!!.typeDescription} entries")
            }
        }

        val pointer = myInputPointer

        if (pointer >= myInputEnd) {
            myMinorState = MINOR_PROPERTY_LEADING_WS_AFTER_COMMA
            return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
        }

        ch = getByteFromBuffer(pointer).toInt()
        myInputPointer = pointer + 1

        if (ch <= 0x0020) {
            ch = skipWhitespace(ch)

            if (ch <= 0) {
                myMinorState = MINOR_PROPERTY_LEADING_WS_AFTER_COMMA
                return myCurrentToken
            }
        }

        updateTokenLocation()

        if (ch != CODE_QUOTE) {
            if (ch == CODE_R_CURLY) {
                if (formatReadFeatures and FEAT_MASK_TRAILING_COMMA != 0) {
                    return closeObjectScope()
                }
            }

            return handleOddName(ch)
        }

        if (myInputPointer + 13 <= myInputEnd) {
            val n = fastParseName()

            if (n != null) {
                return fieldComplete(n)
            }
        }

        return parseEscapedName(0, 0, 0)
    }

    @Throws(CirJacksonException::class)
    private fun startNameAfterCommaAndWhitespace(code: Int): CirJsonToken? {
        var ch = code

        if (ch <= 0x0020) {
            ch = skipWhitespace(ch)

            if (ch <= 0) {
                myMinorState = MINOR_PROPERTY_LEADING_WS_AFTER_COMMA
                return myCurrentToken
            }
        }

        updateTokenLocation()

        if (ch != CODE_QUOTE) {
            if (ch == CODE_R_CURLY) {
                if (formatReadFeatures and FEAT_MASK_TRAILING_COMMA != 0) {
                    return closeObjectScope()
                } else {
                    startUnexpectedValue(ch, true)
                }
            }

            return handleOddName(ch)
        }

        if (myInputPointer + 13 <= myInputEnd) {
            val n = fastParseName()

            if (n != null) {
                return fieldComplete(n)
            }
        }

        return parseEscapedName(0, 0, 0)
    }

    /*
     *******************************************************************************************************************
     * Second level decoding, value decoding
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun startArrayId(code: Int): CirJsonToken? {
        var ch = code

        if (ch <= 0x0020) {
            ch = skipWhitespace(ch)

            if (ch <= 0) {
                myMinorState = MINOR_VALUE_LEADING_WS
                return myCurrentToken
            }
        }

        updateTokenLocation()
        streamReadContext!!.isExpectingComma

        return if (ch == CODE_QUOTE) {
            startString()
        } else {
            reportUnexpectedChar(ch.toChar(), "was expecting array's ID")
        }
    }

    /**
     * Helper method called to detect type of value token (at any level), and possibly decode it if contained in input
     * buffer. Value may be preceded by leading white-space, but no separator (comma).
     */
    @Throws(CirJacksonException::class)
    private fun startValue(code: Int): CirJsonToken? {
        var ch = code

        if (ch <= 0x0020) {
            ch = skipWhitespace(ch)

            if (ch <= 0) {
                myMinorState = MINOR_VALUE_LEADING_WS
                return myCurrentToken
            }
        }

        updateTokenLocation()

        streamReadContext!!.isExpectingComma

        if (ch == CODE_QUOTE) {
            return startString()
        }

        return when (ch.toChar()) {
            '#' -> finishHashComment(MINOR_VALUE_LEADING_WS)

            '+' -> startPositiveNumber()

            '-' -> startNegativeNumber()

            '/' -> startSlashComment(MINOR_VALUE_LEADING_WS)

            '.' -> if (isEnabled(CirJsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)) {
                startFloatThatStartsWithPeriod()
            } else {
                startUnexpectedValue(ch, false)
            }

            '0' -> startNumberLeadingZero()

            '1', '2', '3', '4', '5', '6', '7', '8', '9' -> startPositiveNumber(ch)

            'f' -> startFalseToken()

            'n' -> startNullToken()

            't' -> startTrueToken()

            '[' -> startArrayScope()

            CODE_R_BRACKET.toChar() -> closeArrayScope()

            '{' -> startObjectScope()

            CODE_R_CURLY.toChar() -> closeObjectScope()

            else -> startUnexpectedValue(ch, false)
        }
    }

    /**
     * Helper method called to parse token that is either a value token in array or end-array marker
     */
    @Throws(CirJacksonException::class)
    private fun startValueExpectComma(code: Int): CirJsonToken? {
        var ch = code

        if (ch <= 0x0020) {
            ch = skipWhitespace(ch)

            if (ch <= 0) {
                myMinorState = MINOR_VALUE_EXPECTING_COMMA
                return myCurrentToken
            }
        }

        if (ch != CODE_COMMA) {
            return when (ch) {
                CODE_R_BRACKET -> closeArrayScope()

                CODE_R_CURLY -> closeObjectScope()

                CODE_SLASH -> startSlashComment(MINOR_VALUE_EXPECTING_COMMA)

                CODE_HASH -> finishHashComment(MINOR_VALUE_EXPECTING_COMMA)

                else -> reportUnexpectedChar(ch.toChar(),
                        "was expecting comma to separate ${streamReadContext!!.typeDescription} entries")
            }
        }

        streamReadContext!!.isExpectingComma

        val pointer = myInputPointer

        if (pointer >= myInputEnd) {
            myMinorState = MINOR_VALUE_WS_AFTER_COMMA
            return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
        }

        ch = getByteFromBuffer(pointer).toInt()
        myInputPointer = pointer + 1

        if (ch <= 0x0020) {
            ch = skipWhitespace(ch)

            if (ch <= 0) {
                myMinorState = MINOR_VALUE_WS_AFTER_COMMA
                return myCurrentToken
            }
        }

        updateTokenLocation()

        if (ch == CODE_QUOTE) {
            return startString()
        }

        return when (ch.toChar()) {
            '#' -> finishHashComment(MINOR_VALUE_WS_AFTER_COMMA)

            '+' -> startPositiveNumber()

            '-' -> startNegativeNumber()

            '/' -> startSlashComment(MINOR_VALUE_WS_AFTER_COMMA)

            '.' -> if (isEnabled(CirJsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)) {
                startFloatThatStartsWithPeriod()
            } else {
                startUnexpectedValue(ch, true)
            }

            '0' -> startNumberLeadingZero()

            '1', '2', '3', '4', '5', '6', '7', '8', '9' -> startPositiveNumber(ch)

            'f' -> startFalseToken()

            'n' -> startNullToken()

            't' -> startTrueToken()

            '[' -> startArrayScope()

            CODE_R_BRACKET.toChar() -> if (formatReadFeatures and FEAT_MASK_TRAILING_COMMA != 0) {
                closeArrayScope()
            } else {
                startUnexpectedValue(ch, true)
            }

            '{' -> startObjectScope()

            CODE_R_CURLY.toChar() -> if (formatReadFeatures and FEAT_MASK_TRAILING_COMMA != 0) {
                closeObjectScope()
            } else {
                startUnexpectedValue(ch, true)
            }

            else -> startUnexpectedValue(ch, true)
        }
    }

    /**
     * Helper method called to detect type of value token (at any level), and possibly decode it if contained in input
     * buffer. Value MUST be preceded by a semicolon (which may be surrounded by white-space)
     */
    @Throws(CirJacksonException::class)
    private fun startValueExpectColon(code: Int): CirJsonToken? {
        var ch = code

        if (ch <= 0x0020) {
            ch = skipWhitespace(ch)

            if (ch <= 0) {
                myMinorState = MINOR_VALUE_EXPECTING_COLON
                return myCurrentToken
            }
        }

        if (ch != CODE_COLON) {
            return when (ch) {
                CODE_SLASH -> startSlashComment(MINOR_VALUE_EXPECTING_COLON)

                CODE_HASH -> finishHashComment(MINOR_VALUE_EXPECTING_COLON)

                else -> reportUnexpectedChar(ch.toChar(), "was expecting a colon to separate field name and value")
            }
        }

        val pointer = myInputPointer

        if (pointer >= myInputEnd) {
            myMinorState = MINOR_VALUE_LEADING_WS
            return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
        }

        ch = getByteFromBuffer(pointer).toInt()
        myInputPointer = pointer + 1

        if (ch <= 0x0020) {
            ch = skipWhitespace(ch)

            if (ch <= 0) {
                myMinorState = MINOR_VALUE_LEADING_WS
                return myCurrentToken
            }
        }

        updateTokenLocation()

        if (ch == CODE_QUOTE) {
            return startString()
        }

        return when (ch.toChar()) {
            '#' -> finishHashComment(MINOR_VALUE_LEADING_WS)

            '+' -> startPositiveNumber()

            '-' -> startNegativeNumber()

            '/' -> startSlashComment(MINOR_VALUE_LEADING_WS)

            '.' -> if (isEnabled(CirJsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)) {
                startFloatThatStartsWithPeriod()
            } else {
                startUnexpectedValue(ch, true)
            }

            '0' -> startNumberLeadingZero()

            '1', '2', '3', '4', '5', '6', '7', '8', '9' -> startPositiveNumber(ch)

            'f' -> startFalseToken()

            'n' -> startNullToken()

            't' -> startTrueToken()

            '[' -> startArrayScope()

            CODE_R_BRACKET.toChar() -> if (formatReadFeatures and FEAT_MASK_TRAILING_COMMA != 0) {
                closeArrayScope()
            } else {
                startUnexpectedValue(ch, true)
            }

            '{' -> startObjectScope()

            CODE_R_CURLY.toChar() -> if (formatReadFeatures and FEAT_MASK_TRAILING_COMMA != 0) {
                closeObjectScope()
            } else {
                startUnexpectedValue(ch, true)
            }

            else -> startUnexpectedValue(ch, false)
        }
    }

    /**
     * Method called when we have already gotten a comma (i.e. not the first value)
     */
    @Throws(CirJacksonException::class)
    private fun startValueAfterComma(code: Int): CirJsonToken? {
        var ch = code

        if (ch <= 0x0020) {
            ch = skipWhitespace(ch)

            if (ch <= 0) {
                myMinorState = MINOR_VALUE_WS_AFTER_COMMA
                return myCurrentToken
            }
        }

        updateTokenLocation()

        if (ch == CODE_QUOTE) {
            return startString()
        }

        return when (ch.toChar()) {
            '#' -> finishHashComment(MINOR_VALUE_WS_AFTER_COMMA)

            '+' -> startPositiveNumber()

            '-' -> startNegativeNumber()

            '/' -> startSlashComment(MINOR_VALUE_WS_AFTER_COMMA)

            '.' -> if (isEnabled(CirJsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)) {
                startFloatThatStartsWithPeriod()
            } else {
                startUnexpectedValue(ch, true)
            }

            '0' -> startNumberLeadingZero()

            '1', '2', '3', '4', '5', '6', '7', '8', '9' -> startPositiveNumber(ch)

            'f' -> startFalseToken()

            'n' -> startNullToken()

            't' -> startTrueToken()

            '[' -> startArrayScope()

            '{' -> startObjectScope()

            else -> {
                if (ch or 0x20 == CODE_R_CURLY && formatReadFeatures and FEAT_MASK_TRAILING_COMMA != 0) {
                    if (ch == CODE_R_BRACKET) {
                        closeArrayScope()
                    } else {
                        closeObjectScope()
                    }
                } else {
                    startUnexpectedValue(ch, true)
                }
            }
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun startUnexpectedValue(code: Int, leadingComma: Boolean): CirJsonToken? {
        if (code == CODE_R_BRACKET && streamReadContext!!.isInArray || code == CODE_COMMA) {
            if (!streamReadContext!!.isInRoot) {
                if (formatReadFeatures and FEAT_MASK_ALLOW_MISSING != 0) {
                    --myInputPointer
                    return valueComplete(CirJsonToken.VALUE_NULL)
                }
            }
        }

        return when (code.toChar()) {
            '\'' -> if (formatReadFeatures and FEAT_MASK_ALLOW_SINGLE_QUOTES != 0) {
                startApostropheString()
            } else {
                reportUnexpectedChar(code.toChar(), "expected a valid value ${validCirJsonValueList()}")
            }

            '+' -> finishNonStdToken(NON_STD_TOKEN_PLUS_INFINITY, 1)

            'N' -> finishNonStdToken(NON_STD_TOKEN_NAN, 1)

            'I' -> finishNonStdToken(NON_STD_TOKEN_INFINITY, 1)

            else -> reportUnexpectedChar(code.toChar(), "expected a valid value ${validCirJsonValueList()}")
        }
    }

    /*
     *******************************************************************************************************************
     * Second level decoding, skipping white-space, comments
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun skipWhitespace(code: Int): Int {
        var ch = code

        do {
            if (ch != CODE_SPACE) {
                if (ch == CODE_LF) {
                    ++myCurrentInputRow
                    myCurrentInputRowStart = myInputPointer
                } else if (ch == CODE_CR) {
                    ++myCurrentInputRowAlt
                    myCurrentInputRowStart = myInputPointer
                } else if (ch != CODE_TAB) {
                    return reportInvalidSpace(ch)
                }
            }

            if (myInputPointer >= myInputEnd) {
                myCurrentToken = CirJsonToken.NOT_AVAILABLE
                return 0
            }

            ch = nextUnsignedByteFromBuffer
        } while (ch <= 0x0020)

        return ch
    }

    @Throws(CirJacksonException::class)
    private fun startSlashComment(fromMinorState: Int): CirJsonToken? {
        if (formatReadFeatures and FEAT_MASK_ALLOW_JAVA_COMMENTS == 0) {
            return reportUnexpectedChar('/',
                    "maybe a (non-standard) comment? (not recognized as one since Feature 'ALLOW_COMMENTS' not enabled for parser)")
        }

        if (myInputPointer >= myInputEnd) {
            myPending32 = fromMinorState
            myMinorState = MINOR_COMMENT_LEADING_SLASH
            return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
        }

        return when (val ch = nextSignedByteFromBuffer.toInt()) {
            CODE_ASTERISK -> finishCComment(fromMinorState, false)

            CODE_SLASH -> finishCppComment(fromMinorState)

            else -> reportUnexpectedChar((ch and 0xFF).toChar(), "was expecting either '*' or '/' for a comment")
        }
    }

    @Throws(CirJacksonException::class)
    private fun finishHashComment(fromMinorState: Int): CirJsonToken? {
        if (formatReadFeatures and FEAT_MASK_ALLOW_YAML_COMMENTS == 0) {
            return reportUnexpectedChar('#',
                    "maybe a (non-standard) comment? (not recognized as one since Feature 'ALLOW_YAML_COMMENTS' not enabled for parser)")
        }

        while (true) {
            if (myInputPointer >= myInputEnd) {
                myPending32 = fromMinorState
                myMinorState = MINOR_COMMENT_YAML
                return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
            }

            val ch = nextUnsignedByteFromBuffer

            if (ch < 0x020) {
                if (ch == CODE_LF) {
                    ++myCurrentInputRow
                    myCurrentInputRowStart = myInputPointer
                    break
                } else if (ch == CODE_CR) {
                    ++myCurrentInputRowAlt
                    myCurrentInputRowStart = myInputPointer
                    break
                } else if (ch != CODE_TAB) {
                    return reportInvalidSpace(ch)
                }
            }
        }

        return startAfterComment(fromMinorState)
    }

    @Throws(CirJacksonException::class)
    private fun finishCppComment(fromMinorState: Int): CirJsonToken? {
        while (true) {
            if (myInputPointer >= myInputEnd) {
                myPending32 = fromMinorState
                myMinorState = MINOR_COMMENT_CPP
                return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
            }

            val ch = nextUnsignedByteFromBuffer

            if (ch < 0x020) {
                if (ch == CODE_LF) {
                    ++myCurrentInputRow
                    myCurrentInputRowStart = myInputPointer
                    break
                } else if (ch == CODE_CR) {
                    ++myCurrentInputRowAlt
                    myCurrentInputRowStart = myInputPointer
                    break
                } else if (ch != CODE_TAB) {
                    return reportInvalidSpace(ch)
                }
            }
        }

        return startAfterComment(fromMinorState)
    }

    @Throws(CirJacksonException::class)
    private fun finishCComment(fromMinorState: Int, gotStar: Boolean): CirJsonToken? {
        var realGotStar = gotStar

        while (true) {
            if (myInputPointer >= myInputEnd) {
                myPending32 = fromMinorState
                myMinorState = if (realGotStar) MINOR_COMMENT_CLOSING_ASTERISK else MINOR_COMMENT_C
                return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
            }

            val ch = nextUnsignedByteFromBuffer

            if (ch < 0x020) {
                if (ch == CODE_LF) {
                    ++myCurrentInputRow
                    myCurrentInputRowStart = myInputPointer
                } else if (ch == CODE_CR) {
                    ++myCurrentInputRowAlt
                    myCurrentInputRowStart = myInputPointer
                } else if (ch != CODE_TAB) {
                    return reportInvalidSpace(ch)
                }
            } else if (ch == CODE_ASTERISK) {
                realGotStar = true
                continue
            } else if (ch == CODE_SLASH) {
                if (realGotStar) {
                    break
                }
            }

            realGotStar = false
        }

        return startAfterComment(fromMinorState)
    }

    @Throws(CirJacksonException::class)
    private fun startAfterComment(fromMinorState: Int): CirJsonToken? {
        if (myInputPointer >= myInputEnd) {
            myMinorState = fromMinorState
            return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
        }

        val ch = nextUnsignedByteFromBuffer

        return when (fromMinorState) {
            MINOR_PROPERTY_LEADING_WS -> startIdName(ch)

            MINOR_PROPERTY_LEADING_COMMA -> startNameAfterComma(ch)

            MINOR_PROPERTY_LEADING_WS_AFTER_COMMA -> startNameAfterCommaAndWhitespace(ch)

            MINOR_VALUE_LEADING_WS -> startValue(ch)

            MINOR_VALUE_EXPECTING_COMMA -> startValueExpectComma(ch)

            MINOR_VALUE_EXPECTING_COLON -> startValueExpectColon(ch)

            MINOR_VALUE_WS_AFTER_COMMA -> startValueAfterComma(ch)

            else -> Other.throwInternalReturnAny()
        }
    }

    /*
     *******************************************************************************************************************
     * Tertiary level decoding, simple tokens decoding
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun startFalseToken(): CirJsonToken? {
        var pointer = myInputPointer

        if (pointer + 4 < myInputEnd) {
            if (getByteFromBuffer(pointer++) == 'a'.code.toByte() && getByteFromBuffer(pointer++) == 'l'.code.toByte()
                    && getByteFromBuffer(pointer++) == 's'.code.toByte()
                    && getByteFromBuffer(pointer++) == 'e'.code.toByte()) {
                val ch = getByteFromBuffer(pointer).toInt() and 0xFF

                if (ch < CODE_0 || ch or 0x20 == CODE_R_CURLY) {
                    myInputPointer = pointer
                    return valueComplete(CirJsonToken.VALUE_FALSE)
                }
            }
        }

        myMinorState = MINOR_VALUE_TOKEN_FALSE
        return finishKeywordToken("false", 1, CirJsonToken.VALUE_FALSE)
    }

    @Throws(CirJacksonException::class)
    protected open fun startTrueToken(): CirJsonToken? {
        var pointer = myInputPointer

        if (pointer + 3 < myInputEnd) {
            if (getByteFromBuffer(pointer++) == 'r'.code.toByte() && getByteFromBuffer(pointer++) == 'u'.code.toByte()
                    && getByteFromBuffer(pointer++) == 'e'.code.toByte()) {
                val ch = getByteFromBuffer(pointer).toInt() and 0xFF

                if (ch < CODE_0 || ch or 0x20 == CODE_R_CURLY) {
                    myInputPointer = pointer
                    return valueComplete(CirJsonToken.VALUE_TRUE)
                }
            }
        }

        myMinorState = MINOR_VALUE_TOKEN_TRUE
        return finishKeywordToken("true", 1, CirJsonToken.VALUE_TRUE)
    }

    @Throws(CirJacksonException::class)
    protected open fun startNullToken(): CirJsonToken? {
        var pointer = myInputPointer

        if (pointer + 3 < myInputEnd) {
            if (getByteFromBuffer(pointer++) == 'u'.code.toByte() && getByteFromBuffer(pointer++) == 'l'.code.toByte()
                    && getByteFromBuffer(pointer++) == 'l'.code.toByte()) {
                val ch = getByteFromBuffer(pointer).toInt() and 0xFF

                if (ch < CODE_0 || ch or 0x20 == CODE_R_CURLY) {
                    myInputPointer = pointer
                    return valueComplete(CirJsonToken.VALUE_NULL)
                }
            }
        }

        myMinorState = MINOR_VALUE_TOKEN_NULL
        return finishKeywordToken("true", 1, CirJsonToken.VALUE_NULL)
    }

    @Throws(CirJacksonException::class)
    protected open fun finishKeywordToken(expectedToken: String, matched: Int, result: CirJsonToken): CirJsonToken? {
        var realMatched = matched
        val end = expectedToken.length

        while (true) {
            if (myInputPointer >= myInputEnd) {
                myPending32 = realMatched
                return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
            }

            val ch = getByteFromBuffer(myInputPointer).toInt()

            if (realMatched == end) {
                if (ch < CODE_0 || ch or 0x20 == CODE_R_CURLY) {
                    return valueComplete(result)
                }

                break
            }

            if (ch != expectedToken[realMatched].code) {
                break
            }

            ++realMatched
            ++myInputPointer
        }

        myMinorState = MINOR_VALUE_TOKEN_ERROR
        myTextBuffer.resetWithCopy(expectedToken, 0, realMatched)
        return finishErrorToken()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishKeywordTokenWithEOF(expectedToken: String, matched: Int,
            result: CirJsonToken): CirJsonToken? {
        if (matched == expectedToken.length) {
            return result.also { myCurrentToken = it }
        }

        myTextBuffer.resetWithCopy(expectedToken, 0, matched)
        return finishErrorTokenWithEOF()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNonStdToken(type: Int, matched: Int): CirJsonToken? {
        var realMatched = matched
        val expectedToken = nonStdToken(type)
        val end = expectedToken.length

        while (true) {
            if (myInputPointer >= myInputEnd) {
                myNonStdTokenType = type
                myPending32 = realMatched
                myMinorState = MINOR_VALUE_TOKEN_NON_STD
                return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
            }

            val ch = getByteFromBuffer(myInputPointer).toInt()

            if (realMatched == end) {
                if (ch < CODE_0 || ch or 0x20 == CODE_R_CURLY) {
                    return valueNonStdNumberComplete(type)
                }

                break
            }

            if (ch != expectedToken[realMatched].code) {
                break
            }

            ++realMatched
            ++myInputPointer
        }

        myMinorState = MINOR_VALUE_TOKEN_ERROR
        myTextBuffer.resetWithCopy(expectedToken, 0, realMatched)
        return finishErrorToken()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNonStdTokenWithEOF(type: Int, matched: Int): CirJsonToken? {
        val expectedToken = nonStdToken(type)

        if (matched == expectedToken.length) {
            return valueNonStdNumberComplete(type)
        }

        myTextBuffer.resetWithCopy(expectedToken, 0, matched)
        return finishErrorTokenWithEOF()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishErrorToken(): CirJsonToken {
        while (myInputPointer < myInputEnd) {
            val ch = nextSignedByteFromBuffer.toInt().toChar()

            if (ch.isJavaIdentifierPart()) {
                myTextBuffer.append(ch)

                if (myTextBuffer.size < myIOContext!!.errorReportConfiguration.maxErrorTokenLength) {
                    continue
                }
            }

            return reportErrorToken(myTextBuffer.contentsAsString())
        }

        return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
    }

    @Throws(CirJacksonException::class)
    protected open fun finishErrorTokenWithEOF(): CirJsonToken {
        return reportErrorToken(myTextBuffer.contentsAsString())
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
        myNumberNegative = false
        myIntegralLength = 0
        val outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
        return startFloat(outputBuffer, 0, CODE_PERIOD)
    }

    @Throws(CirJacksonException::class)
    protected open fun startPositiveNumber(code: Int): CirJsonToken? {
        var ch = code
        myNumberNegative = false
        var outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
        outputBuffer[0] = ch.toChar()

        if (myInputPointer >= myInputEnd) {
            myMinorState = MINOR_NUMBER_INTEGER_DIGITS
            myTextBuffer.currentSegmentSize = 1
            return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
        }

        var outputPointer = 1

        ch = getByteFromBuffer(myInputPointer).toInt() and 0xFF

        while (true) {
            if (ch < CODE_0) {
                if (ch == CODE_PERIOD) {
                    myIntegralLength = outputPointer
                    ++myInputPointer
                    return startFloat(outputBuffer, outputPointer, ch)
                }

                break
            }

            if (ch > CODE_9) {
                if (ch == CODE_E_LOWERCASE || ch == CODE_E_UPPERCASE) {
                    myIntegralLength = outputPointer
                    ++myInputPointer
                    return startFloat(outputBuffer, outputPointer, ch)
                }

                break
            }

            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.expandCurrentSegment()
            }

            outputBuffer[outputPointer++] = ch.toChar()

            if (++myInputPointer >= myInputEnd) {
                myMinorState = MINOR_NUMBER_INTEGER_DIGITS
                myTextBuffer.currentSegmentSize = outputPointer
                return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
            }

            ch = getByteFromBuffer(myInputPointer).toInt() and 0xFF
        }

        myIntegralLength = outputPointer
        myTextBuffer.currentSegmentSize = outputPointer
        return valueComplete(CirJsonToken.VALUE_NUMBER_INT)
    }

    @Throws(CirJacksonException::class)
    protected open fun startNegativeNumber(): CirJsonToken? {
        myNumberNegative = true

        if (myInputPointer >= myInputEnd) {
            myMinorState = MINOR_NUMBER_MINUS
            return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
        }

        var ch = nextUnsignedByteFromBuffer

        if (ch <= CODE_0) {
            return if (ch == CODE_0) {
                finishNumberLeadingNegZero()
            } else if (ch == CODE_PERIOD && isEnabled(CirJsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)) {
                val outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
                outputBuffer[0] = '-'
                outputBuffer[1] = '0'
                myIntegralLength = 1
                startFloat(outputBuffer, 2, ch)
            } else {
                reportUnexpectedChar(ch.toChar(), "expected digit (0-9) to follow minus sign, for valid numeric value")
            }
        } else if (ch > CODE_9) {
            return if (ch == 'I'.code) {
                finishNonStdToken(NON_STD_TOKEN_MINUS_INFINITY, 2)
            } else {
                reportUnexpectedChar(ch.toChar(), "expected digit (0-9) to follow minus sign, for valid numeric value")
            }
        }

        var outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
        outputBuffer[0] = '-'
        outputBuffer[1] = ch.toChar()

        if (myInputPointer >= myInputEnd) {
            myMinorState = MINOR_NUMBER_INTEGER_DIGITS
            myTextBuffer.currentSegmentSize = 2
            myIntegralLength = 1
            return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
        }

        ch = getByteFromBuffer(myInputPointer).toInt() and 0xFF
        var outputPointer = 2

        while (true) {
            if (ch < CODE_0) {
                if (ch == CODE_PERIOD) {
                    myIntegralLength = outputPointer - 1
                    ++myInputPointer
                    return startFloat(outputBuffer, outputPointer, ch)
                }

                break
            }

            if (ch > CODE_9) {
                if (ch == CODE_E_LOWERCASE || ch == CODE_E_UPPERCASE) {
                    myIntegralLength = outputPointer - 1
                    ++myInputPointer
                    return startFloat(outputBuffer, outputPointer, ch)
                } else if (ch or 0x20 != CODE_R_CURLY) {
                    return reportUnexpectedChar(ch.toChar(), "Malformed number")
                }

                break
            }

            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.expandCurrentSegment()
            }

            outputBuffer[outputPointer++] = ch.toChar()

            if (++myInputPointer >= myInputEnd) {
                myMinorState = MINOR_NUMBER_INTEGER_DIGITS
                myTextBuffer.currentSegmentSize = outputPointer
                return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
            }

            ch = getByteFromBuffer(myInputPointer).toInt() and 0xFF
        }

        myIntegralLength = outputPointer
        myTextBuffer.currentSegmentSize = outputPointer
        return valueComplete(CirJsonToken.VALUE_NUMBER_INT)
    }

    @Throws(CirJacksonException::class)
    protected open fun startPositiveNumber(): CirJsonToken? {
        myNumberNegative = false

        if (myInputPointer >= myInputEnd) {
            myMinorState = MINOR_NUMBER_PLUS
            return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
        }

        var ch = nextUnsignedByteFromBuffer

        if (ch <= CODE_0) {
            return if (ch == CODE_0) {
                if (isEnabled(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)) {
                    finishNumberLeadingPosZero()
                } else {
                    reportUnexpectedNumberChar('+',
                            "CirJSON spec does not allow numbers to have plus signs: enable `CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS` to allow")
                }
            } else if (ch == CODE_PERIOD && isEnabled(CirJsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)) {
                val outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
                outputBuffer[0] = '+'
                outputBuffer[1] = '0'
                myIntegralLength = 1
                startFloat(outputBuffer, 2, ch)
            } else {
                reportUnexpectedChar(ch.toChar(), "expected digit (0-9) to follow minus sign, for valid numeric value")
            }
        } else if (ch > CODE_9) {
            return if (ch == 'I'.code) {
                finishNonStdToken(NON_STD_TOKEN_PLUS_INFINITY, 2)
            } else {
                reportUnexpectedChar(ch.toChar(), "expected digit (0-9) to follow minus sign, for valid numeric value")
            }
        }

        if (!isEnabled(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)) {
            return reportUnexpectedNumberChar('+',
                    "CirJSON spec does not allow numbers to have plus signs: enable `CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS` to allow")
        }

        var outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
        outputBuffer[0] = '+'
        outputBuffer[1] = ch.toChar()

        if (myInputPointer >= myInputEnd) {
            myMinorState = MINOR_NUMBER_INTEGER_DIGITS
            myTextBuffer.currentSegmentSize = 2
            myIntegralLength = 1
            return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
        }

        ch = getByteFromBuffer(myInputPointer).toInt() and 0xFF
        var outputPointer = 2

        while (true) {
            if (ch < CODE_0) {
                if (ch == CODE_PERIOD) {
                    myIntegralLength = outputPointer - 1
                    ++myInputPointer
                    return startFloat(outputBuffer, outputPointer, ch)
                }

                break
            }

            if (ch > CODE_9) {
                if (ch == CODE_E_LOWERCASE || ch == CODE_E_UPPERCASE) {
                    myIntegralLength = outputPointer - 1
                    ++myInputPointer
                    return startFloat(outputBuffer, outputPointer, ch)
                } else if (ch or 0x20 != CODE_R_CURLY) {
                    return reportUnexpectedChar(ch.toChar(), "Malformed number")
                }

                break
            }

            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.expandCurrentSegment()
            }

            outputBuffer[outputPointer++] = ch.toChar()

            if (++myInputPointer >= myInputEnd) {
                myMinorState = MINOR_NUMBER_INTEGER_DIGITS
                myTextBuffer.currentSegmentSize = outputPointer
                return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
            }

            ch = getByteFromBuffer(myInputPointer).toInt() and 0xFF
        }

        myIntegralLength = outputPointer
        myTextBuffer.currentSegmentSize = outputPointer
        return valueComplete(CirJsonToken.VALUE_NUMBER_INT)
    }

    @Throws(CirJacksonException::class)
    protected open fun startNumberLeadingZero(): CirJsonToken? {
        var pointer = myInputPointer

        if (pointer >= myInputEnd) {
            myMinorState = MINOR_NUMBER_ZERO
            return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
        }

        val ch = getByteFromBuffer(pointer++).toInt() and 0xFF

        if (ch !in CODE_0..CODE_9) {
            if (ch == CODE_PERIOD || ch == CODE_E_LOWERCASE || ch == CODE_E_UPPERCASE) {
                myInputPointer = pointer
                myIntegralLength = 1
                val outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
                outputBuffer[0] = '0'
                return startFloat(outputBuffer, 1, ch)
            }

            if (ch or 0x20 != CODE_R_CURLY && ch > CODE_9) {
                return reportUnexpectedChar(ch.toChar(),
                        "expected digit (0-9) to follow minus sign, for valid numeric value")
            }
        } else {
            return finishNumberLeadingZero()
        }

        return valueCompleteInt(0, "0")
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNumberPlus(code: Int): CirJsonToken? {
        return finishNumberPlusMinus(code, false)
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNumberMinus(code: Int): CirJsonToken? {
        return finishNumberPlusMinus(code, true)
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNumberPlusMinus(code: Int, negative: Boolean): CirJsonToken? {
        return if (code <= CODE_0) {
            if (code == CODE_0) {
                if (negative) {
                    finishNumberLeadingNegZero()
                } else if (isEnabled(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)) {
                    finishNumberLeadingPosZero()
                } else {
                    reportUnexpectedNumberChar('+',
                            "CirJSON spec does not allow numbers to have plus signs: enable `CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS` to allow")
                }
            } else if (code == CODE_PERIOD && isEnabled(CirJsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)) {
                if (negative) {
                    myInputPointer--
                    finishNumberLeadingNegZero()
                } else if (isEnabled(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)) {
                    myInputPointer--
                    finishNumberLeadingPosZero()
                } else {
                    reportUnexpectedNumberChar('+',
                            "CirJSON spec does not allow numbers to have plus signs: enable `CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS` to allow")
                }
            } else {
                val message = if (negative) {
                    "expected digit (0-9) to follow minus sign, for valid numeric value"
                } else {
                    "expected digit (0-9) for valid numeric value"
                }
                reportUnexpectedNumberChar(code.toChar(), message)
            }
        } else if (code > CODE_9) {
            if (code == 'I'.code) {
                val token = if (negative) NON_STD_TOKEN_MINUS_INFINITY else NON_STD_TOKEN_PLUS_INFINITY
                finishNonStdToken(token, 2)
            } else {
                val message = if (negative) {
                    "expected digit (0-9) to follow minus sign, for valid numeric value"
                } else {
                    "expected digit (0-9) for valid numeric value"
                }
                reportUnexpectedNumberChar(code.toChar(), message)
            }
        } else if (!negative && !isEnabled(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)) {
            reportUnexpectedNumberChar('+',
                    "CirJSON spec does not allow numbers to have plus signs: enable `CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS` to allow")
        } else {
            val outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
            outputBuffer[0] = if (negative) '-' else '+'
            outputBuffer[1] = code.toChar()
            myIntegralLength = 1
            finishNumberIntegralPart(outputBuffer, 2)
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNumberLeadingZero(): CirJsonToken? {
        while (true) {
            if (myInputPointer >= myInputEnd) {
                myMinorState = MINOR_NUMBER_ZERO
                return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
            }

            val ch = nextUnsignedByteFromBuffer

            if (ch !in CODE_0..CODE_9) {
                if (ch == CODE_PERIOD || ch == CODE_E_LOWERCASE || ch == CODE_E_UPPERCASE) {
                    myIntegralLength = 1
                    val outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
                    outputBuffer[0] = '0'
                    return startFloat(outputBuffer, 1, ch)
                }

                if (ch or 0x20 != CODE_R_CURLY) {
                    return reportUnexpectedChar(ch.toChar(),
                            "expected digit (0-9) to follow minus sign, for valid numeric value")
                }
            } else {
                if (formatReadFeatures and FEAT_MASK_LEADING_ZEROS == 0) {
                    return reportInvalidNumber("Leading zeroes not allowed")
                }

                if (ch == CODE_0) {
                    continue
                }

                val outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
                outputBuffer[0] = ch.toChar()
                myIntegralLength = 1
                return finishNumberIntegralPart(outputBuffer, 1)
            }

            --myInputPointer
            return valueCompleteInt(0, "0")
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNumberLeadingPosZero(): CirJsonToken? {
        return finishNumberLeadingPosNegZero(false)
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNumberLeadingNegZero(): CirJsonToken? {
        return finishNumberLeadingPosNegZero(true)
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNumberLeadingPosNegZero(negative: Boolean): CirJsonToken? {
        while (true) {
            if (myInputPointer >= myInputEnd) {
                myMinorState = if (negative) MINOR_NUMBER_MINUS_ZERO else MINOR_NUMBER_PLUS_ZERO
                return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
            }

            val ch = nextUnsignedByteFromBuffer

            if (ch !in CODE_0..CODE_9) {
                if (ch == CODE_PERIOD || ch == CODE_E_LOWERCASE || ch == CODE_E_UPPERCASE) {
                    val outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
                    outputBuffer[0] = if (negative) '-' else '+'
                    outputBuffer[1] = '0'
                    myIntegralLength = 1
                    return startFloat(outputBuffer, 2, ch)
                }

                if (ch or 0x20 != CODE_R_CURLY) {
                    return reportUnexpectedChar(ch.toChar(),
                            "expected digit (0-9) to follow minus sign, for valid numeric value")
                }
            } else {
                if (formatReadFeatures and FEAT_MASK_LEADING_ZEROS == 0) {
                    return reportInvalidNumber("Leading zeroes not allowed")
                }

                if (ch == CODE_0) {
                    continue
                }

                val outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()
                outputBuffer[0] = if (negative) '-' else '+'
                outputBuffer[1] = ch.toChar()
                myIntegralLength = 1
                return finishNumberIntegralPart(outputBuffer, 2)
            }

            --myInputPointer
            return valueCompleteInt(0, "0")
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNumberIntegralPart(outputBuffer: CharArray, outputPointer: Int): CirJsonToken? {
        var realOutputBuffer = outputBuffer
        var realOutputPointer = outputPointer
        val negMod = if (myNumberNegative) -1 else 0

        while (true) {
            if (myInputPointer >= myInputEnd) {
                myMinorState = MINOR_NUMBER_INTEGER_DIGITS
                myTextBuffer.currentSegmentSize = realOutputPointer
                return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
            }

            val ch = getByteFromBuffer(myInputPointer).toInt() and 0xFF

            if (ch !in CODE_0..CODE_9) {
                if (ch == CODE_PERIOD || ch == CODE_E_LOWERCASE || ch == CODE_E_UPPERCASE) {
                    myIntegralLength = realOutputPointer + negMod
                    ++myInputPointer
                    return startFloat(realOutputBuffer, realOutputPointer, ch)
                } else if (ch > 0x20 && ch or 0x20 != CODE_R_CURLY && ch != CODE_COMMA && ch != CODE_HASH &&
                        ch != CODE_SLASH) {
                    return reportUnexpectedChar(ch.toChar(), "Malformed number")
                }

                break
            }

            ++myInputPointer

            if (realOutputPointer >= realOutputBuffer.size) {
                realOutputBuffer = myTextBuffer.expandCurrentSegment()
            }

            realOutputBuffer[realOutputPointer++] = ch.toChar()
        }

        myIntegralLength = realOutputPointer + negMod
        myTextBuffer.currentSegmentSize = realOutputPointer
        return valueComplete(CirJsonToken.VALUE_NUMBER_INT)
    }

    @Throws(CirJacksonException::class)
    protected open fun startFloat(outputBuffer: CharArray, outputPointer: Int, code: Int): CirJsonToken? {
        var realOutputBuffer = outputBuffer
        var realOutputPointer = outputPointer
        var ch = code
        var fractionLength = 0

        if (ch == CODE_PERIOD) {
            if (realOutputPointer >= realOutputBuffer.size) {
                realOutputBuffer = myTextBuffer.expandCurrentSegment()
            }

            realOutputBuffer[realOutputPointer++] = '.'

            while (true) {
                if (myInputPointer >= myInputEnd) {
                    myMinorState = MINOR_NUMBER_FRACTION_DIGITS
                    myTextBuffer.currentSegmentSize = realOutputPointer
                    myFractionLength = fractionLength
                    return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
                }

                ch = nextUnsignedByteFromBuffer

                if (ch !in CODE_0..CODE_9) {
                    ch = ch and 0xFF

                    if (fractionLength == 0) {
                        if (!isEnabled(CirJsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS)) {
                            return reportUnexpectedNumberChar(ch.toChar(), "Decimal point not followed by a digit")
                        }
                    }

                    break
                }

                if (realOutputPointer >= realOutputBuffer.size) {
                    realOutputBuffer = myTextBuffer.expandCurrentSegment()
                }

                realOutputBuffer[realOutputPointer++] = ch.toChar()
                ++fractionLength
            }
        }

        myFractionLength = fractionLength
        var exponentLength = 0

        if (ch == CODE_E_LOWERCASE || ch == CODE_E_UPPERCASE) {
            if (realOutputPointer >= realOutputBuffer.size) {
                realOutputBuffer = myTextBuffer.expandCurrentSegment()
            }

            realOutputBuffer[realOutputPointer++] = ch.toChar()

            if (myInputPointer >= myInputEnd) {
                myMinorState = MINOR_NUMBER_EXPONENT_MARKER
                myTextBuffer.currentSegmentSize = realOutputPointer
                myExponentLength = 0
                return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
            }

            ch = nextSignedByteFromBuffer.toInt()

            if (ch == CODE_MINUS || ch == CODE_PLUS) {
                if (realOutputPointer >= realOutputBuffer.size) {
                    realOutputBuffer = myTextBuffer.expandCurrentSegment()
                }

                realOutputBuffer[realOutputPointer++] = ch.toChar()

                if (myInputPointer >= myInputEnd) {
                    myMinorState = MINOR_NUMBER_EXPONENT_DIGITS
                    myTextBuffer.currentSegmentSize = realOutputPointer
                    myExponentLength = 0
                    return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
                }

                ch = nextSignedByteFromBuffer.toInt()
            }

            while (ch in CODE_0..CODE_9) {
                exponentLength++

                if (realOutputPointer >= realOutputBuffer.size) {
                    realOutputBuffer = myTextBuffer.expandCurrentSegment()
                }

                realOutputBuffer[realOutputPointer++] = ch.toChar()

                if (myInputPointer >= myInputEnd) {
                    myMinorState = MINOR_NUMBER_EXPONENT_DIGITS
                    myTextBuffer.currentSegmentSize = realOutputPointer
                    myExponentLength = exponentLength
                    return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
                }

                ch = nextSignedByteFromBuffer.toInt()
            }

            ch = ch and 0xFF

            if (exponentLength == 0) {
                return reportUnexpectedChar(ch.toChar(), "Exponent indicator not followed by a digit")
            }
        } else if (ch > 0x20 && ch or 0x20 != CODE_R_CURLY && ch != CODE_COMMA && ch != CODE_HASH && ch != CODE_SLASH) {
            return reportUnexpectedChar(ch.toChar(), "Malformed number")
        }

        --myInputPointer
        myExponentLength = exponentLength
        myTextBuffer.currentSegmentSize = realOutputPointer
        return valueComplete(CirJsonToken.VALUE_NUMBER_FLOAT)
    }

    @Throws(CirJacksonException::class)
    protected open fun finishFloatFraction(): CirJsonToken? {
        var fractionLength = myFractionLength
        var outputBuffer = myTextBuffer.bufferWithoutReset!!
        var outputPointer = myTextBuffer.currentSegmentSize

        var ch = nextSignedByteFromBuffer.toInt()
        var loop = true

        while (loop) {
            when {
                ch in CODE_0..CODE_9 -> {
                    ++fractionLength

                    if (outputPointer >= outputBuffer.size) {
                        outputBuffer = myTextBuffer.expandCurrentSegment()
                    }

                    outputBuffer[outputPointer++] = ch.toChar()

                    if (myInputPointer >= myInputEnd) {
                        myTextBuffer.currentSegmentSize = outputPointer
                        myFractionLength = fractionLength
                        return CirJsonToken.NOT_AVAILABLE
                    }

                    ch = nextSignedByteFromBuffer.toInt()
                }

                ch or 0x22 == 'f'.code -> {
                    return reportUnexpectedChar(ch.toChar(),
                            "CirJSON does not support parsing numbers that have 'F', 'f', 'G', or 'd' suffixes")
                }

                ch == CODE_PERIOD -> {
                    return reportUnexpectedChar(ch.toChar(), "Cannot parse number with more than one decimal point")
                }

                else -> {
                    loop = false
                }
            }
        }

        if (fractionLength == 0) {
            if (!isEnabled(CirJsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS)) {
                return reportUnexpectedChar(ch.toChar(), "Decimal point not followed by a digit")
            }
        }

        myFractionLength = fractionLength
        myTextBuffer.currentSegmentSize = outputPointer

        if (ch == CODE_E_LOWERCASE || ch == CODE_E_UPPERCASE) {
            myTextBuffer.append(ch.toChar())
            myExponentLength = 0

            return if (myInputPointer < myInputEnd) {
                myMinorState = MINOR_NUMBER_EXPONENT_DIGITS
                finishFloatExponent(nextUnsignedByteFromBuffer, true)
            } else {
                myMinorState = MINOR_NUMBER_EXPONENT_MARKER
                CirJsonToken.NOT_AVAILABLE
            }
        } else if (ch > 0x20 && ch or 0x20 != CODE_R_CURLY && ch != CODE_COMMA && ch != CODE_HASH && ch != CODE_SLASH) {
            return reportUnexpectedChar(ch.toChar(), "Malformed number")
        }

        --myInputPointer
        myExponentLength = 0
        myTextBuffer.currentSegmentSize = outputPointer
        return valueComplete(CirJsonToken.VALUE_NUMBER_FLOAT)
    }

    @Throws(CirJacksonException::class)
    protected open fun finishFloatExponent(code: Int, checkSign: Boolean): CirJsonToken? {
        var ch = code

        if (checkSign) {
            myMinorState = MINOR_NUMBER_EXPONENT_DIGITS

            if (ch == CODE_MINUS || ch == CODE_PLUS) {
                myTextBuffer.append(ch.toChar())

                if (myInputPointer >= myInputEnd) {
                    myMinorState = MINOR_NUMBER_EXPONENT_DIGITS
                    myExponentLength = 0
                    return CirJsonToken.NOT_AVAILABLE
                }

                ch = nextSignedByteFromBuffer.toInt()
            }
        }

        var outputBuffer = myTextBuffer.bufferWithoutReset!!
        var outputPointer = myTextBuffer.currentSegmentSize
        var exponentLength = myExponentLength

        while (ch in CODE_0..CODE_9) {
            ++exponentLength
            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.expandCurrentSegment()
            }

            outputBuffer[outputPointer++] = ch.toChar()

            if (myInputPointer >= myInputEnd) {
                myTextBuffer.currentSegmentSize = outputPointer
                myExponentLength = exponentLength
                return CirJsonToken.NOT_AVAILABLE
            }

            ch = nextSignedByteFromBuffer.toInt()
        }

        ch = ch and 0xFF

        if (exponentLength == 0) {
            return reportUnexpectedChar(ch.toChar(), "Exponent indicator not followed by a digit")
        } else if (ch > 0x20 && ch or 0x20 != CODE_R_CURLY && ch != CODE_COMMA && ch != CODE_HASH && ch != CODE_SLASH) {
            return reportUnexpectedChar(ch.toChar(), "Malformed number")
        }

        --myInputPointer
        myExponentLength = exponentLength
        myTextBuffer.currentSegmentSize = outputPointer
        return valueComplete(CirJsonToken.VALUE_NUMBER_FLOAT)
    }

    /*
     *******************************************************************************************************************
     * Tertiary level decoding, name decoding
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun fastParseName(): String? {
        val codes = INPUT_CODE_LATIN1
        var pointer = myInputPointer

        val q0 = getByteFromBuffer(pointer++).toInt() and 0xFF

        return if (codes[q0] == 0) {
            var i = getByteFromBuffer(pointer++).toInt() and 0xFF

            if (codes[i] == 0) {
                var q = q0 shl 8 or i
                i = getByteFromBuffer(pointer++).toInt() and 0xFF

                if (codes[i] == 0) {
                    q = q shl 8 or i
                    i = getByteFromBuffer(pointer++).toInt() and 0xFF

                    if (codes[i] == 0) {
                        q = q shl 8 or i
                        i = getByteFromBuffer(pointer++).toInt() and 0xFF

                        if (codes[i] == 0) {
                            myQuad1 = q
                            parseMediumName(pointer, i)
                        } else if (i == CODE_QUOTE) {
                            myInputPointer = pointer
                            findName(q, 4)
                        } else {
                            null
                        }
                    } else if (i == CODE_QUOTE) {
                        myInputPointer = pointer
                        findName(q, 3)
                    } else {
                        null
                    }
                } else if (i == CODE_QUOTE) {
                    myInputPointer = pointer
                    findName(q, 2)
                } else {
                    null
                }
            } else if (i == CODE_QUOTE) {
                myInputPointer = pointer
                findName(q0, 1)
            } else {
                null
            }
        } else if (q0 == CODE_QUOTE) {
            myInputPointer = pointer
            ""
        } else {
            null
        }
    }

    @Throws(CirJacksonException::class)
    private fun parseMediumName(pointer: Int, quad2: Int): String? {
        var realPointer = pointer
        var q2 = quad2
        val codes = INPUT_CODE_LATIN1

        var i = getByteFromBuffer(realPointer++).toInt() and 0xFF

        return if (codes[i] == 0) {
            q2 = q2 shl 8 or i
            i = getByteFromBuffer(realPointer++).toInt() and 0xFF

            if (codes[i] == 0) {
                q2 = q2 shl 8 or i
                i = getByteFromBuffer(realPointer++).toInt() and 0xFF

                if (codes[i] == 0) {
                    q2 = q2 shl 8 or i
                    i = getByteFromBuffer(realPointer++).toInt() and 0xFF

                    if (codes[i] == 0) {
                        parseMediumName(realPointer, i, q2)
                    } else if (i == CODE_QUOTE) {
                        myInputPointer = realPointer
                        findName(myQuad1, q2, 4)
                    } else {
                        null
                    }
                } else if (i == CODE_QUOTE) {
                    myInputPointer = realPointer
                    findName(myQuad1, q2, 3)
                } else {
                    null
                }
            } else if (i == CODE_QUOTE) {
                myInputPointer = realPointer
                findName(myQuad1, q2, 2)
            } else {
                null
            }
        } else if (i == CODE_QUOTE) {
            myInputPointer = realPointer
            findName(myQuad1, q2, 1)
        } else {
            null
        }
    }

    @Throws(CirJacksonException::class)
    private fun parseMediumName(pointer: Int, quad3: Int, q2: Int): String? {
        var realPointer = pointer
        var q3 = quad3
        val codes = INPUT_CODE_LATIN1

        var i = getByteFromBuffer(realPointer++).toInt() and 0xFF

        if (codes[i] != 0) {
            return if (i == CODE_QUOTE) {
                myInputPointer = realPointer
                findName(myQuad1, q2, q3, 1)
            } else {
                null
            }
        }

        q3 = q3 shl 8 or i
        i = getByteFromBuffer(realPointer++).toInt() and 0xFF

        if (codes[i] != 0) {
            return if (i == CODE_QUOTE) {
                myInputPointer = realPointer
                findName(myQuad1, q2, q3, 2)
            } else {
                null
            }
        }

        q3 = q3 shl 8 or i
        i = getByteFromBuffer(realPointer++).toInt() and 0xFF

        if (codes[i] != 0) {
            return if (i == CODE_QUOTE) {
                myInputPointer = realPointer
                findName(myQuad1, q2, q3, 3)
            } else {
                null
            }
        }

        q3 = q3 shl 8 or i
        i = getByteFromBuffer(realPointer++).toInt() and 0xFF

        return if (codes[i] != 0) {
            if (i == CODE_QUOTE) {
                myInputPointer = realPointer
                findName(myQuad1, q2, q3, 4)
            } else {
                null
            }
        } else {
            null
        }
    }

    /**
     * Slower parsing method which is generally branched to when an escape sequence is detected (or alternatively for
     * long names, one crossing input buffer boundary). Needs to be able to handle more exceptional cases, gets slower,
     * and hence is offlined to a separate method.
     */
    @Throws(CirJacksonException::class)
    private fun parseEscapedName(quadLength: Int, currentQuad: Int, currentQuadBytes: Int): CirJsonToken {
        var realQuadLength = quadLength
        var realCurrentQuad = currentQuad
        var realCurrentQuadBytes = currentQuadBytes
        var quads = myQuadBuffer

        while (true) {
            if (myInputPointer >= myInputEnd) {
                myQuadLength = realQuadLength
                myPending32 = realCurrentQuad
                myPendingBytes = realCurrentQuadBytes
                myMinorState = MINOR_PROPERTY_NAME
                return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
            }

            var ch = nextUnsignedByteFromBuffer

            if (INPUT_CODE_LATIN1[ch] == 0) {
                if (realCurrentQuadBytes < 4) {
                    ++realCurrentQuadBytes
                    realCurrentQuad = realCurrentQuad shl 8 or ch
                } else {
                    if (realQuadLength >= quads.size) {
                        quads = growNameDecodeBuffer(quads, quads.size)
                        myQuadBuffer = quads
                    }

                    quads[realQuadLength++] = realCurrentQuad
                    realCurrentQuad = ch
                    realCurrentQuadBytes = 1
                }

                continue
            }

            if (ch == CODE_QUOTE) {
                break
            }

            if (ch != CODE_BACKSLASH) {
                throwUnquotedSpace(ch, "name")
            } else {
                ch = decodeCharEscape()

                if (ch < 0) {
                    myMinorState = MINOR_PROPERTY_NAME_ESCAPE
                    myMinorStateAfterSplit = MINOR_PROPERTY_NAME
                    myQuadLength = realQuadLength
                    myPending32 = realCurrentQuad
                    myPendingBytes = realCurrentQuadBytes
                    return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
                }
            }

            if (realQuadLength >= quads.size) {
                quads = growNameDecodeBuffer(quads, quads.size)
                myQuadBuffer = quads
            }

            if (ch > 127) {
                if (realCurrentQuadBytes >= 4) {
                    quads[realQuadLength++] = realCurrentQuad
                    realCurrentQuad = 0
                    realCurrentQuadBytes = 0
                }

                realCurrentQuad = if (ch < 0x800) {
                    realCurrentQuad shl 8 or (0xC0 or (ch shr 6))
                } else {
                    realCurrentQuad = realCurrentQuad shl 8 or (0xE0 or (ch shr 12))
                    ++realCurrentQuadBytes

                    if (realCurrentQuadBytes >= 4) {
                        quads[realQuadLength++] = realCurrentQuad
                        realCurrentQuad = 0
                        realCurrentQuadBytes = 0
                    }

                    realCurrentQuad shl 8 or (0x80 or (ch shr 6 and 0x3F))
                }

                ++realCurrentQuadBytes
                ch = 0x80 or (ch and 0x3F)
            }

            if (realCurrentQuadBytes < 4) {
                ++realCurrentQuadBytes
                realCurrentQuad = realCurrentQuad shl 8 or ch
                continue
            }

            quads[realQuadLength++] = realCurrentQuad
            realCurrentQuad = ch
            realCurrentQuadBytes = 1
        }

        if (realCurrentQuadBytes > 0) {
            if (realQuadLength >= quads.size) {
                quads = growNameDecodeBuffer(quads, quads.size)
                myQuadBuffer = quads
            }

            quads[realQuadLength++] = padLastQuad(realCurrentQuad, realCurrentQuadBytes)
        } else if (realQuadLength == 0) {
            return fieldComplete("")
        }

        val name = mySymbols.findName(quads, realQuadLength) ?: addName(quads, realQuadLength, realCurrentQuadBytes)
        return fieldComplete(name)
    }

    /**
     * Method called when we see non-white space character other than double quote, when expecting a field name. In
     * standard mode will just throw an exception; but in non-standard modes may be able to parse name.
     */
    @Throws(CirJacksonException::class)
    private fun handleOddName(code: Int): CirJsonToken? {
        when (code) {
            '#'.code -> {
                if (formatReadFeatures and FEAT_MASK_ALLOW_YAML_COMMENTS != 0) {
                    return finishHashComment(MINOR_PROPERTY_LEADING_WS)
                }
            }

            '/'.code -> {
                return startSlashComment(MINOR_PROPERTY_LEADING_WS)
            }

            '\''.code -> {
                if (formatReadFeatures and FEAT_MASK_ALLOW_SINGLE_QUOTES != 0) {
                    return finishApostropheName(0, 0, 0)
                }
            }

            CODE_R_BRACKET -> {
                return closeArrayScope()
            }
        }

        if (formatReadFeatures and FEAT_MASK_ALLOW_UNQUOTED_NAMES != 0) {
            return reportUnexpectedChar(code.toChar(), "was expecting double-quote to start field name")
        }

        val codes = CharTypes.inputCodeUtf8JsNames

        if (codes[code] != 0) {
            return reportUnexpectedChar(code.toChar(),
                    "was expecting either valid name character (for unquoted name) or double-quote (for quoted) to start field name")
        }

        return finishUnquotedName(0, code, 1)
    }

    /**
     * Parsing of optionally supported non-standard "unquoted" names: names without either double-quotes or apostrophes
     * surrounding them. Unlike other
     */
    @Throws(CirJacksonException::class)
    private fun finishUnquotedName(quadLength: Int, currentQuad: Int, currentQuadBytes: Int): CirJsonToken {
        var realQuadLength = quadLength
        var realCurrentQuad = currentQuad
        var realCurrentQuadBytes = currentQuadBytes
        var quads = myQuadBuffer
        val codes = CharTypes.inputCodeUtf8JsNames

        while (true) {
            if (myInputPointer >= myInputEnd) {
                myQuadLength = realQuadLength
                myPending32 = realCurrentQuad
                myPendingBytes = realCurrentQuadBytes
                myMinorState = MINOR_PROPERTY_UNQUOTED_NAME
                return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
            }

            val ch = getByteFromBuffer(myInputPointer).toInt() and 0xFF

            if (codes[ch] != 0) {
                break
            }

            ++myInputPointer

            if (realCurrentQuadBytes < 4) {
                ++realCurrentQuadBytes
                realCurrentQuad = realCurrentQuad shl 8 or ch
            } else {
                if (realQuadLength >= quads.size) {
                    quads = growNameDecodeBuffer(quads, quads.size)
                    myQuadBuffer = quads
                }

                quads[realQuadLength++] = realCurrentQuad
                realCurrentQuad = ch
                realCurrentQuadBytes = 1
            }
        }

        if (currentQuadBytes > 0) {
            if (realQuadLength >= quads.size) {
                quads = growNameDecodeBuffer(quads, quads.size)
                myQuadBuffer = quads
            }

            quads[realQuadLength++] = realCurrentQuad
        }

        val name = mySymbols.findName(quads, realQuadLength) ?: addName(quads, realQuadLength, realCurrentQuadBytes)
        return fieldComplete(name)
    }

    @Throws(CirJacksonException::class)
    private fun finishApostropheName(quadLength: Int, currentQuad: Int, currentQuadBytes: Int): CirJsonToken {
        var realQuadLength = quadLength
        var realCurrentQuad = currentQuad
        var realCurrentQuadBytes = currentQuadBytes
        var quads = myQuadBuffer

        while (true) {
            if (myInputPointer >= myInputEnd) {
                myQuadLength = realQuadLength
                myPending32 = realCurrentQuad
                myPendingBytes = realCurrentQuadBytes
                myMinorState = MINOR_PROPERTY_NAME
                return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
            }

            var ch = nextUnsignedByteFromBuffer

            if (ch == CODE_APOSTROPHE) {
                break
            }

            if (ch != CODE_QUOTE && INPUT_CODE_LATIN1[ch] != 0) {
                throwUnquotedSpace(ch, "name")
            } else {
                ch = decodeCharEscape()

                if (ch < 0) {
                    myMinorState = MINOR_PROPERTY_NAME_ESCAPE
                    myMinorStateAfterSplit = MINOR_PROPERTY_NAME
                    myQuadLength = realQuadLength
                    myPending32 = realCurrentQuad
                    myPendingBytes = realCurrentQuadBytes
                    myMinorState = MINOR_PROPERTY_NAME
                    return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
                }
            }

            if (realQuadLength >= quads.size) {
                quads = growNameDecodeBuffer(quads, quads.size)
                myQuadBuffer = quads
            }

            if (ch > 127) {
                if (realCurrentQuadBytes >= 4) {
                    quads[realQuadLength++] = realCurrentQuad
                    realCurrentQuad = 0
                    realCurrentQuadBytes = 0
                }

                realCurrentQuad = if (ch < 0x800) {
                    realCurrentQuad shl 8 or (0xC0 or (ch shr 6))
                } else {
                    realCurrentQuad = realCurrentQuad shl 8 or (0xE0 or (ch shr 12))
                    ++realCurrentQuadBytes

                    if (realCurrentQuadBytes >= 4) {
                        quads[realQuadLength++] = realCurrentQuad
                        realCurrentQuad = 0
                        realCurrentQuadBytes = 0
                    }

                    realCurrentQuad shl 8 or (0x80 or (ch shr 6 and 0x3F))
                }

                ++realCurrentQuadBytes
                ch = 0x80 or (ch and 0x3F)
            }

            if (realCurrentQuadBytes < 4) {
                ++realCurrentQuadBytes
                realCurrentQuad = realCurrentQuad shl 8 or ch
                continue
            }

            quads[realQuadLength++] = realCurrentQuad
            realCurrentQuad = ch
            realCurrentQuadBytes = 1
        }

        if (currentQuadBytes > 0) {
            if (realQuadLength >= quads.size) {
                quads = growNameDecodeBuffer(quads, quads.size)
                myQuadBuffer = quads
            }

            quads[realQuadLength++] = padLastQuad(realCurrentQuad, realCurrentQuadBytes)
        } else if (realQuadLength == 0) {
            return fieldComplete("")
        }

        val name = mySymbols.findName(quads, realQuadLength) ?: addName(quads, realQuadLength, realCurrentQuadBytes)
        return fieldComplete(name)
    }

    @Throws(CirJacksonException::class)
    private fun finishPropertyWithEscape(): CirJsonToken {
        var ch = decodeSplitEscaped(myQuoted32, myQuotedDigits)

        if (ch < 0) {
            myMinorState = MINOR_PROPERTY_NAME_ESCAPE
            return CirJsonToken.NOT_AVAILABLE
        }

        if (myQuadLength >= myQuadBuffer.size) {
            myQuadBuffer = growNameDecodeBuffer(myQuadBuffer, 32)
        }

        var currentQuad = myPending32
        var currentQuadBytes = myPendingBytes

        if (ch > 127) {
            if (currentQuadBytes >= 4) {
                myQuadBuffer[myQuadLength++] = currentQuad
                currentQuad = 0
                currentQuadBytes = 0
            }

            currentQuad = if (ch < 0x800) {
                currentQuad shl 8 or (0xC0 or (ch shr 6))
            } else {
                currentQuad = currentQuad shl 8 or (0xE0 or (ch shr 12))
                ++currentQuadBytes

                if (currentQuadBytes >= 4) {
                    myQuadBuffer[myQuadLength++] = currentQuad
                    currentQuad = 0
                    currentQuadBytes = 0
                }

                currentQuad shl 8 or (0x80 or (ch shr 6 and 0x3F))
            }

            ++currentQuadBytes
            ch = 0x80 or (ch and 0x3F)
        }

        if (currentQuadBytes < 4) {
            ++currentQuadBytes
            currentQuad = currentQuad shl 8 or ch
        } else {
            myQuadBuffer[myQuadLength++] = currentQuad
            currentQuad = ch
            currentQuadBytes = 1
        }

        return if (myMinorStateAfterSplit == MINOR_PROPERTY_APOS_NAME) {
            finishApostropheName(myQuadLength, currentQuad, currentQuadBytes)
        } else {
            parseEscapedName(myQuadLength, currentQuad, currentQuadBytes)
        }
    }

    @Throws(CirJacksonException::class)
    private fun decodeSplitEscaped(value: Int, bytesRead: Int): Int {
        var realValue = value
        var realBytesRead = bytesRead

        if (myInputPointer >= myInputEnd) {
            myQuoted32 = realValue
            myQuotedDigits = realBytesRead
            return -1
        }

        var c = nextSignedByteFromBuffer.toInt()

        if (realBytesRead == -1) {
            when (c) {
                'b'.code -> return '\b'.code
                't'.code -> return '\t'.code
                'n'.code -> return '\n'.code
                'r'.code -> return '\r'.code
                'f'.code -> return '\u000c'.code
                '"'.code, '/'.code, '\\'.code -> return c
                'u'.code -> {}
                else -> return handleUnrecognizedCharacterEscape(c.toChar()).code
            }

            if (myInputPointer >= myInputEnd) {
                myQuoted32 = 0
                myQuotedDigits = 0
                return -1
            }

            c = nextSignedByteFromBuffer.toInt()
            realBytesRead = 0
        }

        c = c and 0xFF

        while (true) {
            val digit = CharTypes.charToHex(c.toChar())

            if (digit < 0) {
                return reportUnexpectedChar((c and 0xFF).toChar(), "expected a hex-digit for character escape sequence")
            }

            realValue = realValue shl 4 or digit

            if (++realBytesRead == 4) {
                return realValue
            }

            if (myInputPointer >= myInputEnd) {
                myQuoted32 = realValue
                myQuotedDigits = realBytesRead
                return -1
            }

            c = nextSignedByteFromBuffer.toInt()
        }
    }

    /*
     *******************************************************************************************************************
     * Second level decoding, String decoding
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun startString(): CirJsonToken? {
        var pointer = myInputPointer
        var outputPointer = 0
        val outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()

        val max = min(myInputEnd, pointer + outputBuffer.size)

        while (pointer < max) {
            val c = getByteFromBuffer(pointer).toInt() and 0xFF

            if (INPUT_CODE_UTF8[c] != 0) {
                if (c == CODE_QUOTE) {
                    myInputPointer = pointer + 1
                    myTextBuffer.currentSegmentSize = outputPointer
                    return valueComplete(CirJsonToken.VALUE_STRING)
                }

                break
            }

            ++pointer
            outputBuffer[outputPointer++] = c.toChar()
        }

        myInputPointer = pointer
        myTextBuffer.currentSegmentSize = outputPointer
        return finishRegularString()
    }

    @Throws(CirJacksonException::class)
    private fun finishRegularString(): CirJsonToken? {
        val codes = INPUT_CODE_UTF8

        var c: Int

        var pointer = myInputPointer
        var outputPointer = myTextBuffer.currentSegmentSize
        var outputBuffer = myTextBuffer.bufferWithoutReset!!
        val safeEnd = myInputEnd - 5

        while (true) {
            asciiLoop@ while (true) {
                if (pointer >= myInputEnd) {
                    myInputPointer = pointer
                    myMinorState = MINOR_VALUE_STRING
                    myTextBuffer.currentSegmentSize = outputPointer
                    return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
                }

                if (outputPointer >= outputBuffer.size) {
                    outputBuffer = myTextBuffer.finishCurrentSegment()
                    outputPointer = 0
                }

                val max = min(myInputEnd, pointer + outputBuffer.size - outputPointer)

                while (pointer < max) {
                    c = getByteFromBuffer(pointer++).toInt() and 0xFF

                    if (codes[c] != 0) {
                        break@asciiLoop
                    }

                    outputBuffer[outputPointer++] = c.toChar()
                }
            }

            if (c == CODE_QUOTE) {
                myInputPointer = pointer
                myTextBuffer.currentSegmentSize = outputPointer
                return valueComplete(CirJsonToken.VALUE_STRING)
            }

            if (pointer >= safeEnd) {
                myInputPointer = pointer
                myTextBuffer.currentSegmentSize = outputPointer

                if (!decodeSplitMultiByte(c, codes[c], pointer < myInputEnd)) {
                    myMinorStateAfterSplit = MINOR_VALUE_STRING
                    return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
                }

                outputBuffer = myTextBuffer.bufferWithoutReset!!
                outputPointer = myTextBuffer.currentSegmentSize
                pointer = myInputPointer
                continue
            }

            when (codes[c]) {
                1 -> {
                    myInputPointer = pointer
                    c = decodeFastCharEscape()
                    pointer = myInputPointer
                }

                2 -> {
                    c = decodeUTF8V2(c, getByteFromBuffer(pointer++).toInt())
                }

                3 -> {
                    c = decodeUTF8V3(c, getByteFromBuffer(pointer++).toInt(), getByteFromBuffer(pointer++).toInt())
                }

                4 -> {
                    c = decodeUTF8V4(c, getByteFromBuffer(pointer++).toInt(), getByteFromBuffer(pointer++).toInt(),
                            getByteFromBuffer(pointer++).toInt())
                    outputBuffer[outputPointer++] = (c shr 10 or 0xD800).toChar()

                    if (outputPointer >= outputBuffer.size) {
                        outputBuffer = myTextBuffer.finishCurrentSegment()
                        outputPointer = 0
                    }

                    c = c and 0x3FF or 0xDC00
                }

                else -> {
                    if (c < CODE_SPACE) {
                        throwUnquotedSpace(c, "string value")
                    } else {
                        return reportInvalidChar(c)
                    }
                }
            }

            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.finishCurrentSegment()
                outputPointer = 0
            }

            outputBuffer[outputPointer++] = c.toChar()
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun startApostropheString(): CirJsonToken? {
        var pointer = myInputPointer
        var outputPointer = 0
        val outputBuffer = myTextBuffer.emptyAndGetCurrentSegment()

        val max = min(myInputEnd, pointer + outputBuffer.size)

        while (pointer < max) {
            val c = getByteFromBuffer(pointer).toInt() and 0xFF

            if (c == CODE_APOSTROPHE) {
                myInputPointer = pointer + 1
                myTextBuffer.currentSegmentSize = outputPointer
                return valueComplete(CirJsonToken.VALUE_STRING)
            }

            if (INPUT_CODE_UTF8[c] != 0) {
                break
            }

            ++pointer
            outputBuffer[outputPointer++] = c.toChar()
        }

        myInputPointer = pointer + 1
        myTextBuffer.currentSegmentSize = outputPointer
        return finishApostropheString()
    }

    @Throws(CirJacksonException::class)
    private fun finishApostropheString(): CirJsonToken? {
        val codes = INPUT_CODE_UTF8

        var c: Int

        var pointer = myInputPointer
        var outputPointer = myTextBuffer.currentSegmentSize
        var outputBuffer = myTextBuffer.bufferWithoutReset!!
        val safeEnd = myInputEnd - 5

        while (true) {
            asciiLoop@ while (true) {
                if (pointer >= myInputEnd) {
                    myInputPointer = pointer
                    myMinorState = MINOR_VALUE_APOS_STRING
                    myTextBuffer.currentSegmentSize = outputPointer
                    return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
                }

                if (outputPointer >= outputBuffer.size) {
                    outputBuffer = myTextBuffer.finishCurrentSegment()
                    outputPointer = 0
                }

                val max = min(myInputEnd, pointer + outputBuffer.size - outputPointer)

                while (pointer < max) {
                    c = getByteFromBuffer(pointer++).toInt() and 0xFF

                    if (codes[c] != 0 && c != CODE_APOSTROPHE) {
                        break@asciiLoop
                    }

                    if (c == CODE_APOSTROPHE) {
                        myInputPointer = pointer
                        myTextBuffer.currentSegmentSize = outputPointer
                        return valueComplete(CirJsonToken.VALUE_STRING)
                    }

                    outputBuffer[outputPointer++] = c.toChar()
                }
            }

            if (pointer >= safeEnd) {
                myInputPointer = pointer
                myTextBuffer.currentSegmentSize = outputPointer

                if (!decodeSplitMultiByte(c, codes[c], pointer < myInputEnd)) {
                    myMinorStateAfterSplit = MINOR_VALUE_APOS_STRING
                }

                outputBuffer = myTextBuffer.bufferWithoutReset!!
                outputPointer = myTextBuffer.currentSegmentSize
                pointer = myInputPointer
                continue
            }

            when (codes[c]) {
                1 -> {
                    myInputPointer = pointer
                    c = decodeFastCharEscape()
                    pointer = myInputPointer
                }

                2 -> {
                    c = decodeUTF8V2(c, getByteFromBuffer(pointer++).toInt())
                }

                3 -> {
                    c = decodeUTF8V3(c, getByteFromBuffer(pointer++).toInt(), getByteFromBuffer(pointer++).toInt())
                }

                4 -> {
                    c = decodeUTF8V4(c, getByteFromBuffer(pointer++).toInt(), getByteFromBuffer(pointer++).toInt(),
                            getByteFromBuffer(pointer++).toInt())
                    outputBuffer[outputPointer++] = (c shr 10 or 0xD800).toChar()

                    if (outputPointer >= outputBuffer.size) {
                        outputBuffer = myTextBuffer.finishCurrentSegment()
                        outputPointer = 0
                    }

                    c = c and 0x3FF or 0xDC00
                }

                else -> {
                    if (c < CODE_SPACE) {
                        throwUnquotedSpace(c, "string value")
                    } else {
                        return reportInvalidChar(c)
                    }
                }
            }

            if (outputPointer >= outputBuffer.size) {
                outputBuffer = myTextBuffer.finishCurrentSegment()
                outputPointer = 0
            }

            outputBuffer[outputPointer++] = c.toChar()
        }
    }

    @Throws(CirJacksonException::class)
    private fun decodeSplitMultiByte(code: Int, type: Int, gotNext: Boolean): Boolean {
        var c = code

        return when (type) {
            1 -> {
                c = decodeSplitEscaped(0, -1)

                if (c < 0) {
                    myMinorState = MINOR_VALUE_STRING_ESCAPE
                    false
                } else {
                    myTextBuffer.append(c.toChar())
                    true
                }
            }

            2 -> {
                if (gotNext) {
                    c = decodeUTF8V2(c, nextSignedByteFromBuffer.toInt())
                    myTextBuffer.append(c.toChar())
                    true
                } else {
                    myMinorState = MINOR_VALUE_STRING_UTF8_2
                    myPending32 = c
                    false
                }
            }

            3 -> {
                c = c and 0x0F

                if (gotNext) {
                    decodeSplitUTF8V3(c, 1, nextSignedByteFromBuffer.toInt())
                } else {
                    myMinorState = MINOR_VALUE_STRING_UTF8_3
                    myPending32 = c
                    myPendingBytes = 1
                    false
                }
            }

            4 -> {
                c = c and 0x07

                if (gotNext) {
                    decodeSplitUTF8V4(c, 1, nextSignedByteFromBuffer.toInt())
                } else {
                    myMinorState = MINOR_VALUE_STRING_UTF8_4
                    myPending32 = c
                    myPendingBytes = 1
                    false
                }
            }

            else -> {
                if (c < CODE_SPACE) {
                    throwUnquotedSpace(c, "string value")
                    myTextBuffer.append(c.toChar())
                    true
                } else {
                    reportInvalidChar(c)
                }
            }
        }
    }

    @Throws(CirJacksonException::class)
    private fun decodeSplitUTF8V3(previous: Int, previousCount: Int, next: Int): Boolean {
        var realPrevious = previous
        var realNext = next

        if (previousCount == 1) {
            if (realNext and 0xC0 != 0x080) {
                return reportInvalidOther(realNext and 0xFF, myInputPointer)
            }

            realPrevious = realPrevious shl 6 or (realNext and 0x3F)

            if (myInputPointer >= myInputEnd) {
                myMinorState = MINOR_VALUE_STRING_UTF8_3
                myPending32 = realPrevious
                myPendingBytes = 2
                return false
            }

            realNext = nextSignedByteFromBuffer.toInt()
        }

        if (realNext and 0xC0 != 0x080) {
            return reportInvalidOther(realNext and 0xFF, myInputPointer)
        }

        myTextBuffer.append((realPrevious shl 6 or (realNext and 0x3F)).toChar())
        return true
    }

    @Throws(CirJacksonException::class)
    private fun decodeSplitUTF8V4(previous: Int, previousCount: Int, next: Int): Boolean {
        var realPrevious = previous
        var realPreviousCount = previousCount
        var realNext = next

        if (realPreviousCount == 1) {
            if (realNext and 0xC0 != 0x080) {
                return reportInvalidOther(realNext and 0xFF, myInputPointer)
            }

            realPrevious = realPrevious shl 6 or (realNext and 0x3F)

            if (myInputPointer >= myInputEnd) {
                myMinorState = MINOR_VALUE_STRING_UTF8_3
                myPending32 = realPrevious
                myPendingBytes = 2
                return false
            }

            realPreviousCount = 2
            realNext = nextSignedByteFromBuffer.toInt()
        }

        if (realPreviousCount == 2) {
            if (realNext and 0xC0 != 0x080) {
                return reportInvalidOther(realNext and 0xFF, myInputPointer)
            }

            realPrevious = realPrevious shl 6 or (realNext and 0x3F)

            if (myInputPointer >= myInputEnd) {
                myMinorState = MINOR_VALUE_STRING_UTF8_3
                myPending32 = realPrevious
                myPendingBytes = 2
                return false
            }

            realNext = nextSignedByteFromBuffer.toInt()
        }

        if (realNext and 0xC0 != 0x080) {
            return reportInvalidOther(realNext and 0xFF, myInputPointer)
        }

        var c = (realPrevious shl 6 or (realNext and 0x3F)) - 0x10000
        myTextBuffer.append((c shr 10 or 0xD800).toChar())
        c = c and 0x3FF or 0xDC00
        myTextBuffer.append(c.toChar())
        return true
    }

    /*
     *******************************************************************************************************************
     * Internal methods, UTF8 decoding
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun decodeCharEscape(): Int {
        val left = myInputEnd - myInputPointer

        return if (left < 5) {
            decodeSplitEscaped(0, -1)
        } else {
            decodeFastCharEscape()
        }
    }

    @Throws(CirJacksonException::class)
    private fun decodeFastCharEscape(): Int {
        when (val c = nextSignedByteFromBuffer.toInt()) {
            'b'.code -> return '\b'.code
            't'.code -> return '\t'.code
            'n'.code -> return '\n'.code
            'r'.code -> return '\r'.code
            'f'.code -> return '\u000c'.code
            '"'.code, '/'.code, '\\'.code -> return c
            'u'.code -> {}
            else -> return handleUnrecognizedCharacterEscape(c.toChar()).code
        }

        var ch = nextSignedByteFromBuffer.toInt()
        var digit = CharTypes.charToHex(ch.toChar())
        var result = digit

        if (digit >= 0) {
            ch = nextSignedByteFromBuffer.toInt()
            digit = CharTypes.charToHex(ch.toChar())

            if (digit >= 0) {
                result = result shl 4 or digit
                ch = nextSignedByteFromBuffer.toInt()
                digit = CharTypes.charToHex(ch.toChar())

                if (digit >= 0) {
                    result = result shl 4 or digit
                    ch = nextSignedByteFromBuffer.toInt()
                    digit = CharTypes.charToHex(ch.toChar())

                    if (digit >= 0) {
                        return result shl 4 or digit
                    }
                }
            }
        }

        return reportUnexpectedChar((ch and 0xFF).toChar(), "expected a hex-digit for character escape sequence")
    }

    @Throws(CirJacksonException::class)
    private fun decodeUTF8V2(c: Int, d: Int): Int {
        return if (d and 0xC0 != 0x080) {
            reportInvalidOther(d and 0xFF, myInputPointer)
        } else {
            c and 0x1F shl 6 or (d and 0x3F)
        }
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun decodeUTF8V3(c: Int, d: Int, e: Int): Int {
        var c = c and 0x0F

        return if (d and 0xC0 != 0x080) {
            reportInvalidOther(d and 0xFF, myInputPointer)
        } else {
            c = c shl 6 or (d and 0x3F)

            if (e and 0xC0 != 0x080) {
                reportInvalidOther(e and 0xFF, myInputPointer)
            } else {
                c shl 6 or (e and 0x3F)
            }
        }
    }

    @Throws(CirJacksonException::class)
    @Suppress("NAME_SHADOWING")
    private fun decodeUTF8V4(c: Int, d: Int, e: Int, f: Int): Int {
        if (d and 0xC0 != 0x080) {
            return reportInvalidOther(d and 0xFF, myInputPointer)
        }

        var c = c and 0x07 shl 6 or (d and 0x3F)

        if (e and 0xC0 != 0x080) {
            return reportInvalidOther(e and 0xFF, myInputPointer)
        }

        c = c shl 6 or (e and 0x3F)

        if (f and 0xC0 != 0x080) {
            return reportInvalidOther(f and 0xFF, myInputPointer)
        }

        return (c shl 6 or (f and 0x3F)) - 0x10000
    }

    @Throws(CirJacksonException::class)
    protected open fun decodeCharForError(firstByte: Int): Int {
        var c = firstByte and 0xFF

        if (c > 0x7F) {
            val needed = when {
                c and 0xE0 == 0xC0 -> {
                    c = c and 0x1F
                    1
                }

                c and 0xF0 == 0xE0 -> {
                    c = c and 0x0F
                    2
                }

                c and 0xF8 == 0xF0 -> {
                    c = c and 0x07
                    3
                }

                else -> {
                    return reportInvalidInitial(c and 0xFF)
                }
            }

            var d = nextUnsignedByteFromBuffer

            if (d and 0xC0 != 0x080) {
                return reportInvalidOther(d and 0xFF)
            }

            c = c shl 6 or (d and 0x3F)

            if (needed > 1) {
                d = nextUnsignedByteFromBuffer

                if (d and 0xC0 != 0x080) {
                    return reportInvalidOther(d and 0xFF)
                }

                c = c shl 6 or (d and 0x3F)

                if (needed > 2) {
                    d = nextUnsignedByteFromBuffer

                    if (d and 0xC0 != 0x080) {
                        return reportInvalidOther(d and 0xFF)
                    }

                    c = c shl 6 or (d and 0x3F)
                }
            }
        }

        return c
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