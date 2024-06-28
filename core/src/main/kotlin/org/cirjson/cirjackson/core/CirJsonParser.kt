package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.CirJsonParser.NumberTypeFP.UNKNOWN
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.util.CirJacksonFeatureSet
import java.io.Closeable

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