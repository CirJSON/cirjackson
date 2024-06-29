package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.CirJsonParser.NumberTypeFP.UNKNOWN
import org.cirjson.cirjackson.core.async.NonBlockingInputFeeder
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.exception.InputCoercionException
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.symbols.PropertyNameMatcher
import org.cirjson.cirjackson.core.util.CirJacksonFeatureSet
import org.cirjson.cirjackson.core.util.TextBuffer
import java.io.*
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
     * reference (such as [java.io.File] or [java.net.URL]) and creates stream or reader it does own them.
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
     * Accessor for context object provided by higher level data-binding functionality (or, in some cases, simple
     * placeholder of the same) that allows some level of interaction including ability to trigger deserialization of
     * Object values through generator instance.
     *
     * Context object is used by parser to implement some methods, like `readValueAs(...)`
     */
    abstract val objectReadContext: ObjectReadContext

    /*
     *******************************************************************************************************************
     * Public API, input source, location access
     *******************************************************************************************************************
     */

    /**
     * Method that return the **starting** location of the current token; that is, position of the first character from
     * input that starts the current token.
     *
     * Note that the location is not guaranteed to be accurate (although most implementation will try their best): some
     * implementations may only return [CirJsonLocation.NA] due to not having access to input location information (when
     * delegating actual decoding work to other library).
     *
     * @return Starting location of the token parser currently points to
     */
    abstract fun currentTokenLocation(): CirJsonLocation

    /**
     * Method that returns location of the last processed character; usually for error reporting purposes.
     *
     * Note that the location is not guaranteed to be accurate (although most implementation will try their best): some
     * implementations may only report specific boundary locations (start or end locations of tokens) and others only
     * return [CirJsonLocation.NA] due to not having access to input location information (when delegating actual
     * decoding work to other library).
     *
     * @return Location of the last processed input unit (byte or character)
     */
    abstract fun currentLocation(): CirJsonLocation

    /**
     * Method that can be used to get access to object that is used to access input being parsed; this is usually either
     * [InputStream] or [Reader], depending on what parser was constructed with. Note that returned value may be `null`
     * in some cases; including case where parser implementation does not want to expose raw source to caller. In cases
     * where input has been decorated, object returned here is the decorated version; this allows some level of
     * interaction between users of parser and decorator object.
     *
     * In general use of this accessor should be considered as "last effort", i.e. only used if no other mechanism is
     * applicable.
     *
     * @return Input source this parser was configured with
     */
    abstract fun streamReadInputSource(): Any?

    /*
     *******************************************************************************************************************
     * Attaching additional metadata: current value
     *******************************************************************************************************************
     */

    /**
     * Helper method, usually equivalent to:
     * ```
     * parsingContext.currentValue()
     * ```
     *
     * Note that "current value" is NOT populated (or used) by Streaming parser; it is only used by higher-level
     * data-binding functionality. The reason it is included here is that it can be stored and accessed hierarchically,
     * and gets passed through data-binding.
     *
     * @return "Current value" for the current input context this parser has
     */
    abstract fun currentValue(): Any?

    /**
     * Helper method, usually equivalent to:
     * ```
     * parsingContext.assignCurrentValue(value);
     * ```
     *
     * @param value "Current value" to assign to the current input context of this parser
     */
    abstract fun assignCurrentValue(value: Any?)

    /*
     *******************************************************************************************************************
     * Optional support for non-blocking parsing
     *******************************************************************************************************************
     */

    /**
     * Method that can be called to determine if this parser instance uses non-blocking ("asynchronous") input access
     * for decoding or not. Access mode is determined by earlier calls via [CirJsonFactory]; it may not be changed after
     * construction.
     *
     * If non-blocking decoding is `true`, it is possible to call [nonBlockingInputFeeder] to obtain object to use for
     * feeding input; otherwise (`false` returned) input is read by blocking.
     *
     * @return `true` if this is a non-blocking ("asynchronous") parser
     */
    open val isParsingAsyncPossible = false

    /**
     * Method that will either return a feeder instance (if parser uses non-blocking, aka asynchronous access); or
     * `null` for parsers that use blocking I/O.
     *
     * @return Input feeder to use with non-blocking (async) parsing
     */
    open fun nonBlockingInputFeeder(): NonBlockingInputFeeder? {
        return null
    }

    /*
     *******************************************************************************************************************
     * Buffer handling
     *******************************************************************************************************************
     */

    /**
     * Method that can be called to push back any content that has been read but not consumed by the parser. This is
     * usually done after reading all content of interest using parser. Content is released by writing it, if possible,
     * to given stream; if the underlying input is byte-based, it can be released, if not (char-based) it can not.
     *
     * @param output OutputStream to which buffered, undecoded content is written to
     *
     * @return `-1` if the underlying content source is not byte based (that is, input can not be sent to
     * [OutputStream]; otherwise number of bytes released (`0` if there was nothing to release)
     *
     * @throws CirJacksonException if write to stream threw exception
     */
    @Throws(CirJacksonException::class)
    open fun releaseBuffered(output: OutputStream): Int {
        return -1
    }

    /**
     * Method that can be called to push back any content that has been read but not consumed by the parser. This is
     * usually done after reading all content of interest using parser. Content is released by writing it, if possible,
     * to given writer; if underlying input is char-based it can be released, if not (byte-based) it can not.
     *
     * @param writer Writer to which buffered but unprocessed content is written to
     *
     * @return -1 if the underlying content source is not char-based (that is, input can not be sent to [Writer];
     * otherwise number of chars released (0 if there was nothing to release)
     *
     * @throws CirJacksonException if write using Writer threw exception
     */
    @Throws(CirJacksonException::class)
    open fun releaseBuffered(writer: Writer): Int {
        return -1
    }

    /*
     *******************************************************************************************************************
     * Public API, configuration
     *******************************************************************************************************************
     */

    /**
     * Method for checking whether specified [StreamReadFeature] is enabled.
     *
     * @param feature Feature to check
     *
     * @return `true` if feature is enabled; `false` otherwise
     */
    abstract fun isEnabled(feature: StreamReadFeature): Boolean

    /**
     * Bit mask that defines current states of all standard [StreamReadFeature]s.
     */
    abstract val streamReadFeatures: Int

    /**
     * Accessor for Schema that this parser uses, if any. Default implementation returns `null`.
     */
    open val schema: FormatSchema? = null

    /**
     * Accessor for getting metadata on capabilities of this parser, based on underlying data format being read
     * (directly or indirectly).
     */
    abstract val streamReadCapabilities: CirJacksonFeatureSet<StreamReadCapability>

    /**
     * Get the constraints to apply when performing streaming reads.
     */
    abstract val streamReadConstraints: StreamReadConstraints

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

    /**
     * Iteration method that will advance stream enough to determine type of the next token that is a value type
     * (including CirJSON Array and Object start/end markers). Or put another way, `nextToken()` will be called once,
     * and if [CirJsonToken.PROPERTY_NAME] is returned, another time to get the value of the property. Method is most
     * useful for iterating over value entries of CirJSON objects; Object property name will still be available by
     * calling [currentName] when parser points to the value.
     *
     * @return Next non-field-name token from the stream, if any found, or `null` to indicate end-of-input (or, for
     * non-blocking parsers, [CirJsonToken.NOT_AVAILABLE] if no tokens were available yet)
     *
     * @throws CirJacksonException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    abstract fun nextValue(): CirJsonToken

    /**
     * Method that will skip all child tokens of an array or object token that the parser currently points to, if stream
     * points to [CirJsonToken.START_OBJECT] or [CirJsonToken.START_ARRAY]. If not, it will do nothing. After skipping,
     * stream will point to **matching** [CirJsonToken.END_OBJECT] or [CirJsonToken.END_ARRAY] (possibly skipping nested
     * pairs of START/END OBJECT/ARRAY tokens as well as value tokens). The idea is that after calling this method,
     * application will call [nextToken] to point to the next available token, if any.
     *
     * @return This parser, to allow call chaining
     *
     * @throws CirJacksonException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    abstract fun skipChildren(): CirJsonParser

    /**
     * Method that may be used to force full handling of the current token so that even if lazy processing is enabled,
     * the whole contents are read for possible retrieval. This is usually used to ensure that the token end location is
     * available, as well as token contents (similar to what calling, say [textCharacters], would achieve).
     *
     * Note that for many dataformat implementations this method will not do anything; this is the default
     * implementation unless overridden by subclasses.
     *
     * @throws CirJacksonException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    abstract fun finishToken()

    /*
     *******************************************************************************************************************
     * Public API, iterating accessors: property names
     *******************************************************************************************************************
     */

    /**
     * Method that fetches next token (as if calling [nextToken]) and verifies whether it is
     * [CirJsonToken.PROPERTY_NAME]; if it is, returns same as [currentName], otherwise `null`.
     *
     * @return Name of the `CirJsonToken.PROPERTY_NAME` parser advanced to, if any; `null` if next token is of some
     * other type
     *
     * @throws CirJacksonException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    abstract fun nextName(): String

    /**
     * Method that fetches next token (as if calling [nextToken]) and verifies whether it is
     * [CirJsonToken.PROPERTY_NAME] with specified name and returns result of that comparison. It is functionally
     * equivalent to:
     * ```
     *  return nextToken() == CirJsonToken.PROPERTY_NAME && string.value == currentName()
     * ```
     * but may be faster for parser to verify, and can therefore be used if caller expects to get such a property name
     * from input next.
     *
     * @param string Property name to compare next token to (if next token is `CirJsonToken.PROPERTY_NAME`)
     *
     * @return `true` if parser advanced to `CirJsonToken.PROPERTY_NAME` with specified name; `false` otherwise
     * (different token or non-matching name)
     *
     * @throws CirJacksonException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    abstract fun nextName(string: SerializableString): Boolean

    /**
     * Method that tries to match next token from stream as [CirJsonToken.PROPERTY_NAME], and if so, further match it to
     * one of pre-specified (field) names. If match succeeds, property index (non-negative `Int`) is returned; otherwise
     * one of marker constants from [PropertyNameMatcher].
     *
     * @param matcher Matcher that will handle actual matching
     *
     * @return Index of the matched property name, if non-negative, or a negative error code otherwise (see
     * [PropertyNameMatcher] for details)
     *
     * @throws CirJacksonException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    abstract fun nextNameMatch(matcher: PropertyNameMatcher): Int

    /**
     * Method that verifies that the current token (see [currentToken]) is [CirJsonToken.PROPERTY_NAME] and if so,
     * further match that associated name (see [currentName]) to one of pre-specified (property) names. If there is a
     * match succeeds, the property index (non-negative `Int`) is returned; otherwise one of marker constants from
     * [PropertyNameMatcher] is returned.
     *
     * @param matcher Matcher that will handle actual matching
     *
     * @return Index of the matched property name, if non-negative, or a negative error code otherwise (see
     * [PropertyNameMatcher] for details)
     *
     * @throws CirJacksonException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    abstract fun currentNameMatch(matcher: PropertyNameMatcher): Int

    /*
     *******************************************************************************************************************
     * Public API, iterating accessors: typed values
     *******************************************************************************************************************
     */

    /**
     * Method that fetches next token (as if calling [nextToken]) and if it is [CirJsonToken.VALUE_STRING] returns
     * contained String value; otherwise returns `null`. It is functionally equivalent to:
     * ```
     * return if (nextToken() == CirJsonToken.VALUE_STRING) text else null
     * ```
     * but may be faster for parser to process, and can therefore be used if caller expects to get a String value next
     * from input.
     *
     * @return Text value of the `CirJsonToken.VALUE_STRING` token parser advanced to; or `null` if next token is of
     * some other type
     *
     * @throws CirJacksonException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    open fun nextTextValue(): String? {
        return if (nextToken() == CirJsonToken.VALUE_STRING) text else null
    }

    /**
     * Method that fetches next token (as if calling [nextToken]) and if it is [CirJsonToken.VALUE_NUMBER_INT] returns
     * 32-bit Int value; otherwise returns specified default value It is functionally equivalent to:
     * ```
     * return if (nextToken() == CirJsonToken.VALUE_NUMBER_INT) intValue else defaultValue
     * ```
     * but may be faster for parser to process, and can therefore be used if caller expects to get an Int value next
     * from input.
     *
     * NOTE: value checks are performed similar to [intValue]
     *
     * @param defaultValue Value to return if next token is NOT of type `CirJsonToken.VALUE_NUMBER_INT`
     *
     * @return Int value of the `CirJsonToken.VALUE_NUMBER_INT` token parser advanced   to; or `defaultValue` if next token is of some other type
     *
     * @throws CirJacksonException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     *
     * @throws InputCoercionException if integer number does not fit in `Int`
     */
    @Throws(CirJacksonException::class)
    open fun nextIntValue(defaultValue: Int): Int {
        return if (nextToken() == CirJsonToken.VALUE_NUMBER_INT) intValue else defaultValue
    }

    /**
     * Method that fetches next token (as if calling [nextToken]) and if it is [CirJsonToken.VALUE_NUMBER_INT] returns
     * 64-bit Long value; otherwise returns specified default value It is functionally equivalent to:
     * ```
     * return if (nextToken() == CirJsonToken.VALUE_NUMBER_INT) longValue else defaultValue
     * ```
     * but may be faster for parser to process, and can therefore be used if caller expects to get a Long value next
     * from input.
     *
     * NOTE: value checks are performed similar to [longValue]
     *
     * @param defaultValue Value to return if next token is NOT of type `CirJsonToken.VALUE_NUMBER_INT`
     *
     * @return `Long` value of the `CirJsonToken.VALUE_NUMBER_INT` token parser advanced to; or `defaultValue` if next
     * token is of some other type
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     *
     * @throws InputCoercionException if integer number does not fit in `Long`
     */
    @Throws(CirJacksonException::class)
    open fun nextLongValue(defaultValue: Long): Long {
        return if (nextToken() == CirJsonToken.VALUE_NUMBER_INT) longValue else defaultValue
    }

    /**
     * Method that fetches next token (as if calling [nextToken]) and if it is [CirJsonToken.VALUE_TRUE] or
     * [CirJsonToken.VALUE_FALSE] returns matching Boolean value; otherwise return null. It is functionally equivalent
     * to:
     * ```
     * return when (nextToken()) {
     *     CirJsonToken.VALUE_TRUE -> true
     *     CirJsonToken.VALUE_FALSE -> false
     *     else -> null
     * }
     * ```
     * but may be faster for parser to process, and can therefore be used if caller
     * expects to get a Boolean value next from input.
     *
     * @return `Boolean` value of the `CirJsonToken.VALUE_TRUE` or `CirJsonToken.VALUE_FALSE` token parser advanced to;
     * or `null` if next token is of some other type
     *
     * @throws CirJacksonIOException for low-level read issues
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    open fun nextBooleanValue(): Boolean? {
        return when (nextToken()) {
            CirJsonToken.VALUE_TRUE -> true
            CirJsonToken.VALUE_FALSE -> false
            else -> null
        }
    }

    /*
     *******************************************************************************************************************
     * Public API, simple token id/type access
     *******************************************************************************************************************
     */

    /**
     * Accessor to find which token parser currently points to, if any; `null` will be returned if none. If return value
     * is non-null, data associated with the token is available via other accessor methods.
     *
     * @return Type of the token this parser currently points to, if any: `null` before any tokens have been read,
     * and after end-of-input has been encountered, as well as if the current token has been explicitly cleared.
     */
    abstract fun currentToken(): CirJsonToken?

    /**
     * Method similar to [currentToken] but that returns an `Int` instead of [CirJsonToken] (enum value).
     *
     * Use of int directly is typically more efficient on switch statements, so this method may be useful when building
     * low-overhead codecs. Note, however, that effect may not be big enough to matter: make sure to profile performance
     * before deciding to use this method.
     *
     * @return `Int` matching one of constants from [CirJsonTokenId].
     */
    abstract fun currentTokenId(): Int

    /**
     * Method for checking whether parser currently points to a token (and data for that token is available). Equivalent
     * to check for `parser.currentToken() != null`.
     *
     * @return `true` if the parser just returned a valid token via [nextToken]; `false` otherwise (parser was just
     * constructed, encountered end-of-input and returned `null` from [nextToken], or the token has been consumed)
     */
    abstract val isCurrentTokenNotNull: Boolean

    /**
     * Method that is functionally equivalent to:
     * ```
     * return currentTokenId() == id
     * ```
     * but may be more efficiently implemented.
     *
     * Note that no traversal or conversion is performed; so in some cases calling accessor like
     * [isExpectedStartArrayToken] is necessary instead.
     *
     * @param id Token id to match (from [CirJsonTokenId])
     *
     * @return `true` if the parser current points to specified token
     */
    abstract fun hasTokenId(id: Int): Boolean

    /**
     * Method that is functionally equivalent to:
     * ```
     * return currentToken() == token
     * ```
     * but may be more efficiently implemented.
     *
     * Note that no traversal or conversion is performed; so in some cases calling accessor like
     * [isExpectedStartArrayToken] is necessary instead.
     *
     * @param token Token to match
     *
     * @return `true` if the parser current points to specified token
     */
    abstract fun hasToken(token: CirJsonToken): Boolean

    /**
     * Specialized accessor that can be used to verify that the current token indicates start array (usually meaning
     * that current token is [CirJsonToken.START_ARRAY]) when start array is expected. For some specialized parsers this
     * can return true for other cases as well; this is usually done to emulate arrays in cases underlying format is
     * ambiguous (XML, for example, has no format-level difference between Objects and Arrays; it just has elements).
     *
     * Default implementation is equivalent to:
     * ```
     * currentToken() == CirJsonToken.START_ARRAY
     * ```
     * but may be overridden by custom parser implementations.
     *
     * @return `true` if the current token can be considered as a start-array marker (such [CirJsonToken.START_ARRAY]);
     * `false` if not
     */
    abstract val isExpectedStartArrayToken: Boolean

    /**
     * Similar to [isExpectedStartArrayToken], but checks whether stream currently points to
     * [CirJsonToken.START_OBJECT].
     *
     * @return `true` if the current token can be considered as a start-array marker (such [CirJsonToken.START_OBJECT]);
     * `false` if not
     */
    abstract val isExpectedStartObjectToken: Boolean

    /**
     * Similar to [isExpectedStartArrayToken], but checks whether stream currently points to [CirJsonToken.VALUE_NUMBER_INT].
     *
     * The initial use case is for XML backend to efficiently (attempt to) coerce textual content into numbers.
     *
     * @return True if the current token can be considered as a start-array marker (such [CirJsonToken.VALUE_NUMBER_INT]); `false` if not
     */
    abstract val isExpectedNumberIntToken: Boolean

    /**
     * Accessor for checking whether current token is a special "not-a-number" (NaN) token (including both "NaN" AND
     * positive/negative infinity!). These values are not supported by all formats: CirJSON, for example, only supports
     * them if [CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS] is enabled.
     *
     * NOTE: in case where numeric value is outside range of requested type -- most notably [Float] or [Double] -- and
     * decoding results effectively in a NaN value, this method DOES NOT return `true`: only explicit incoming markers
     * do. This is because value could still be accessed as a valid [BigDecimal].
     *
     * @return `true` if the current token is reported as [CirJsonToken.VALUE_NUMBER_FLOAT] and represents a "Not a
     * Number" value; `false` for other tokens and regular floating-point numbers.
     */
    abstract val isNaN: Boolean

    /*
     *******************************************************************************************************************
     * Public API, token state overrides
     *******************************************************************************************************************
     */

    /**
     * Method called to "consume" the current token by effectively removing it so that [isCurrentTokenNotNull] returns
     * `false`, and [currentToken] `null`. Cleared token value can still be accessed by calling [lastClearedToken] (if
     * absolutely needed), but usually isn't.
     *
     * Method was added to be used by the optional data binder, since it has to be able to consume last token used for
     * binding (so that it will not be used again).
     */
    abstract fun clearCurrentToken()

    /**
     * Method that can be called to get the last token that was cleared using [clearCurrentToken]. This is not
     * necessarily the latest token read. Will return `null` if no tokens have been cleared, or if parser has been
     * closed.
     *
     * @return Last cleared token, if any; `null` otherwise
     */
    abstract val lastClearedToken: CirJsonToken?

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
     * Method to read the textual representation of the current token in chunks and pass it to the given Writer.
     * Conceptually same as calling:
     * ```
     * writer.write(parser.text);
     * ```
     * but should typically be more efficient as longer content does need to be combined into a single `String` to
     * return, and write can occur directly from intermediate buffers CirJackson uses.
     *
     * NOTE: textual content **will** still be buffered (usually using [TextBuffer]) and **will** be accessible with
     * other `text` calls (that is, it will not be consumed). So this accessor only avoids construction of [String]
     * compared to plain [text] accessor.
     *
     * @param writer Writer to write textual content to
     *
     * @return The number of characters written to the Writer
     *
     * @throws CirJacksonIOException for low-level read issues, or failed write using [Writer]
     *
     * @throws StreamReadException for decoding problems
     */
    @Throws(CirJacksonException::class)
    abstract fun getText(writer: Writer): Int

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
     * Generic number value accessor method that will work for all kinds of numeric values. It will return the optimal
     * (simplest/smallest possible) wrapper object that can express the numeric value just parsed.
     *
     * @throws InputCoercionException If the current token is not of numeric type
     */
    @get:Throws(InputCoercionException::class)
    abstract val numberValue: Number

    /**
     * Accessor similar to [numberValue] with the difference that for floating-point numbers value returned may be
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
     * Method similar to [numberValue] but that returns **either** same [Number] value as [numberValue] (if already
     * decoded), **or** `String` representation of as-of-yet undecoded number. Typically textual formats allow deferred
     * decoding from String, whereas binary formats either decode numbers eagerly or have binary representation from
     * which to decode value to return.
     *
     * Same constraints apply to calling this method as to [numberValue]: current token must be either
     * [CirJsonToken.VALUE_NUMBER_INT] or [CirJsonToken.VALUE_NUMBER_FLOAT]; otherwise an exception is thrown.
     *
     * Default implementation simply returns [numberValue].
     *
     * @throws InputCoercionException If the current token is not of numeric type
     */
    @get:Throws(InputCoercionException::class)
    abstract val numberValueDeferred: Number

    /**
     * If current token is of type [CirJsonToken.VALUE_NUMBER_INT] or [CirJsonToken.VALUE_NUMBER_FLOAT], returns one of
     * [NumberType] constants; otherwise returns `null`.
     */
    abstract val numberType: NumberType

    /**
     * If current token is of type [CirJsonToken.VALUE_NUMBER_FLOAT], returns one of [NumberTypeFP] constants; otherwise
     * returns [NumberTypeFP.UNKNOWN]. If parser does not point to numeric token; returns `null`.
     */
    abstract val numberTypeFP: NumberTypeFP?

    /**
     * Numeric accessor that can be called when the current token is of type [CirJsonToken.VALUE_NUMBER_INT] and it can
     * be expressed as a value of `Byte` primitive type. Note that in addition to "natural" input range of
     * `[-128, 127]`, this also allows "unsigned 8-bit byte" values `[128, 255]`: but for this range value will be
     * translated by truncation, leading to sign change.
     *
     * It can also be called for [CirJsonToken.VALUE_NUMBER_FLOAT]; if so, it is equivalent to calling [doubleValue] and
     * then casting; except for possible overflow/underflow exception.
     *
     * Note: if the resulting integer value falls outside range of `[-128, 255]`, an [InputCoercionException] will be
     * thrown to indicate numeric overflow/underflow.
     *
     * @throws InputCoercionException If either token type is not a number OR numeric value exceeds allowed range
     */
    @get:Throws(InputCoercionException::class)
    abstract val byteValue: Byte

    /**
     * Numeric accessor that can be called when the current token is of type [CirJsonToken.VALUE_NUMBER_INT] and it can
     * be expressed as a value of `Short` primitive type. It can also be called for [CirJsonToken.VALUE_NUMBER_FLOAT];
     * if so, it is equivalent to calling [doubleValue] and then casting; except for possible overflow/underflow
     * exception.
     *
     * Note: if the resulting integer value falls outside range of `Short`, an [InputCoercionException] will be thrown
     * to indicate numeric overflow/underflow.
     *
     * @throws InputCoercionException If either token type is not a number OR numeric value exceeds allowed range
     */
    @get:Throws(InputCoercionException::class)
    abstract val shortValue: Short

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