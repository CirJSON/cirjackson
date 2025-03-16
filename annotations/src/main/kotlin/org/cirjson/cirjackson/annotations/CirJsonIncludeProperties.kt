package org.cirjson.cirjackson.annotations

import kotlin.reflect.KClass

/**
 * Annotation that can be used to either only include serialization of properties (during serialization), or only
 * include processing of CirJSON properties read (during deserialization).
 *
 * Example:
 * ```
 * // to only include specified fields from being serialized or deserialized
 * // (i.e. only include in CirJSON output; or being set even if they were included)
 * @CirJsonIncludeProperties(["internalId", "secretKey"])
 * ```
 *
 * Annotation can be applied both to classes and to properties. If used for both, actual set will be union of all
 * includes: that is, you can only add properties to include, not remove or override. So you can not remove properties
 * to include using per-property annotation.
 *
 * @property value Names of properties to include.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD,
        AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonIncludeProperties(val value: Array<String> = []) {

    /**
     * Helper class used to contain information from a single [CirJsonIncludeProperties] annotation, as well as to
     * provide possible overrides from non-annotation sources.
     *
     * @property included Name of the properties to include. `null` means that all properties are included, empty means
     * none.
     */
    class Value private constructor(val included: Set<String>?) : CirJacksonAnnotationValue<CirJsonIncludeProperties> {

        /**
         * Mutant factory method to override the current value with another, merging the included fields so that only
         * entries that exist in both original and override set are included, taking into account that "undefined"
         * [Values][Value] do not count ("undefined" meaning that `included` returns `null`). So: overriding with
         * "undefined" returns original `Value` as-is; overriding an "undefined" `Value` returns override `Value` as-is.
         */
        fun withOverrides(overrides: Value?): Value {
            if (overrides?.included == null) {
                return this
            }

            included ?: return overrides

            val toInclude = HashSet<String>()

            for (inc in overrides.included) {
                if (included.contains(inc)) {
                    toInclude.add(inc)
                }
            }

            return Value(toInclude)
        }

        override fun valueFor(): KClass<CirJsonIncludeProperties> {
            return CirJsonIncludeProperties::class
        }

        override fun toString(): String {
            return "CirJsonIncludeProperties.Value(included=$included)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }

            if (other !is Value) {
                return false
            }

            return included == other.included
        }

        override fun hashCode(): Int {
            return included?.size ?: 0
        }

        companion object {

            /**
             * Default instance has no explicitly included fields
             */
            val ALL = Value(null)

            fun from(src: CirJsonIncludeProperties?): Value {
                src ?: return ALL
                return Value(asSet(src.value))
            }

            fun merge(base: Value?, overrides: Value?): Value? {
                return base?.withOverrides(overrides) ?: overrides
            }

            private fun asSet(values: Array<String>?): Set<String> {
                if (values.isNullOrEmpty()) {
                    return emptySet()
                }

                val set = HashSet<String>(values.size)

                for (v in values) {
                    set.add(v)
                }

                return set
            }

        }

    }

}
