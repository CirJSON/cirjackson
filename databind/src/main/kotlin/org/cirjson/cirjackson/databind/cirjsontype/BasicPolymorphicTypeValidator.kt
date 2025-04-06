package org.cirjson.cirjackson.databind.cirjsontype

import org.cirjson.cirjackson.databind.DatabindContext
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.cirjsontype.BasicPolymorphicTypeValidator.TypeMatcher
import org.cirjson.cirjackson.databind.util.isArray
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import kotlin.reflect.KClass

/**
 * Standard [PolymorphicTypeValidator] implementation that users may want to use for constructing validators based on
 * simple class hierarchy and/or name patterns to allow and/or deny certain subtypes.
 *
 * Most commonly this is used to allow known safe subtypes based on common super type or package name.
 *
 * @property myInvalidBaseTypes Set of specifically denied base types to indicate that use of specific base types is not
 * allowed: most commonly used to fully block use of [Any] as the base type.
 *
 * @property myBaseTypeMatchers Set of matchers that can validate all values of polymorphic properties that match
 * specified allowed base types.
 *
 * @property mySubtypeNameMatchers Set of matchers that can validate specific values of polymorphic properties that
 * match subtype class name criteria.
 *
 * @property mySubtypeClassMatchers Set of matchers that can validate specific values of polymorphic properties that
 * match subtype class criteria.
 */
open class BasicPolymorphicTypeValidator(protected val myInvalidBaseTypes: Set<KClass<*>>?,
        protected val myBaseTypeMatchers: Array<TypeMatcher>?, protected val mySubtypeNameMatchers: Array<NameMatcher>?,
        protected val mySubtypeClassMatchers: Array<TypeMatcher>?) : PolymorphicTypeValidator.Base() {

    override fun validateBaseType(context: DatabindContext, baseType: KotlinType): Validity {
        val clazz = baseType.rawClass

        if (myInvalidBaseTypes?.contains(clazz) == true) {
            return Validity.DENIED
        }

        for (matcher in myBaseTypeMatchers ?: return Validity.INDETERMINATE) {
            if (matcher.match(context, clazz)) {
                return Validity.ALLOWED
            }
        }

        return Validity.INDETERMINATE
    }

    override fun validateSubClassName(context: DatabindContext, baseType: KotlinType, subClassName: String): Validity {
        for (matcher in mySubtypeNameMatchers ?: return Validity.INDETERMINATE) {
            if (matcher.match(context, subClassName)) {
                return Validity.ALLOWED
            }
        }

        return Validity.INDETERMINATE
    }

    override fun validateSubtype(context: DatabindContext, baseType: KotlinType, subType: KotlinType): Validity {
        mySubtypeClassMatchers ?: return Validity.INDETERMINATE

        val clazz = subType.rawClass

        for (matcher in mySubtypeClassMatchers) {
            if (matcher.match(context, clazz)) {
                return Validity.ALLOWED
            }
        }

        return Validity.INDETERMINATE
    }

    /**
     * General matcher interface (predicate) for validating class values (base type or resolved subtype)
     */
    fun interface TypeMatcher {

        fun match(context: DatabindContext, clazz: KClass<*>): Boolean

    }

    fun interface NameMatcher {

        /**
         * General matcher interface (predicate) for validating unresolved subclass class name.
         */
        fun match(context: DatabindContext, className: String): Boolean

    }

    /**
     * Builder class for configuring and constructing immutable [BasicPolymorphicTypeValidator] instances. Criteria for
     * allowing polymorphic subtypes is specified by adding rules in priority order, starting with the rules to evaluate
     * first: when a matching rule is found, its status ([PolymorphicTypeValidator.Validity.ALLOWED] or
     * [PolymorphicTypeValidator.Validity.DENIED]) is used and no further rules are checked.
     */
    open class Builder {

        /**
         * Optional set of base types (exact match) that are NOT accepted as base types for polymorphic properties. May
         * be used to prevent "unsafe" base types like [Any] or [Serializable].
         */
        protected var myInvalidBaseTypes: MutableSet<KClass<*>>? = null

        /**
         * Collected matchers for base types to allow.
         */
        protected var myBaseTypeMatchers: MutableList<TypeMatcher>? = null

        /**
         * Collected name-based matchers for subtypes to allow.
         */
        protected var mySubtypeNameMatchers: MutableList<NameMatcher>? = null

        /**
         * Collected KClass-based matchers for subtypes to allow.
         */
        protected var mySubtypeClassMatchers: MutableList<TypeMatcher>? = null

        /**
         * Method for appending matcher that will allow all subtypes in cases where nominal base type is specified
         * class, or one of its subtypes. For example, call to
         * ```
         * builder.allowIfBaseType(MyBaseType::class)
         * ```
         * would indicate that any polymorphic properties where declared base type is `MyBaseType` (or subclass thereof)
         * would allow all legal (assignment-compatible) subtypes.
         */
        fun allowIfBaseType(baseOfBase: KClass<*>): Builder {
            return appendBaseTypeMatcher { _, clazz -> baseOfBase.isAssignableFrom(clazz) }
        }

        /**
         * Method for appending matcher that will allow all subtypes in cases where nominal base type's class name
         * matches given [Regex] For example, call to
         * ```
         * builder.allowIfBaseType(Regex("com\\.mycompany\\..*"))
         * ```
         * would indicate that any polymorphic properties where declared base type is in package `com.mycompany` would
         * allow all legal (assignment-compatible) subtypes.
         *
         * NOTE! [Regex] match is applied using `if (patternForBase.matches(typeId)) { }` that is, it must
         * match the whole class name, not just part.
         */
        fun allowIfBaseType(patternForBase: Regex): Builder {
            return appendBaseTypeMatcher { _, clazz -> patternForBase.matches(clazz.qualifiedName!!) }
        }

        /**
         * Method for appending matcher that will allow all subtypes in cases where nominal base type's class name
         * starts with specific prefix. For example, call to
         * ```
         * builder.allowIfBaseType("com.mycompany.")
         * ```
         * would indicate that any polymorphic properties where declared base type is in package `com.mycompany` would
         * allow all legal (assignment-compatible) subtypes.
         */
        fun allowIfBaseType(prefixForBase: String): Builder {
            return appendBaseTypeMatcher { _, clazz -> clazz.qualifiedName!!.startsWith(prefixForBase) }
        }

        /**
         * Method for appending custom matcher called with base type: if matcher returns `true`, all possible subtypes
         * will be accepted; if `false`, other matchers are applied.
         *
         * @param matcher Custom matcher to apply to base type
         *
         * @return This Builder to allow call chaining
         */
        fun allowIfBaseType(matcher: TypeMatcher): Builder {
            return appendBaseTypeMatcher(matcher)
        }

        /**
         * Method for appending matcher that will mark any polymorphic properties with exact specific class to be
         * invalid. For example, call to
         * ```
         * builder.denyForExactBaseType(Object.class)
         * ```
         * would indicate that any polymorphic properties where declared base type is `Any` would be deemed invalid, and
         * attempt to deserialize values of such types should result in an exception.
         */
        fun denyForExactBaseType(baseTypeToDeny: KClass<*>): Builder {
            if (myInvalidBaseTypes == null) {
                myInvalidBaseTypes = HashSet()
            }

            myInvalidBaseTypes!!.add(baseTypeToDeny)
            return this
        }

        /**
         * Method for appending matcher that will allow specific subtype (regardless of declared base type) if it is
         * `subtypeBase` or its subtype. For example, call to
         * ```
         * builder.allowIfSubtype(MyImplType::class)
         * ```
         * would indicate that any polymorphic values with type of is `MyImplType` (or subclass thereof) would be allowed.
         */
        fun allowIfSubtype(subtypeBase: KClass<*>): Builder {
            return appendSubtypeClassMatcher { _, clazz -> subtypeBase.isAssignableFrom(clazz) }
        }

        /**
         * Method for appending matcher that will allow specific subtype (regardless of declared base type) in cases
         * where subclass name matches given [Regex]. For example, call to
         * ```
         * builder.allowIfSubtype(Regex("com\\.mycompany\\."))
         * ```
         * would indicate that any polymorphic values in package `com.mycompany` would be allowed.
         *
         * NOTE! [Regex] match is applied using `if (patternForSubtype.matches(typeId)) { }` that is, it must match the
         * whole class name, not just part.
         */
        fun allowIfSubtype(patternForSubtype: Regex): Builder {
            return appendSubtypeNameMatcher { _, className -> patternForSubtype.matches(className) }
        }

        /**
         * Method for appending matcher that will allow specific subtype (regardless of declared base type) in cases.
         * where subclass name starts with specified prefix For example, call to
         * ```
         * builder.allowIfSubtype("com.mycompany.")
         * ```
         * would indicate that any polymorphic values in package `com.mycompany` would be allowed.
         */
        fun allowIfSubtype(prefixForSubtype: String): Builder {
            return appendSubtypeNameMatcher { _, className -> className.startsWith(prefixForSubtype) }
        }

        /**
         * Method for appending custom matcher called with resolved subtype: if matcher returns `true`, type will be
         * accepted; if `false`, other matchers are applied.
         *
         * @param matcher Custom matcher to apply to resolved subtype
         *
         * @return This Builder to allow call chaining
         */
        fun allowIfSubtype(matcher: TypeMatcher): Builder {
            return appendSubtypeClassMatcher(matcher)
        }

        /**
         * Method for appending matcher that will allow all subtypes that are Java arrays (regardless of element type).
         * Note that this does NOT validate element type itself as long as Polymorphic Type handling is enabled for
         * element type: this is the case with all standard "Default Typing" inclusion criteria as well as for
         * annotation (`@CirJsonTypeInfo`) use case (since annotation only applies to element types, not container).
         *
         * NOTE: not used with other collection types ([Lists][List], [Collections][Collection]), mostly since use of
         * generic types as polymorphic values is not (well) supported.
         */
        fun allowIfSubtypeIsArray(): Builder {
            return appendSubtypeClassMatcher(TypeMatcher { _, clazz -> clazz.isArray })
        }

        /**
         * Method for appending matcher that will allow all subtypes for which a
         * [org.cirjson.cirjackson.databind.ValueDeserializer]) is explicitly provided by either `cirjackson-databind`
         * itself or one of registered [CirJacksonModules][org.cirjson.cirjackson.databind.CirJacksonModule].
         * Determination is implementation by calling
         * [org.cirjson.cirjackson.databind.deserialization.DeserializerFactory.hasExplicitDeserializerFor].
         *
         * In practice this matcher should remove the need to register any standard CirJackson-supported types, as well
         * as most if not all 3rd party types; leaving only POJOs and those 3rd party types that are not supported by
         * relevant modules. In turn this should not open security holes to "gadget" types since insecure types should
         * not be supported by datatype modules. For highest security cases (where input is untrusted) it is still
         * preferable to add more specific allow-rules, if possible.
         *
         * NOTE: Modules need to provide support for detection so if 3rd party types do not seem to be supported, Module
         * in question may need to be updated to indicate existence of explicit deserializers.
         */
        fun allowSubtypesWithExplicitDeserializer(): Builder {
            return appendSubtypeClassMatcher { context, clazz ->
                (context as DeserializationContext).hasExplicitDeserializerFor(clazz)
            }
        }

        fun build(): BasicPolymorphicTypeValidator {
            return BasicPolymorphicTypeValidator(myInvalidBaseTypes, myBaseTypeMatchers?.toTypedArray(),
                    mySubtypeNameMatchers?.toTypedArray(), mySubtypeClassMatchers?.toTypedArray())
        }

        protected fun appendBaseTypeMatcher(matcher: TypeMatcher): Builder {
            if (myBaseTypeMatchers == null) {
                myBaseTypeMatchers = ArrayList()
            }

            myBaseTypeMatchers!!.add(matcher)
            return this
        }

        protected fun appendSubtypeNameMatcher(matcher: NameMatcher): Builder {
            if (mySubtypeNameMatchers == null) {
                mySubtypeNameMatchers = ArrayList()
            }

            mySubtypeNameMatchers!!.add(matcher)
            return this
        }

        protected fun appendSubtypeClassMatcher(matcher: TypeMatcher): Builder {
            if (mySubtypeClassMatchers == null) {
                mySubtypeClassMatchers = ArrayList()
            }

            mySubtypeClassMatchers!!.add(matcher)
            return this
        }

    }

    companion object {

        fun builder(): Builder {
            return Builder()
        }

    }

}