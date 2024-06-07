package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.StreamReadConstraints.Companion.DEFAULT_MAX_DEPTH
import org.cirjson.cirjackson.core.StreamReadConstraints.Companion.DEFAULT_MAX_DOC_LEN
import org.cirjson.cirjackson.core.StreamReadConstraints.Companion.DEFAULT_MAX_NAME_LEN
import org.cirjson.cirjackson.core.StreamReadConstraints.Companion.DEFAULT_MAX_NUM_LEN
import org.cirjson.cirjackson.core.StreamReadConstraints.Companion.DEFAULT_MAX_STRING_LEN

/**
 * The constraints to use for streaming reads: used to guard against malicious input by preventing processing of "too
 * big" input constructs (values, structures).
 *
 * Constraints are registered with `TokenStreamFactory` (such as `JsonFactory`); if nothing explicitly specified,
 * default constraints are used.
 *
 * Currently constrained aspects, with default settings, are:
 * * Maximum Number value length: default 1000 (see [DEFAULT_MAX_NUM_LEN])
 * * Maximum String value length: default 20_000_000 (see [DEFAULT_MAX_STRING_LEN])
 * * Maximum Property name length: default 50_000 (see [DEFAULT_MAX_NAME_LEN])
 * * Maximum Nesting depth: default 500 (see [DEFAULT_MAX_DEPTH])
 * * Maximum Document length: default `unlimited` (coded as `-1`, (see [DEFAULT_MAX_DOC_LEN])
 *
 * @property maxNestingDepth Accessor for maximum depth. See [Builder.maxNestingDepth] for details.
 *
 * @property maxDocumentLength Accessor for maximum document length. See [Builder.maxDocumentLength] for details.
 *
 * @property maxNumberLength Accessor for maximum length of numbers to decode. See [Builder.maxNumberLength] for details.
 *
 * @property maxStringLength Accessor for maximum length of strings to decode. See [Builder.maxStringLength] for details.
 *
 * @property maxNameLength Accessor for maximum length of names to decode. See [Builder.maxNameLength] for details.
 */
open class StreamReadConstraints protected constructor(val maxNestingDepth: Int, val maxDocumentLength: Long,
        val maxNumberLength: Int, val maxStringLength: Int, val maxNameLength: Int) {

    companion object {

        /**
         * Default setting for maximum depth: see [Builder.maxNestingDepth] for details.
         */
        const val DEFAULT_MAX_DEPTH: Int = 500

        /**
         * Default setting for maximum document length: see [Builder.maxDocumentLength] for details.
         */
        const val DEFAULT_MAX_DOC_LEN: Long = -1L

        /**
         * Default setting for maximum number length: see [Builder.maxNumberLength] for details.
         */
        const val DEFAULT_MAX_NUM_LEN: Int = 1000

        /**
         * Default setting for maximum string length: see [Builder.maxStringLength] for details.
         */
        const val DEFAULT_MAX_STRING_LEN: Int = 20000000

        /**
         * Default setting for maximum name length: see [Builder.maxNameLength] for details.
         */
        const val DEFAULT_MAX_NAME_LEN: Int = 50000

        private val DEFAULT = StreamReadConstraints(DEFAULT_MAX_DEPTH, DEFAULT_MAX_DOC_LEN, DEFAULT_MAX_NUM_LEN,
                DEFAULT_MAX_STRING_LEN, DEFAULT_MAX_NAME_LEN)

        private var CURRENT_DEFAULT = DEFAULT

        /**
         * Override the default StreamReadConstraints. These defaults are only used when [TokenStreamFactory] instances
         * are not configured with their own StreamReadConstraints.
         *
         * Library maintainers should not set this as it will affect other code that uses CirJackson. Library
         * maintainers who want to configure StreamReadConstraints for the CirJackson usage within their lib should
         * create `ObjectMapper` instances that have a [TokenStreamFactory] instance with the required
         * StreamReadConstraints.
         *
         * This method is meant for users delivering applications. If they use this, they set it when they start their
         * application to avoid having other code initialize their mappers before the defaults are overridden.
         *
         * @param streamReadConstraints new default for StreamReadConstraints (a null value will reset to built-in
         * default)
         *
         * @see defaults
         * @see builder
         */
        fun overrideDefaultStreamReadConstraints(streamReadConstraints: StreamReadConstraints?) {
            CURRENT_DEFAULT = streamReadConstraints ?: DEFAULT
        }

        fun defaults() = CURRENT_DEFAULT

    }

}