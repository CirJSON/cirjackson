package org.cirjson.cirjackson.core.base

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.exception.InputCoercionException
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.exception.UnexpectedEndOfInputException
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.io.NumberInput
import org.cirjson.cirjackson.core.symbols.PropertyNameMatcher
import org.cirjson.cirjackson.core.type.ResolvedType
import org.cirjson.cirjackson.core.type.TypeReference
import org.cirjson.cirjackson.core.util.ByteArrayBuilder
import org.cirjson.cirjackson.core.util.CirJacksonFeatureSet
import org.cirjson.cirjackson.core.util.Other
import java.io.IOException
import java.io.Writer
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Intermediate base class used by all CirJackson [CirJsonParser] implementations, but does not add any additional
 * fields that depend on particular method of obtaining input.
 *
 * Note that 'minimal' here mostly refers to minimal number of fields (size) and functionality that is specific to
 * certain types of parser implementations; but not necessarily to number of methods.
 *
 * @property myIOContext I/O context for this reader. It handles buffer allocation for the reader, including possible
 * reuse/recycling.
 */
abstract class ParserMinimalBase private constructor(override val objectReadContext: ObjectReadContext,
        private val myIOContext: IOContext?, override val streamReadFeatures: Int,
        override val streamReadConstraints: StreamReadConstraints) : CirJsonParser() {

    override var isClosed: Boolean = false
        protected set

    /**
     * Last token retrieved via [nextToken], if any.
     * `null` before the first call to <code>nextToken</code>,
     * as well as if token has been explicitly cleared
     */
    protected var myCurrentToken: CirJsonToken? = null

    override var lastClearedToken: CirJsonToken? = null
        protected set

    /**
     * Main constructor for subclasses to use
     *
     * @param objectReadContext Context for databinding
     *
     * @param ioContext Context for I/O handling, buffering
     *
     * @param streamReadFeatures Bit set of [StreamReadFeature]s.
     */
    constructor(objectReadContext: ObjectReadContext, ioContext: IOContext, streamReadFeatures: Int) : this(
            objectReadContext, ioContext, streamReadFeatures, ioContext.streamReadConstraints)

    /**
     * Alternate constructors for cases where there is no real [IOContext] in use; typically for abstractions that
     * operate over non-streaming/incremental sources (such as cirjackson-databind `TokenBuffer`)
     *
     * @param objectReadContext Context for databinding
     */
    constructor(objectReadContext: ObjectReadContext) : this(objectReadContext, null,
            objectReadContext.getStreamReadFeatures(STREAM_READ_FEATURE_DEFAULTS),
            objectReadContext.streamReadConstraints)

    /*
     *******************************************************************************************************************
     * Configuration overrides if any
     *******************************************************************************************************************
     */

    override fun isEnabled(feature: StreamReadFeature): Boolean {
        return feature.isEnabledIn(streamReadFeatures)
    }

    /*
     *******************************************************************************************************************
     * Configuration access, capability introspection
     *******************************************************************************************************************
     */

    override val streamReadCapabilities: CirJacksonFeatureSet<StreamReadCapability>
        get() = DEFAULT_READ_CAPABILITIES

    /*
     *******************************************************************************************************************
     * CirJsonParser impl: open / close / release
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun close() {
        if (isClosed) {
            return
        }

        isClosed = true

        try {
            closeInput()
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        } finally {
            releaseBuffers()
            myIOContext?.close()
        }
    }

    /**
     * Abstract method for subclasses to implement; to be called by [close] implementation here.
     *
     * @throws IOException from underlying input source if thrown
     */
    @Throws(IOException::class)
    protected abstract fun closeInput()

    /**
     * Method called to release internal buffers owned by the base reader. This is expected to be called after
     * [closeInput] since the buffers are expected not to be needed any longer.
     */
    protected abstract fun releaseBuffers()

    /**
     * Method subclasses need to implement to check whether end-of-content is allowed at the current decoding position:
     * formats often want to verify the all start/end token pairs match, for example.
     *
     * @throws CirJacksonException if end-of-content not allowed at current position.
     */
    @Throws(CirJacksonException::class)
    protected abstract fun handleEOF()

    /*
     *******************************************************************************************************************
     * CirJsonParser impl: basic stream iteration
     *******************************************************************************************************************
     */

    override fun finishToken() {}

    override fun currentToken(): CirJsonToken? {
        return myCurrentToken
    }

    override fun currentTokenId(): Int {
        return myCurrentToken?.id ?: CirJsonTokenId.ID_NO_TOKEN
    }

    override val isCurrentTokenNotNull: Boolean
        get() = myCurrentToken != null

    override fun hasTokenId(id: Int): Boolean {
        val token = myCurrentToken ?: return id == CirJsonTokenId.ID_NO_TOKEN
        return token.id == id
    }

    override fun hasToken(token: CirJsonToken): Boolean {
        return token === myCurrentToken
    }

    override val isExpectedStartArrayToken: Boolean
        get() = myCurrentToken === CirJsonToken.START_ARRAY

    override val isExpectedStartObjectToken: Boolean
        get() = myCurrentToken === CirJsonToken.START_OBJECT

    override val isExpectedNumberIntToken: Boolean
        get() = myCurrentToken === CirJsonToken.VALUE_NUMBER_INT

    @Throws(CirJacksonException::class)
    override fun nextValue(): CirJsonToken? {
        val token = nextToken()

        return if (token !== CirJsonToken.PROPERTY_NAME && token !== CirJsonToken.CIRJSON_ID_PROPERTY_NAME) {
            token
        } else {
            nextToken()
        }
    }

    @Throws(CirJacksonException::class)
    override fun skipChildren(): CirJsonParser {
        if (myCurrentToken !== CirJsonToken.START_OBJECT && myCurrentToken !== CirJsonToken.START_ARRAY) {
            return this
        }

        var open = 1

        while (true) {
            val token = nextToken()

            if (token == null) {
                handleEOF()
                return this
            }

            if (token.isStructStart) {
                ++open
            } else if (token.isStructEnd) {
                if (--open == 0) {
                    return this
                }
            } else if (token == CirJsonToken.NOT_AVAILABLE) {
                return reportError(
                        "Not enough content available for `skipChildren()`: non-blocking parser? (${javaClass.name})")
            }
        }
    }

    /*
     *******************************************************************************************************************
     * CirJsonParser impl: stream iteration, property names
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun nextName(): String? {
        return if (nextToken() === CirJsonToken.PROPERTY_NAME) currentName() else null
    }

    @Throws(CirJacksonException::class)
    override fun nextName(string: SerializableString): Boolean {
        return nextToken() === CirJsonToken.PROPERTY_NAME && string.value == currentName()
    }

    @Throws(CirJacksonException::class)
    override fun nextNameMatch(matcher: PropertyNameMatcher): Int {
        val string = nextName()

        return if (string != null) {
            matcher.matchName(string)
        } else if (myCurrentToken === CirJsonToken.END_OBJECT) {
            PropertyNameMatcher.MATCH_END_OBJECT
        } else {
            PropertyNameMatcher.MATCH_ODD_TOKEN
        }
    }

    @Throws(CirJacksonException::class)
    override fun currentNameMatch(matcher: PropertyNameMatcher): Int {
        return if (myCurrentToken === CirJsonToken.PROPERTY_NAME) {
            matcher.matchName(currentName()!!)
        } else if (myCurrentToken === CirJsonToken.END_OBJECT) {
            PropertyNameMatcher.MATCH_END_OBJECT
        } else {
            PropertyNameMatcher.MATCH_ODD_TOKEN
        }
    }

    /*
     *******************************************************************************************************************
     * Public API, token state override
     *******************************************************************************************************************
     */

    override fun clearCurrentToken() {
        if (myCurrentToken != null) {
            lastClearedToken = myCurrentToken
            myCurrentToken = null
        }
    }

    /*
     *******************************************************************************************************************
     * Public API, access to token information, text
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun getText(writer: Writer): Int {
        val string = text ?: return 0

        try {
            writer.write(string)
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }

        return string.length
    }

    /*
     *******************************************************************************************************************
     * Public API, access to token information, numeric
     *******************************************************************************************************************
     */

    override val numberTypeFP: NumberTypeFP?
        get() = NumberTypeFP.UNKNOWN

    @get:Throws(InputCoercionException::class)
    override val numberValueExact: Number
        get() = numberValue

    @get:Throws(InputCoercionException::class)
    override val numberValueDeferred: Number
        get() = numberValue

    @get:Throws(InputCoercionException::class)
    override val byteValue: Byte
        get() {
            val value = intValue

            if (value !in INT_MIN_BYTE..INT_MAX_BYTE) {
                reportOverflowByte(text!!, currentToken()!!)
            }

            return value.toByte()
        }

    @get:Throws(InputCoercionException::class)
    override val shortValue: Short
        get() {
            val value = intValue

            if (value !in INT_MIN_SHORT..INT_MAX_SHORT) {
                reportOverflowShort(text!!, currentToken()!!)
            }

            return value.toShort()
        }

    /*
     *******************************************************************************************************************
     * Public API, access to token information, other
     *******************************************************************************************************************
     */

    @get:Throws(InputCoercionException::class)
    override val booleanValue: Boolean
        get() {
            val token = currentToken()

            return if (token === CirJsonToken.VALUE_TRUE) {
                true
            } else if (token === CirJsonToken.VALUE_FALSE) {
                false
            } else {
                throw constructInputCoercion("Current token ($token) not of boolean type", token!!, Boolean::class.java)
            }
        }

    /*
     *******************************************************************************************************************
     * Public API, access to token information, binary
     *******************************************************************************************************************
     */

    override val embeddedObject: Any?
        get() = null

    /*
     *******************************************************************************************************************
     * Public API, access with conversion/coercion
     *******************************************************************************************************************
     */

    override fun getValueAsBoolean(defaultValue: Boolean): Boolean {
        val token = myCurrentToken ?: return defaultValue

        return when (token.id) {
            CirJsonTokenId.ID_STRING -> {
                val string = text!!.trim()

                if (string == "true") {
                    true
                } else if (string == "false") {
                    false
                } else if (hasTextualNull(string)) {
                    false
                } else {
                    defaultValue
                }
            }

            CirJsonTokenId.ID_NUMBER_INT -> intValue != 0

            CirJsonTokenId.ID_TRUE -> true

            CirJsonTokenId.ID_FALSE, CirJsonTokenId.ID_NULL -> false

            CirJsonTokenId.ID_EMBEDDED_OBJECT -> (embeddedObject as? Boolean) ?: defaultValue

            else -> defaultValue
        }
    }

    override val valueAsInt: Int
        get() = if (myCurrentToken === CirJsonToken.VALUE_NUMBER_INT
                || myCurrentToken === CirJsonToken.VALUE_NUMBER_FLOAT) {
            intValue
        } else {
            getValueAsInt(0)
        }

    override fun getValueAsInt(defaultValue: Int): Int {
        val token = myCurrentToken ?: return defaultValue

        if (token === CirJsonToken.VALUE_NUMBER_INT || token === CirJsonToken.VALUE_NUMBER_FLOAT) {
            return intValue
        }


        return when (token.id) {
            CirJsonTokenId.ID_STRING -> {
                val string = text

                return if (!hasTextualNull(string)) {
                    NumberInput.parseAsInt(string, defaultValue)
                } else {
                    0
                }
            }

            CirJsonTokenId.ID_TRUE -> 1

            CirJsonTokenId.ID_FALSE, CirJsonTokenId.ID_NULL -> 0

            CirJsonTokenId.ID_EMBEDDED_OBJECT -> (embeddedObject as? Number)?.toInt() ?: defaultValue

            else -> defaultValue
        }
    }

    override val valueAsLong: Long
        get() = if (myCurrentToken === CirJsonToken.VALUE_NUMBER_INT
                || myCurrentToken === CirJsonToken.VALUE_NUMBER_FLOAT) {
            longValue
        } else {
            getValueAsLong(0L)
        }

    override fun getValueAsLong(defaultValue: Long): Long {
        val token = myCurrentToken ?: return defaultValue

        if (token === CirJsonToken.VALUE_NUMBER_INT || token === CirJsonToken.VALUE_NUMBER_FLOAT) {
            return longValue
        }


        return when (token.id) {
            CirJsonTokenId.ID_STRING -> {
                val string = text

                return if (!hasTextualNull(string)) {
                    NumberInput.parseAsLong(string, defaultValue)
                } else {
                    0L
                }
            }

            CirJsonTokenId.ID_TRUE -> 1L

            CirJsonTokenId.ID_FALSE, CirJsonTokenId.ID_NULL -> 0L

            CirJsonTokenId.ID_EMBEDDED_OBJECT -> (embeddedObject as? Number)?.toLong() ?: defaultValue

            else -> defaultValue
        }
    }

    override fun getValueAsDouble(defaultValue: Double): Double {
        val token = myCurrentToken ?: return defaultValue

        if (token === CirJsonToken.VALUE_NUMBER_INT || token === CirJsonToken.VALUE_NUMBER_FLOAT) {
            return doubleValue
        }

        return when (token.id) {
            CirJsonTokenId.ID_STRING -> {
                val string = text

                return if (!hasTextualNull(string)) {
                    NumberInput.parseAsDouble(string, defaultValue, isEnabled(StreamReadFeature.USE_FAST_DOUBLE_PARSER))
                } else {
                    0.0
                }
            }

            CirJsonTokenId.ID_TRUE -> 1.0

            CirJsonTokenId.ID_FALSE, CirJsonTokenId.ID_NULL -> 0.0

            CirJsonTokenId.ID_EMBEDDED_OBJECT -> (embeddedObject as? Number)?.toDouble() ?: defaultValue

            else -> defaultValue
        }
    }

    override val valueAsString: String?
        get() = getValueAsString(null)

    override fun getValueAsString(defaultValue: String?): String? {
        return if (myCurrentToken === CirJsonToken.VALUE_STRING) {
            text
        } else if (myCurrentToken === CirJsonToken.CIRJSON_ID_PROPERTY_NAME) {
            idName
        } else if (myCurrentToken === CirJsonToken.PROPERTY_NAME) {
            currentName()
        } else if (myCurrentToken == null || myCurrentToken === CirJsonToken.VALUE_NULL
                || !myCurrentToken!!.isScalarValue) {
            defaultValue
        } else {
            text
        }
    }

    /*
     *******************************************************************************************************************
     * Databind callbacks
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun <T> readValueAs(valueType: Class<T>): T {
        return objectReadContext.readValue(this, valueType)
    }

    @Throws(CirJacksonException::class)
    override fun <T> readValueAs(valueTypeReference: TypeReference<T>): T {
        return objectReadContext.readValue(this, valueTypeReference)
    }

    @Throws(CirJacksonException::class)
    override fun <T> readValueAs(type: ResolvedType): T {
        return objectReadContext.readValue(this, type)
    }

    @Throws(CirJacksonException::class)
    override fun <T : TreeNode> readValueAsTree(): T? {
        return objectReadContext.readTree(this)
    }

    /*
     *******************************************************************************************************************
     * Helper methods, Base64 decoding
     *******************************************************************************************************************
     */

    /**
     * Helper method that can be used for base64 decoding in cases where encoded content has already been read as a
     * String.
     *
     * @param string String to decode
     *
     * @param builder Builder used to buffer binary content decoded
     *
     * @param base64Variant Base64 variant expected in content
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    protected fun decodeBase64(string: String, builder: ByteArrayBuilder, base64Variant: Base64Variant) {
        try {
            base64Variant.decode(string, builder)
        } catch (e: IllegalArgumentException) {
            reportError<Unit>(e.message!!)
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    /**
     * Factory method used to provide location for cases where we must read and consume a single "wrong" character (to
     * possibly allow error recovery), but need to report accurate location for that character: if so, the current
     * location is past location we want, and location we want will be "one location earlier".
     *
     * Default implementation simply returns [currentLocation].
     *
     * @return Same as [currentLocation] except offset by `-1`
     */
    protected fun currentLocationMinusOne(): CirJsonLocation {
        return currentLocation()
    }

    protected fun hasTextualNull(value: String?): Boolean {
        return value == "null"
    }

    /*
     *******************************************************************************************************************
     * Error reporting, generic
     *******************************************************************************************************************
     */

    protected fun <T> reportError(message: String): T {
        throw constructReadException(message)
    }

    protected fun wrapIOFailure(exception: IOException): CirJacksonIOException {
        return CirJacksonIOException.construct(exception, this)
    }

    protected fun <T> throwInternal(): T {
        return Other.throwInternalReturnAny()
    }

    /*
     *******************************************************************************************************************
     * Error reporting, numeric conversion/parsing issues
     *******************************************************************************************************************
     */

    @Throws(InputCoercionException::class)
    protected fun reportOverflowByte(numDesc: String, inputType: CirJsonToken) {
        throw constructInputCoercion(
                "Numeric value (${longIntegerDesc(numDesc)}) out of range of `Byte` ($INT_MIN_BYTE - $INT_MAX_BYTE)",
                inputType, Byte::class.java)
    }

    @Throws(InputCoercionException::class)
    protected fun reportOverflowShort(numDesc: String, inputType: CirJsonToken) {
        throw constructInputCoercion(
                "Numeric value (${longIntegerDesc(numDesc)}) out of range of `Short` ($INT_MIN_SHORT - $INT_MAX_SHORT)",
                inputType, Byte::class.java)
    }

    /**
     * Method called to throw an exception for integral (not floating point) input token with value outside the signed
     * 32-bit range when requested as `Int`. Result will be [InputCoercionException] being thrown.
     *
     * @throws InputCoercionException Exception that describes problem with number range validity
     */
    @Throws(InputCoercionException::class)
    protected fun reportOverflowInt() {
        reportOverflowInt(text!!)
    }

    @Throws(InputCoercionException::class)
    protected fun reportOverflowInt(numDesc: String) {
        reportOverflowInt(numDesc, currentToken()!!)
    }

    @Throws(InputCoercionException::class)
    protected fun reportOverflowInt(numDesc: String, inputType: CirJsonToken) {
        throw constructInputCoercion("Numeric value (${
            longIntegerDesc(numDesc)
        }) out of range of `Int` (${Int.MIN_VALUE} - ${Int.MAX_VALUE})", inputType, Int::class.java)
    }

    /**
     * Method called to throw an exception for integral (not floating point) input token with value outside the signed
     * 64-bit range when requested as `Long`. Result will be [InputCoercionException] being thrown.
     *
     * @throws InputCoercionException Exception that describes problem with number range validity
     */
    @Throws(InputCoercionException::class)
    protected fun reportOverflowLong() {
        reportOverflowLong(text!!)
    }

    @Throws(InputCoercionException::class)
    protected fun reportOverflowLong(numDesc: String) {
        reportOverflowLong(numDesc, currentToken()!!)
    }

    @Throws(InputCoercionException::class)
    protected fun reportOverflowLong(numDesc: String, inputType: CirJsonToken) {
        throw constructInputCoercion("Numeric value (${
            longIntegerDesc(numDesc)
        }) out of range of `Long` (${Long.MIN_VALUE} - ${Long.MAX_VALUE})", inputType, Long::class.java)
    }

    protected fun longIntegerDesc(numDesc: String): String {
        var rawLength = numDesc.length

        if (rawLength < 1000) {
            return numDesc
        }

        if (numDesc.startsWith("-")) {
            rawLength--
        }

        return "[Integer with $rawLength digits]"
    }

    protected fun longNumberDesc(numDesc: String): String {
        var rawLength = numDesc.length

        if (rawLength < 1000) {
            return numDesc
        }

        if (numDesc.startsWith("-")) {
            rawLength--
        }

        return "[Number with $rawLength characters]"
    }

    /*
     *******************************************************************************************************************
     * Error reporting, EOF, unexpected chars/content
     *******************************************************************************************************************
     */

    @Throws(StreamReadException::class)
    protected fun <T> reportBadInputStream(readLength: Int): T {
        throw wrapIOFailure(IOException(
                "Bad input source: InputStream.read() returned 0 bytes when trying to read $readLength bytes"))
    }

    @Throws(StreamReadException::class)
    protected fun <T> reportBadReader(readLength: Int): T {
        throw wrapIOFailure(
                IOException("Bad input source: Reader.read() returned 0 bytes when trying to read $readLength bytes"))
    }

    @Throws(StreamReadException::class)
    protected fun <T> reportInvalidEOF(): T {
        return reportInvalidEOF("in $myCurrentToken", myCurrentToken!!)
    }

    @Throws(StreamReadException::class)
    protected fun <T> reportInvalidEOFInValue(currentToken: CirJsonToken): T {
        val message = if (currentToken === CirJsonToken.VALUE_STRING) {
            "in a String value"
        } else if (currentToken === CirJsonToken.VALUE_NUMBER_INT || currentToken === CirJsonToken.VALUE_NUMBER_FLOAT) {
            "in a Number value"
        } else {
            "in a value"
        }

        return reportInvalidEOF(message, currentToken)
    }

    @Throws(StreamReadException::class)
    protected fun <T> reportInvalidEOF(message: String, currentToken: CirJsonToken): T {
        throw UnexpectedEndOfInputException(this, currentToken, "Unexpected end-of-input $message")
    }

    @Throws(StreamReadException::class)
    protected fun <T> reportInvalidSpace(code: Int): T {
        val char = code.toChar()

        val message = "Illegal character (${
            getCharDesc(char)
        }): only regular white space (\\r, \\n, \\t) is allowed between tokens"
        throw constructReadException(message, currentLocationMinusOne())
    }

    @Throws(StreamReadException::class)
    protected fun <T> reportMissingRootWhiteSpace(char: Char): T {
        return reportUnexpectedChar(char, "Expected space separating root-level values")
    }

    @Throws(StreamReadException::class)
    protected fun <T> reportUnexpectedChar(char: Char, comment: String?): T {
        if (char.code < 0) {
            return reportInvalidEOF()
        }

        var message = "Unexpected character (${getCharDesc(char)})"

        if (comment != null) {
            message += ": $comment"
        }

        throw constructReadException(message, currentLocationMinusOne())
    }

    @Throws(StreamReadException::class)
    protected fun <T> reportUnexpectedNumberChar(char: Char, comment: String?): T {
        var message = "Unexpected character (${getCharDesc(char)}) in numeric value"

        if (comment != null) {
            message += ": $comment"
        }

        throw constructReadException(message, currentLocationMinusOne())
    }

    /*
     *******************************************************************************************************************
     * Error reporting, input coercion support
     *******************************************************************************************************************
     */

    protected fun constructNotNumericType(actualToken: CirJsonToken, expectedNumericType: Int): InputCoercionException {
        val message = "Current token ($actualToken) not numeric, can not use numeric value accessors"
        val targetType = when (expectedNumericType) {
            NUMBER_INT -> Int::class.java
            NUMBER_LONG -> Long::class.java
            NUMBER_BIG_INT -> BigInteger::class.java
            NUMBER_FLOAT -> Float::class.java
            NUMBER_DOUBLE -> Double::class.java
            NUMBER_BIG_DECIMAL -> BigDecimal::class.java
            else -> Number::class.java
        }

        return constructInputCoercion(message, actualToken, targetType)
    }

    protected fun constructInputCoercion(message: String, inputType: CirJsonToken,
            targetType: Class<*>): InputCoercionException {
        return InputCoercionException(this, message, inputType, targetType)
    }

    companion object {

        // Control chars:

        const val CODE_TAB = '\t'.code

        const val CODE_LF = '\n'.code

        const val CODE_CR = '\r'.code

        const val CODE_SPACE = 0x0020

        // Markup

        const val CODE_L_BRACKET = '['.code

        const val CODE_R_BRACKET = ']'.code

        const val CODE_L_CURLY = '{'.code

        const val CODE_R_CURLY = '}'.code

        const val CODE_QUOTE = '"'.code

        const val CODE_APOSTROPHE = '\''.code

        const val CODE_BACKSLASH = '\\'.code

        const val CODE_SLASH = '/'.code

        const val CODE_ASTERISK = '*'.code

        const val CODE_COLON = ':'.code

        const val CODE_COMMA = ','.code

        const val CODE_HASH = '#'.code

        // Numbers

        const val CODE_0 = '0'.code

        const val CODE_9 = '9'.code

        const val CODE_MINUS = '-'.code

        const val CODE_PLUS = '+'.code

        const val CODE_PERIOD = '.'.code

        const val CODE_E_LOWERCASE = 'e'.code

        const val CODE_E_UPPERCASE = 'E'.code

        // Other

        const val CODE_NULL_CHAR = '\u0000'.code

        val NO_BYTES = byteArrayOf()

        val NO_INTEGERS = intArrayOf()

        /*
         ***************************************************************************************************************
         * Constants and fields write number handling
         ***************************************************************************************************************
         */

        const val NUMBER_UNKNOWN = 0

        // Integer types

        const val NUMBER_INT = 0x0001

        const val NUMBER_LONG = 0x0002

        const val NUMBER_BIG_INT = 0x0004

        const val NUMBER_DOUBLE = 0x0008

        const val NUMBER_BIG_DECIMAL = 0x0010

        /**
         * NOTE! Not used by CirJSON implementation but used by many of binary codecs
         */
        const val NUMBER_FLOAT = 0x0020

        // Numeric constants

        val BIG_INT_MIN_INT = BigInteger.valueOf(Int.MIN_VALUE.toLong())

        val BIG_INT_MAX_INT = BigInteger.valueOf(Int.MAX_VALUE.toLong())

        val BIG_INT_MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE)

        val BIG_INT_MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE)

        val BIG_DECIMAL_MIN_INT = BigDecimal(Int.MIN_VALUE.toLong())

        val BIG_DECIMAL_MAX_INT = BigDecimal(Int.MAX_VALUE.toLong())

        val BIG_DECIMAL_MIN_LONG = BigDecimal(Long.MIN_VALUE)

        val BIG_DECIMAL_MAX_LONG = BigDecimal(Long.MAX_VALUE)

        const val INT_MIN_BYTE = Byte.MIN_VALUE

        /**
         * Allow range up to and including 255, to support signed AND unsigned bytes
         */
        const val INT_MAX_BYTE = 255

        const val INT_MIN_SHORT = Short.MIN_VALUE

        const val INT_MAX_SHORT = Short.MAX_VALUE

        const val LONG_MIN_INT = Int.MIN_VALUE.toLong()

        const val LONG_MAX_INT = Int.MAX_VALUE.toLong()

        const val DOUBLE_MIN_INT = Int.MIN_VALUE.toDouble()

        const val DOUBLE_MAX_INT = Int.MAX_VALUE.toDouble()

        const val DOUBLE_MIN_LONG = Long.MIN_VALUE.toDouble()

        const val DOUBLE_MAX_LONG = Long.MAX_VALUE.toDouble()

        /*
         ***************************************************************************************************************
         * Other
         ***************************************************************************************************************
         */

        val STREAM_READ_FEATURE_DEFAULTS = StreamReadFeature.collectDefaults()

        fun getCharDesc(char: Char): String {
            val code = char.code

            return if (char.isISOControl()) {
                "(CTRL-CHAR, code $code)"
            } else if (code > 255) {
                "'$char' (code $code / 0x${code.toString(16)})"
            } else {
                "'$char' (code $code)"
            }
        }

    }

}