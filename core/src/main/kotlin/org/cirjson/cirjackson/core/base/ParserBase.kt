package org.cirjson.cirjackson.core.base

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.exception.InputCoercionException
import org.cirjson.cirjackson.core.exception.StreamConstraintsException
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.extensions.growBy
import org.cirjson.cirjackson.core.io.ContentReference
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.io.NumberInput
import org.cirjson.cirjackson.core.util.ByteArrayBuilder
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.max

/**
 * Intermediate base class used by many (but not all) CirJackson [CirJsonParser] implementations. Contains most common
 * things that are independent of actual underlying input source.
 */
abstract class ParserBase(objectReadContext: ObjectReadContext, ioContext: IOContext, streamReadFeatures: Int) :
        ParserMinimalBase(objectReadContext, ioContext, streamReadFeatures) {

    /**
     * Pointer to next available character in buffer
     */
    protected var myInputPointer = 0

    /**
     * Index of character after last available one in the buffer.
     */
    protected var myInputEnd = 0

    /**
     * Number of characters/bytes that were contained in previous blocks (blocks that were already processed prior to
     * the current buffer).
     */
    protected var myCurrentInputProcessed = 0L

    /**
     * Current row location of current point in input buffer, starting from 1, if available.
     */
    protected var myCurrentInputRow = 1L

    /**
     * Current index of the first character of the current row in input buffer. Needed to calculate column position, if
     * necessary; benefit of not having column itself is that this only has to be updated once per line.
     */
    protected var myCurrentInputRowStart = 0L

    /**
     * Total number of bytes/characters read before start of current token. For big (gigabyte-sized) sizes are possible,
     * needs to be Long, unlike pointers and sizes related to in-memory buffers.
     */
    var tokenCharacterOffset = 0L
        protected set

    /**
     * Input row on which current token starts, 1-based
     */
    var tokenLineNumber = 0
        protected set

    /**
     * Column on input row that current token starts; 0-based (although in the end it'll be converted to 1-based in
     * [tokenInputColumn])
     */
    protected var myTokenInputColumn = 1

    /**
     * Column on input row that current token starts; 1-based
     */
    val tokenInputColumn
        get() = if (myTokenInputColumn >= 0) myTokenInputColumn + 1 else myTokenInputColumn

    /**
     * Buffer that contains contents of String values, including property names if necessary (name split across
     * boundary, contains escape sequence, or access needed to Char array)
     */
    protected val myTextBuffer = ioContext.constructReadConstrainedTextBuffer()

    private val myByteArrayBuilderDelegate = lazy { ByteArrayBuilder() }

    /**
     * ByteArrayBuilder is needed if 'getBinaryValue' is called. If so, we better reuse it for remainder of content.
     */
    private val myByteArrayBuilder by myByteArrayBuilderDelegate

    protected val byteArrayBuilder: ByteArrayBuilder
        get() {
            if (myByteArrayBuilderDelegate.isInitialized()) {
                myByteArrayBuilder.reset()
            }

            return myByteArrayBuilder
        }

    /**
     * We will hold on to decoded binary data, for duration of current event, so that multiple calls to [getBinaryValue]
     * will not need to decode data more than once.
     */
    var myBinaryValue: ByteArray? = null

    /**
     * Bitfield that indicates which numeric representations
     * have been calculated for the current type
     */
    protected var myNumberTypesValid = NUMBER_UNKNOWN

    protected var myNumberInt = 0

    protected var myNumberLong = 0L

    protected var myNumberFloat = 0.0f

    protected var myNumberDouble = 0.0

    protected var myNumberBigInteger: BigInteger? = null

    protected var myNumberBigDecimal: BigDecimal? = null

    /**
     * Textual number representation captured from input in cases lazy-parsing is desired.
     */
    protected var myNumberString: String? = null

    /**
     * Marker for explicit "Not a Number" (NaN) values that may be read by some formats: this includes positive and
     * negative infinity, as well as "NaN" result for some arithmetic operations.
     *
     * In case of CirJSON, such values can only be handled with non-standard processing: for some other formats they can
     * be passed normally.
     *
     * NOTE: this marker is NOT set in case of value overflow/underflow for `Double` or `Float` values.
     */
    protected var myNumberIsNaN = false

    /**
     * Flag that indicates whether numeric value has a negative value. That is, whether its textual representation
     * starts with minus character.
     */
    protected var myNumberNegative = false

    /**
     * Length of integer part of the number, in characters
     */
    protected var myIntLength = 0

    /**
     * Length of the fractional part (not including decimal point or exponent), in characters. Not used for pure integer
     * values.
     */
    protected var myFractionLength = 0

    /**
     * Length of the exponent part of the number, if any, not including 'e' marker or sign, just digits. Not used for
     * pure integer values.
     */
    protected var myExponentLength = 0

    /*
     *******************************************************************************************************************
     * CirJsonParser impl
     *******************************************************************************************************************
     */

    override fun currentValue(): Any? {
        val context = streamReadContext
        return context?.currentValue()
    }

    override fun assignCurrentValue(value: Any?) {
        val context = streamReadContext
        context?.assignCurrentValue(value)
    }

    override fun close() {
        super.close()
        myInputPointer = max(myInputPointer, myInputEnd)
    }

    /*
     *******************************************************************************************************************
     * Public API, access to token information, text and similar
     *******************************************************************************************************************
     */

    override val isTextCharactersAvailable: Boolean
        get() = false

    override fun getBinaryValue(base64Variant: Base64Variant): ByteArray {
        if (myBinaryValue == null) {
            if (myCurrentToken !== CirJsonToken.VALUE_STRING) {
                return reportError(
                        "Current token ($myCurrentToken) not VALUE_EMBEDDED_OBJECT or VALUE_STRING, can not access as binary")
            }
        }

        return myBinaryValue!!
    }

    /*
     *******************************************************************************************************************
     * Low-level reading, other
     *******************************************************************************************************************
     */

    override fun releaseBuffers() {
        myTextBuffer.releaseBuffers()
    }

    /**
     * Method called when an EOF is encountered between tokens. If so, it may be a legitimate EOF, but **only** if there
     * is no open non-root context.
     */
    override fun handleEOF() {
        val context = streamReadContext ?: return

        if (context.isInRoot) {
            return
        }

        val marker = if (context.isInArray) {
            "Array"
        } else if (context.isInObject) {
            "Object"
        } else {
            throw IllegalStateException("Either `isInArray` or `isInObject` should be true")
        }

        return reportInvalidEOF(
                ": expected close marker for $marker (start marker at ${context.startLocation(contentReference())})",
                null)
    }

    /**
     * Method used to return "end-of-input" or throw an exception if [handleEOF] fails.
     *
     * @return If no exception is thrown, `-1` which is used as marked for "end-of-input"
     *
     * @throws StreamReadException If check on `handleEOF()` fails; usually because the current context is not root
     * context (missing end markers in content)
     */
    @Throws(StreamReadException::class)
    protected fun eofAsNextChar(): Int {
        handleEOF()
        return -1
    }

    /*
     *******************************************************************************************************************
     * Methods related to number handling
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun reset(negative: Boolean, intLength: Int, fractionLength: Int, exponentLength: Int): CirJsonToken {
        return if (fractionLength < 1 && exponentLength < 1) {
            resetInt(negative, intLength)
        } else {
            resetFloat(negative, intLength, fractionLength, exponentLength)
        }
    }

    @Throws(CirJacksonException::class)
    protected fun resetInt(negative: Boolean, intLength: Int): CirJsonToken {
        streamReadConstraints.validateIntegerLength(intLength)
        myNumberNegative = negative
        myNumberIsNaN = false
        myIntLength = intLength
        myFractionLength = 0
        myExponentLength = 0
        myNumberTypesValid = NUMBER_UNKNOWN
        return CirJsonToken.VALUE_NUMBER_INT
    }

    @Throws(CirJacksonException::class)
    protected fun resetFloat(negative: Boolean, intLength: Int, fractionLength: Int,
            exponentLength: Int): CirJsonToken {
        streamReadConstraints.validateFloatingPointLength(intLength + fractionLength + exponentLength)
        myNumberNegative = negative
        myIntLength = intLength
        myFractionLength = fractionLength
        myExponentLength = exponentLength
        myNumberIsNaN = false
        myNumberTypesValid = NUMBER_UNKNOWN
        return CirJsonToken.VALUE_NUMBER_FLOAT
    }

    protected fun resetAsNaN(valueString: String, value: Double): CirJsonToken {
        myTextBuffer.resetWithString(valueString)
        myNumberDouble = value
        myNumberTypesValid = NUMBER_DOUBLE
        myNumberIsNaN = true
        return CirJsonToken.VALUE_NUMBER_FLOAT
    }

    override val isNaN: Boolean
        get() = myCurrentToken == CirJsonToken.VALUE_NUMBER_FLOAT && myNumberIsNaN

    /*
     *******************************************************************************************************************
     * Public API, numeric accessors
     *******************************************************************************************************************
     */

    override val numberValue: Number
        get() {
            if (myNumberTypesValid == NUMBER_UNKNOWN) {
                parseNumericValue(NUMBER_UNKNOWN)
            }

            return when {
                myCurrentToken == CirJsonToken.VALUE_NUMBER_INT -> {
                    when {
                        myNumberTypesValid and NUMBER_INT != 0 -> myNumberInt
                        myNumberTypesValid and NUMBER_LONG != 0 -> myNumberLong
                        myNumberTypesValid and NUMBER_BIG_INTEGER != 0 -> bigInteger
                        else -> throwInternal()
                    }
                }

                myNumberTypesValid and NUMBER_BIG_DECIMAL != 0 -> bigDecimal

                myNumberTypesValid and NUMBER_FLOAT != 0 -> numberFloat

                myNumberTypesValid and NUMBER_DOUBLE != 0 -> numberDouble

                else -> throwInternal()
            }
        }

    override val numberValueExact: Number
        get() {
            if (myCurrentToken == CirJsonToken.VALUE_NUMBER_INT) {
                if (myNumberTypesValid == NUMBER_UNKNOWN) {
                    parseNumericValue(NUMBER_UNKNOWN)
                }

                return when {
                    myNumberTypesValid and NUMBER_INT != 0 -> myNumberInt
                    myNumberTypesValid and NUMBER_LONG != 0 -> myNumberLong
                    myNumberTypesValid and NUMBER_BIG_INTEGER != 0 -> bigInteger
                    else -> throwInternal()
                }
            }

            if (myNumberTypesValid == NUMBER_UNKNOWN) {
                parseNumericValue(NUMBER_BIG_DECIMAL)
            }

            return when {
                myNumberTypesValid and NUMBER_BIG_DECIMAL != 0 -> bigDecimal
                myNumberTypesValid and NUMBER_FLOAT != 0 -> numberFloat
                myNumberTypesValid and NUMBER_DOUBLE != 0 -> numberDouble
                else -> throwInternal()
            }
        }

    override val numberValueDeferred: Any
        get() {
            return if (myCurrentToken == CirJsonToken.VALUE_NUMBER_INT) {
                if (myNumberTypesValid == NUMBER_UNKNOWN) {
                    parseNumericValue(NUMBER_UNKNOWN)
                }

                when {
                    myNumberTypesValid and NUMBER_INT != 0 -> myNumberInt

                    myNumberTypesValid and NUMBER_LONG != 0 -> myNumberLong

                    myNumberTypesValid and NUMBER_BIG_INTEGER != 0 -> {
                        if (myNumberBigInteger != null) {
                            myNumberBigInteger!!
                        } else if (myNumberString != null) {
                            myNumberString!!
                        } else {
                            bigInteger
                        }
                    }

                    else -> throwInternal()
                }
            } else if (myCurrentToken == CirJsonToken.VALUE_NUMBER_FLOAT) {
                when {
                    myNumberTypesValid and NUMBER_BIG_DECIMAL != 0 -> bigDecimal
                    myNumberTypesValid and NUMBER_FLOAT != 0 -> numberFloat
                    myNumberTypesValid and NUMBER_DOUBLE != 0 -> numberDouble
                    else -> myTextBuffer.contentsAsString()
                }
            } else {
                numberValue
            }
        }

    override val numberType: NumberType
        get() {
            if (myNumberTypesValid == NUMBER_UNKNOWN) {
                parseNumericValue(NUMBER_UNKNOWN)
            }

            return when {
                myCurrentToken == CirJsonToken.VALUE_NUMBER_INT -> {
                    when {
                        myNumberTypesValid and NUMBER_INT != 0 -> NumberType.INT
                        myNumberTypesValid and NUMBER_LONG != 0 -> NumberType.LONG
                        myNumberTypesValid and NUMBER_BIG_INTEGER != 0 -> NumberType.BIG_INTEGER
                        else -> throwInternal()
                    }
                }

                myNumberTypesValid and NUMBER_BIG_DECIMAL != 0 -> NumberType.BIG_DECIMAL

                myNumberTypesValid and NUMBER_FLOAT != 0 -> NumberType.FLOAT

                else -> NumberType.DOUBLE
            }
        }

    @get:Throws(CirJacksonException::class)
    override val intValue: Int
        get() {
            if (myNumberTypesValid and NUMBER_INT == 0) {
                if (myNumberTypesValid == NUMBER_UNKNOWN) {
                    return parseIntValue()
                }

                if (myNumberTypesValid and NUMBER_INT == 0) {
                    convertNumberToInt()
                }
            }

            return myNumberInt
        }

    @get:Throws(CirJacksonException::class)
    override val longValue: Long
        get() {
            if (myNumberTypesValid and NUMBER_LONG == 0) {
                if (myNumberTypesValid == NUMBER_UNKNOWN) {
                    parseNumericValue(NUMBER_LONG)
                }

                if (myNumberTypesValid and NUMBER_LONG == 0) {
                    convertNumberToLong()
                }
            }

            return myNumberLong
        }

    @get:Throws(CirJacksonException::class)
    override val bigIntegerValue: BigInteger
        get() {
            if (myNumberTypesValid and NUMBER_BIG_INTEGER == 0) {
                if (myNumberTypesValid == NUMBER_UNKNOWN) {
                    parseNumericValue(NUMBER_BIG_INTEGER)
                }

                if (myNumberTypesValid and NUMBER_BIG_INTEGER == 0) {
                    convertNumberToBigInteger()
                }
            }

            return bigInteger
        }

    @get:Throws(CirJacksonException::class)
    override val floatValue: Float
        get() {
            if (myNumberTypesValid and NUMBER_FLOAT == 0) {
                if (myNumberTypesValid == NUMBER_UNKNOWN) {
                    parseNumericValue(NUMBER_FLOAT)
                }

                if (myNumberTypesValid and NUMBER_FLOAT == 0) {
                    convertNumberToFloat()
                }
            }

            return numberFloat
        }

    @get:Throws(CirJacksonException::class)
    override val doubleValue: Double
        get() {
            if (myNumberTypesValid and NUMBER_DOUBLE == 0) {
                if (myNumberTypesValid == NUMBER_UNKNOWN) {
                    parseNumericValue(NUMBER_DOUBLE)
                }

                if (myNumberTypesValid and NUMBER_DOUBLE == 0) {
                    convertNumberToDouble()
                }
            }

            return numberDouble
        }

    @get:Throws(CirJacksonException::class)
    override val bigDecimalValue: BigDecimal
        get() {
            if (myNumberTypesValid and NUMBER_BIG_DECIMAL == 0) {
                if (myNumberTypesValid == NUMBER_UNKNOWN) {
                    parseNumericValue(NUMBER_BIG_DECIMAL)
                }

                if (myNumberTypesValid and NUMBER_BIG_DECIMAL == 0) {
                    convertNumberToBigDecimal()
                }
            }

            return bigDecimal
        }

    /*
     *******************************************************************************************************************
     * Abstract methods subclasses will need to provide
     *******************************************************************************************************************
     */

    /**
     * Method that will parse actual numeric value out of a syntactically valid number value. Type it will parse into
     * depends on whether it is a floating point number, as well as its magnitude: smallest legal type (of ones
     * available) is used for efficiency.
     *
     * @param numericType Numeric type that we will immediately need, if any; mostly necessary to optimize handling of
     * floating point numbers
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws InputCoercionException if the current token not of numeric type
     *
     * @throws StreamReadException for number decoding problems
     */
    @Throws(CirJacksonException::class, InputCoercionException::class)
    protected abstract fun parseNumericValue(numericType: Int)

    @Throws(CirJacksonException::class)
    protected abstract fun parseIntValue(): Int

    /*
     *******************************************************************************************************************
     * Numeric conversions
     *******************************************************************************************************************
     */

    @Throws(InputCoercionException::class)
    protected fun convertNumberToInt() {
        myNumberInt = when {
            myNumberTypesValid and NUMBER_LONG != 0 -> {
                val result = myNumberLong.toInt()

                if (result.toLong() != myNumberLong) {
                    reportOverflowInt(text!!, currentToken()!!)
                }

                result
            }

            myNumberTypesValid and NUMBER_BIG_INTEGER != 0 -> {
                val bigInteger = bigInteger

                if (bigInteger !in BIG_INT_MIN_INT..BIG_INT_MAX_INT) {
                    reportOverflowInt()
                }

                bigInteger.toInt()
            }

            myNumberTypesValid and NUMBER_DOUBLE != 0 -> {
                val double = numberDouble

                if (double !in DOUBLE_MIN_INT..DOUBLE_MAX_INT) {
                    reportOverflowInt()
                }

                double.toInt()
            }

            myNumberTypesValid and NUMBER_BIG_DECIMAL != 0 -> {
                val bigDecimal = bigDecimal

                if (bigDecimal !in BIG_DECIMAL_MIN_INT..BIG_DECIMAL_MAX_INT) {
                    reportOverflowInt()
                }

                bigDecimal.toInt()
            }

            else -> throwInternal()
        }

        myNumberTypesValid = myNumberTypesValid or NUMBER_INT
    }

    @Throws(InputCoercionException::class)
    protected fun convertNumberToLong() {
        myNumberLong = when {
            myNumberTypesValid and NUMBER_INT != 0 -> {
                myNumberInt.toLong()
            }

            myNumberTypesValid and NUMBER_BIG_INTEGER != 0 -> {
                val bigInteger = bigInteger

                if (bigInteger !in BIG_INT_MIN_LONG..BIG_INT_MAX_LONG) {
                    reportOverflowLong()
                }

                bigInteger.toLong()
            }

            myNumberTypesValid and NUMBER_DOUBLE != 0 -> {
                val double = numberDouble

                if (double !in DOUBLE_MIN_LONG..DOUBLE_MAX_LONG) {
                    reportOverflowLong()
                }

                double.toLong()
            }

            myNumberTypesValid and NUMBER_BIG_DECIMAL != 0 -> {
                val bigDecimal = bigDecimal

                if (bigDecimal !in BIG_DECIMAL_MIN_LONG..BIG_DECIMAL_MAX_LONG) {
                    reportOverflowLong()
                }

                bigDecimal.toLong()
            }

            else -> throwInternal()
        }

        myNumberTypesValid = myNumberTypesValid or NUMBER_LONG
    }

    @Throws(InputCoercionException::class)
    protected fun convertNumberToBigInteger() {
        myNumberBigInteger = when {
            myNumberTypesValid and NUMBER_BIG_DECIMAL != 0 -> {
                convertBigDecimalToBigInteger(bigDecimal)
            }

            myNumberTypesValid and NUMBER_LONG != 0 -> {
                BigInteger.valueOf(myNumberLong)
            }

            myNumberTypesValid and NUMBER_INT != 0 -> {
                BigInteger.valueOf(myNumberInt.toLong())
            }

            myNumberTypesValid and NUMBER_DOUBLE != 0 -> {
                if (myNumberString != null) {
                    convertBigDecimalToBigInteger(bigDecimal)
                } else {
                    convertBigDecimalToBigInteger(BigDecimal.valueOf(numberDouble))
                }
            }

            else -> throwInternal()
        }

        myNumberTypesValid = myNumberTypesValid or NUMBER_BIG_INTEGER
    }

    @Throws(InputCoercionException::class)
    protected fun convertNumberToDouble() {
        myNumberDouble = when {
            myNumberTypesValid and NUMBER_BIG_DECIMAL != 0 -> {
                if (myNumberString != null) {
                    numberDouble
                } else {
                    bigDecimal.toDouble()
                }
            }

            myNumberTypesValid and NUMBER_BIG_INTEGER != 0 -> {
                if (myNumberString != null) {
                    numberDouble
                } else {
                    bigInteger.toDouble()
                }
            }

            myNumberTypesValid and NUMBER_LONG != 0 -> {
                myNumberLong.toDouble()
            }

            myNumberTypesValid and NUMBER_INT != 0 -> {
                myNumberInt.toDouble()
            }

            myNumberTypesValid and NUMBER_FLOAT != 0 -> {
                if (myNumberString != null) {
                    numberDouble
                } else {
                    numberFloat.toDouble()
                }
            }

            else -> throwInternal()
        }

        myNumberTypesValid = myNumberTypesValid or NUMBER_DOUBLE
    }

    @Throws(InputCoercionException::class)
    protected fun convertNumberToFloat() {
        myNumberFloat = when {
            myNumberTypesValid and NUMBER_BIG_DECIMAL != 0 -> {
                if (myNumberString != null) {
                    numberFloat
                } else {
                    bigDecimal.toFloat()
                }
            }

            myNumberTypesValid and NUMBER_BIG_INTEGER != 0 -> {
                if (myNumberString != null) {
                    numberFloat
                } else {
                    bigInteger.toFloat()
                }
            }

            myNumberTypesValid and NUMBER_LONG != 0 -> {
                myNumberLong.toFloat()
            }

            myNumberTypesValid and NUMBER_INT != 0 -> {
                myNumberInt.toFloat()
            }

            myNumberTypesValid and NUMBER_DOUBLE != 0 -> {
                if (myNumberString != null) {
                    numberFloat
                } else {
                    numberDouble.toFloat()
                }
            }

            else -> throwInternal()
        }

        myNumberTypesValid = myNumberTypesValid or NUMBER_FLOAT
    }

    @Throws(InputCoercionException::class)
    protected fun convertNumberToBigDecimal() {
        myNumberBigDecimal = when {
            myNumberTypesValid and NUMBER_DOUBLE != 0 -> {
                val numberString = myNumberString ?: text!!
                NumberInput.parseBigDecimal(numberString, isEnabled(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER))
            }

            myNumberTypesValid and NUMBER_BIG_INTEGER != 0 -> {
                BigDecimal(bigInteger)
            }

            myNumberTypesValid and NUMBER_LONG != 0 -> {
                BigDecimal.valueOf(myNumberLong)
            }

            myNumberTypesValid and NUMBER_INT != 0 -> {
                BigDecimal.valueOf(myNumberInt.toLong())
            }

            else -> throwInternal()
        }

        myNumberTypesValid = myNumberTypesValid or NUMBER_BIG_DECIMAL
    }

    protected fun convertBigDecimalToBigInteger(bigDecimal: BigDecimal): BigInteger {
        streamReadConstraints.validateBigIntegerScale(bigDecimal.scale())
        return bigDecimal.toBigInteger()
    }

    /**
     * Internal accessor that needs to be used for accessing number value of type [BigInteger] which is typically lazily
     * parsed.
     */
    protected val bigInteger: BigInteger
        get() {
            if (myNumberBigInteger != null) {
                return myNumberBigInteger!!
            } else if (myNumberString == null) {
                throw IllegalStateException("Cannot get BigInteger from current parser state")
            }

            try {
                myNumberBigInteger = NumberInput.parseBigInteger(myNumberString!!,
                        isEnabled(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER))
            } catch (e: NumberFormatException) {
                throw constructReadException("Malformed numeric value (${longNumberDesc(myNumberString!!)})", e)
            }

            myNumberString = null

            return myNumberBigInteger!!
        }

    /**
     * Internal accessor that needs to be used for accessing number value of type [BigDecimal] which is typically lazily
     * parsed.
     */
    protected val bigDecimal: BigDecimal
        get() {
            if (myNumberBigDecimal != null) {
                return myNumberBigDecimal!!
            } else if (myNumberString == null) {
                throw IllegalStateException("Cannot get BigInteger from current parser state")
            }

            try {
                myNumberBigDecimal = NumberInput.parseBigDecimal(myNumberString!!,
                        isEnabled(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER))
            } catch (e: NumberFormatException) {
                throw constructReadException("Malformed numeric value (${longNumberDesc(myNumberString!!)})", e)
            }

            myNumberString = null

            return myNumberBigDecimal!!
        }

    /**
     * Internal accessor that needs to be used for accessing number value of type `Double` which will be lazily parsed.
     */
    protected val numberDouble: Double
        get() {
            if (myNumberString != null) {
                try {
                    myNumberDouble = NumberInput.parseDouble(myNumberString!!,
                            isEnabled(StreamReadFeature.USE_FAST_DOUBLE_PARSER))
                } catch (e: NumberFormatException) {
                    throw constructReadException("Malformed numeric value (${longNumberDesc(myNumberString!!)})", e)
                }
            }

            return myNumberDouble
        }

    /**
     * Internal accessor that needs to be used for accessing number value of type `Float` which will be lazily parsed.
     */
    protected val numberFloat: Float
        get() {
            if (myNumberString != null) {
                try {
                    myNumberFloat = NumberInput.parseFloat(myNumberString!!,
                            isEnabled(StreamReadFeature.USE_FAST_DOUBLE_PARSER))
                } catch (e: NumberFormatException) {
                    throw constructReadException("Malformed numeric value (${longNumberDesc(myNumberString!!)})", e)
                }
            }

            return myNumberFloat
        }

    /*
     *******************************************************************************************************************
     * Base64 handling support
     *******************************************************************************************************************
     */

    /**
     * Method that subclasses must implement to support escaped sequences in base64-encoded sections. Subclasses that do
     * not need base64 support can leave this as is.
     *
     * Default implementation throws [UnsupportedOperationException].
     *
     * @return Character decoded, if any
     *
     * @throws CirJacksonException If escape decoding fails
     */
    @Throws(CirJacksonException::class)
    protected open fun decodeEscaped(): Char {
        throw UnsupportedOperationException()
    }

    @Throws(CirJacksonException::class)
    protected fun decodeBase64Escape(base64Variant: Base64Variant, code: Int, index: Int): Int {
        if (code != '\\'.code) {
            return reportInvalidBase64Char(base64Variant, code.toChar(), index)
        }

        val unescaped = decodeEscaped()

        if (unescaped.code <= CODE_SPACE) {
            if (index == 0) {
                return -1
            }
        }

        val bits = base64Variant.decodeBase64Char(unescaped)

        if (bits < 0) {
            if (bits != Base64Variant.BASE64_VALUE_PADDING) {
                return reportInvalidBase64Char(base64Variant, unescaped, index)
            }
        }

        return bits
    }

    @Throws(CirJacksonException::class)
    protected fun decodeBase64Escape(base64Variant: Base64Variant, char: Char, index: Int): Int {
        if (char != '\\') {
            return reportInvalidBase64Char(base64Variant, char, index)
        }

        val unescaped = decodeEscaped()

        if (unescaped.code <= CODE_SPACE) {
            if (index == 0) {
                return -1
            }
        }

        val bits = base64Variant.decodeBase64Char(unescaped)

        if (bits < 0) {
            if (bits != Base64Variant.BASE64_VALUE_PADDING || index < 2) {
                return reportInvalidBase64Char(base64Variant, unescaped, index)
            }
        }

        return bits
    }

    @Throws(StreamReadException::class)
    protected fun <T> reportInvalidBase64Char(base64Variant: Base64Variant, char: Char, index: Int): T {
        return reportInvalidBase64Char(base64Variant, char, index, null)
    }

    @Throws(StreamReadException::class)
    protected fun <T> reportInvalidBase64Char(base64Variant: Base64Variant, char: Char, index: Int,
            message: String?): T {
        val code = char.code

        var base = when {
            code <= CODE_SPACE -> {
                "Illegal white space character (code 0x${
                    code.toString(16)
                }) as character #${index + 1} of 4-char base64 unit: can only used between units"
            }

            base64Variant.usesPaddingChar(char) -> {
                "Unexpected padding character ('${base64Variant.paddingChar}') as character #${
                    index + 1
                } of 4-char base64 unit: padding only legal as 3rd or 4th character"
            }

            !char.isDefined() || char.isISOControl() -> {
                "Illegal character (code 0x${code.toString(16)}) in base64 content"
            }

            else -> {
                "Illegal character '$char' (code 0x${code.toString(16)}) in base64 content"
            }
        }

        if (message != null) {
            base += ": $message"
        }

        return reportError(base)
    }

    @Throws(StreamReadException::class)
    protected fun <T> handleBase64MissingPadding(base64Variant: Base64Variant): T {
        return reportError(base64Variant.missingPaddingMessage)
    }

    /*
     *******************************************************************************************************************
     * Internal/package methods: other
     *******************************************************************************************************************
     */

    /**
     * Helper method used to encapsulate logic of including (or not) of "content reference" when constructing
     * [CirJsonLocation] instances.
     *
     * @return ContentReference object to use.
     */
    protected fun contentReference(): ContentReference {
        return if (isEnabled(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)) {
            myIOContext!!.contentReference!!
        } else {
            contentReferenceRedacted()
        }
    }

    /**
     * Helper method used to encapsulate logic of providing "content reference" when constructing [CirJsonLocation]
     * instances and source information is **NOT** to be included (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION`
     * disabled).
     *
     * Default implementation will simply return [ContentReference.redacted].
     *
     * @return ContentReference object to use when source is not to be included
     */
    protected fun contentReferenceRedacted(): ContentReference {
        return ContentReference.redacted()
    }

    @Throws(StreamConstraintsException::class)
    protected fun growNameDecodeBuffer(array: IntArray, more: Int): IntArray {
        streamReadConstraints.validateNameLength(array.size shl 2)
        return array.growBy(more)
    }

}