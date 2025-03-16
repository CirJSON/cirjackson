package org.cirjson.cirjackson.annotations

import org.cirjson.cirjackson.annotations.CirJsonInclude.Include
import kotlin.reflect.KClass

/** Annotation used to indicate when value of the annotated property (when used for a field, method or constructor
 *  parameter), or all properties of the annotated class, is to be serialized. Without annotation property values are
 *  always included, but by using this annotation one can specify simple exclusion rules to reduce amount of properties
 *  to write out.
 *
 * Note that the main inclusion criteria (one annotated with [value]) is checked on **object level**, for the annotated
 * type, and **NOT** on CirJSON output -- so even with [Include.NON_NULL] it is possible that CirJSON `null` values are
 * output, if object reference in question is not `null`. An example is [java.util.concurrent.atomic.AtomicReference]
 * instance constructed to reference `null` value: such a value would be serialized as CirJSON null, and not filtered
 * out.
 *
 * To base inclusion on value of contained value(s), you will typically also need to specify [content] annotation; for
 * example, specifying only [value] as [Include.NON_EMPTY] for a [Map] would exclude `Map`s with no values, but would
 * include `Map`s with `null` values. To exclude Map with only `null` value, you would use both annotations like so:
 * ```
 * class Bean {
 *     @CirJsonInclude(value=Include.NON_EMPTY, content=Include.NON_NULL)
 *     val entries: Map<String, String>
 * }
 * ```
 * Similarly, you could exclude `Map`s that only contain "empty" elements, or "non-default" values (see
 * [Include.NON_EMPTY] and [Include.NON_DEFAULT] for more details).
 *
 * In addition to `Map`s, `content` concept is also supported for referential types (like
 * [java.util.concurrent.atomic.AtomicReference]). Note that `content` is NOT currently supported for arrays or
 * [Collections][Collection], but support may be added in future versions.
 *
 * @property value Inclusion rule to use for instances (values) of types (Classes) or properties annotated; defaults to
 * [Include.ALWAYS].
 *
 * @property content Inclusion rule to use for entries ("content") of annotated [Maps][Map] and referential types (like
 * [java.util.concurrent.atomic.AtomicReference]); defaults to [Include.ALWAYS].
 *
 * @property valueFilter Specifies type of "Filter Object" to use in case [value] is [Include.CUSTOM]: if so, an
 * instance is created by calling `HandlerInstantiator` (of  `ObjectMapper`), which by default simply calls
 * zero-argument constructor of the Filter Class.
 *
 * Whether the value is to be included or not is determined by calling Filter's `equals(value)` method: if it returns
 * `true` value is NOT included (it is "filtered out"); if `false` value IS included ("not filtered out").
 *
 * @property contentFilter Specifies type of "Filter Object" to use in case [content] is [Include.CUSTOM]: if so, an
 * instance is created by calling  `HandlerInstantiator` (of  `ObjectMapper`), which by default simply calls
 * zero-argument constructor of the Filter Class.
 *
 * Whether the content value is to be included or not is determined by calling Filter's `equals(value)` method: if it
 * returns `true` content value is NOT included (it is "filtered out"); if `false` content value IS included ("not
 * filtered out").
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD,
        AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonInclude(val value: Include = Include.USE_DEFAULTS, val content: Include = Include.USE_DEFAULTS,
        val valueFilter: KClass<*> = Nothing::class, val contentFilter: KClass<*> = Nothing::class) {

    /**
     * Enumeration used with [CirJsonInclude] to define which properties of Beans are to be included in serialization.
     */
    enum class Include {

        /**
         * Value that indicates that property is to be always included, independent of value of the property.
         */
        ALWAYS,

        /**
         * Value that indicates that only properties with non-`null` values are to be included.
         */
        NON_NULL,

        /**
         * Value that indicates that properties are included unless their value is:
         *
         * * `null`
         *
         * * "absent" value of a referential type (like `Optional`, or [java.util.concurrent.atomic.AtomicReference]);
         * that is, something that would not deference to a non-`null` value.
         *
         * This option is mostly used to work with "Optional"s (Java 8, Guava).
         */
        NON_ABSENT,

        /**
         * Value that indicates that only properties with `null` value, or what is considered empty, are not to be
         * included. Definition of emptiness is data type specific; see below for details on actual handling.
         *
         * Default emptiness for all types includes:
         *
         * * `null` values.
         *
         * * "Absent" values (see [NON_ABSENT])
         *
         * so that as baseline, "empty" set includes values that would be excluded by both [NON_NULL] and [NON_ABSENT].
         *
         * Beyond this base, following types have additional empty values:
         *
         * * For [Collections][Collection] and [Maps][Map], method `isEmpty()` is called;
         *
         * For arrays, empty arrays are ones with length of `0`
         *
         * For [Strings][String], `length` is called, and return value of `0` indicates empty `String`
         *
         * and for other types, `null` values are excluded but other exclusions (if any).
         *
         * Note that this default handling can be overridden by custom `CirJsonSerializer` implementation: if method
         * `isEmpty()` is overridden, it will be called to see if non-`null` values are considered empty (`null` is
         * always considered empty).
         */
        NON_EMPTY,

        /**
         * Meaning of this setting depends on context: whether annotation is specified for POJO type (class), or not. In
         * latter case annotation is either used as the global default, or as property override.
         *
         * When used for a POJO, definition is that only values that differ from the default values of POJO properties
         * are included. This is done by creating an instance of POJO using zero-argument constructor, and accessing
         * property values: value is used as the default value by using `equals()` method, except for the case where
         * property has `null` value in which case straight null check is used.
         *
         * When NOT used for a POJO (that is, as a global default, or as property override), definition is such that:
         *
         * * All values considered "empty" (as per [NON_EMPTY]) are excluded
         *
         * * Primitive/wrapper default values are excluded
         *
         * * Date/time values that have timestamp (`long` value of milliseconds since epoch, see [java.util.Date]) of
         * `0L` are excluded
         */
        NON_DEFAULT,

        /**
         * Value that indicates that separate `filter` Object (specified by [CirJsonInclude.valueFilter] for value
         * itself, and/or [CirJsonInclude.contentFilter] for contents of structured types) is to be used for determining
         * inclusion criteria. Filter object's `equals(value)` method is called with value to serialize; if it returns
         * `true` value is **excluded** (that is, filtered out); if `false` value is **included**.
         *
         * NOTE: the filter will be called for every value, including `null` values.
         */
        CUSTOM,

        /**
         * Pseudo-value used to indicate that the higher-level defaults make sense, to avoid overriding inclusion value.
         * For example, if returned for a property this would use defaults for the class that contains property, if any
         * defined; and if none defined for that, then global serialization inclusion details.
         */
        USE_DEFAULTS

    }

    /**
     * Helper class used to contain information from a single [CirJsonInclude] annotation.
     */
    class Value private constructor(valueInclusion: Include?, contentInclusion: Include?, valueFilter: KClass<*>?,
            contentFilter: KClass<*>?) : CirJacksonAnnotationValue<CirJsonInclude> {

        val valueInclusion = valueInclusion ?: Include.USE_DEFAULTS

        val contentInclusion = contentInclusion ?: Include.USE_DEFAULTS

        val valueFilter = valueFilter?.takeUnless { it == Nothing::class }

        val contentFilter = contentFilter?.takeUnless { it == Nothing::class }

        constructor(src: CirJsonInclude) : this(src.value, src.content, src.valueFilter, src.contentFilter)

        fun withOverrides(overrides: Value?): Value {
            if (overrides == null || overrides === EMPTY) {
                return this
            }

            val valueInclusion = overrides.valueInclusion
            val contentInclusion = overrides.contentInclusion
            val valueFilter = overrides.valueFilter
            val contentFilter = overrides.contentFilter

            val valueInclusionDifferent =
                    valueInclusion != this.valueInclusion && valueInclusion != Include.USE_DEFAULTS
            val contentInclusionDifferent =
                    contentInclusion != this.contentInclusion && contentInclusion != Include.USE_DEFAULTS
            val filterDifferent = valueFilter != this.valueFilter && contentFilter != this.contentFilter

            return if (valueInclusionDifferent) {
                if (contentInclusionDifferent) {
                    Value(valueInclusion, contentInclusion, valueFilter, contentFilter)
                } else {
                    Value(valueInclusion, this.contentInclusion, valueFilter, contentFilter)
                }
            } else if (contentInclusionDifferent) {
                Value(this.valueInclusion, contentInclusion, valueFilter, contentFilter)
            } else if (filterDifferent) {
                Value(this.valueInclusion, this.contentInclusion, valueFilter, contentFilter)
            } else {
                this
            }
        }

        fun withValueInclusion(valueInclusion: Include?): Value {
            if (valueInclusion == this.valueInclusion) {
                return this
            }

            return Value(valueInclusion, contentInclusion, valueFilter, contentFilter)
        }

        fun withContentInclusion(contentInclusion: Include?): Value {
            if (contentInclusion == this.contentInclusion) {
                return this
            }

            return Value(valueInclusion, contentInclusion, valueFilter, contentFilter)
        }

        /**
         * Mutant factory that will either
         *
         * * Set `valueInclusion` as [Include.CUSTOM] and `valueFilter` to `filter` (if filter not `null`); or
         *
         * * Set `valueInclusion` as [Include.USE_DEFAULTS] (if filter `null`)
         */
        fun withValueFilter(filter: KClass<*>?): Value {
            var valueFilter = filter

            val valueInclusion = if (valueFilter == null || valueFilter == Nothing::class) {
                valueFilter = null
                Include.USE_DEFAULTS
            } else {
                Include.CUSTOM
            }

            return construct(valueInclusion, contentInclusion, valueFilter, contentFilter)
        }

        /**
         * Mutant factory that will either
         *
         * * Set `contentInclusion` as [Include.CUSTOM] and `contentFilter` to `filter` (if filter not `null`); or
         *
         * * Set `contentInclusion` as [Include.USE_DEFAULTS] (if filter `null`)
         */
        fun withContentFilter(filter: KClass<*>?): Value {
            var contentFilter = filter

            val contentInclusion = if (contentFilter == null || contentFilter == Nothing::class) {
                contentFilter = null
                Include.USE_DEFAULTS
            } else {
                Include.CUSTOM
            }

            return construct(valueInclusion, contentInclusion, valueFilter, contentFilter)
        }

        override fun valueFor(): KClass<CirJsonInclude> {
            return CirJsonInclude::class
        }

        override fun toString(): String {
            val stringBuilder = StringBuilder(90)

            stringBuilder.append("CirJsonInclude.Value(value=").append(valueInclusion).append(",content=")
                    .append(contentInclusion)

            if (valueFilter != null) {
                stringBuilder.append(",valueFilter=").append(valueFilter.java.name).append("::class")
            }

            if (contentFilter != null) {
                stringBuilder.append(",contentFilter=").append(contentFilter.java.name).append("::class")
            }

            stringBuilder.append(')')

            return stringBuilder.toString()
        }

        override fun hashCode(): Int {
            return (valueInclusion.hashCode() shl 2) + contentInclusion.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }

            if (other !is Value) {
                return false
            }

            return valueInclusion == other.valueInclusion && contentInclusion == other.contentInclusion &&
                    valueFilter == other.valueFilter && contentFilter == other.contentFilter
        }

        companion object {

            val EMPTY = Value(Include.USE_DEFAULTS, Include.USE_DEFAULTS, null, null)

            /**
             * Helper method that will try to combine values from two [Value] instances, using one as base settings, and
             * the other as overrides to use instead of base values when defined; base values are only use if override
             * does not specify a value (matching value is `null` or logically missing).
             *
             * Note that one or both of value instances may be `null`, directly; if both are `null`, result will also be
             * `null`; otherwise never `null`.
             */
            fun merge(base: Value?, overrides: Value?): Value? {
                return base?.withOverrides(overrides) ?: overrides
            }

            /**
             * Factory method to use for constructing an instance for components
             */
            fun construct(valueInclusion: Include?, contentInclusion: Include?): Value {
                if ((valueInclusion == null || valueInclusion == Include.USE_DEFAULTS) &&
                        (contentInclusion == null || contentInclusion == Include.USE_DEFAULTS)) {
                    return EMPTY
                }

                return Value(valueInclusion, contentInclusion, null, null)
            }

            /**
             * Factory method to use for constructing an instance for components
             */
            fun construct(valueInclusion: Include?, contentInclusion: Include?, valueFilter: KClass<*>?,
                    contentFilter: KClass<*>?): Value {
                val realValueFilter = valueFilter?.takeUnless { it == Nothing::class }
                val realContentFilter = contentFilter?.takeUnless { it == Nothing::class }

                if ((valueInclusion == null || valueInclusion == Include.USE_DEFAULTS) &&
                        (contentInclusion == null || contentInclusion == Include.USE_DEFAULTS) &&
                        realValueFilter != null && realContentFilter != null) {
                    return EMPTY
                }

                return Value(valueInclusion, contentInclusion, realValueFilter, realContentFilter)
            }

            /**
             * Factory method to use for constructing an instance from instance of [CirJsonInclude]
             */
            fun from(src: CirJsonInclude?): Value {
                src ?: return EMPTY

                val valueInclusion = src.value
                val contentInclusion = src.content

                if (valueInclusion == Include.USE_DEFAULTS && contentInclusion == Include.USE_DEFAULTS) {
                    return EMPTY
                }

                val valueFilter = src.valueFilter.takeUnless { it == Nothing::class }
                val contentFilter = src.contentFilter.takeUnless { it == Nothing::class }

                return Value(valueInclusion, contentInclusion, valueFilter, contentFilter)
            }

        }

    }

}
