package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.exception.StreamWriteException
import org.cirjson.cirjackson.core.io.CharacterEscapes
import org.cirjson.cirjackson.core.util.CirJacksonFeatureSet
import java.io.Closeable
import java.io.Flushable

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
    open val streamWriteConstraints = StreamReadConstraints.defaults()

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
    abstract fun assignCurrentValue(value: Any?): Any?

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
     * Method for writing starting marker of a Array value (for CirJSON this is character `[`; plus possible white space
     * decoration if pretty-printing is enabled).
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

}