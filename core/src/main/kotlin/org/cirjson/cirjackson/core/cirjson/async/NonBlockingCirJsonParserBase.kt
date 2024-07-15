package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.cirjson.CirJsonParserBase
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.symbols.ByteQuadsCanonicalizer
import java.io.IOException
import java.io.OutputStream
import java.io.Writer
import kotlin.math.max

/**
 * Intermediate base class for non-blocking CirJSON parsers.
 *
 * @property mySymbols Symbol table that contains property names encountered so far.
 */
abstract class NonBlockingCirJsonParserBase(objectReadContext: ObjectReadContext, ioContext: IOContext,
        streamReadFeatures: Int, formatReadFeatures: Int, protected val mySymbols: ByteQuadsCanonicalizer) :
        CirJsonParserBase(objectReadContext, ioContext, streamReadFeatures, formatReadFeatures) {

    protected var myQuadBuffer = IntArray(8)

    protected var myQuadLength = 0

    protected var myQuad1 = 0

    protected var myPending32 = 0

    protected var myPendingBytes = 0

    protected var myQuoted32 = 0

    protected var myQuotedDigits = 0

    /**
     * Current main decoding state within logical tree
     */
    protected var myMajorState = 0

    /**
     * Value of {@link myMajorState} after completing a scalar value
     */
    protected var myMajorStateAfterValue = 0

    /**
     * Additional indicator within state; contextually relevant for just that state
     */
    protected var myMinorState = 0

    /**
     * Secondary minor state indicator used during decoding of escapes and/or multibyte Unicode characters
     */
    protected var myMinorStateAfterSplit = 0

    /**
     * Flag that is sent when calling application indicates that there will be no more input to parse.
     */
    protected var myEndOfInput = false

    /**
     * When tokenizing non-standard ("odd") tokens, this is the type to consider; also works as index to actual textual
     * representation.
     */
    protected var myNonStdTokenType = 0

    /**
     * Since we are fed content that may or may not start at zero offset, we need to keep track of the first byte within
     * that buffer, to be able to calculate logical offset within input "stream"
     */
    protected var myCurrentBufferStart = 0

    /**
     * Alternate row tracker, used to keep track of position by `\r` marker (whereas `myCurrentInputRow` tracks `\n`).
     * Used to simplify tracking of linefeeds, assuming that input typically uses various linefeed combinations (`\r`,
     * `\n` or `\r\n`) consistently, in which case we can simply choose max of two row candidates.
     */
    protected var myCurrentInputRowAlt = 0

    override val isParsingAsyncPossible: Boolean
        get() = true

    internal fun symbolTableForTests(): ByteQuadsCanonicalizer {
        return mySymbols
    }

    /*
     *******************************************************************************************************************
     * Overridden methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    abstract override fun releaseBuffered(output: OutputStream): Int

    @Throws(CirJacksonException::class)
    override fun releaseBuffers() {
        super.releaseBuffers()
        mySymbols.release()
    }

    override fun streamReadInputSource(): Any? {
        return null
    }

    override fun closeInput() {
        myCurrentBufferStart = 0
        myInputEnd = 0
    }

    override val isTextCharactersAvailable: Boolean
        get() = when (myCurrentToken) {
            CirJsonToken.VALUE_STRING -> myTextBuffer.isHavingTextAsCharacters
            CirJsonToken.PROPERTY_NAME -> myIsNameCopied
            else -> false
        }

    override fun currentLocation(): CirJsonLocation {
        val column = myInputPointer - myCurrentInputRowStart
        val row = max(myCurrentInputRow, myCurrentInputRowAlt)
        return CirJsonLocation(contentReference(), myCurrentInputProcessed + myInputPointer + myCurrentBufferStart, -1L,
                row, column)
    }

    override fun currentTokenLocation(): CirJsonLocation {
        return CirJsonLocation(contentReference(), tokenCharacterOffset, -1L, tokenLineNumber, myTokenInputColumn)
    }

    /*
     *******************************************************************************************************************
     * Public API, access to token information, text
     *******************************************************************************************************************
     */

    @get:Throws(CirJacksonException::class)
    override val text: String?
        get() = if (myCurrentToken == CirJsonToken.VALUE_STRING) {
            myTextBuffer.contentsAsString()
        } else {
            getText(myCurrentToken)
        }

    @Throws(CirJacksonException::class)
    protected fun getText(token: CirJsonToken?): String? {
        token ?: return null
        return when (token.id) {
            CirJsonTokenId.ID_NOT_AVAILABLE -> null

            CirJsonTokenId.ID_PROPERTY_NAME -> streamReadContext!!.currentName

            CirJsonTokenId.ID_STRING, CirJsonTokenId.ID_NUMBER_INT, CirJsonTokenId.ID_NUMBER_FLOAT -> {
                myTextBuffer.contentsAsString()
            }

            else -> token.token
        }
    }

    @Throws(CirJacksonException::class)
    override fun getText(writer: Writer): Int {
        val token = myCurrentToken

        return try {
            when (token) {
                null -> 0

                CirJsonToken.VALUE_STRING -> myTextBuffer.contentsToWriter(writer)

                CirJsonToken.PROPERTY_NAME -> {
                    val name = streamReadContext!!.currentName
                    writer.write(name!!)
                    return name.length
                }

                else -> {
                    if (token.isNumeric) {
                        myTextBuffer.contentsToWriter(writer)
                    } else if (token == CirJsonToken.NOT_AVAILABLE) {
                        reportError("Current token not available: can not call this method")
                    } else {
                        val chars = token.token!!.toCharArray()
                        writer.write(chars)
                        chars.size
                    }
                }
            }
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }
    }

    @get:Throws(CirJacksonException::class)
    override val valueAsString: String?
        get() = when (myCurrentToken) {
            CirJsonToken.VALUE_STRING -> myTextBuffer.contentsAsString()
            CirJsonToken.PROPERTY_NAME -> currentName
            else -> super.getValueAsString(null)
        }

    @Throws(CirJacksonException::class)
    override fun getValueAsString(defaultValue: String?): String? {
        return when (myCurrentToken) {
            CirJsonToken.VALUE_STRING -> myTextBuffer.contentsAsString()
            CirJsonToken.PROPERTY_NAME -> currentName
            else -> super.getValueAsString(null)
        }
    }

    @get:Throws(CirJacksonException::class)
    override val textCharacters: CharArray?
        get() = when (myCurrentToken?.id) {
            null -> null

            CirJsonTokenId.ID_PROPERTY_NAME -> currentNameInBuffer()

            CirJsonTokenId.ID_STRING, CirJsonTokenId.ID_NUMBER_INT, CirJsonTokenId.ID_NUMBER_FLOAT -> {
                myTextBuffer.textBuffer
            }

            else -> myCurrentToken!!.token!!.toCharArray()
        }

    @get:Throws(CirJacksonException::class)
    override val textLength: Int
        get() = when (myCurrentToken?.id) {
            null -> 0

            CirJsonTokenId.ID_PROPERTY_NAME -> streamReadContext!!.currentName!!.length

            CirJsonTokenId.ID_STRING, CirJsonTokenId.ID_NUMBER_INT, CirJsonTokenId.ID_NUMBER_FLOAT -> {
                myTextBuffer.size
            }

            else -> myCurrentToken!!.token!!.toCharArray().size
        }

    @get:Throws(CirJacksonException::class)
    override val textOffset: Int
        get() = when (myCurrentToken?.id) {
            null -> 0

            CirJsonTokenId.ID_PROPERTY_NAME -> 0

            CirJsonTokenId.ID_STRING, CirJsonTokenId.ID_NUMBER_INT, CirJsonTokenId.ID_NUMBER_FLOAT -> {
                myTextBuffer.textOffset
            }

            else -> 0
        }

    /*
     *******************************************************************************************************************
     * Public API, access to token information, binary
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun getBinaryValue(base64Variant: Base64Variant): ByteArray {
        if (myCurrentToken != CirJsonToken.VALUE_STRING) {
            return reportError(
                    "Current token ($myCurrentToken) not VALUE_STRING or VALUE_EMBEDDED_OBJECT, can not access as binary")
        }

        if (myBinaryValue == null) {
            val builder = byteArrayBuilder
            decodeBase64(text!!, builder, base64Variant)
            myBinaryValue = builder.toByteArray()
        }

        return myBinaryValue!!
    }

    @Throws(CirJacksonException::class)
    override fun readBinaryValue(base64Variant: Base64Variant, output: OutputStream): Int {
        val bytes = getBinaryValue(base64Variant)

        try {
            output.write(bytes)
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }

        return bytes.size
    }

    @get:Throws(CirJacksonException::class)
    override val embeddedObject: Any?
        get() = if (myCurrentToken == CirJsonToken.VALUE_EMBEDDED_OBJECT) myBinaryValue else null

    /*
     *******************************************************************************************************************
     * Handling of nested scope, state
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun startArrayScope(): CirJsonToken {
        createChildArrayContext(-1, -1)
        myMajorState = MAJOR_ARRAY_ELEMENT_FIRST
        myMinorStateAfterSplit = MAJOR_ARRAY_ELEMENT_NEXT
        return CirJsonToken.START_ARRAY.also { myCurrentToken = it }
    }

    @Throws(CirJacksonException::class)
    protected fun startObjectScope(): CirJsonToken {
        createChildObjectContext(-1, -1)
        myMajorState = MAJOR_OBJECT_PROPERTY_FIRST
        myMinorStateAfterSplit = MAJOR_OBJECT_PROPERTY_NEXT
        return CirJsonToken.START_OBJECT.also { myCurrentToken = it }
    }

    @Throws(CirJacksonException::class)
    protected fun closeArrayScope(): CirJsonToken {
        if (!streamReadContext!!.isInArray) {
            reportMismatchedEndMarker(']', '}')
        }

        val context = streamReadContext!!.parent!!
        streamReadContext = context

        val state = if (context.isInObject) {
            MAJOR_OBJECT_PROPERTY_NEXT
        } else if (context.isInArray) {
            MAJOR_ARRAY_ELEMENT_NEXT
        } else {
            MAJOR_ROOT
        }

        myMajorState = state
        myMinorStateAfterSplit = state
        return CirJsonToken.END_ARRAY.also { myCurrentToken = it }
    }

    @Throws(CirJacksonException::class)
    protected fun closeObjectScope(): CirJsonToken {
        if (!streamReadContext!!.isInObject) {
            reportMismatchedEndMarker('}', ']')
        }

        val context = streamReadContext!!.parent!!
        streamReadContext = context

        val state = if (context.isInObject) {
            MAJOR_OBJECT_PROPERTY_NEXT
        } else if (context.isInArray) {
            MAJOR_ARRAY_ELEMENT_NEXT
        } else {
            MAJOR_ROOT
        }

        myMajorState = state
        myMinorStateAfterSplit = state
        return CirJsonToken.END_OBJECT.also { myCurrentToken = it }
    }

    /*
     *******************************************************************************************************************
     * Internal methods, symbol (name) handling
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun findName(quad1: Int, lastQuadBytes: Int): String {
        val q1 = padLastQuad(quad1, lastQuadBytes)
        val name = mySymbols.findName(q1)

        if (name != null) {
            return name
        }

        myQuadBuffer[0] = q1

        return addName(myQuadBuffer, 1, lastQuadBytes)
    }

    @Throws(CirJacksonException::class)
    protected fun findName(q1: Int, quad2: Int, lastQuadBytes: Int): String {
        val q2 = padLastQuad(quad2, lastQuadBytes)
        val name = mySymbols.findName(q1, q2)

        if (name != null) {
            return name
        }

        myQuadBuffer[0] = q1
        myQuadBuffer[1] = q2

        return addName(myQuadBuffer, 2, lastQuadBytes)
    }

    @Throws(CirJacksonException::class)
    protected fun findName(q1: Int, q2: Int, quad3: Int, lastQuadBytes: Int): String {
        val q3 = padLastQuad(quad3, lastQuadBytes)
        val name = mySymbols.findName(q1, q2, q3)

        if (name != null) {
            return name
        }

        myQuadBuffer[0] = q1
        myQuadBuffer[1] = q2
        myQuadBuffer[2] = q3

        return addName(myQuadBuffer, 3, lastQuadBytes)
    }

    @Throws(CirJacksonException::class)
    protected fun addName(quads: IntArray, quadsLength: Int, lastQuadBytes: Int): String {
        val byteLength = (quadsLength shl 2) - 4 + lastQuadBytes
        streamReadConstraints.validateNameLength(byteLength)

        val lastQuad = if (lastQuadBytes < 4) {
            val value = quads[quadsLength - 1]
            quads[quadsLength - 1] = value shl (4 - lastQuadBytes shl 3)
            value
        } else {
            0
        }

        var cbuf = myTextBuffer.emptyAndGetCurrentSegment()
        var cix = 0
        var ix = 0

        while (ix < byteLength) {
            var ch = quads[ix shr 2]
            var byteIx = ix and 3
            ch = ch shr (3 - byteIx shl 3) and 0xFF
            ix++

            if (ch > 127) {
                val needed = when {
                    (ch and 0xE0 == 0xC0) -> {
                        ch = ch and 0x1F
                        1
                    }

                    ch and 0xF0 == 0xE0 -> {
                        ch = ch and 0x0F
                        2
                    }

                    ch and 0xF8 == 0xF0 -> {
                        ch = ch and 0x07
                        3
                    }

                    else -> reportInvalidInitial(ch)
                }

                if (ix + needed > byteLength) {
                    return reportInvalidEOF("in property name", CirJsonToken.PROPERTY_NAME)
                }

                var ch2 = quads[ix shr 2]
                byteIx = ix and 3
                ch2 = ch2 shr (3 - byteIx shl 3)
                ix++

                if (ch2 and 0xC0 != 0x080) {
                    return reportInvalidOther(ch2)
                }

                ch = ch shl 6 or (ch2 and 0x3F)

                if (needed > 1) {
                    ch2 = quads[ix shr 2]
                    byteIx = ix and 3
                    ch2 = ch2 shr (3 - byteIx shl 3)
                    ix++

                    if (ch2 and 0xC0 != 0x080) {
                        return reportInvalidOther(ch2)
                    }

                    ch = ch shl 6 or (ch2 and 0x3F)

                    if (needed > 2) {
                        ch2 = quads[ix shr 2]
                        byteIx = ix and 3
                        ch2 = ch2 shr (3 - byteIx shl 3)
                        ix++

                        if (ch2 and 0xC0 != 0x080) {
                            return reportInvalidOther(ch2)
                        }

                        ch = ch shl 6 or (ch2 and 0x3F)
                    }
                }

                if (needed > 2) {
                    ch -= 0x10000

                    if (cix >= cbuf.size) {
                        cbuf = myTextBuffer.expandCurrentSegment()
                    }

                    cbuf[cix++] = (0x0800 + (ch shr 10)).toChar()
                    ch = ch and 0x03FF or 0xDC00
                }
            }

            if (cix >= cbuf.size) {
                cbuf = myTextBuffer.expandCurrentSegment()
            }

            cbuf[cix++] = ch.toChar()
        }

        val baseName = String(cbuf, 0, cix)

        if (!mySymbols.isCanonicalizing) {
            return baseName
        }

        if (lastQuadBytes < 4) {
            quads[quadsLength - 1] = lastQuad
        }

        return mySymbols.addName(baseName, quads, quadsLength)
    }

    /*
     *******************************************************************************************************************
     * Internal methods, state changes
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun eofAsNextToken(): CirJsonToken? {
        myMajorState = MAJOR_CLOSED

        if (!streamReadContext!!.isInRoot) {
            handleEOF()
        }

        close()
        return null.also { myCurrentToken = null }
    }

    @Throws(CirJacksonException::class)
    protected fun fieldComplete(name: String): CirJsonToken {
        myMajorState = MAJOR_OBJECT_VALUE
        streamReadContext!!.currentName = name
        return CirJsonToken.PROPERTY_NAME.also { myCurrentToken = it }
    }

    @Throws(CirJacksonException::class)
    protected fun valueComplete(token: CirJsonToken): CirJsonToken {
        myMajorState = myMajorStateAfterValue
        return token.also { myCurrentToken = it }
    }

    @Throws(CirJacksonException::class)
    protected fun valueCompleteInt(value: Int, asText: String): CirJsonToken {
        myTextBuffer.resetWithString(asText)
        myIntLength = asText.length
        myNumberTypesValid = NUMBER_INT
        myNumberInt = value
        myMajorState = myMajorStateAfterValue
        return CirJsonToken.VALUE_NUMBER_INT.also { myCurrentToken = it }
    }

    protected fun nonStdToken(type: Int): String {
        return NON_STD_TOKENS[type]
    }

    /*
     *******************************************************************************************************************
     * Internal methods, error reporting, related
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun valueNonStdNumberComplete(type: Int): CirJsonToken {
        val tokenString = NON_STD_TOKENS[type]
        myTextBuffer.resetWithString(tokenString)

        if (!isEnabled(CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)) {
            return reportError(
                    "Non-standard token '$tokenString': enable `JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS` to allow")
        }

        myIntLength = 0
        myNumberTypesValid = NUMBER_DOUBLE
        myNumberDouble = NON_STD_TOKEN_VALUES[type]
        myMajorState = myMajorStateAfterValue
        return CirJsonToken.VALUE_NUMBER_FLOAT.also { myCurrentToken = it }
    }

    protected fun updateTokenLocation() {
        tokenLineNumber = max(myCurrentInputRow, myCurrentInputRowAlt)
        val pointer = myInputPointer
        myTokenInputColumn = pointer - myCurrentInputRowStart
        tokenCharacterOffset = myCurrentInputProcessed + pointer - myCurrentBufferStart
    }

    @Throws(CirJacksonException::class)
    protected fun <T> reportInvalidChar(code: Int): T {
        return if (code < CODE_SPACE) {
            reportInvalidSpace(code)
        } else {
            reportInvalidInitial(code)
        }
    }

    @Throws(CirJacksonException::class)
    protected fun <T> reportInvalidInitial(mask: Int): T {
        return reportError("Invalid UTF-8 start byte 0x${mask.toString(16)}")
    }

    @Throws(CirJacksonException::class)
    protected fun <T> reportInvalidOther(mask: Int, pointer: Int): T {
        myInputPointer = pointer
        return reportInvalidOther(mask)
    }

    @Throws(CirJacksonException::class)
    protected fun <T> reportInvalidOther(mask: Int): T {
        return reportError("Invalid UTF-8 middle byte 0x${mask.toString(16)}")
    }

    companion object {

        /*
         ***************************************************************************************************************
         * Major state constants
         ***************************************************************************************************************
         */

        /**
         * State right after parser has been constructed, before seeing the first byte to handle possible (but optional)
         * BOM.
         */
        const val MAJOR_INITIAL = 0

        /**
         * State right after parser a root value has been finished, but next token has not yet been recognized.
         */
        const val MAJOR_ROOT = 1

        const val MAJOR_OBJECT_PROPERTY_FIRST = 2

        const val MAJOR_OBJECT_PROPERTY_NEXT = 3

        const val MAJOR_OBJECT_VALUE = 4

        const val MAJOR_ARRAY_ELEMENT_FIRST = 5

        const val MAJOR_ARRAY_ELEMENT_NEXT = 6

        /**
         * State after non-blocking input source has indicated that no more input is forthcoming AND we have exhausted
         * all the input
         */
        const val MAJOR_CLOSED = 7

        /*
         ***************************************************************************************************************
         * Minor state constants
         ***************************************************************************************************************
         */

        /**
         * State in which part of (UTF-8) BOM has been detected, but not yet completely.
         */
        const val MINOR_ROOT_BOM = 1

        /**
         * State between root-level value, waiting for at least one white-space character as separator
         */
        const val MINOR_ROOT_NEED_SEPARATOR = 2

        /**
         * State between root-level value, having processed at least one white-space character, and expecting either
         * more, start of a value, or end of input stream.
         */
        const val MINOR_ROOT_GOT_SEPARATOR = 3

        /**
         * State before property name itself, waiting for quote (or unquoted name)
         */
        const val MINOR_PROPERTY_LEADING_WS = 4

        /**
         * State before property name, expecting comma (or closing curly), then property name
         */
        const val MINOR_PROPERTY_LEADING_COMMA = 5

        /**
         * State within regular (double-quoted) property name
         */
        const val MINOR_PROPERTY_NAME = 7

        /**
         * State within regular (double-quoted) property name, within escape (having encountered either just backslash,
         * or backslash and 'u' and 0 - 3 hex digits,
         */
        const val MINOR_PROPERTY_NAME_ESCAPE = 8

        const val MINOR_PROPERTY_APOS_NAME = 9

        const val MINOR_PROPERTY_UNQUOTED_NAME = 10

        const val MINOR_VALUE_LEADING_WS = 12

        const val MINOR_VALUE_EXPECTING_COMMA = 13

        const val MINOR_VALUE_EXPECTING_COLON = 14

        const val MINOR_VALUE_WS_AFTER_COMMA = 15

        const val MINOR_VALUE_TOKEN_NULL = 16

        const val MINOR_VALUE_TOKEN_TRUE = 17

        const val MINOR_VALUE_TOKEN_FALSE = 18

        const val MINOR_VALUE_TOKEN_NON_STD = 19

        const val MINOR_NUMBER_PLUS = 22

        const val MINOR_NUMBER_MINUS = 23

        /**
         * Zero as first, possibly trimming multiple
         */
        const val MINOR_NUMBER_ZERO = 24

        /**
         * "-0" (and possibly more zeroes) receive
         */
        const val MINOR_NUMBER_MINUS_ZERO = 25

        const val MINOR_NUMBER_INTEGER_DIGITS = 26

        const val MINOR_NUMBER_FRACTION_DIGITS = 30

        const val MINOR_NUMBER_EXPONENT_MARKER = 31

        const val MINOR_NUMBER_EXPONENT_DIGITS = 32

        const val MINOR_VALUE_STRING = 40

        const val MINOR_VALUE_STRING_ESCAPE = 41

        const val MINOR_VALUE_STRING_UTF8_2 = 42

        const val MINOR_VALUE_STRING_UTF8_3 = 43

        const val MINOR_VALUE_STRING_UTF8_4 = 44

        const val MINOR_VALUE_APOS_STRING = 45

        /**
         * Special state at which point decoding of a non-quoted token has encountered a problem; that is, either not
         * matching fully (like "truf" instead of "true", at "tru"), or not having trailing separator (or end of input),
         * like "trueful". Attempt is made, then, to decode likely full input token to report suitable error.
         */
        const val MINOR_VALUE_TOKEN_ERROR = 50

        const val MINOR_COMMENT_LEADING_SLASH = 51

        const val MINOR_COMMENT_CLOSING_ASTERISK = 52

        const val MINOR_COMMENT_C = 53

        const val MINOR_COMMENT_CPP = 54

        const val MINOR_COMMENT_YAML = 55

        /*
         ***************************************************************************************************************
         * Additional parsing state: non-standard tokens
         ***************************************************************************************************************
         */

        const val NON_STD_TOKEN_NAN = 0

        const val NON_STD_TOKEN_INFINITY = 1

        const val NON_STD_TOKEN_PLUS_INFINITY = 2

        const val NON_STD_TOKEN_MINUS_INFINITY = 3

        val NON_STD_TOKENS = arrayOf("NaN", "Infinity", "+Infinity", "-Infinity")

        val NON_STD_TOKEN_VALUES = doubleArrayOf(Double.NaN, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY)

        /*
         ***************************************************************************************************************
         * Helper method
         ***************************************************************************************************************
         */

        fun padLastQuad(quad: Int, lastQuadBytes: Int): Int {
            return if (lastQuadBytes != 4) quad or (-1 shl (lastQuadBytes shl 3)) else quad
        }

    }

}