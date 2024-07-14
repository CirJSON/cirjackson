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

            MINOR_NUMBER_ZERO, MINOR_NUMBER_MINUS_ZERO -> valueCompleteInt(0, "0")

            MINOR_NUMBER_INTEGER_DIGITS -> {
                var length = myTextBuffer.currentSegmentSize

                if (myNumberNegative) {
                    --length
                }

                myIntLength = length
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
    private fun startNameAfterComma(code: Int): CirJsonToken? {
        var ch = code

        if (ch <= 0x0020) {
            ch = skipWhitespace(ch)

            if (ch <= 0) {
                myMinorState = MINOR_PROPERTY_LEADING_WS
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
            myMinorState = MINOR_PROPERTY_LEADING_WS
            return CirJsonToken.NOT_AVAILABLE.also { myCurrentToken = it }
        }

        ch = getByteFromBuffer(pointer).toInt()
        myInputPointer = pointer + 1

        if (ch <= 0x0020) {
            ch = skipWhitespace(ch)

            if (ch <= 0) {
                myMinorState = MINOR_PROPERTY_LEADING_WS
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

            '1', '2', '3', '4', '5', '6', '7', '8', '9' -> startPositiveNumber()

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

            '1', '2', '3', '4', '5', '6', '7', '8', '9' -> startPositiveNumber()

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

            '0' -> startNumberLeadingZero()

            '1', '2', '3', '4', '5', '6', '7', '8', '9' -> startPositiveNumber()

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

            '0' -> startNumberLeadingZero()

            '1', '2', '3', '4', '5', '6', '7', '8', '9' -> startPositiveNumber()

            'f' -> startFalseToken()

            'n' -> startNullToken()

            't' -> startTrueToken()

            '[' -> startArrayScope()

            '{' -> startObjectScope()

            else -> startUnexpectedValue(ch, true)
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun startUnexpectedValue(code: Int, leadingComma: Boolean): CirJsonToken? {
        if (code == CODE_R_BRACKET && streamReadContext!!.isInArray || code == CODE_COMMA) {
            if (streamReadContext!!.isInRoot) {
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
                    myInputEnd = pointer
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
                    myInputEnd = pointer
                    return valueComplete(CirJsonToken.VALUE_TRUE)
                }
            }
        }

        myMinorState = MINOR_VALUE_TOKEN_FALSE
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
                    myInputEnd = pointer
                    return valueComplete(CirJsonToken.VALUE_NULL)
                }
            }
        }

        myMinorState = MINOR_VALUE_TOKEN_FALSE
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
    protected open fun finishNumberLeadingPosZero(): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNumberLeadingNegZero(): CirJsonToken? {
        TODO()
    }

    @Throws(CirJacksonException::class)
    protected open fun finishNumberLeadingPosNegZero(negative: Boolean): CirJsonToken? {
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
    private fun parseEscapedName(quadLength: Int, currentQuad: Int, currentQuadBytes: Int): CirJsonToken? {
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