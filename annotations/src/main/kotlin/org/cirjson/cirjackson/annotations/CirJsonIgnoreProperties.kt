package org.cirjson.cirjackson.annotations

import kotlin.reflect.KClass

/**
 * Annotation that can be used to either suppress serialization of properties (during serialization), or ignore
 * processing of CirJSON properties read (during deserialization).
 *
 * Example:
 * ```
 * // to prevent specified fields from being serialized or deserialized
 * // (i.e. not include in CirJSON output; or being set even if they were included)
 * @CirJsonIgnoreProperties({ "internalId", "secretKey" })
 * // To ignore any unknown properties in CirJSON input without exception:
 * @CirJsonIgnoreProperties(ignoreUnknown=true)
 * ```
 *
 * Annotation can be applied both to classes and to properties. If used for both, actual set will be union of all
 * ignorals: that is, you can only add properties to ignore, not remove or override. So you can not remove properties to
 * ignore using per-property annotation.
 *
 * @property value Names of properties to ignore.
 *
 * @property ignoreUnknown Property that defines whether it is ok to just ignore any unrecognized properties during
 * deserialization. If `true`, all properties that are unrecognized -- that is, there are no setters or creators that
 * accept them -- are ignored without warnings (although handlers for unknown properties, if any, will still be called)
 * without exception.
 *
 * Does not have any effect on serialization.
 *
 * @property allowGetters Property that can be enabled to allow "getters" to be used (that is, prevent ignoral of
 * getters for properties listed in [value]). This is commonly set to support defining "read-only" properties; ones for
 * which there is a getter, but no matching setter: in this case, properties should be ignored for deserialization but
 * NOT serialization. Another way to think about this setting is that setting it to `true` will "disable" ignoring of
 * getters.
 *
 * Default value is `false`, which means that getters with matching names will be ignored.
 *
 * @property allowSetters Property that can be enabled to allow "setters" to be used (that is, prevent ignoral of
 * setters for properties listed in [value]). This could be used to specify "write-only" properties; ones that should
 * not be serialized out, but that may be provided in for deserialization. Another way to think about this setting is
 * that setting it to `true` will "disable" ignoring of setters.
 *
 * Default value is `false`, which means that setters with matching names will be ignored.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonIgnoreProperties(val value: Array<String> = [], val ignoreUnknown: Boolean = false,
        val allowGetters: Boolean = false, val allowSetters: Boolean = false) {

    /**
     * Helper class used to contain information from a single [CirJsonIgnoreProperties] annotation, as well as to
     * provide possible overrides from non-annotation sources.
     */
    class Value(ignored: Set<String>?, val ignoreUnknown: Boolean, val allowGetters: Boolean, val allowSetters: Boolean,
            val merge: Boolean) : CirJacksonAnnotationValue<CirJsonIgnoreProperties> {

        /**
         * Names of properties to ignore.
         */
        val ignored = ignored ?: emptySet()

        /**
         * Mutant factory method that merges values of this value with given override values, so that any explicitly
         * defined inclusion in overrides has precedence over settings of this value instance. If no overrides exist
         * will return `this` instance; otherwise new [Value] with changed inclusion values.
         */
        fun withOverrides(overrides: Value?): Value {
            if (overrides == null || overrides === EMPTY) {
                return this
            }

            if (!overrides.merge) {
                return overrides
            }

            if (equals(this, overrides)) {
                return this
            }

            val ignored = merge(ignored, overrides.ignored)
            val ignoreUnknown = ignoreUnknown || overrides.ignoreUnknown
            val allowGetters = allowGetters || overrides.allowGetters
            val allowSetters = allowSetters || overrides.allowSetters
            return construct(ignored, ignoreUnknown, allowGetters, allowSetters, true)
        }

        fun withIgnored(ignored: Set<String>?): Value {
            return construct(ignored, ignoreUnknown, allowGetters, allowSetters, merge)
        }

        fun withIgnored(vararg ignored: String): Value {
            return construct(asSet(ignored), ignoreUnknown, allowGetters, allowSetters, merge)
        }

        fun withoutIgnored(): Value {
            return construct(null, ignoreUnknown, allowGetters, allowSetters, merge)
        }

        fun withIgnoreUnknown(): Value {
            if (ignoreUnknown) {
                return this
            }

            return construct(ignored, true, allowGetters, allowSetters, merge)
        }

        fun withoutIgnoreUnknown(): Value {
            if (!ignoreUnknown) {
                return this
            }

            return construct(ignored, false, allowGetters, allowSetters, merge)
        }

        fun withAllowGetters(): Value {
            if (allowGetters) {
                return this
            }

            return construct(ignored, ignoreUnknown, true, allowSetters, merge)
        }

        fun withoutAllowGetters(): Value {
            if (!allowGetters) {
                return this
            }

            return construct(ignored, ignoreUnknown, false, allowSetters, merge)
        }

        fun withAllowSetters(): Value {
            if (allowSetters) {
                return this
            }

            return construct(ignored, ignoreUnknown, allowGetters, true, merge)
        }

        fun withoutAllowSetters(): Value {
            if (!allowSetters) {
                return this
            }

            return construct(ignored, ignoreUnknown, allowGetters, false, merge)
        }

        fun withMerge(): Value {
            if (merge) {
                return this
            }

            return construct(ignored, ignoreUnknown, allowGetters, allowSetters, true)
        }

        fun withoutMerge(): Value {
            if (!merge) {
                return this
            }

            return construct(ignored, ignoreUnknown, allowGetters, allowSetters, false)
        }

        override fun valueFor(): KClass<CirJsonIgnoreProperties> {
            return CirJsonIgnoreProperties::class
        }

        /**
         * Method called to find names of properties to ignore when used for serialization: functionally same as
         * [ignored] if [allowGetters] is `false` (that is, there is "allowGetters=false" or equivalent), otherwise
         * returns empty [Set].
         */
        fun findIgnoredForSerialization(): Set<String> {
            if (allowGetters) {
                return emptySet()
            }

            return ignored
        }

        /**
         * Method called to find names of properties to ignore when used for deserialization: functionally same as
         * [ignored] if [allowSetters] is false (that is, there is "allowSetters=false" or equivalent), otherwise
         * returns empty [Set].
         */
        fun findIgnoredForDeserialization(): Set<String> {
            if (allowSetters) {
                return emptySet()
            }

            return ignored
        }

        override fun toString(): String {
            return "CirJsonIgnoreProperties.Value(ignored=$ignored,ignoreUnknown=$ignoreUnknown,allowGetters=$allowGetters,allowSetters=$allowSetters,merge=$merge)"
        }

        override fun hashCode(): Int {
            return ignored.size + (if (ignoreUnknown) 1 else -3) + (if (allowGetters) 3 else -7) +
                    (if (allowSetters) 7 else -11) + (if (merge) 11 else -13)
        }

        override fun equals(other: Any?): Boolean {
            if (super.equals(other)) {
                return true
            }

            if (other !is Value) {
                return false
            }

            return equals(this, other)
        }

        companion object {

            /**
             * Default instance has no explicitly ignored fields, does not ignore unknowns, does not explicitly allow
             * getters/setters (that is, ignorals apply to both), but does use merging for combining overrides with base
             * settings
             */
            val EMPTY =
                    Value(emptySet(), ignoreUnknown = false, allowGetters = false, allowSetters = false, merge = true)

            fun from(annotation: CirJsonIgnoreProperties?): Value {
                annotation ?: return EMPTY
                return construct(asSet(annotation.value), annotation.ignoreUnknown, annotation.allowGetters,
                        annotation.allowSetters, false)
            }

            /**
             * Factory method that may be used (although is NOT the recommended way) to construct an instance from a
             * full set of properties. Most users would be better off starting by [EMPTY] instance and using
             * `withXxx()`/`withoutXxx()` methods, as this factory method may need to be changed if new properties are
             * added in [CirJsonIgnoreProperties] annotation.
             */
            fun construct(ignored: Set<String>?, ignoreUnknown: Boolean, allowGetters: Boolean, allowSetters: Boolean,
                    merge: Boolean): Value {
                if (isEmptyValue(ignored, ignoreUnknown, allowGetters, allowSetters, merge)) {
                    return EMPTY
                }

                return Value(ignored, ignoreUnknown, allowGetters, allowSetters, merge)
            }

            /**
             * Helper method that will try to combine values from two [Value] instances, using one as base settings, and
             * the other as overrides to use instead of base values when defined; base values are only use if override
             * does not specify a value (matching value is null or logically missing). Note that one or both of value
             * instances may be `null` directly; if both are `null`, result will also be `null`; otherwise never `null`.
             */
            fun merge(base: Value?, overrides: Value?): Value? {
                return base?.withOverrides(overrides) ?: overrides
            }

            fun mergeAll(vararg values: Value?): Value? {
                var result: Value? = null

                for (value in values) {
                    if (value != null) {
                        result = result?.withOverrides(value) ?: value
                    }
                }

                return result
            }

            fun forIgnoredProperties(ignored: Set<String>?): Value {
                return EMPTY.withIgnored(ignored)
            }

            fun forIgnoredProperties(vararg ignored: String): Value {
                if (ignored.isEmpty()) {
                    return EMPTY
                }

                return EMPTY.withIgnored(asSet(ignored))
            }

            fun forIgnoreUnknown(state: Boolean): Value {
                return if (state) EMPTY.withIgnoreUnknown() else EMPTY.withoutIgnoreUnknown()
            }

            private fun asSet(value: Array<out String>?): Set<String> {
                if (value.isNullOrEmpty()) {
                    return emptySet()
                }

                val set = HashSet<String>()
                set.addAll(value)
                return set
            }

            private fun equals(value1: Value, value2: Value): Boolean {
                return value1.ignoreUnknown == value2.ignoreUnknown && value1.merge == value2.merge &&
                        value1.allowGetters == value2.allowGetters && value1.allowSetters == value2.allowSetters &&
                        value1.ignored == value2.ignored
            }

            private fun merge(set1: Set<String>, set2: Set<String>): Set<String> {
                if (set1.isEmpty()) {
                    return set2
                }

                if (set2.isEmpty()) {
                    return set1
                }

                val result = HashSet<String>(set1.size + set2.size)
                result.addAll(set1)
                result.addAll(set2)
                return result
            }

            private fun isEmptyValue(ignored: Set<String>?, ignoreUnknown: Boolean, allowGetters: Boolean,
                    allowSetters: Boolean, merge: Boolean): Boolean {
                return ignored.isNullOrEmpty() && !ignoreUnknown && !allowGetters && !allowSetters && merge
            }

        }

    }

}