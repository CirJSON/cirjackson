package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.StreamReadConstraints.Builder
import org.cirjson.cirjackson.core.StreamReadConstraints.Companion.DEFAULT_MAX_DEPTH
import org.cirjson.cirjackson.core.StreamReadConstraints.Companion.DEFAULT_MAX_DOC_LEN
import org.cirjson.cirjackson.core.StreamReadConstraints.Companion.DEFAULT_MAX_NAME_LEN
import org.cirjson.cirjackson.core.StreamReadConstraints.Companion.DEFAULT_MAX_NUM_LEN
import org.cirjson.cirjackson.core.StreamReadConstraints.Companion.DEFAULT_MAX_STRING_LEN
import org.cirjson.cirjackson.core.exception.StreamConstraintsException
import kotlin.math.abs

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

    /**
     * Convenience accessor, basically same as:
     * ```
     * getMaxDocumentLength() > 0L
     * ```
     *
     * Return `True` if this constraints instance has a limit for maximum document length to enforce; `false` otherwise.
     */
    val hasMaxDocumentLength: Boolean
        get() = maxDocumentLength > 0

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

    /**
     * Convenience method that can be used to verify that the document length does not exceed the maximum specified by
     * this constraints object (if any): if it does, a [StreamConstraintsException] is thrown.
     *
     * @param length Current length of processed document content
     *
     * @throws StreamConstraintsException If length exceeds maximum
     */
    @Throws(StreamConstraintsException::class)
    fun validateDocumentLength(length: Long) {
        if (maxDocumentLength in 1L..<length) {
            throw constructException("Document length ($length) exceeds the maximum allowed ($maxDocumentLength, from ${
                constrainRef("maxDocumentLength")
            })")
        }
    }

    /*
     *******************************************************************************************************************
     * Convenience methods for validation, token lengths
     *******************************************************************************************************************
     */

    /**
     * Convenience method that can be used to verify that a floating-point number of specified length does not exceed
     * maximum specified by this constraints object: if it does, a [StreamConstraintsException] is thrown.
     *
     * @param length Length of number in input units
     *
     * @throws StreamConstraintsException If length exceeds maximum
     */
    @Throws(StreamConstraintsException::class)
    fun validateFPLength(length: Int) {
        if (length > maxNumberLength) {
            throw constructException(
                    "Number value length ($length) exceeds the maximum allowed ($maxNumberLength, from ${
                        constrainRef("maxNumberLength")
                    })")
        }
    }

    /**
     * Convenience method that can be used to verify that an integer number of specified length does not exceed maximum
     * specific by this constraints object: if it does, a [StreamConstraintsException] is thrown.
     *
     * @param length Length of number in input units
     *
     * @throws StreamConstraintsException If length exceeds maximum
     */
    @Throws(StreamConstraintsException::class)
    fun validateIntegerLength(length: Int) {
        if (length > maxNumberLength) {
            throw constructException(
                    "Number value length ($length) exceeds the maximum allowed ($maxNumberLength, from ${
                        constrainRef("maxNumberLength")
                    })")
        }
    }

    /**
     * Convenience method that can be used to verify that a String of specified length does not exceed maximum specific
     * by this constraints object: if it does, a [StreamConstraintsException] is thrown.
     *
     * @param length Length of string in input units
     *
     * @throws StreamConstraintsException If length exceeds maximum
     */
    @Throws(StreamConstraintsException::class)
    fun validateStringLength(length: Int) {
        if (length > maxStringLength) {
            throw constructException(
                    "String value length ($length) exceeds the maximum allowed ($maxStringLength, from ${
                        constrainRef("maxStringLength")
                    })")
        }
    }

    /**
     * Convenience method that can be used to verify that a name of specified length does not exceed maximum specific by
     * this constraints object: if it does, a [StreamConstraintsException] is thrown.
     *
     * @param length Length of name in input units
     *
     * @throws StreamConstraintsException If length exceeds maximum
     */
    @Throws(StreamConstraintsException::class)
    fun validateNameLength(length: Int) {
        if (length > maxNameLength) {
            throw constructException("Name length ($length) exceeds the maximum allowed ($maxNameLength, from ${
                constrainRef("maxNameLength")
            })")
        }
    }


    /*
     *******************************************************************************************************************
     * Convenience methods for validation, other
     *******************************************************************************************************************
     */

    /**
     * Convenience method that can be used to verify that a conversion to [java.math.BigInteger] does not have the scale
     * exceed 100k digits: if it does, a [StreamConstraintsException] is thrown.
     *
     * @param scale Scale (possibly negative) of [java.math.BigDecimal] to convert
     *
     * @throws StreamConstraintsException If magnitude (absolute value) of scale exceeds maximum allowed
     */
    @Throws(StreamConstraintsException::class)
    fun validateBigIntegerScale(scale: Int) {
        val absScale = abs(scale)
        val limit = MAX_BIGINT_SCALE_MAGNITUDE

        if (absScale > limit) {
            throw constructException("BigDecimal scale ($scale) magnitude exceeds the maximum allowed ($limit)")
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
        return "`StreamReadConstraints.$method`"
    }

    class Builder(private var myMaxNestingDepth: Int, private var myMaxDocumentLength: Long,
            private var myMaxNumberLength: Int, private var myMaxStringLength: Int, private var myMaxNameLength: Int) {

        constructor() : this(DEFAULT_MAX_DEPTH, DEFAULT_MAX_DOC_LEN, DEFAULT_MAX_NUM_LEN, DEFAULT_MAX_STRING_LEN,
                DEFAULT_MAX_NAME_LEN)

        constructor(src: StreamReadConstraints) : this(src.maxNestingDepth, src.maxDocumentLength, src.maxNumberLength,
                src.maxStringLength, src.maxNameLength)

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

        /**
         * Sets the maximum allowed document length (for positive values over 0) or indicate that any length is
         * acceptable (`0L` or negative number). The length is in input units of the input source, that is, in `byte`s
         * or `char`s.
         *
         * @param maxDocumentLength the maximum allowed document if positive number above 0; otherwise (`0` or negative
         * number) means "unlimited".
         *
         * @return this builder
         */
        fun maxDocumentLength(maxDocumentLength: Long): Builder {
            if (maxDocumentLength <= 0L) {
                myMaxDocumentLength = -1L
            }

            myMaxDocumentLength = maxDocumentLength
            return this
        }

        /**
         * Sets the maximum number length (in chars or bytes, depending on input context). The default is 1000.
         *
         * @param maxNumberLength the maximum number length (in chars or bytes, depending on input context)
         *
         * @return this builder
         *
         * @throws IllegalArgumentException if the maxNumLen is set to a negative value
         */
        fun maxNumberLength(maxNumberLength: Int): Builder {
            if (maxNumberLength < 0) {
                throw IllegalArgumentException("Cannot set maxNumberLength to a negative value")
            }

            myMaxNumberLength = maxNumberLength
            return this
        }

        /**
         * Sets the maximum string length (in chars or bytes, depending on input context). The default is 20,000,000.
         * This limit is not exact, the limit is applied when we increase internal buffer sizes and an exception will
         * happen at sizes greater than this limit. Some text values that are a little bigger than the limit may be
         * treated as valid but no text values with sizes less than or equal to this limit will be treated as invalid.
         *
         * Setting this value to lower than the [maxNumberLength] is not recommended.
         *
         * @param maxStringLength the maximum string length (in chars or bytes, depending on input context)
         *
         * @return this builder
         * @throws IllegalArgumentException if the maxStringLen is set to a negative value
         */
        fun maxStringLength(maxStringLength: Int): Builder {
            if (maxStringLength < 0) {
                throw IllegalArgumentException("Cannot set maxStringLength to a negative value")
            }

            myMaxStringLength = maxStringLength
            return this
        }

        /**
         * Sets the maximum name length (in chars or bytes, depending on input context). The default is 50,000. This
         * limit is not exact, the limit is applied when we increase internal buffer sizes and an exception will happen
         * at sizes greater than this limit. Some text values that are a little bigger than the limit may be treated as
         * valid but no text values with sizes less than or equal to this limit will be treated as invalid.
         *
         * @param maxNameLength the maximum string length (in chars or bytes, depending on input context)
         *
         * @return this builder
         *
         * @throws IllegalArgumentException if the maxStringLen is set to a negative value
         */
        fun maxNameLength(maxNameLength: Int): Builder {
            if (maxNameLength < 0) {
                throw IllegalArgumentException("Cannot set maxNameLength to a negative value")
            }

            myMaxNameLength = maxNameLength
            return this
        }

        fun build(): StreamReadConstraints {
            return StreamReadConstraints(myMaxNestingDepth, myMaxDocumentLength, myMaxNumberLength, myMaxStringLength,
                    myMaxNameLength)
        }

    }

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
        const val DEFAULT_MAX_STRING_LEN: Int = 20_000_000

        /**
         * Default setting for maximum name length: see [Builder.maxNameLength] for details.
         */
        const val DEFAULT_MAX_NAME_LEN: Int = 50_000

        /**
         * Limit for the maximum magnitude of Scale of [java.math.BigDecimal] that can be converted to
         * [java.math.BigInteger].
         *
         * "100k digits ought to be enough for anybody!"
         */
        private const val MAX_BIGINT_SCALE_MAGNITUDE = 100_000

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

        /**
         * @return the default [StreamReadConstraints] (when none is set on the [TokenStreamFactory] explicitly)
         *
         * @see overrideDefaultStreamReadConstraints
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