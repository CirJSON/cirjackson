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

    companion object {

    }

}