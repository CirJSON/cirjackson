package org.cirjson.cirjackson.core

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

    companion object {

    }

}