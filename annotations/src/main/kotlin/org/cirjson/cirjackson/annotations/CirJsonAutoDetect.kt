package org.cirjson.cirjackson.annotations

import org.cirjson.cirjackson.annotations.CirJsonAutoDetect.Visibility
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KVisibility

/**
 * Class annotation that can be used to define which kinds of Methods are to be detected by auto-detection, and with
 * what minimum access level. Auto-detection means using name conventions and/or signature templates to find methods to
 * use for data binding. For example, so-called "getters" can be auto-detected by looking for public member methods that
 * return a value, do not take argument, and have prefix "get" in their name.
 *
 * Default setting for all accessors is [Visibility.DEFAULT], which in turn means that the global defaults are used.
 * Defaults are different for different accessor types (getters need to be public; setters can have any access modifier,
 * for example). If you assign different [Visibility] type then it will override global defaults: for example, to
 * require that all setters must be public, you would use:
 *
 * ```
 * @CirJsonAutoDetect(setterVisibility=Visibility.PUBLIC_ONLY)
 * ```
 *
 * @param fieldVisibility Minimum visibility required for auto-detecting member fields.
 *
 * @param getterVisibility Minimum visibility required for auto-detecting regular getter methods.
 *
 * @param isGetterVisibility Minimum visibility required for auto-detecting is-getter methods.
 *
 * @param setterVisibility Minimum visibility required for auto-detecting setter methods.
 *
 * @param creatorVisibility Minimum visibility required for auto-detecting Creator methods, except for no-argument
 * constructors (which are always detected no matter what), and single-scalar-argument Creators for which there is
 * separate setting.
 *
 * @param scalarConstructorVisibility Minimum visibility required for auto-detecting single-scalar-argument
 * constructors, as distinct from "regular" creators (see [creatorVisibility]). Specifically a small set of scalar types
 * is allowed; see [PropertyAccessor.SCALAR_CONSTRUCTOR] for list.
 *
 * Default value is more permissive than that of general Creators: all non-private scalar-constructors are detected by
 * default.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonAutoDetect(val fieldVisibility: Visibility = Visibility.DEFAULT,
        val getterVisibility: Visibility = Visibility.DEFAULT,
        val isGetterVisibility: Visibility = Visibility.DEFAULT, val setterVisibility: Visibility = Visibility.DEFAULT,
        val creatorVisibility: Visibility = Visibility.DEFAULT,
        val scalarConstructorVisibility: Visibility = Visibility.DEFAULT) {

    /**
     * Enumeration for possible visibility thresholds (minimum visibility) that can be used to limit which methods
     * (and fields) are auto-detected.
     */
    enum class Visibility {

        /**
         * Value that means that all kinds of access modifiers are acceptable, from private to public.
         */
        ANY,

        /**
         * Value that means that any other access modifier other than 'private' is considered auto-detectable.
         */
        NON_PRIVATE,

        /**
         * Value that means access modifiers 'protected' and 'public' are auto-detectable (and 'private' and "package
         * access" == no modifiers are not
         */
        PROTECTED_AND_PUBLIC,

        /**
         * Value to indicate that only 'public' access modifier is considered auto-detectable.
         */
        PUBLIC_ONLY,

        /**
         * Value that indicates that no access modifiers are auto-detectable: this can be used to explicitly disable
         * auto-detection for specified types.
         */
        NONE,

        /**
         * Value that indicates that default visibility level (whatever it is, depends on context) is to be used. This
         * usually means that inherited value (from parent visibility settings) is to be used.
         */
        DEFAULT;

        fun <V> isVisible(member: KProperty<V>): Boolean {
            return when (this) {
                ANY -> true

                NON_PRIVATE -> member.visibility != KVisibility.PRIVATE

                PROTECTED_AND_PUBLIC, PUBLIC_ONLY -> this == PROTECTED_AND_PUBLIC &&
                        member.visibility == KVisibility.PROTECTED || member.visibility == KVisibility.PUBLIC

                NONE, DEFAULT -> false
            }
        }

    }

    /**
     * Helper class used to contain information from a single [CirJsonAutoDetect] annotation, as well as to provide
     * possible overrides from non-annotation sources.
     */
    class Value(val fieldVisibility: Visibility, val getterVisibility: Visibility, val isGetterVisibility: Visibility,
            val setterVisibility: Visibility, val creatorVisibility: Visibility,
            val scalarConstructorVisibility: Visibility) : CirJacksonAnnotationValue<CirJsonAutoDetect> {

        fun withFieldVisibility(visibility: Visibility): Value {
            return construct(visibility, getterVisibility, isGetterVisibility, setterVisibility, creatorVisibility,
                    scalarConstructorVisibility)
        }

        fun withGetterVisibility(visibility: Visibility): Value {
            return construct(fieldVisibility, visibility, isGetterVisibility, setterVisibility, creatorVisibility,
                    scalarConstructorVisibility)
        }

        fun withIsGetterVisibility(visibility: Visibility): Value {
            return construct(fieldVisibility, getterVisibility, visibility, setterVisibility, creatorVisibility,
                    scalarConstructorVisibility)
        }

        fun withSetterVisibility(visibility: Visibility): Value {
            return construct(fieldVisibility, getterVisibility, isGetterVisibility, visibility, creatorVisibility,
                    scalarConstructorVisibility)
        }

        fun withCreatorVisibility(visibility: Visibility): Value {
            return construct(fieldVisibility, getterVisibility, isGetterVisibility, setterVisibility, visibility,
                    scalarConstructorVisibility)
        }

        fun withScalarConstructorVisibility(visibility: Visibility): Value {
            return construct(fieldVisibility, getterVisibility, isGetterVisibility, setterVisibility, creatorVisibility,
                    visibility)
        }

        fun withOverrides(overrides: Value?): Value {
            overrides ?: return this

            if (overrides === NO_OVERRIDES || overrides === this) {
                return this
            }

            val fieldVisibility = override(fieldVisibility, overrides.fieldVisibility)
            val getterVisibility = override(getterVisibility, overrides.getterVisibility)
            val isGetterVisibility = override(isGetterVisibility, overrides.isGetterVisibility)
            val setterVisibility = override(setterVisibility, overrides.setterVisibility)
            val creatorVisibility = override(creatorVisibility, overrides.creatorVisibility)
            val scalarConstructorVisibility =
                    override(scalarConstructorVisibility, overrides.scalarConstructorVisibility)

            if (equals(this, fieldVisibility, getterVisibility, isGetterVisibility, setterVisibility, creatorVisibility,
                            scalarConstructorVisibility)) {
                return this
            }

            return construct(fieldVisibility, getterVisibility, isGetterVisibility, setterVisibility, creatorVisibility,
                    scalarConstructorVisibility)
        }

        override fun valueFor(): KClass<CirJsonAutoDetect> {
            return CirJsonAutoDetect::class
        }

        fun readResolve(): Any {
            return predefined(fieldVisibility, getterVisibility, isGetterVisibility, setterVisibility,
                    creatorVisibility, scalarConstructorVisibility) ?: this
        }

        override fun toString(): String {
            return "CirJsonAutoDetect.Value(fieldVisibility=$fieldVisibility,getterVisibility=$getterVisibility,isGetterVisibility=$isGetterVisibility,setterVisibility=$setterVisibility,creatorVisibility=$creatorVisibility,scalarConstructorVisibility=$scalarConstructorVisibility)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Value

            return equals(this, other.fieldVisibility, other.getterVisibility, other.isGetterVisibility,
                    other.setterVisibility, other.creatorVisibility, other.scalarConstructorVisibility)
        }

        override fun hashCode(): Int {
            return 1 + fieldVisibility.ordinal xor
                    3 * getterVisibility.ordinal - 7 * isGetterVisibility.ordinal + 11 * setterVisibility.ordinal xor
                    13 * creatorVisibility.ordinal + 17 * scalarConstructorVisibility.ordinal
        }

        companion object {

            private val DEFAULT_FIELD_VISIBILITY = Visibility.PUBLIC_ONLY

            /**
             * Default instance with baseline visibility checking:
             *
             * * Only public fields visible
             *
             * * Only public getters, is-getters visible
             *
             * * All setters (regardless of access) visible
             *
             * * Only public Creators visible (except see below)
             *
             * * All non-private single-scalar constructors are visible
             */
            val DEFAULT = Value(DEFAULT_FIELD_VISIBILITY, Visibility.PUBLIC_ONLY, Visibility.PUBLIC_ONLY,
                    Visibility.ANY, Visibility.PUBLIC_ONLY, Visibility.NON_PRIVATE)

            /**
             * Empty instance that specifies no overrides, that is, all visibility levels set as [Visibility.DEFAULT].
             */
            val NO_OVERRIDES = Value(Visibility.DEFAULT, Visibility.DEFAULT, Visibility.DEFAULT, Visibility.DEFAULT,
                    Visibility.DEFAULT, Visibility.DEFAULT)

            fun from(src: CirJsonAutoDetect): Value {
                return construct(src.fieldVisibility, src.getterVisibility, src.isGetterVisibility,
                        src.setterVisibility, src.creatorVisibility, src.scalarConstructorVisibility)
            }

            fun construct(accessor: PropertyAccessor, visibility: Visibility): Value {
                var fieldVisibility: Visibility = Visibility.DEFAULT
                var getterVisibility: Visibility = Visibility.DEFAULT
                var isGetterVisibility: Visibility = Visibility.DEFAULT
                var setterVisibility: Visibility = Visibility.DEFAULT
                var creatorVisibility: Visibility = Visibility.DEFAULT
                var scalarConstructorVisibility: Visibility = Visibility.DEFAULT

                when (accessor) {
                    PropertyAccessor.FIELD -> fieldVisibility = visibility

                    PropertyAccessor.GETTER -> getterVisibility = visibility

                    PropertyAccessor.IS_GETTER -> isGetterVisibility = visibility

                    PropertyAccessor.SETTER -> setterVisibility = visibility

                    PropertyAccessor.CREATOR -> creatorVisibility = visibility

                    PropertyAccessor.SCALAR_CONSTRUCTOR -> scalarConstructorVisibility = visibility

                    PropertyAccessor.ALL -> {
                        fieldVisibility = visibility
                        getterVisibility = visibility
                        isGetterVisibility = visibility
                        setterVisibility = visibility
                        creatorVisibility = visibility
                        scalarConstructorVisibility = visibility
                    }

                    PropertyAccessor.NONE -> {}
                }

                return construct(fieldVisibility, getterVisibility, isGetterVisibility, setterVisibility,
                        creatorVisibility, scalarConstructorVisibility)
            }

            fun construct(fieldVisibility: Visibility, getterVisibility: Visibility, isGetterVisibility: Visibility,
                    setterVisibility: Visibility, creatorVisibility: Visibility,
                    scalarConstructorVisibility: Visibility): Value {
                return predefined(fieldVisibility, getterVisibility, isGetterVisibility, setterVisibility,
                        creatorVisibility, scalarConstructorVisibility) ?: Value(fieldVisibility, getterVisibility,
                        isGetterVisibility, setterVisibility, creatorVisibility, scalarConstructorVisibility)
            }

            private fun predefined(fieldVisibility: Visibility, getterVisibility: Visibility,
                    isGetterVisibility: Visibility,
                    setterVisibility: Visibility, creatorVisibility: Visibility,
                    scalarConstructorVisibility: Visibility): Value? {
                if (fieldVisibility == DEFAULT_FIELD_VISIBILITY) {
                    if (getterVisibility == DEFAULT.getterVisibility &&
                            isGetterVisibility == DEFAULT.isGetterVisibility &&
                            setterVisibility == DEFAULT.setterVisibility &&
                            creatorVisibility == DEFAULT.creatorVisibility &&
                            scalarConstructorVisibility == DEFAULT.scalarConstructorVisibility) {
                        return DEFAULT
                    }
                } else if (fieldVisibility == Visibility.DEFAULT) {
                    if (getterVisibility == Visibility.DEFAULT && isGetterVisibility == Visibility.DEFAULT &&
                            setterVisibility == Visibility.DEFAULT && creatorVisibility == Visibility.DEFAULT &&
                            scalarConstructorVisibility == Visibility.DEFAULT) {
                        return NO_OVERRIDES
                    }
                }

                return null
            }

            fun merge(base: Value?, overrides: Value?): Value? {
                return base?.withOverrides(overrides) ?: overrides
            }

            private fun override(base: Visibility, overrides: Visibility): Visibility {
                return if (overrides === Visibility.DEFAULT) base else overrides
            }

            private fun equals(value: Value, fieldVisibility: Visibility, getterVisibility: Visibility,
                    isGetterVisibility: Visibility, setterVisibility: Visibility, creatorVisibility: Visibility,
                    scalarConstructorVisibility: Visibility): Boolean {
                return value.fieldVisibility == fieldVisibility && value.getterVisibility == getterVisibility &&
                        value.isGetterVisibility == isGetterVisibility && value.setterVisibility == setterVisibility &&
                        value.creatorVisibility == creatorVisibility &&
                        value.scalarConstructorVisibility == scalarConstructorVisibility
            }

        }

    }

}