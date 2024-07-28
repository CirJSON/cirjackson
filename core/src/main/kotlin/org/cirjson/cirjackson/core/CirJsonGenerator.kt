package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.exception.StreamReadException
import org.cirjson.cirjackson.core.exception.StreamWriteException
import org.cirjson.cirjackson.core.io.CharacterEscapes
import org.cirjson.cirjackson.core.type.WritableTypeID
import org.cirjson.cirjackson.core.util.CirJacksonFeatureSet
import java.io.*
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Base class that defines public API for writing CirJSON content. Instances are created using factory methods of a
 * [TokenStreamFactory] instance.
 */
abstract class CirJsonGenerator protected constructor() : Closeable, Flushable, Versioned {

    /*
     *******************************************************************************************************************
     * Implementations
     *******************************************************************************************************************
     */

    /**
     * Method called to close this generator, so that no more content can be written.
     *
     * Whether the underlying target (stream, writer) gets closed depends on whether this generator either manages the
     * target (i.e. is the only one with access to the target -- case if caller passes a reference to the resource such
     * as File, but not stream); or has feature [StreamWriteFeature.AUTO_CLOSE_TARGET] enabled. If either of above is
     * true, the target is also closed. Otherwise, (not managing, feature not enabled), target is not closed.
     */
    abstract override fun close()

    /**
     * Method called to flush any buffered content to the underlying target (output stream, writer), and to flush the
     * target itself as well.
     */
    abstract override fun flush()

    /**
     * Accessor that can be called to determine whether this generator is closed or not. If it is closed, no more output
     * can be done.
     */
    abstract val isClosed: Boolean

    /*
     *******************************************************************************************************************
     * Public API, output configuration, state access
     *******************************************************************************************************************
     */

    /**
     * Get the constraints to apply when performing streaming writes.
     */
    open val streamWriteConstraints = StreamWriteConstraints.defaults()

    /**
     * Accessor for context object that provides information about low-level logical position withing output token
     * stream.
     */
    abstract val streamWriteContext: TokenStreamContext

    /**
     * Accessor for context object provided by higher-level databinding functionality (or, in some cases, simple
     * placeholder of the same) that allows some level of interaction including ability to trigger serialization of
     * Object values through generator instance.
     */
    abstract val objectWriteContext: ObjectWriteContext

    /**
     * Accessor that can be used to get access to object that is used as target for generated output; this is usually
     * either [java.io.OutputStream] or [java.io.Writer], depending on what generator was constructed with.
     *
     * Note that returned value may be null in some cases; including case where implementation does not want to expose
     * raw source to caller.
     *
     * In cases where output has been decorated, object returned here is the decorated version; this allows some level
     * of interaction between users of generator and decorator object.
     *
     * In general use of this accessor should be considered as "last effort", i.e. only used if no other mechanism is
     * applicable.
     */
    abstract val streamWriteOutputTarget: Any?

    /**
     * Accessor for verifying amount of content that is buffered by generator but not yet flushed to the underlying
     * target (stream, writer), in units (byte, char) that the generator implementation uses for buffering; or -1 if
     * this information is not available.
     *
     * Unit used is often the same as the unit of underlying target (that is, `Byte` for [java.io.OutputStream], `Char`
     * for [java.io.Writer]), but may differ if buffering is done before encoding.
     *
     * Default CirJSON-backed implementations do use matching units.
     */
    abstract val streamWriteOutputBuffered: Int

    /**
     * Helper method, usually equivalent to:
     * ```
     * outputContext.currentValue()
     * ```
     *
     * Note that "current value" is NOT populated (or used) by Streaming generator; it is only used by higher-level
     * data-binding functionality. The reason it is included here is that it can be stored and accessed hierarchically,
     * and gets passed through data-binding.
     *
     * @return "Current value" for the current context this generator has
     */
    abstract fun currentValue(): Any?

    /**
     * Helper method, usually equivalent to:
     * ```
     * outputContext.assignCurrentValue(value)
     * ```
     * used to assign "current value" for the current context of this generator. It is usually assigned and used by
     * higher level data-binding functionality (instead of streaming parsers/generators) but is stored at streaming
     * level.
     *
     * @param value "Current value" to assign to the current output context of this generator
     */
    abstract fun assignCurrentValue(value: Any?)

    /*
     *******************************************************************************************************************
     * Public API, Feature configuration
     *******************************************************************************************************************
     */

    /**
     * Method for enabling or disabling specified feature: check [StreamWriteFeature] for list of available features.
     *
     * NOTE: mostly here just to support disabling of [StreamWriteFeature.AUTO_CLOSE_CONTENT] by `cirjackson-databind`
     *
     * @param feature Feature to enable or disable
     *
     * @param state Whether to enable the feature (`true`) or disable (`false`)
     *
     * @return This generator, to allow call chaining
     */
    abstract fun configure(feature: StreamWriteFeature, state: Boolean): CirJsonGenerator

    /**
     * Method for checking whether given feature is enabled. Check [StreamWriteFeature] for list of available features.
     *
     * @param feature Feature to check
     *
     * @return `true` if feature is enabled; `false` if not
     */
    abstract fun isEnabled(feature: StreamWriteFeature): Boolean

    /**
     * Bulk access method for getting state of all standard (format-agnostic)
     * [StreamWriteFeature]s.
     *
     * @return Bit mask that defines current states of all standard [StreamWriteFeature]s.
     */
    abstract val streamWriteFeatures: Int

    /*
     *******************************************************************************************************************
     * Public API, Other configuration
     *******************************************************************************************************************
     */

    /**
     * [FormatSchema] this generator is configured to use, if any; `null` if none
     *
     * Default implementation returns null.
     */
    open val schema: FormatSchema? = null

    /**
     * Accessor method for testing what is the highest unescaped character configured for this generator. This may be
     * either positive value (when escaping configuration has been set and is in effect), or 0 to indicate that no
     * additional escaping is in effect. Some generators may not support additional escaping: for example, generators
     * for binary formats that do not use escaping should simply return 0.
     *
     * Default implementation returns 0
     */
    open val highestNonEscapedChar = 0

    /**
     * [CharacterEscapes] this generator is configured to use, if any; `null` if none
     *
     * Default implementation of getter returns `null`
     *
     * Default implementation of setter does nothing
     */
    open var characterEscapes: CharacterEscapes?
        get() = null
        set(value) {}

    /*
     *******************************************************************************************************************
     * Public API, capability introspection methods
     *******************************************************************************************************************
     */

    /**
     * Introspection method that may be called to see if the underlying data format supports some kind of Type Ids
     * natively (many do not; for example, CirJSON doesn't). This method **must** be called prior to calling
     * [writeTypeId].
     *
     * Default implementation returns false; overridden by data formats that do support native Type Ids. Caller is
     * expected to either use a non-native notation (explicit property or such), or fail, in case it can not use native
     * type ids.
     */
    open val isAbleWriteTypeId = false

    /**
     * Introspection method to call to check whether it is ok to omit writing of Object properties or not. Most formats
     * do allow omission, but certain positional formats (such as CSV) require output of placeholders, even if no real
     * values are to be emitted.
     */
    open val isAbleOmitProperties = true

    /**
     * Accessor for getting metadata on capabilities of this generator, based on underlying data format being read
     * (directly or indirectly).
     */
    abstract val streamWriteCapabilities: CirJacksonFeatureSet<StreamWriteCapability>

    /*
     *******************************************************************************************************************
     * Protected API, ID generation methods
     *******************************************************************************************************************
     */

    /**
     * Method for getting the ID of the specified Object
     *
     * @param obj The object for which the ID is required
     *
     * @return The object's ID
     */
    protected abstract fun getObjectID(obj: Any): String

    /**
     * Method for getting the ID of the specified array
     *
     * @param array The array for which the ID is required
     *
     * @param T The type of the array
     *
     * @return The array's ID
     */
    protected abstract fun <T> getArrayID(array: Array<T>): String

    /**
     * Method for getting the ID of the specified array
     *
     * @param array The array for which the ID is required
     *
     * @return The array's ID
     */
    protected abstract fun getArrayID(array: IntArray): String

    /**
     * Method for getting the ID of the specified array
     *
     * @param array The array for which the ID is required
     *
     * @return The array's ID
     */
    protected abstract fun getArrayID(array: LongArray): String

    /**
     * Method for getting the ID of the specified array
     *
     * @param array The array for which the ID is required
     *
     * @return The array's ID
     */
    protected abstract fun getArrayID(array: DoubleArray): String

    /*
     *******************************************************************************************************************
     * Public API, write methods, structural
     *******************************************************************************************************************
     */

    /**
     * Method for writing starting marker of an Array value (for CirJSON this is character `[`; plus possible white
     * space decoration if pretty-printing is enabled).
     *
     * Array values can be written in any context where values are allowed: meaning everywhere except for when a
     * property name is expected.
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeStartArray(): CirJsonGenerator

    /**
     * Method for writing start marker of an Array value, similar to [writeStartArray], but also specifying what is the
     * object that the Array Object being written represents (if any); `null` may be passed if not known or not
     * applicable. This value is accessible from context as "current value"
     *
     * @param currentValue Object that Array being written represents, if any (or `null` if not known or not applicable)
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeStartArray(currentValue: Any?): CirJsonGenerator

    /**
     * Method for writing start marker of an Array value, similar to [writeEndArray], but also specifying what is the
     * object that the Array Object being written represents (if any) and how many elements will be written for the
     * array before calling [writeEndArray].
     *
     * @param currentValue Java Object that Array being written represents, if any (or `null` if not known or not
     * applicable)
     *
     * @param size Number of elements this Array will have: actual number of values written (before matching call to
     * [writeEndArray] MUST match; generator MAY verify this is the case (and SHOULD if format itself encodes length)
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeStartArray(currentValue: Any?, size: Int): CirJsonGenerator

    /**
     * Method for writing closing marker of a CirJSON Array value (character `]`; plus possible white space decoration
     * if pretty-printing is enabled).
     *
     * Marker can be written if the innermost structured type is Array.
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeEndArray(): CirJsonGenerator

    /**
     * Method for writing starting marker of an Object value (character `{`; plus possible white space decoration if
     * pretty-printing is enabled).
     *
     * Object values can be written in any context where values are allowed: meaning everywhere except for when a
     * property name is expected.
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeStartObject(): CirJsonGenerator

    /**
     * Method for writing starting marker of an Object value to represent the given Object value. Argument is offered as
     * metadata, but more importantly it should be assigned as the "current value" for the Object content that gets
     * constructed and initialized.
     *
     * Object values can be written in any context where values are allowed: meaning everywhere except for when a
     * property name is expected.
     *
     * @param currentValue Java Object that Object being written represents, if any (or `null` if not known or not
     * applicable)
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeStartObject(currentValue: Any?): CirJsonGenerator

    /**
     * Method for writing starting marker of an Object value to represent the given Object value. Argument is offered as
     * metadata, but more importantly it should be assigned as the "current value" for the Object content that gets
     * constructed and initialized. In addition, caller knows number of key/value pairs ("properties") that will get
     * written for the Object value: this is relevant for some format backends (but not, as an example, for CirJSON).
     *
     * Object values can be written in any context where values are allowed: meaning everywhere except for when a
     * property name is expected.
     *
     * @param currentValue Object value to be written (assigned as "current value" for the Object context that gets
     * created)
     *
     * @param size Number of key/value pairs this Object will have: actual number of entries written (before matching
     * call to [writeEndObject] MUST match; generator MAY verify this is the case (and SHOULD if format itself encodes
     * length)
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeStartObject(currentValue: Any?, size: Int): CirJsonGenerator

    /**
     * Method for writing closing marker of an Object value (character `}`; plus possible white space decoration if
     * pretty-printing is enabled).
     *
     * Marker can be written if the innermost structured type is Object, and the last written event was either a
     * complete value, or START-OBJECT marker (see CirJSON specification for more details).
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeEndObject(): CirJsonGenerator

    /**
     * Method for writing an Object Property name (CirJSON String surrounded by double quotes: syntactically identical
     * to a CirJSON String value), possibly decorated by white space if pretty-printing is enabled.
     *
     * Property names can only be written in Object context (check out CirJSON specification for details), when Object
     * Property name is expected (property names alternate with values).
     *
     * @param name Name of the Object Property to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeName(name: String): CirJsonGenerator

    /**
     * Method similar to [writeName], main difference being that it may perform better as some of the processing (such
     * as quoting of certain characters, or encoding into external encoding if supported by generator) can be done just
     * once and reused for later calls.
     *
     * Default implementation simple uses unprocessed name container in serialized String; implementations are strongly
     * encouraged to make use of more efficient methods argument object has.
     *
     * @param name Pre-encoded name of the Object Property to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeName(name: SerializableString): CirJsonGenerator

    /**
     * Alternative to [writeName] that may be used in cases where Object Property key is of numeric type; usually where
     * underlying format supports such notion (some binary formats do, unlike CirJSON). Default implementation will
     * simply convert id into `String` and call [writeName].
     *
     * @param id Property key id to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writePropertyId(id: Long): CirJsonGenerator

    /*
     *******************************************************************************************************************
     * Public API, write methods, scalar arrays
     *******************************************************************************************************************
     */

    /**
     * Value write method that can be called to write a single array (sequence of [CirJsonToken.START_ARRAY], zero or
     * more [CirJsonToken.VALUE_NUMBER_INT], [CirJsonToken.END_ARRAY])
     *
     * @param array Array that contains values to write
     *
     * @param offset Offset of the first element to write, within array
     *
     * @param length Number of elements in array to write, from `offset` to `offset + len - 1`
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    open fun writeArray(array: IntArray, offset: Int, length: Int): CirJsonGenerator {
        verifyOffsets(array.size, offset, length)
        writeStartArray(array, length)

        for (i in offset..<offset + length) {
            writeNumber(array[i])
        }

        writeEndArray()
        return this
    }

    protected fun verifyOffsets(arrayLength: Int, offset: Int, length: Int) {
        if (offset < 0 || offset + length > arrayLength) {
            throw IllegalArgumentException(
                    "Invalid argument(s) (offset=$offset, length=$length) for input array of $arrayLength element")
        }
    }

    /**
     * Value write method that can be called to write a single array (sequence of [CirJsonToken.START_ARRAY], zero or
     * more [CirJsonToken.VALUE_NUMBER_INT], [CirJsonToken.END_ARRAY])
     *
     * @param array Array that contains values to write
     *
     * @param offset Offset of the first element to write, within array
     *
     * @param length Number of elements in array to write, from `offset` to `offset + len - 1`
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    open fun writeArray(array: LongArray, offset: Int, length: Int): CirJsonGenerator {
        verifyOffsets(array.size, offset, length)
        writeStartArray(array, length)

        for (i in offset..<offset + length) {
            writeNumber(array[i])
        }

        writeEndArray()
        return this
    }

    /**
     * Value write method that can be called to write a single array (sequence of [CirJsonToken.START_ARRAY], zero or
     * more [CirJsonToken.VALUE_NUMBER_INT], [CirJsonToken.END_ARRAY])
     *
     * @param array Array that contains values to write
     *
     * @param offset Offset of the first element to write, within array
     *
     * @param length Number of elements in array to write, from `offset` to `offset + len - 1`
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    open fun writeArray(array: DoubleArray, offset: Int, length: Int): CirJsonGenerator {
        verifyOffsets(array.size, offset, length)
        writeStartArray(array, length)

        for (i in offset..<offset + length) {
            writeNumber(array[i])
        }

        writeEndArray()
        return this
    }

    /**
     * Value write method that can be called to write a single array (sequence of [CirJsonToken.START_ARRAY], zero or
     * more [CirJsonToken.VALUE_NUMBER_INT], [CirJsonToken.END_ARRAY])
     *
     * @param array Array that contains values to write
     *
     * @param offset Offset of the first element to write, within array
     *
     * @param length Number of elements in array to write, from `offset` to `offset + len - 1`
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    open fun writeArray(array: Array<String>, offset: Int, length: Int): CirJsonGenerator {
        verifyOffsets(array.size, offset, length)
        writeStartArray(array, length)

        for (i in offset..<offset + length) {
            writeNumber(array[i])
        }

        writeEndArray()
        return this
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, text/String values
     *******************************************************************************************************************
     */

    /**
     * Method for outputting a String value. Depending on context
     * this means either array element, (object) property value or
     * a stand-alone (root-level value) String; but in all cases, String will be
     * surrounded in double quotes, and contents will be properly
     * escaped as required by CirJSON specification.
     *
     * @param value String value to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeString(value: String): CirJsonGenerator

    /**
     * Method for outputting a String value. Depending on context this means either array element, (object) property
     * value or a standalone String; but in all cases, String will be surrounded in double quotes, and contents will be
     * properly escaped as required by CirJSON specification. If `length` is < 0, then write all contents of the reader.
     * Otherwise, write only `length` characters.
     *
     * Note: actual length of content available may exceed `length` but can not be less than it: if not enough content
     * available, a [StreamWriteException] will be thrown.
     *
     * @param reader Reader to use for reading Text value to write
     *
     * @param length Maximum Length of Text value to read (in `Char`s, non-negative) if known; `-1` to indicate "read
     * and write it all"
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream (including the case where `reader` does not
     * provide enough content)
     */
    @Throws(CirJacksonException::class)
    abstract fun writeString(reader: Reader, length: Int): CirJsonGenerator

    /**
     * Method for outputting a String value. Depending on context this means either array element, (object) property
     * value or a standalone String; but in all cases, String will be surrounded in double quotes, and contents will be
     * properly escaped as required by CirJSON specification.
     *
     * @param buffer Buffer that contains String value to write
     *
     * @param offset Offset in `buffer` of the first character of String value to write
     *
     * @param length Length of the String value (in characters) to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeString(buffer: CharArray, offset: Int, length: Int): CirJsonGenerator

    /**
     * Method similar to [writeString], but that takes [SerializableString] which can make this potentially more
     * efficient to call as generator may be able to reuse quoted and/or encoded representation.
     *
     * Default implementation just calls [writeString]; subclasses should override it with more efficient implementation
     * if possible.
     *
     * @param value Pre-encoded String value to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeString(value: SerializableString): CirJsonGenerator

    /**
     * Method similar to [writeString] but that takes as its input a UTF-8 encoded String that is to be output as-is,
     * without additional escaping (type of which depends on data format; backslashes for CirJSON). However, quoting
     * that data format requires (like double-quotes for CirJSON) will be added around the value if and as necessary.
     *
     * Note that some backends may choose not to support this method: for example, if underlying destination is a
     * [java.io.Writer] using this method would require UTF-8 decoding. If so, implementation may instead choose to
     * throw a [UnsupportedOperationException] due to ineffectiveness of having to decode input.
     *
     * @param buffer Buffer that contains String value to write
     *
     * @param offset Offset in `buffer` of the first byte of String value to write
     *
     * @param length Length of the String value (in characters) to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeRawUTF8String(buffer: ByteArray, offset: Int, length: Int): CirJsonGenerator

    /**
     * Method similar to [writeString] but that takes as its input a UTF-8 encoded String which has **not** been escaped
     * using whatever escaping scheme data format requires (for CirJSON that is backslash-escaping for control
     * characters and double-quotes; for other formats something else). This means that textual CirJSON backends need to
     * check if value needs CirJSON escaping, but otherwise can just be copied as is to output. Also, quoting that data
     * format requires (like double-quotes for CirJSON) will be added around the value if and as necessary.
     *
     * Note that some backends may choose not to support this method: for example, if underlying destination is a
     * [java.io.Writer] using this method would require UTF-8 decoding. In this case generator implementation may
     * instead choose to throw a [UnsupportedOperationException] due to ineffectiveness of having to decode input.
     *
     * @param buffer Buffer that contains String value to write
     *
     * @param offset Offset in `buffer` of the first byte of String value to write
     *
     * @param length Length of the String value (in characters) to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeUTF8String(buffer: ByteArray, offset: Int, length: Int): CirJsonGenerator

    /*
     *******************************************************************************************************************
     * Public API, write methods, raw content
     *******************************************************************************************************************
     */

    /**
     * Method that will force generator to copy input text verbatim with **no** modifications (including that no
     * escaping is done and no separators are added even if context [array, object] would otherwise require such). If
     * such separators are desired, use [writeRawValue] instead.
     *
     * Note that not all generator implementations necessarily support such by-pass methods: those that do not will
     * throw [UnsupportedOperationException].
     *
     * @return This generator, to allow call chaining
     *
     * @param text Textual contents to include as-is in output.
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeRaw(text: String): CirJsonGenerator

    /**
     * Method that will force generator to copy input text verbatim with **no** modifications (including that no
     * escaping is done and no separators are added even if context [array, object] would otherwise require such). If
     * such separators are desired, use [writeRawValue] instead.
     *
     * Note that not all generator implementations necessarily support such by-pass methods: those that do not will
     * throw [UnsupportedOperationException].
     *
     * @param text String that has contents to include as-is in output
     *
     * @param offset Offset within `text` of the first character to output
     *
     * @param length Length of content (from `text`, starting at offset `offset`) to output
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeRaw(text: String, offset: Int, length: Int): CirJsonGenerator

    /**
     * Method that will force generator to copy input text verbatim with **no** modifications (including that no
     * escaping is done and no separators are added even if context [array, object] would otherwise require such). If
     * such separators are desired, use [writeRawValue] instead.
     *
     * Note that not all generator implementations necessarily support such by-pass methods: those that do not will
     * throw [UnsupportedOperationException].
     *
     * @param buffer Buffer that has contents to include as-is in output
     * @param offset Offset within `text` of the first character to output
     * @param length Length of content (from `text`, starting at offset `offset`) to output
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeRaw(buffer: CharArray, offset: Int, length: Int): CirJsonGenerator

    /**
     * Method that will force generator to copy input text verbatim with **no** modifications (including that no
     * escaping is done and no separators are added even if context [array, object] would otherwise require such). If
     * such separators are desired, use [writeRawValue] instead.
     *
     * Note that not all generator implementations necessarily support such by-pass methods: those that do not will
     * throw [UnsupportedOperationException].
     *
     * @param char Character to included in output
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeRaw(char: Char): CirJsonGenerator

    /**
     * Method that will force generator to copy input text verbatim with **no** modifications (including that no
     * escaping is done and no separators are added even if context [array, object] would otherwise require such). If
     * such separators are desired, use [writeRawValue] instead.
     *
     * Note that not all generator implementations necessarily support such by-pass methods: those that do not will
     * throw [UnsupportedOperationException].
     *
     * The default implementation does `writeRaw(raw.value)`; other backends that support raw inclusion of text are
     * encouraged to implement it in more efficient manner (especially if they use UTF-8 encoding).
     *
     * @param raw Pre-encoded textual contents to included in output
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    open fun writeRaw(raw: SerializableString): CirJsonGenerator {
        return writeRaw(raw.value)
    }

    /**
     * Method that will force generator to copy input text verbatim without any modifications, but assuming it must
     * constitute a single legal CirJSON value (number, string, boolean, null, Array or List). Assuming this, proper
     * separators are added if and as needed (comma or colon), and generator state updated to reflect this.
     *
     * @param text Textual contents to included in output
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeRawValue(text: String): CirJsonGenerator

    /**
     * Method that will force generator to copy input text verbatim without any modifications, but assuming it must
     * constitute a single legal CirJSON value (number, string, boolean, null, Array or List). Assuming this, proper
     * separators are added if and as needed (comma or colon), and generator state updated to reflect this.
     *
     * @param text Textual contents to included in output
     *
     * @param offset Offset within `text` of the first character to output
     *
     * @param length Length of content (from `text`, starting at offset `offset`) to output
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeRawValue(text: String, offset: Int, length: Int): CirJsonGenerator

    /**
     * Method that will force generator to copy input text verbatim without any modifications, but assuming it must
     * constitute a single legal CirJSON value (number, string, boolean, null, Array or List). Assuming this, proper
     * separators are added if and as needed (comma or colon), and generator state updated to reflect this.
     *
     * @param text Textual contents to included in output
     *
     * @param offset Offset within `text` of the first character to output
     *
     * @param length Length of content (from `text`, starting at offset `offset`) to output
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeRawValue(text: CharArray, offset: Int, length: Int): CirJsonGenerator

    /**
     * Method similar to [writeRawValue], but potentially more efficient as it may be able to use pre-encoded content
     * (similar to [writeRaw]).
     *
     * @param raw Pre-encoded textual contents to included in output
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    open fun writeRawValue(raw: SerializableString): CirJsonGenerator {
        return writeRawValue(raw.value)
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, Binary values
     *******************************************************************************************************************
     */

    /**
     * Method that will output given chunk of binary data as base64 encoded, as a complete String value (surrounded by
     * double quotes).
     *
     * Note: because CirJSON Strings can not contain unescaped linefeeds, if linefeeds are included (as per last
     * argument), they must be escaped. This adds overhead for decoding without improving readability. Alternatively if
     * linefeeds are not included, resulting String value may violate the requirement of base64 RFC which mandates
     * line-length of 76 characters and use of linefeeds. However, all [CirJsonParser] implementations are required to
     * accept such "long line base64"; as do typical production-level base64 decoders.
     *
     * @param variant Base64 variant to use: defines details such as whether padding is used (and if so, using which
     * character); what is the maximum line length before adding linefeed, and also the underlying alphabet to use.
     *
     * @param data Buffer that contains binary data to write
     *
     * @param offset Offset in `data` of the first byte of data to write
     *
     * @param length Length of data to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeBinary(variant: Base64Variant, data: ByteArray, offset: Int, length: Int): CirJsonGenerator

    /**
     * Similar to [writeBinary], but default to using the CirJackson default Base64 variant (which is
     * [Base64Variants.MIME_NO_LINEFEEDS]).
     *
     * @param data Buffer that contains binary data to write
     *
     * @param offset Offset in `data` of the first byte of data to write
     *
     * @param length Length of data to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    open fun writeBinary(data: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        return writeBinary(Base64Variants.defaultVariant, data, offset, length)
    }

    /**
     * Similar to [writeBinary], but assumes default to using the CirJackson default Base64 variant (which is
     * [Base64Variants.MIME_NO_LINEFEEDS]). Also assumes that whole byte array is to be output.
     *
     * @param data Buffer that contains binary data to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    open fun writeBinary(data: ByteArray): CirJsonGenerator {
        return writeBinary(Base64Variants.defaultVariant, data, 0, data.size)
    }

    /**
     * Method similar to [writeBinary], but where input is provided through a stream, allowing for incremental writes
     * without holding the whole input in memory.
     *
     * @param variant Base64 variant to use
     *
     * @param data InputStream to use for reading binary data to write. Will not be closed after successful write
     * operation
     *
     * @param dataLength (optional) number of bytes that will be available; or -1 to indicate it is not known. If a
     * positive length is given, `data` MUST provide at least that many bytes: if not, an exception will be thrown. Note
     * that implementations need not support cases where length is not known in advance; this depends on underlying data
     * format: CirJSON output does NOT require length, other formats may.
     *
     * @return Number of bytes read from `data` and written as binary payload
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeBinary(variant: Base64Variant, data: InputStream, dataLength: Int): CirJsonGenerator

    /**
     * Similar to [writeBinary], but assumes default to using the CirJackson default Base64 variant (which is
     * [Base64Variants.MIME_NO_LINEFEEDS]).
     *
     * @param data InputStream to use for reading binary data to write. Will not be closed after successful write
     * operation
     *
     * @param dataLength (optional) number of bytes that will be available; or -1 to indicate it is not known. Note that
     * implementations need not support cases where length is not known in advance; this depends on underlying data
     * format: CirJSON output does NOT require length, other formats may
     *
     * @return Number of bytes actually written
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    open fun writeBinary(data: InputStream, dataLength: Int): CirJsonGenerator {
        return writeBinary(Base64Variants.defaultVariant, data, dataLength)
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, numeric
     *******************************************************************************************************************
     */

    /**
     * Method for outputting given value as CirJSON number. Can be called in any context where a value is expected
     * (Array value, Object property value, root-level value). Additional white space may be added around the value if
     * pretty-printing is enabled.
     *
     * @param value Number value to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeNumber(value: Short): CirJsonGenerator

    /**
     * Method for outputting given value as CirJSON number. Can be called in any context where a value is expected
     * (Array value, Object property value, root-level value). Additional white space may be added around the value if
     * pretty-printing is enabled.
     *
     * @param value Number value to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeNumber(value: Int): CirJsonGenerator

    /**
     * Method for outputting given value as CirJSON number. Can be called in any context where a value is expected
     * (Array value, Object property value, root-level value). Additional white space may be added around the value if
     * pretty-printing is enabled.
     *
     * @param value Number value to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeNumber(value: Long): CirJsonGenerator

    /**
     * Method for outputting given value as CirJSON number. Can be called in any context where a value is expected
     * (Array value, Object property value, root-level value). Additional white space may be added around the value if
     * pretty-printing is enabled.
     *
     * @param value Number value to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeNumber(value: BigInteger): CirJsonGenerator

    /**
     * Method for outputting given value as CirJSON number. Can be called in any context where a value is expected
     * (Array value, Object property value, root-level value). Additional white space may be added around the value if
     * pretty-printing is enabled.
     *
     * @param value Number value to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeNumber(value: Double): CirJsonGenerator

    /**
     * Method for outputting given value as CirJSON number. Can be called in any context where a value is expected
     * (Array value, Object property value, root-level value). Additional white space may be added around the value if
     * pretty-printing is enabled.
     *
     * @param value Number value to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeNumber(value: Float): CirJsonGenerator

    /**
     * Method for outputting given value as CirJSON number. Can be called in any context where a value is expected
     * (Array value, Object property value, root-level value). Additional white space may be added around the value if
     * pretty-printing is enabled.
     *
     * @param value Number value to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeNumber(value: BigDecimal): CirJsonGenerator

    /**
     * Write method that can be used for custom numeric types that can not be (easily?) converted to "standard" number
     * types. Because numbers are not surrounded by double quotes, regular [writeString] method can not be used; nor
     * [writeRaw] because that does not properly handle value separators needed in Array or Object contexts.
     *
     * Note: because of lack of type safety, some generator implementations may not be able to implement this method.
     * For example, if a binary CirJSON format is used, it may require type information for encoding; similarly for
     * generator-wrappers around objects or CirJSON nodes. If implementation does not implement this method, it needs to
     * throw [UnsupportedOperationException].
     *
     * @param encodedValue Textual (possibly format) number representation to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws UnsupportedOperationException If underlying data format does not support numbers serialized textually AND
     * if generator is not allowed to just output a String instead (Schema-based formats may require actual number, for
     * example)
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeNumber(encodedValue: String): CirJsonGenerator

    /**
     * Overloaded version of [writeNumber] with same semantics but possibly more efficient operation.
     *
     * @param encodedValueBuffer Buffer that contains the textual number representation to write
     *
     * @param offset Offset of the first character of value to write
     *
     * @param length Length of the value (in characters) to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    open fun writeNumber(encodedValueBuffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        return writeNumber(String(encodedValueBuffer, offset, length))
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, literal types
     *******************************************************************************************************************
     */

    /**
     * Method for outputting literal CirJSON boolean value (one of Strings 'true' and 'false'). Can be called in any
     * context where a value is expected (Array value, Object property value, root-level value). Additional white space
     * may be added around the value if pretty-printing is enabled.
     *
     * @param state Boolean value to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeBoolean(state: Boolean): CirJsonGenerator

    /**
     * Method for outputting literal CirJSON null value. Can be called in any context where a value is expected (Array
     * value, Object property value, root-level value). Additional white space may be added around the value if
     * pretty-printing is enabled.
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeNull(): CirJsonGenerator

    /*
     *******************************************************************************************************************
     * Public API, write methods, other value types
     *******************************************************************************************************************
     */

    /**
     * Method that can be called on backends that support passing opaque native values that some data formats support;
     * not used with CirJSON backend, more common with binary formats.
     *
     * NOTE: this is NOT the method to call for serializing regular POJOs, see [writePOJO] instead.
     *
     * @param obj Native format-specific value to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    open fun writeEmbeddedObject(obj: Any?): CirJsonGenerator {
        obj ?: return this.also { writeNull() }

        if (obj is ByteArray) {
            return this.also { writeBinary(obj) }
        }

        throw constructWriteException("No native support for writing embedded objects of type ${obj.javaClass.name}")
    }

    protected fun constructWriteException(message: String): StreamWriteException {
        return StreamWriteException(this, message)
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, other value types
     *******************************************************************************************************************
     */

    /**
     * Method that can be called to output so-called native Object ID.
     *
     * @param referenced Referenced value, for which Object ID is expected to be written
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeObjectId(referenced: Any): CirJsonGenerator

    /**
     * Method that can be called to output so-called native type ID. Note that it may only be called after ensuring this
     * is legal (with [isAbleWriteTypeId]), as not all data formats have native type id support; and some may only allow
     * them in certain positions or locations. If output is not allowed by the data format in this position, a
     * [StreamWriteException] will be thrown.
     *
     * @param id Native Type ID to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    open fun writeTypeId(id: Any): CirJsonGenerator {
        throw constructWriteException("No native support for writing type IDs")
    }

    /**
     * Replacement method for [writeTypeId] which is called regardless of whether format has native type IDs. If it does
     * have native type ids, those are to be used (if configuration allows this), if not, structural type ID inclusion
     * is to be used. For CirJSON, for example, no native type ids exist and structural inclusion is always used.
     *
     * NOTE: databind may choose to skip calling this method for some special cases (and instead included type ID via
     * regular write methods and/or [writeTypeId]) -- this is discouraged, but not illegal, and may be necessary as a
     * work-around in some cases.
     *
     * @param typeIdDefinition Full Type ID definition
     *
     * @return [WritableTypeID] for caller to retain and pass to matching [writeTypeSuffix] call
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeTypePrefix(typeIdDefinition: WritableTypeID): WritableTypeID {
        val id = typeIdDefinition.id
        val valueShape = typeIdDefinition.valueShape

        if (isAbleWriteTypeId) {
            typeIdDefinition.isWrapperWritten = false
            writeTypeId(id!!)
        } else {
            val idString = (id as? String) ?: id.toString()
            var inclusion = typeIdDefinition.inclusion!!

            if (valueShape != CirJsonToken.START_OBJECT && inclusion.isRequiringObjectContext) {
                inclusion = WritableTypeID.Inclusion.WRAPPER_ARRAY.also { typeIdDefinition.inclusion = it }
            }

            when (inclusion) {
                WritableTypeID.Inclusion.PARENT_PROPERTY, WritableTypeID.Inclusion.PAYLOAD_PROPERTY -> {}

                WritableTypeID.Inclusion.METADATA_PROPERTY -> {
                    writeStartObject(typeIdDefinition.forValue)
                    writeStringProperty(typeIdDefinition.asProperty!!, idString)
                    return typeIdDefinition
                }

                WritableTypeID.Inclusion.WRAPPER_OBJECT -> {
                    writeStartObject()
                    writeName(idString)
                }

                WritableTypeID.Inclusion.WRAPPER_ARRAY -> {
                    writeStartArray()
                    writeString(idString)
                }
            }
        }

        if (valueShape == CirJsonToken.START_OBJECT) {
            writeStartObject(typeIdDefinition.forValue)
        } else if (valueShape == CirJsonToken.START_ARRAY) {
            writeStartArray()
        }

        return typeIdDefinition
    }

    @Throws(CirJacksonException::class)
    fun writeTypeSuffix(typeIdDefinition: WritableTypeID): WritableTypeID {
        val valueShape = typeIdDefinition.valueShape

        if (valueShape == CirJsonToken.START_OBJECT) {
            writeEndObject()
        } else if (valueShape == CirJsonToken.START_ARRAY) {
            writeEndArray()
        }

        if (typeIdDefinition.isWrapperWritten) {
            when (typeIdDefinition.inclusion) {
                WritableTypeID.Inclusion.METADATA_PROPERTY, WritableTypeID.Inclusion.PAYLOAD_PROPERTY -> {}

                WritableTypeID.Inclusion.WRAPPER_ARRAY -> {
                    writeEndArray()
                }

                WritableTypeID.Inclusion.WRAPPER_OBJECT -> {
                    writeEndObject()
                }

                WritableTypeID.Inclusion.PARENT_PROPERTY -> {
                    val id = typeIdDefinition.id
                    val idString = (id as? String) ?: id.toString()
                    writeStringProperty(typeIdDefinition.asProperty!!, idString)
                }

                else -> {
                    writeEndObject()
                }
            }
        }

        return typeIdDefinition
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, serializing objects
     *******************************************************************************************************************
     */

    /**
     * Method for writing given object (POJO) as tokens into stream this generator manages; serialization must be a
     * valid CirJSON Value (Object, Array, null, Number, String or Boolean). This is done by delegating call to
     * [ObjectWriteContext.writeValue].
     *
     * @param pojo Java Object (POJO) value to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writePOJO(pojo: Any?): CirJsonGenerator

    /**
     * Method for writing given CirJSON tree (expressed as a tree where given [TreeNode] is the root) using this
     * generator. This is done by delegating call to [ObjectWriteContext.writeTree].
     *
     * @param rootNode [TreeNode] to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    abstract fun writeTree(rootNode: TreeNode?): CirJsonGenerator

    /*
     *******************************************************************************************************************
     * Public API, convenience property write methods
     *******************************************************************************************************************
     */

    /**
     * Convenience method for outputting an Object property that contains specified data in base64-encoded form.
     * Equivalent to:
     * ```
     *  writeName(propertyName)
     *  writeBinary(value)
     * ```
     *
     * @param propertyName Name of Object Property to write
     * @param data Binary value of the property to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws JacksonIOException if there is an underlying I/O problem
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeBinaryProperty(propertyName: String, data: ByteArray): CirJsonGenerator {
        writeName(propertyName)
        return writeBinary(data)
    }

    /**
     * Convenience method for outputting an Object property that has a boolean value. Equivalent to:
     * ```
     * writeName(propertyName)
     * writeBoolean(value)
     * ```
     *
     * @param propertyName Name of Object Property to write
     * @param value Boolean value of the property to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeBoolean(propertyName: String, value: Boolean): CirJsonGenerator {
        writeName(propertyName)
        return writeBoolean(value)
    }

    /**
     * Convenience method for outputting an Object property that has CirJSON literal value null. Equivalent to:
     * ```
     * writeName(propertyName)
     * writeNull()
     * ```
     *
     * @param propertyName Name of the null-valued property to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeNullProperty(propertyName: String): CirJsonGenerator {
        writeName(propertyName)
        return writeNull()
    }

    /**
     * Convenience method for outputting an Object property that has a String value. Equivalent to:
     * ```
     * writeName(propertyName)
     * writeString(value)
     * ```
     *
     * @param propertyName Name of the property to write
     * @param value String value of the property to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeStringProperty(propertyName: String, value: String): CirJsonGenerator {
        writeName(propertyName)
        return writeString(value)
    }

    /**
     * Convenience method for outputting an Object property that has the specified numeric value. Equivalent to:
     * ```
     * writeName(propertyName)
     * writeNumber(value)
     * ```
     *
     * @param propertyName Name of the property to write
     * @param value Numeric value of the property to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeNumberProperty(propertyName: String, value: Short): CirJsonGenerator {
        writeName(propertyName)
        return writeNumber(value)
    }

    /**
     * Convenience method for outputting an Object property that has the specified numeric value. Equivalent to:
     * ```
     * writeName(propertyName)
     * writeNumber(value)
     * ```
     *
     * @param propertyName Name of the property to write
     * @param value Numeric value of the property to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeNumberProperty(propertyName: String, value: Int): CirJsonGenerator {
        writeName(propertyName)
        return writeNumber(value)
    }

    /**
     * Convenience method for outputting an Object property that has the specified numeric value. Equivalent to:
     * ```
     * writeName(propertyName)
     * writeNumber(value)
     * ```
     *
     * @param propertyName Name of the property to write
     * @param value Numeric value of the property to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeNumberProperty(propertyName: String, value: Long): CirJsonGenerator {
        writeName(propertyName)
        return writeNumber(value)
    }

    /**
     * Convenience method for outputting an Object property that has the specified numeric value. Equivalent to:
     * ```
     * writeName(propertyName)
     * writeNumber(value)
     * ```
     *
     * @param propertyName Name of the property to write
     * @param value Numeric value of the property to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeNumberProperty(propertyName: String, value: BigInteger): CirJsonGenerator {
        writeName(propertyName)
        return writeNumber(value)
    }

    /**
     * Convenience method for outputting an Object property that has the specified numeric value. Equivalent to:
     * ```
     * writeName(propertyName)
     * writeNumber(value)
     * ```
     *
     * @param propertyName Name of the property to write
     * @param value Numeric value of the property to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeNumberProperty(propertyName: String, value: Float): CirJsonGenerator {
        writeName(propertyName)
        return writeNumber(value)
    }

    /**
     * Convenience method for outputting an Object property that has the specified numeric value. Equivalent to:
     * ```
     * writeName(propertyName)
     * writeNumber(value)
     * ```
     *
     * @param propertyName Name of the property to write
     * @param value Numeric value of the property to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeNumberProperty(propertyName: String, value: Double): CirJsonGenerator {
        writeName(propertyName)
        return writeNumber(value)
    }

    /**
     * Convenience method for outputting an Object property that has the specified numeric value. Equivalent to:
     * ```
     * writeName(propertyName)
     * writeNumber(value)
     * ```
     *
     * @param propertyName Name of the property to write
     * @param value Numeric value of the property to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeNumberProperty(propertyName: String, value: BigDecimal): CirJsonGenerator {
        writeName(propertyName)
        return writeNumber(value)
    }

    /**
     * Convenience method for outputting an Object property (that will contain a CirJSON Array value), and the
     * START_ARRAY marker. Equivalent to:
     * ```
     * writeName(propertyName)
     * writeStartArray()
     * ```
     *
     * Note: caller still has to take care to close the array (by calling [writeEndArray]) after writing all values of
     * the value Array.
     *
     * @param propertyName Name of the Array property to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeArrayPropertyStart(propertyName: String): CirJsonGenerator {
        writeName(propertyName)
        return writeStartArray()
    }

    /**
     * Convenience method for outputting an Object property (that will contain an Object value), and the START_OBJECT
     * marker. Equivalent to:
     * ```
     * writeName(propertyName)
     * writeStartObject()
     * ```
     *
     * Note: caller still has to take care to close the Object
     * (by calling [writeEndObject]) after writing all
     * entries of the value Object.
     *
     * @param propertyName Name of the Object property to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeObjectPropertyStart(propertyName: String): CirJsonGenerator {
        writeName(propertyName)
        return writeStartObject()
    }

    /**
     * Convenience method for outputting am Object property that has contents of specific object (POJO) as its value.
     * Equivalent to:
     * ```
     * writeName(propertyName)
     * writeObject(pojo)
     * ```
     *
     * NOTE: see [writePOJO] for details on how POJO value actually
     * gets written (uses delegation).
     *
     * @param propertyName Name of the property to write
     * @param pojo POJO value of the property to write
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writePOJOProperty(propertyName: String, pojo: Any): CirJsonGenerator {
        writeName(propertyName)
        return writePOJO(pojo)
    }

    /**
     * Method called to indicate that a property in this position was skipped. It is usually only called for generators
     * that return `false` from [isAbleOmitProperties].
     *
     * Default implementation does nothing; method is overridden by some format backends.
     *
     * @param propertyName Name of the property that is being omitted
     *
     * @return This generator, to allow call chaining
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    open fun writeOmittedProperty(propertyName: String): CirJsonGenerator {
        return this
    }

    /*
     *******************************************************************************************************************
     * Public API, copy-through methods
     *******************************************************************************************************************
     */

    /**
     * Method for copying contents of the current event that the given parser instance points to. Note that the method
     * **will not** copy any other events, such as events contained within CirJSON Array or Object structures.
     *
     * Calling this method will not advance the given parser, although it may cause parser to internally process more
     * data (if it lazily loads contents of value events, for example)
     *
     * @param parser Parser that points to the event to copy
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem (reading or writing)
     *
     * @throws StreamReadException for problems with decoding of token stream
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    open fun copyCurrentEvent(parser: CirJsonParser) {
        val token = parser.currentToken()
        val tokenId = token?.id ?: CirJsonTokenId.ID_NOT_AVAILABLE

        when (tokenId) {
            CirJsonTokenId.ID_NOT_AVAILABLE -> {
                reportError<Unit>("No current event to copy")
            }

            CirJsonTokenId.ID_START_OBJECT -> {
                writeStartObject()
            }

            CirJsonTokenId.ID_END_OBJECT -> {
                writeEndObject()
            }

            CirJsonTokenId.ID_START_ARRAY -> {
                writeStartArray()
            }

            CirJsonTokenId.ID_END_ARRAY -> {
                writeEndArray()
            }

            CirJsonTokenId.ID_CIRJSON_ID_PROPERTY_NAME -> {
                writeName(parser.idName)
            }

            CirJsonTokenId.ID_PROPERTY_NAME -> {
                writeName(parser.currentName!!)
            }

            CirJsonTokenId.ID_STRING -> {
                copyCurrentStringValue(parser)
            }

            CirJsonTokenId.ID_NUMBER_INT -> {
                copyCurrentIntValue(parser)
            }

            CirJsonTokenId.ID_NUMBER_FLOAT -> {
                copyCurrentFloatValue(parser)
            }

            CirJsonTokenId.ID_TRUE -> {
                writeBoolean(true)
            }

            CirJsonTokenId.ID_FALSE -> {
                writeBoolean(false)
            }

            CirJsonTokenId.ID_NULL -> {
                writeNull()
            }

            CirJsonTokenId.ID_EMBEDDED_OBJECT -> {
                writePOJO(parser.embeddedObject!!)
            }

            else -> {
                throw IllegalArgumentException("Internal error: unknown current token, $token")
            }
        }
    }

    /**
     * Helper method used for constructing and throwing [StreamWriteException] with given message.
     *
     * Note that subclasses may override this method to add more detail or use a [StreamWriteException] subclass.
     *
     * @param T Bogus type parameter to "return anything" so that compiler won't complain when chaining calls
     *
     * @param message Message to construct exception with
     *
     * @return Does not return at all as exception is always thrown, but nominally returns "anything"
     *
     * @throws StreamWriteException that was constructed with given message
     */
    @Throws(StreamWriteException::class)
    protected open fun <T> reportError(message: String): T {
        throw constructWriteException(message)
    }

    /**
     * Method for copying current [CirJsonToken.VALUE_STRING] value; overridable by format backend implementations.
     *
     * @param parser Parser that points to the value to copy
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem (reading or writing)
     *
     * @throws StreamReadException for problems with decoding of token stream
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    protected open fun copyCurrentStringValue(parser: CirJsonParser) {
        if (parser.isTextCharactersAvailable) {
            writeString(parser.textCharacters!!, parser.textOffset, parser.textLength)
        } else {
            writeString(parser.text!!)
        }
    }

    /**
     * Method for copying current [CirJsonToken.VALUE_NUMBER_INT] value; overridable by format backend implementations.
     *
     * @param parser Parser that points to the value to copy
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem (reading or writing)
     *
     * @throws StreamReadException for problems with decoding of token stream
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    protected open fun copyCurrentIntValue(parser: CirJsonParser) {
        val numberType = parser.numberType

        when (numberType) {
            CirJsonParser.NumberType.INT -> {
                writeNumber(parser.intValue)
            }

            CirJsonParser.NumberType.LONG -> {
                writeNumber(parser.longValue)
            }

            else -> {
                writeNumber(parser.bigIntegerValue)
            }
        }
    }

    /**
     * Method for copying current [CirJsonToken.VALUE_NUMBER_FLOAT] value; overridable by format backend
     * implementations. Implementation checks [CirJsonParser.numberType] for declared type and uses matching accessors:
     * this may cause inexact conversion for some textual formats (depending on settings). If this is problematic, use
     * [copyCurrentFloatValueExact] instead (note that doing so may add overhead).
     *
     * @param parser Parser that points to the value to copy
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem (reading or writing)
     *
     * @throws StreamReadException for problems with decoding of token stream
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    protected open fun copyCurrentFloatValue(parser: CirJsonParser) {
        val numberType = parser.numberType

        when (numberType) {
            CirJsonParser.NumberType.BIG_DECIMAL -> {
                writeNumber(parser.bigDecimalValue)
            }

            CirJsonParser.NumberType.FLOAT -> {
                writeNumber(parser.floatValue)
            }

            else -> {
                writeNumber(parser.doubleValue)
            }
        }
    }

    /**
     * Same as [copyCurrentEvent] with the exception that copying of numeric values tries to avoid any conversion
     * losses; in particular for floating-point numbers. This usually matters when transcoding from textual format like
     * CirJSON to a binary format. See [copyCurrentFloatValueExact] for details.
     *
     * @param parser Parser that points to the event to copy
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem (reading or writing)
     *
     * @throws StreamReadException for problems with decoding of token stream
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    open fun copyCurrentEventExact(parser: CirJsonParser) {
        val token = parser.currentToken()
        val tokenId = token?.id ?: CirJsonTokenId.ID_NOT_AVAILABLE

        if (tokenId == CirJsonTokenId.ID_NUMBER_FLOAT) {
            copyCurrentFloatValueExact(parser)
        } else {
            copyCurrentEvent(parser)
        }
    }

    /**
     * Method for copying current [CirJsonToken.VALUE_NUMBER_FLOAT] value; overridable by format backend
     * implementations. Implementation ensures it uses most accurate accessors necessary to retain exact value in case
     * of possible numeric conversion: in practice this means that [BigDecimal] is usually used as the representation
     * accessed from [CirJsonParser], regardless of whether [Double] might be accurate (since detecting lossy conversion
     * is not possible to do efficiently). If minimal overhead is desired, use [copyCurrentFloatValue] instead.
     *
     * @param parser Parser that points to the value to copy
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem (reading or writing)
     *
     * @throws StreamReadException for problems with decoding of token stream
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    protected open fun copyCurrentFloatValueExact(parser: CirJsonParser) {
        when (val number = parser.numberValueExact) {
            is BigDecimal -> {
                writeNumber(number)
            }

            is Double -> {
                writeNumber(number)
            }

            else -> {
                writeNumber(number.toFloat())
            }
        }
    }

    @Throws(CirJacksonException::class)
    open fun copyCurrentStructure(parser: CirJsonParser) {
        var token = parser.currentToken()
        var id = token?.id ?: CirJsonTokenId.ID_NOT_AVAILABLE

        if (id == CirJsonTokenId.ID_PROPERTY_NAME) {
            writeName(parser.currentName!!)
            token = parser.nextToken()
            id = token?.id ?: CirJsonTokenId.ID_NOT_AVAILABLE
        }

        when (id) {
            CirJsonTokenId.ID_START_OBJECT -> {
                writeStartObject()
                copyCurrentContents(parser)
            }

            CirJsonTokenId.ID_START_ARRAY -> {
                writeStartArray()
                copyCurrentContents(parser)
            }

            else -> {
                copyCurrentEvent(parser)
            }
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun copyCurrentContents(parser: CirJsonParser) {
        var depth = 1
        lateinit var token: CirJsonToken

        while (parser.nextToken()?.also { token = it } != null) {
            when (token.id) {
                CirJsonTokenId.ID_CIRJSON_ID_PROPERTY_NAME -> {
                    writeName(parser.idName)
                }

                CirJsonTokenId.ID_PROPERTY_NAME -> {
                    writeName(parser.currentName!!)
                }

                CirJsonTokenId.ID_START_ARRAY -> {
                    writeStartArray()
                    ++depth
                }

                CirJsonTokenId.ID_START_OBJECT -> {
                    writeStartObject()
                    ++depth
                }

                CirJsonTokenId.ID_END_ARRAY -> {
                    writeEndArray()

                    if (--depth == 0) {
                        return
                    }
                }

                CirJsonTokenId.ID_END_OBJECT -> {
                    writeEndObject()

                    if (--depth == 0) {
                        return
                    }
                }

                CirJsonTokenId.ID_STRING -> {
                    copyCurrentStringValue(parser)
                }

                CirJsonTokenId.ID_NUMBER_INT -> {
                    copyCurrentIntValue(parser)
                }

                CirJsonTokenId.ID_NUMBER_FLOAT -> {
                    copyCurrentFloatValue(parser)
                }

                CirJsonTokenId.ID_TRUE -> {
                    writeBoolean(true)
                }

                CirJsonTokenId.ID_FALSE -> {
                    writeBoolean(false)
                }

                CirJsonTokenId.ID_NULL -> {
                    writeNull()
                }

                CirJsonTokenId.ID_EMBEDDED_OBJECT -> {
                    writePOJO(parser.embeddedObject!!)
                }

                else -> {
                    throw IllegalArgumentException("Internal error: unknown current token, $token")
                }
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods for sub-classes
     *******************************************************************************************************************
     */

    protected open fun <T> reportUnsupportedOperation(): T {
        return reportUnsupportedOperation("Operation not supported by `CirJsonGenerator` of type ${javaClass.name}")
    }

    protected open fun <T> reportUnsupportedOperation(message: String): T {
        throw UnsupportedOperationException(message)
    }

    /**
     * Helper method used for constructing and throwing [StreamWriteException] with given message, in cases where
     * argument(s) used for a call (usually one of {@code writeX()} methods) is invalid.
     *
     * Default implementation simply delegates to [reportError].
     *
     * @param T Bogus type parameter to "return anything" so that compiler won't complain when chaining calls
     *
     * @param message Message to construct exception with
     *
     * @return Does not return at all as exception is always thrown, but nominally returns "anything"
     *
     * @throws StreamWriteException that was constructed with given message
     */
    @Throws(StreamWriteException::class)
    protected open fun <T> reportArgumentError(message: String): T {
        return reportError(message)
    }

    protected fun wrapIOFailure(e: IOException): CirJacksonException {
        return CirJacksonIOException.construct(e, this)
    }

}