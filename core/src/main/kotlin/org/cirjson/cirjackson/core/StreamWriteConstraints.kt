package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.StreamWriteConstraints.Builder
import org.cirjson.cirjackson.core.StreamWriteConstraints.Companion.DEFAULT_MAX_DEPTH
import org.cirjson.cirjackson.core.exception.StreamConstraintsException

/**
 * The constraints to use for streaming writes: used to guard against problematic output by preventing processing of
 * "too big" output constructs (values, structures). Constraints are registered with `TokenStreamFactory` (such as
 * `CirJsonFactory`); if nothing explicitly specified, default constraints are used.
 *
 * Currently constrained aspects, with default settings, are:
 * * Maximum Nesting depth: default 500 (see [DEFAULT_MAX_DEPTH])
 *
 * @property maxNestingDepth Accessor for maximum depth. See [Builder.maxNestingDepth] for details.
 */
open class StreamWriteConstraints protected constructor(val maxNestingDepth: Int) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * @return New [Builder] initialized with settings of this constraints instance
     */
    fun rebuild(): Builder {
        return Builder(this)
    }

    /*
     *******************************************************************************************************************
     * Convenience methods for validation, document limits
     *******************************************************************************************************************
     */

    /**
     * Convenience method that can be used to verify that the nesting depth does not exceed the maximum specified by
     * this constraints object: if it does, a [StreamConstraintsException] is thrown.
     *
     * @param depth count of unclosed objects and arrays
     *
     * @throws StreamConstraintsException If depth exceeds maximum
     */
    @Throws(StreamConstraintsException::class)
    fun validateNestingDepth(depth: Int) {
        if (depth > maxNestingDepth) {
            throw constructException(
                    "Document nesting depth ($depth) exceeds the maximum allowed ($maxNestingDepth, from ${
                        constrainRef("maxNestingDepth")
                    })")
        }
    }

    /*
     *******************************************************************************************************************
     * Error reporting
     *******************************************************************************************************************
     */

    @Throws(StreamConstraintsException::class)
    protected fun constructException(message: String): StreamConstraintsException {
        throw StreamConstraintsException(message)
    }

    protected fun constrainRef(method: String): String {
        return "`StreamWriteConstraints.$method`"
    }

    class Builder(private var myMaxNestingDepth: Int) {

        constructor() : this(DEFAULT_MAX_DEPTH)

        constructor(src: StreamWriteConstraints) : this(src.maxNestingDepth)

        /**
         * Sets the maximum nesting depth. The depth is a count of objects and arrays that have not been closed, `{` and
         * `[` respectively.
         *
         * @param maxNestingDepth the maximum depth
         *
         * @return this builder
         *
         * @throws IllegalArgumentException if the maxNestingDepth is set to a negative value
         */
        fun maxNestingDepth(maxNestingDepth: Int): Builder {
            if (maxNestingDepth < 0) {
                throw IllegalArgumentException("Cannot set maxNestingDepth to a negative value")
            }

            myMaxNestingDepth = maxNestingDepth
            return this
        }

        fun build(): StreamWriteConstraints {
            return StreamWriteConstraints(myMaxNestingDepth)
        }

    }

    companion object {

        /**
         * Default setting for maximum depth: see [Builder.maxNestingDepth] for details.
         */
        const val DEFAULT_MAX_DEPTH = 500

        private val DEFAULT = StreamWriteConstraints(DEFAULT_MAX_DEPTH)

        private var CURRENT_DEFAULT = DEFAULT

        /**
         * Override the default StreamWriteConstraints. These defaults are only used when [TokenStreamFactory] instances
         * are not configured with their own StreamWriteConstraints.
         *
         * Library maintainers should not set this as it will affect other code that uses CirJackson. Library
         * maintainers who want to configure StreamWriteConstraints for the CirJackson usage within their lib should
         * create `ObjectMapper` instances that have a [TokenStreamFactory] instance with the required
         * StreamWriteConstraints.
         *
         * This method is meant for users delivering applications. If they use this, they set it when they start their
         * application to avoid having other code initialize their mappers before the defaults are overridden.
         *
         * @param streamWriteConstraints new default for StreamWriteConstraints (a null value will reset to built-in
         * default)
         *
         * @see defaults
         * @see builder
         */
        fun overrideDefaultStreamWriteConstraints(streamWriteConstraints: StreamWriteConstraints?) {
            CURRENT_DEFAULT = streamWriteConstraints ?: DEFAULT
        }

        /**
         * @return the default [StreamWriteConstraints] (when none is set on the [TokenStreamFactory] explicitly)
         *
         * @see overrideDefaultStreamWriteConstraints
         */
        fun defaults() = CURRENT_DEFAULT

        /**
         * @return New [Builder] initialized with settings of this constraints instance
         */
        fun builder(): Builder {
            return Builder()
        }

    }

}