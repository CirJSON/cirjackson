package org.cirjson.cirjackson.core

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
     * Method for enabling or disabling specified feature:
     * check {@link StreamWriteFeature} for list of available features.
     *
     * NOTE: mostly here just to support disabling of
     * {@link StreamWriteFeature.AUTO_CLOSE_CONTENT} by {@code cirjackson-databind}
     *
     * @param feature Feature to enable or disable
     *
     * @param state Whether to enable the feature ({@code true}) or disable ({@code false})
     *
     * @return This generator, to allow call chaining
     */
    abstract fun configure(feature: StreamWriteFeature, state: Boolean): CirJsonGenerator

    /**
     * Method for checking whether given feature is enabled.
     * Check {@link StreamWriteFeature} for list of available features.
     *
     * @param feature Feature to check
     *
     * @return {@code true} if feature is enabled; {@code false} if not
     */
    abstract fun isEnabled(feature: StreamWriteFeature): Boolean

    /**
     * Bulk access method for getting state of all standard (format-agnostic)
     * {@link StreamWriteFeature}s.
     *
     * @return Bit mask that defines current states of all standard {@link StreamWriteFeature}s.
     */
    abstract val streamWriteFeatures: Int

    /*
     *******************************************************************************************************************
     * Public API, Other configuration
     *******************************************************************************************************************
     */

    /**
     * {@link FormatSchema} this generator is configured to use, if any; {@code null} if none
     *
     * Default implementation returns null.
     */
    open val schema: FormatSchema? = null

    /**
     * Accessor method for testing what is the highest unescaped character
     * configured for this generator. This may be either positive value
     * (when escaping configuration has been set and is in effect), or
     * 0 to indicate that no additional escaping is in effect.
     * Some generators may not support additional escaping: for example,
     * generators for binary formats that do not use escaping should
     * simply return 0.
     *
     * Default implementation returns 0
     */
    open val highestNonEscapedChar = 0

    /**
     * {@link CharacterEscapes} this generator is configured to use, if any; {@code null} if none
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
     * Introspection method that may be called to see if the underlying
     * data format supports some kind of Type Ids natively (many do not;
     * for example, CirJSON doesn't).
     * This method <b>must</b> be called prior to calling
     * {@link writeTypeId}.
     *
     * Default implementation returns false; overridden by data formats
     * that do support native Type Ids. Caller is expected to either
     * use a non-native notation (explicit property or such), or fail,
     * in case it can not use native type ids.
     */
    open val isAbleWriteTypeId = false

    /**
     * Introspection method to call to check whether it is ok to omit
     * writing of Object properties or not. Most formats do allow omission,
     * but certain positional formats (such as CSV) require output of
     * placeholders, even if no real values are to be emitted.
     */
    open val isAbleOmitProperties = true

    /**
     * Accessor for getting metadata on capabilities of this generator, based on
     * underlying data format being read (directly or indirectly).
     */
    abstract val streamWriteCapabilities: CirJacksonFeatureSet<StreamWriteCapability>

    /*
     *******************************************************************************************************************
     * Public API, capability introspection methods
     *******************************************************************************************************************
     */

    companion object {

    }

}