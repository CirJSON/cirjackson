package org.cirjson.cirjackson.core.base

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.exception.InputCoercionException
import org.cirjson.cirjackson.core.exception.StreamConstraintsException
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.extentions.growBy
import org.cirjson.cirjackson.core.io.ContentReference
import org.cirjson.cirjackson.core.io.IOContext
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
        get() = myCurrentToken === CirJsonToken.VALUE_NUMBER_FLOAT && myNumberIsNaN

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
                myCurrentToken === CirJsonToken.VALUE_NUMBER_INT -> {
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
            if (myCurrentToken === CirJsonToken.VALUE_NUMBER_INT) {
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
            return if (myCurrentToken === CirJsonToken.VALUE_NUMBER_INT) {
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
            } else if (myCurrentToken === CirJsonToken.VALUE_NUMBER_FLOAT) {
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
                myCurrentToken === CirJsonToken.VALUE_NUMBER_INT -> {
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
        TODO()
    }

    @Throws(InputCoercionException::class)
    protected fun convertNumberToLong() {
        TODO()
    }

    @Throws(InputCoercionException::class)
    protected fun convertNumberToBigInteger() {
        TODO()
    }

    @Throws(InputCoercionException::class)
    protected fun convertNumberToDouble() {
        TODO()
    }

    @Throws(InputCoercionException::class)
    protected fun convertNumberToFloat() {
        TODO()
    }

    @Throws(InputCoercionException::class)
    protected fun convertNumberToBigDecimal() {
        TODO()
    }

    /**
     * Internal accessor that needs to be used for accessing number value of type [BigInteger] which is typically lazily
     * parsed.
     */
    protected val bigInteger: BigInteger
        get() {
            TODO()
        }

    /**
     * Internal accessor that needs to be used for accessing number value of type [BigDecimal] which is typically lazily
     * parsed.
     */
    protected val bigDecimal: BigDecimal
        get() {
            TODO()
        }

    /**
     * Internal accessor that needs to be used for accessing number value of type `Double` which will be lazily parsed.
     */
    protected val numberDouble: Double
        get() {
            TODO()
        }

    /**
     * Internal accessor that needs to be used for accessing number value of type `Float` which will be lazily parsed.
     */
    protected val numberFloat: Float
        get() {
            TODO()
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