package org.cirjson.cirjackson.annotations

import kotlin.reflect.KClass

/**
 * Annotation that can be used to define a non-static, single-argument method to be used as a "setter" for a logical
 * property as an alternative to recommended [CirJsonProperty] annotation; or, specify additional aspects of the
 * assigning property a value during serialization.
 *
 * @property value Optional default argument that defines logical property this method is used to modify ("set"); this
 * is the property name used in JSON content.
 *
 * @property nulls Specifies action to take when input contains explicit `null` value (if format has one). Default
 * action, in absence of any explicit configuration, is usually [Nulls.SET], meaning that the `null` is set as value
 * using setter.
 *
 * NOTE: is not usually used in case property value is missing, unless data format specifies that there is defaulting
 * which would result in an explicit `null` assignment.
 *
 * @property contentNulls Specifies action to take when input to match into content value (of a [Collection], [Map],
 * array, or referential value) contains explicit `null` value (if format has one) to bind. Default action, in absence
 * of any explicit configuration, is usually [Nulls.SET], meaning that the `null` is included as usual.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY,
        AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@CirJacksonAnnotation
annotation class CirJsonSetter(val value: String = "", val nulls: Nulls = Nulls.DEFAULT,
        val contentNulls: Nulls = Nulls.DEFAULT) {

    /**
     * Helper class used to contain information from a single [CirJsonSetter] annotation, as well as to provide possible
     * overrides from non-annotation sources.
     */
    class Value private constructor(val nulls: Nulls, val contentNulls: Nulls) :
            CirJacksonAnnotationValue<CirJsonSetter> {

        /**
         * Mutant factory method that merges values of this value with given override values, so that any explicitly
         * defined inclusion in overrides has precedence over settings of this value instance. If no overrides exist
         * will return `this` instance; otherwise new [Value] with changed inclusion values.
         */
        fun withOverrides(overrides: Value?): Value {
            if (overrides == null || overrides === EMPTY) {
                return this
            }

            val nulls = overrides.nulls.takeUnless { it == Nulls.DEFAULT } ?: nulls
            val contentNulls = overrides.contentNulls.takeUnless { it == Nulls.DEFAULT } ?: contentNulls

            if (nulls == this.nulls && contentNulls == this.contentNulls) {
                return this
            }

            return construct(nulls, contentNulls)
        }

        fun withNulls(nulls: Nulls?): Value {
            val realNulls = nulls ?: Nulls.DEFAULT

            if (realNulls == this.nulls) {
                return this
            }

            return construct(realNulls, contentNulls)
        }

        fun withContentNulls(contentNulls: Nulls?): Value {
            val realContentNulls = contentNulls ?: Nulls.DEFAULT

            if (realContentNulls == this.contentNulls) {
                return this
            }

            return construct(nulls, realContentNulls)
        }

        fun with(nulls: Nulls?, contentNulls: Nulls?): Value {
            val realNulls = nulls ?: Nulls.DEFAULT
            val realContentNulls = contentNulls ?: Nulls.DEFAULT

            if (realNulls == this.nulls && realContentNulls == this.contentNulls) {
                return this
            }

            return construct(realNulls, realContentNulls)
        }

        /**
         * Returns same as [nulls] unless value would be [Nulls.DEFAULT], in which case `null` is returned.
         */
        fun nonDefaultNulls(): Nulls? {
            return nulls.takeUnless { it == Nulls.DEFAULT }
        }

        /**
         * Returns same as [contentNulls] unless value would be [Nulls.DEFAULT], in which case `null` is returned.
         */
        fun nonDefaultContentNulls(): Nulls? {
            return contentNulls.takeUnless { it == Nulls.DEFAULT }
        }

        override fun valueFor(): KClass<CirJsonSetter> {
            return CirJsonSetter::class
        }

        override fun toString(): String {
            return "CirJsonSetter.Value(nulls=$nulls,contentNulls=$contentNulls)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }

            if (other !is Value) {
                return false
            }

            return nulls == other.nulls && contentNulls == other.contentNulls
        }

        override fun hashCode(): Int {
            return nulls.ordinal + (contentNulls.ordinal shl 2)
        }

        companion object {

            /**
             * Default instance used in place of "default settings".
             */
            val EMPTY = Value(Nulls.DEFAULT, Nulls.DEFAULT)

            /**
             * Factory method that may be used (although is NOT the recommended way) to construct an instance from a
             * full set of properties. Most users would be better off starting by [EMPTY] instance and using
             * `withXxx`/`withoutXxx` methods, as this factory method may need to be changed if new properties are added
             * in [CirJsonIgnoreProperties] annotation.
             */
            fun construct(nulls: Nulls?, contentNulls: Nulls?): Value {
                val realNulls = nulls ?: Nulls.DEFAULT
                val realContentNulls = contentNulls ?: Nulls.DEFAULT

                if (isEmpty(realNulls, realContentNulls)) {
                    return EMPTY
                }

                return Value(realNulls, realContentNulls)
            }

            fun from(src: CirJsonSetter?): Value {
                src ?: return EMPTY
                return construct(src.nulls, src.contentNulls)
            }

            /**
             * Helper method that will try to combine values from two [Value] instances, using one as base settings, and
             * the other as overrides to use instead of base values when defined; base values are only use if override
             * does not specify a value (matching value is `null` or logically missing). Note that one or both of value
             * instances may be `null`, directly; if both are `null`, result will also be `null`; otherwise never
             * `null`.
             */
            fun merge(base: Value?, overrides: Value?): Value? {
                return base?.withOverrides(overrides) ?: overrides
            }

            fun forNulls(nulls: Nulls?): Value {
                return construct(nulls, Nulls.DEFAULT)
            }

            fun forContentNulls(contentNulls: Nulls?): Value {
                return construct(Nulls.DEFAULT, contentNulls)
            }

            private fun isEmpty(nulls: Nulls, contentNulls: Nulls): Boolean {
                return nulls == Nulls.DEFAULT && contentNulls == Nulls.DEFAULT
            }

        }

    }

}
