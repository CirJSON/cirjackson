package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.databind.configuration.ConstructorDetector.Companion.DEFAULT
import org.cirjson.cirjackson.databind.configuration.ConstructorDetector.SingleArgConstructor.HEURISTIC
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import org.cirjson.cirjackson.databind.util.isJdkClass
import kotlin.reflect.KClass

/**
 * Configurable handler used to select aspects of selecting constructor to use as "Creator" for POJOs. Defines the API
 * for handlers, a pre-defined set of standard instances and methods for constructing alternative configurations.
 *
 * @property myRequireCtorAnnotation Whether explicit [org.cirjson.cirjackson.annotations.CirJsonCreator] is always
 * required for detecting constructors (even if visible) other than the default (no argument) constructor.
 *
 * @property myAllowJDKTypeCtors Whether auto-detection of "JDK types" constructors (those in packages `java.` and
 * `javax.`) is allowed or not
 */
class ConstructorDetector private constructor(private val mySingleArgMode: SingleArgConstructor,
        private val myRequireCtorAnnotation: Boolean, private val myAllowJDKTypeCtors: Boolean) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * Constructor used for default configurations which only varies by [mySingleArgMode]
     */
    private constructor(singleArgMode: SingleArgConstructor) : this(singleArgMode, false, false)

    fun withSingleArgMode(singleArgMode: SingleArgConstructor): ConstructorDetector {
        return ConstructorDetector(singleArgMode, myRequireCtorAnnotation, myAllowJDKTypeCtors)
    }

    fun withRequireAnnotation(state: Boolean): ConstructorDetector {
        return ConstructorDetector(mySingleArgMode, state, myAllowJDKTypeCtors)
    }

    fun withAllowJDKTypeConstructors(state: Boolean): ConstructorDetector {
        return ConstructorDetector(mySingleArgMode, myRequireCtorAnnotation, state)
    }

    /*
     *******************************************************************************************************************
     * API
     *******************************************************************************************************************
     */

    fun singleArgMode(): SingleArgConstructor {
        return mySingleArgMode
    }

    fun requireConstructorAnnotation(): Boolean {
        return myRequireCtorAnnotation
    }

    fun allowJDKTypeConstructors(): Boolean {
        return myAllowJDKTypeCtors
    }

    fun singleArgCreatorDefaultsToDelegating(): Boolean {
        return mySingleArgMode == SingleArgConstructor.DELEGATING
    }

    fun singleArgCreatorDefaultsToProperties(): Boolean {
        return mySingleArgMode == SingleArgConstructor.PROPERTIES
    }

    /**
     * Accessor that combines checks for whether implicit creators are allowed and, if so, whether JDK type constructors
     * are allowed (if type is JDK type) to determine whether implicit constructor detection should be enabled for given
     * type or not.
     *
     * @param rawType Value type to consider
     *
     * @return `true` if implicit constructor detection should be enabled; `false` if not
     */
    fun shouldIntrospectorImplicitConstructors(rawType: KClass<*>): Boolean {
        if (myRequireCtorAnnotation) {
            return false
        }

        if (myAllowJDKTypeCtors) {
            return true
        }

        if (!rawType.isJdkClass) {
            return true
        }

        return Throwable::class.isAssignableFrom(rawType)
    }

    /**
     * Definition of alternate handling modes of single-argument constructors that are annotated with
     * [org.cirjson.cirjackson.annotations.CirJsonCreator] but without "mode" definition (or explicit name for the
     * argument): this is the case where two interpretations are possible -- "properties" (in which case the argument is
     * named parameter of a CirJSON Object) and "delegating (in which case the argument maps to the whole CirJSON
     * value).
     *
     * Default choice is [HEURISTIC]
     *
     * NOTE: does NOT have any effect if explicit `@CirJsonCreator` annotation is required.
     */
    enum class SingleArgConstructor {

        /**
         * Assume "delegating" mode if not explicitly annotated otherwise
         */
        DELEGATING,

        /**
         * Assume "properties" mode if not explicitly annotated otherwise
         */
        PROPERTIES,

        /**
         * Use heuristics to see if "properties" mode is to be used (POJO has a property with the same name as the
         * implicit name [if available] of the constructor argument).
         */
        HEURISTIC,

        /**
         * Refuse to decide implicit mode and instead throw a
         * [org.cirjson.cirjackson.databind.exception.InvalidDefinitionException] in ambiguous case.
         */
        REQUIRE_MODE

    }

    companion object {

        /*
         ***************************************************************************************************************
         * Global default instances to use
         ***************************************************************************************************************
         */

        /**
         * Instance used by default, which:
         *
         * * Uses [SingleArgConstructor.HEURISTIC] for the single-argument constructor case
         *
         * * Does not require explicit `@CirJsonCreator` annotations (so allows auto-detection of Visible constructors)
         * (except for JDK types)
         *
         * * Does not allow auto-detection of Visible constructors for so-called JDK types; that is, classes in packages
         * `java.*` and `javax.*`
         */
        val DEFAULT = ConstructorDetector(SingleArgConstructor.HEURISTIC)

        /**
         * Instance similar to [DEFAULT] except that, for the single-argument case, uses setting of
         * [SingleArgConstructor.PROPERTIES].
         */
        val USE_PROPERTIES_BASED = ConstructorDetector(SingleArgConstructor.PROPERTIES)

        /**
         * Instance similar to [DEFAULT] except that, for the single-argument case, uses setting of
         * [SingleArgConstructor.DELEGATING].
         */
        val USE_DELEGATING = ConstructorDetector(SingleArgConstructor.DELEGATING)

        /**
         * Instance similar to [DEFAULT] except that, for the single-argument case, uses setting of
         * [SingleArgConstructor.REQUIRE_MODE].
         */
        val EXPLICIT_ONLY = ConstructorDetector(SingleArgConstructor.REQUIRE_MODE)

    }

}