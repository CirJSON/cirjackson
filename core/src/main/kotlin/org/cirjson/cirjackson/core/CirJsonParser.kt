package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.CirJsonParser.NumberTypeFP.UNKNOWN
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.exception.InputCoercionException
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.util.CirJacksonFeatureSet
import java.io.Closeable
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Base class that defines public API for reading CirJSON content.
 * Instances are created using factory methods of
 * a [CirJsonFactory] instance.
 */
abstract class CirJsonParser : Closeable, Versioned {

    /**
     * Closes the parser so that no further iteration or data access can be made; will also close the underlying input
     * source if parser either **owns** the input source, or feature [StreamReadFeature.AUTO_CLOSE_SOURCE] is enabled.
     * Whether parser owns the input source depends on factory method that was used to construct instance (so check
     * [org.cirjson.cirjackson.core.cirjson.CirJsonFactory] for details), but the general idea is that if caller passes
     * in closable resource (such as [InputStream] or [Reader]) parser does NOT own the source; but if it passes a
     * reference (such as [java.io.File] or [java.net.URL] and creates stream or reader it does own them.
     */
    abstract override fun close()

    /**
     * Accessor that can be called to determine whether this parser is closed or not. If it is closed, no new tokens can
     * be retrieved by calling [nextToken] (and the underlying stream may be closed). Closing may be due to an explicit
     * call to [close] or because parser has encountered end of input.
     */
    abstract val isClosed: Boolean

    /*
     *******************************************************************************************************************
     * Public API: basic context access
     *******************************************************************************************************************
     */

    /**
     * Accessor that can be used to access current parsing context reader is in. There are 3 different types: root,
     * array, and object contexts, with slightly different available information. Contexts are hierarchically nested,
     * and can be used for example for figuring out part of the input document that correspond to specific array or
     * object (for highlighting purposes, or error reporting). Contexts can also be used for simple xpath-like matching
     * of input, if so desired.
     */
    abstract val streamReadContext: TokenStreamContext

    /**
     * Accessor for context object provided by higher level data-binding functionality (or, in some cases, simple placeholder of the same) that allows some level of interaction including ability to trigger deserialization of Object values through generator instance.
     *
     * Context object is used by parser to implement some methods, like `readValueAs(...)`
     */
    abstract val objectReadContext: ObjectReadContext

    /*
     *******************************************************************************************************************
     * Public API, input source, location access
     *******************************************************************************************************************
     */

    /*
     *******************************************************************************************************************
     * Public API, iterating accessors: general
     *******************************************************************************************************************
     */

    /**
     * Main iteration method, which will advance stream enough to determine type of the next token, if any. If none
     * remaining (stream has no content other than possible white space before ending), `null` will be returned.
     *
     * @return Next token from the stream, if any found, or `null` to indicate end-of-input
     *
     * @throws CirJacksonException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    abstract fun nextToken(): CirJsonToken?

    /*
     *******************************************************************************************************************
     * Public API, simple token id/type access
     *******************************************************************************************************************
     */

    /**
     * Accessor to find which token parser currently points to, if any; `null` will be returned if none. If return value
     * is non-null, data associated with the token is available via other accessor methods.
     *
     * @return Type of the token this parser currently points to, if any: `null` before any tokens have been read, and after end-of-input has been encountered, as well as if the current token has been explicitly cleared.
     */
    abstract fun currentToken(): CirJsonToken?

    /*
     *******************************************************************************************************************
     * Public API, access to token information, text
     *******************************************************************************************************************
     */

    /**
     * Accessor that returns the (unquoted) name of the ID property. The returned value can vary depending on the
     * implementation.
     *
     * For example, for CirJSON, it returns `__cirJsonId__`
     */
    abstract val idName: String

    /**
     * Method that can be called to get the name associated with the current token: for [CirJsonToken.PROPERTY_NAME]s it
     * will be the same as what [text] returns; for Object property values it will be the preceding property name; and
     * for others (array element, root-level values) `null`.
     *
     * @return Name of the current property name, if any, in the parsing context (`null` if none)
     */
    abstract fun currentName(): String?

    /**
     * Accessor for textual representation of the current token; if no current token (before first call to [nextToken],
     * or after encountering end-of-input), returns null. Accessor can be called for any token type.
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @get:Throws(CirJacksonException::class)
    abstract val text: String

    /**
     * Accessor similar to [text], but that will return underlying (unmodifiable) character array that contains textual
     * value, instead of constructing a String object to contain this information. Note, however, that:
     * * Textual contents are not guaranteed to start at index 0 (rather, call [textOffset]) to know the actual offset
     *
     * * Length of textual contents may be less than the length of returned buffer: call [textLength] for actual length
     * of returned content.
     *
     * Note that caller **MUST NOT** modify the returned character array in any way -- doing so may corrupt current
     * parser state and render parser instance useless.
     *
     * The only reason to call this method (over [text]) is to avoid construction of a String object (which will make a
     * copy of contents).
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @get:Throws(CirJacksonException::class)
    abstract val textCharacters: CharArray

    /**
     * Accessor used with [textCharacters], to know length of String stored in returned buffer.
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @get:Throws(CirJacksonException::class)
    abstract val textLength: Int

    /**
     * Accessor used with [textCharacters], to know offset of the first text content character within buffer.
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @get:Throws(CirJacksonException::class)
    abstract val textOffset: Int

    /**
     * Accessor that can be used to determine whether calling of [textCharacters] would be the most efficient way to
     * access textual content for the event parser currently points to. Returns `true` if parser currently has character
     * array that can be efficiently returned via [textCharacters]; `false` means that it may or may not exist.
     *
     * Default implementation simply returns false since only actual implementation class has knowledge of its internal
     * buffering state. Implementations are strongly encouraged to properly override this method, to allow efficient
     * copying of content by other code.
     */
    @get:Throws(CirJacksonException::class)
    abstract val isTextCharactersAvailable: Boolean

    /*
     *******************************************************************************************************************
     * Public API, access to token information, numeric
     *******************************************************************************************************************
     */

    /**
     * Method similar to [numberValue] with the difference that for floating-point numbers value returned may be
     * [BigDecimal] if the underlying format does not store floating-point numbers using native representation: for
     * example, textual formats represent numbers as Strings (which are 10-based), and conversion to [Double] is
     * potentially lossy operation.
     *
     * Default implementation simply returns [numberValue]
     *
     * @throws InputCoercionException If the current token is not of numeric type
     */
    @get:Throws(InputCoercionException::class)
    abstract val numberValueExact: Number

    /**
     * If current token is of type [CirJsonToken.VALUE_NUMBER_INT] or [CirJsonToken.VALUE_NUMBER_FLOAT], returns one of
     * [NumberType] constants; otherwise returns `null`.
     */
    abstract val numberType: NumberType

    /**
     * Numeric accessor that can be called when the current token is of type [CirJsonToken.VALUE_NUMBER_INT] and it can
     * be expressed as a value of `Int` primitive type. It can also be called for [CirJsonToken.VALUE_NUMBER_FLOAT]; if
     * so, it is equivalent to calling [doubleValue] and then casting; except for possible overflow/underflow exception.
     *
     * Note: if the resulting integer value falls outside range of `Int`, an [InputCoercionException] may be thrown to
     * indicate numeric overflow/underflow.
     *
     * @throws InputCoercionException If either token type is not a number OR numeric value exceeds allowed range
     */
    @get:Throws(InputCoercionException::class)
    abstract val intValue: Int

    /**
     * Numeric accessor that can be called when the current token is of type [CirJsonToken.VALUE_NUMBER_INT] and it can
     * be expressed as a `Long` primitive type. It can also be called for [CirJsonToken.VALUE_NUMBER_FLOAT]; if so, it
     * is equivalent to calling [doubleValue] and then casting to `Long`; except for possible overflow/underflow
     * exception.
     *
     * Note: if the token is an integer, but its value falls outside of range of `Long`, a [InputCoercionException] may
     * be thrown to indicate numeric overflow/underflow.
     *
     * @throws InputCoercionException If either token type is not a number OR numeric value exceeds allowed range
     */
    @get:Throws(InputCoercionException::class)
    abstract val longValue: Long

    /**
     * Numeric accessor that can be called when the current token is of type [CirJsonToken.VALUE_NUMBER_INT] and it can
     * not be used as a `Long` primitive type due to its magnitude. It can also be called for
     * [CirJsonToken.VALUE_NUMBER_FLOAT]; if so, it is equivalent to calling [bigDecimalValue] and then constructing a
     * [BigInteger] from that value.
     *
     * @throws InputCoercionException If either token type is not a number
     */
    @get:Throws(InputCoercionException::class)
    abstract val bigIntegerValue: BigInteger

    /**
     * Numeric accessor that can be called when the current token is of type [CirJsonToken.VALUE_NUMBER_FLOAT] and it
     * can be expressed as a `Float` primitive type. It can also be called for [CirJsonToken.VALUE_NUMBER_INT]; if so,
     * it is equivalent to calling [longValue] and then casting; except for possible overflow/underflow exception.
     *
     * Note: if the value falls outside of range of Java float, a [InputCoercionException] will be thrown to indicate
     * numeric overflow/underflow.
     *
     * @throws InputCoercionException If either token type is not a number OR numeric value exceeds allowed range
     */
    @get:Throws(InputCoercionException::class)
    abstract val floatValue: Float

    /**
     * Numeric accessor that can be called when the current token is of type [CirJsonToken.VALUE_NUMBER_FLOAT] and it
     * can be expressed as a `Double` primitive type. It can also be called for [CirJsonToken.VALUE_NUMBER_INT]; if so,
     * it is equivalent to calling [longValue] and then casting; except for possible overflow/underflow exception.
     *
     * Note: if the value falls outside of range of `Double`, a [InputCoercionException] will be thrown to indicate
     * numeric overflow/underflow.
     *
     * @throws InputCoercionException If either token type is not a number OR numeric value exceeds allowed range
     */
    @get:Throws(InputCoercionException::class)
    abstract val doubleValue: Double

    /**
     * Numeric accessor that can be called when the current token is of type [CirJsonToken.VALUE_NUMBER_FLOAT] or
     * [CirJsonToken.VALUE_NUMBER_INT]. Never throws under/overflow exceptions.
     *
     * @throws InputCoercionException If either token type is not a number
     */
    @get:Throws(InputCoercionException::class)
    abstract val bigDecimalValue: BigDecimal

    /*
     *******************************************************************************************************************
     * Public API, access to token information, other
     *******************************************************************************************************************
     */

    /**
     * Accessor that can be called if (and only if) the current token is [CirJsonToken.VALUE_EMBEDDED_OBJECT]. For other
     * token types, `null` is returned.
     *
     * Note: only some specialized parser implementations support embedding of objects (usually ones that are facades on
     * top of non-streaming sources, such as object trees). One exception is access to binary content (whether via
     * base64 encoding or not) which typically is accessible using this method, as well as [binaryValue].
     */
    abstract val embeddedObject: Any?

    /**
     * Enumeration of possible "native" (optimal) types that can be used for numbers.
     */
    enum class NumberType {

        INT,

        LONG,

        BIG_INTEGER,

        FLOAT,

        DOUBLE,

        BIG_DECIMAL

    }

    /**
     * Enumeration of possible physical Floating-Point types that underlying format uses. Used to indicate most accurate
     * (and efficient) representation if known (otherwise, [UNKNOWN] is used).
     */
    enum class NumberTypeFP {

        /**
         * Special "mini-float" that some binary formats support.
         */
        FLOAT16,

        /**
         * Standard IEEE-754 single-precision 32-bit binary value
         */
        FLOAT32,

        /**
         * Standard IEEE-754 double-precision 64-bit binary value
         */
        DOUBLE64,

        /**
         * Unlimited precision, decimal (10-based) values
         */
        BIG_DECIMAL,

        /**
         * Constant used when type is not known, or there is no specific type to match: most commonly used for textual
         * formats like CirJSON where representation does not necessarily have single easily detectable optimal
         * representation (for example, value `0.1` has no exact binary representation whereas `0.25` has exact
         * representation in every binary type supported)
         */
        UNKNOWN;

    }

    companion object {

        /**
         * Set of default [StreamReadCapabilities][StreamReadCapability] enabled: usable as basis for format-specific
         * instances or placeholder if non-null instance needed.
         */
        val DEFAULT_READ_CAPABILITIES = CirJacksonFeatureSet.fromDefaults(StreamReadCapability.entries)

    }

}