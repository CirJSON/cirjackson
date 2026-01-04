package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.base.ParserBase
import org.cirjson.cirjackson.core.exception.InputCoercionException
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.io.NumberInput
import org.cirjson.cirjackson.core.util.CirJacksonFeatureSet
import kotlin.math.max

/**
 * Another intermediate base class, only used by actual CirJSON-backed parser implementations.
 *
 * @property formatReadFeatures Bit flag for [CirJsonReadFeature]s that are enabled.
 */
abstract class CirJsonParserBase(objectReadContext: ObjectReadContext, ioContext: IOContext, streamReadFeatures: Int,
        protected var formatReadFeatures: Int) : ParserBase(objectReadContext, ioContext, streamReadFeatures) {

    override val idName: String = ID_NAME

    /**
     * Information about parser context, context in which the next token is to be parsed (root, array, object).
     */
    protected var myStreamReadContext = CirJsonReadContext.createRootContext(
            if (StreamReadFeature.STRICT_DUPLICATE_DETECTION.isEnabledIn(streamReadFeatures)) {
                DuplicateDetector.rootDetector(this)
            } else {
                null
            })

    /**
     * Secondary token related to the next token after current one; used if its type is known. This may be value token
     * that follows [CirJsonToken.PROPERTY_NAME], for example.
     */
    protected var myNextToken: CirJsonToken? = null

    /**
     * Temporary buffer that is needed if an Object property name is accessed using [textCharacters] method (instead of
     * String returning alternatives)
     */
    private var myNameCopyBuffer = NO_CHARS

    /**
     * Flag set to indicate whether the Object property name is available from the name copy buffer or not (in addition
     * to its String representation being available via read context)
     */
    protected var myIsNameCopied = false

    /*
     *******************************************************************************************************************
     * Versioned, capabilities, config
     *******************************************************************************************************************
     */

    override fun version(): Version {
        return PackageVersion.VERSION
    }

    override fun streamReadCapabilities(): CirJacksonFeatureSet<StreamReadCapability> = DEFAULT_READ_CAPABILITIES

    /*
     *******************************************************************************************************************
     * ParserBase method implementations/overrides
     *******************************************************************************************************************
     */

    override fun streamReadContext(): TokenStreamContext {
        return myStreamReadContext
    }

    override fun currentValue(): Any? {
        return myStreamReadContext.currentValue()
    }

    override fun assignCurrentValue(value: Any?) {
        myStreamReadContext.assignCurrentValue(value)
    }

    override fun currentName(): String? {
        if (myCurrentToken != CirJsonToken.START_OBJECT && myCurrentToken != CirJsonToken.START_ARRAY) {
            return myStreamReadContext.currentName
        }

        val parent = myStreamReadContext.parent ?: return myStreamReadContext.currentName
        return parent.currentName
    }

    override val isTextCharactersAvailable: Boolean
        get() = when (myCurrentToken) {
            CirJsonToken.VALUE_STRING, CirJsonToken.CIRJSON_ID_PROPERTY_NAME -> true
            CirJsonToken.PROPERTY_NAME -> myIsNameCopied
            else -> false
        }

    /*
     *******************************************************************************************************************
     * Internal/package methods: Context handling
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun createChildArrayContext(lineNumber: Int, columnNumber: Int) {
        myStreamReadContext = myStreamReadContext.createChildArrayContext(lineNumber, columnNumber)
        streamReadConstraints().validateNestingDepth(myStreamReadContext.nestingDepth)
    }

    @Throws(CirJacksonException::class)
    protected fun createChildObjectContext(lineNumber: Int, columnNumber: Int) {
        myStreamReadContext = myStreamReadContext.createChildObjectContext(lineNumber, columnNumber)
        streamReadConstraints().validateNestingDepth(myStreamReadContext.nestingDepth)
    }

    /*
     *******************************************************************************************************************
     * Numeric parsing method implementations
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class, InputCoercionException::class)
    override fun parseNumericValue(numericType: Int) {
        if (myCurrentToken == CirJsonToken.VALUE_NUMBER_FLOAT) {
            parseSlowFloat(numericType)
            return
        }

        if (myCurrentToken != CirJsonToken.VALUE_NUMBER_INT) {
            throw constructNotNumericType(myCurrentToken!!, numericType)
        }

        val length = myIntegralLength

        if (length <= 9) {
            myNumberInt = myTextBuffer.contentAsInt(myNumberNegative)
            myNumberTypesValid = NUMBER_INT
            return
        }

        if (length <= 18) {
            val long = myTextBuffer.contentAsLong(myNumberNegative)

            if (length == 10) {
                if (myNumberNegative) {
                    if (long >= LONG_MIN_INT) {
                        myNumberInt = long.toInt()
                        myNumberTypesValid = NUMBER_INT
                        return
                    }
                } else if (long <= LONG_MAX_INT) {
                    myNumberInt = long.toInt()
                    myNumberTypesValid = NUMBER_INT
                    return
                }
            }

            myNumberLong = long
            myNumberTypesValid = NUMBER_LONG
            return
        }

        if (length == 19) {
            val buffer = myTextBuffer.textBuffer
            var offset = myTextBuffer.textOffset

            if (myNumberNegative) {
                offset++
            }

            if (NumberInput.inLongRange(buffer, offset, length, myNumberNegative)) {
                myNumberLong = NumberInput.parseLong19(buffer, offset, myNumberNegative)
                myNumberTypesValid = NUMBER_LONG
                return
            }
        }

        parseSlowInt(numericType)
    }

    @Throws(CirJacksonException::class)
    private fun parseSlowFloat(numericType: Int) {
        when (numericType) {
            NUMBER_BIG_DECIMAL -> {
                myNumberBigDecimal = null
                myNumberString = myTextBuffer.contentsAsString()
                myNumberTypesValid = NUMBER_BIG_DECIMAL
            }

            NUMBER_FLOAT -> {
                myNumberFloat = 0.0f
                myNumberString = myTextBuffer.contentsAsString()
                myNumberTypesValid = NUMBER_FLOAT
            }

            else -> {
                myNumberDouble = 0.0
                myNumberString = myTextBuffer.contentsAsString()
                myNumberTypesValid = NUMBER_DOUBLE
            }
        }
    }

    @Throws(CirJacksonException::class)
    private fun parseSlowInt(numericType: Int) {
        val rawNumber = myTextBuffer.contentsAsString()

        when (numericType) {
            NUMBER_INT, NUMBER_LONG -> {
                reportTooLongIntegral(numericType, rawNumber)
            }

            NUMBER_DOUBLE, NUMBER_FLOAT -> {
                myNumberString = rawNumber
                myNumberTypesValid = NUMBER_DOUBLE
            }

            else -> {
                myNumberBigInteger = null
                myNumberString = rawNumber
                myNumberTypesValid = NUMBER_BIG_INTEGER
            }
        }
    }

    @Throws(CirJacksonException::class)
    override fun parseIntValue(): Int {
        if (myCurrentToken == CirJsonToken.VALUE_NUMBER_INT) {
            if (myIntegralLength <= 9) {
                val i = myTextBuffer.contentAsInt(myNumberNegative)
                myNumberInt = i
                myNumberTypesValid = NUMBER_INT
                return i
            }
        }

        parseNumericValue(NUMBER_INT)

        if (myNumberTypesValid and NUMBER_INT == 0) {
            convertNumberToInt()
        }

        return myNumberInt
    }

    /*
     *******************************************************************************************************************
     * Internal/package methods: Configuration access
     *******************************************************************************************************************
     */

    fun isEnabled(feature: CirJsonReadFeature): Boolean {
        return feature.isEnabledIn(formatReadFeatures)
    }

    /*
     *******************************************************************************************************************
     * Internal/package methods: Buffer handling
     *******************************************************************************************************************
     */

    protected fun currentNameInBuffer(): CharArray {
        if (myIsNameCopied) {
            return myNameCopyBuffer
        }

        val name = myStreamReadContext.currentName!!
        val nameLength = name.length

        if (myNameCopyBuffer.size < nameLength) {
            myNameCopyBuffer = CharArray(max(nameLength, 32))
        }

        name.toCharArray(myNameCopyBuffer, 0, 0, nameLength)
        myIsNameCopied = true
        return myNameCopyBuffer
    }

    /*
     *******************************************************************************************************************
     * Internal/package methods: Error reporting
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun reportTooLongIntegral(numericType: Int, rawNumber: String) {
        return if (numericType == NUMBER_INT) {
            reportOverflowInt(rawNumber)
        } else {
            reportOverflowLong(rawNumber)
        }
    }

    @Throws(StreamReadException::class)
    protected fun handleUnrecognizedCharacterEscape(char: Char): Char {
        if (isEnabled(CirJsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)) {
            return char
        }

        if (char == '\'' && isEnabled(CirJsonReadFeature.ALLOW_SINGLE_QUOTES)) {
            return char
        }

        throw constructReadException("Unrecognized character escape ${getCharDesc(char)}", currentLocationMinusOne())
    }

    @Throws(StreamReadException::class)
    protected fun reportMismatchedEndMarker(actualChar: Char, expectedChar: Char) {
        val context = myStreamReadContext
        val message = "Unexpected close marker '$actualChar': expected '$expectedChar' (for ${
            context.typeDescription
        } starting at ${context.startLocation(contentReference())})"
        throw constructReadException(message, currentLocationMinusOne())
    }

    /**
     * Method called to report a problem with unquoted control character. Note: it is possible to suppress some
     * instances of exception by enabling [CirJsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS].
     */
    @Throws(StreamReadException::class)
    protected fun throwUnquotedSpace(code: Int, contextDescription: String) {
        if (isEnabled(CirJsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS) && code <= CODE_SPACE) {
            return
        }

        val char = code.toChar()
        val message = "Illegal unquoted character (${
            getCharDesc(char)
        }): has to be escaped using backslash to be included in $contextDescription"
        throw constructReadException(message, currentLocationMinusOne())
    }

    /**
     * Description to use as "valid tokens" in an exception message about invalid (unrecognized) CirJSON token: called
     * when parser finds something that looks like unquoted textual token
     */
    protected fun validCirJsonTokenList(): String {
        return validCirJsonValueList()
    }

    /**
     * Description to use as "valid CirJSON values" in an exception message about invalid (unrecognized) CirJSON value:
     * called when parser finds something that does not look like a value or separator.
     *
     * @return The description to use
     */
    protected fun validCirJsonValueList(): String {
        return if (isEnabled(CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)) {
            "(CirJSON String, Number (or 'NaN'/'+INF'/'-INF'), Array, Object or token 'null', 'true' or 'false')"
        } else {
            "(CirJSON String, Number, Array, Object or token 'null', 'true' or 'false')"
        }
    }

    companion object {

        private val NO_CHARS = charArrayOf()

        private const val ID_NAME = "__cirJsonId__"

    }

}