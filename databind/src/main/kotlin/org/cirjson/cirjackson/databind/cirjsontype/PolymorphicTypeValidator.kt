package org.cirjson.cirjackson.databind.cirjsontype

import org.cirjson.cirjackson.databind.DatabindContext
import org.cirjson.cirjackson.databind.DatabindException
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.ObjectMapper
import org.cirjson.cirjackson.databind.cirjsontype.PolymorphicTypeValidator.Validity
import kotlin.reflect.KClass

/**
 * Interface for classes that handle validation of class-name - based subtypes used with Polymorphic Deserialization:
 * both via "default typing" and explicit `@CirJsonTypeInfo` when using KClass name as Type Identifier. The main
 * purpose, initially, is to allow pluggable allow lists to avoid security problems that occur with unlimited class
 * names.
 *
 * Calls to methods are done as follows:
 *
 * 1. When a deserializer is needed for a polymorphic property (including root values) -- either for explicitly
 * annotated polymorphic type, or "default typing" -- [validateBaseType] is called to see if validity can be determined
 * for all possible types: if [Validity.ALLOWED] is returned no further checks are made for any subtypes; of
 * [Validity.DENIED] is returned, an exception will be thrown to indicate invalid polymorphic property
 *
 * 2. If neither deny nor allowed was returned for property with specific base type, first time specific Type ID (Class
 * Name) is encountered, method [validateSubClassName] is called with resolved class name: it may indicate
 * allowed/denied, resulting in either allowed use or denial with exception
 *
 * 3. If no denial/allowance indicated, class name is resolved to actual [KClass], and [validateSubtype] is called: if
 * [Validity.ALLOWED] is returned, usage is accepted; otherwise (denied or indeterminate) the usage is not allowed
 * and the exception is thrown.
 *
 * Notes on implementations: implementations must be thread-safe and shareable (usually meaning they are stateless).
 * Determinations for validity are usually effectively cached on per-property basis (by virtue of subtype deserializers
 * being cached by polymorphic deserializers) so caching at validator level is usually not needed. If caching is used,
 * however, it must be done in thread-safe manner as validators are shared within [ObjectMapper] as well as possible
 * across mappers (in case of default/standard validator).
 *
 * Also note that it is strongly recommended that all implementations are based on provided abstract base class,
 * [PolymorphicTypeValidator.Base] which contains helper methods and default implementations for returning
 * [Validity.INDETERMINATE] for validation methods (to allow only overriding relevant methods implementation cares
 * about)
 */
abstract class PolymorphicTypeValidator {

    /**
     * Method called when a property with polymorphic value is encountered, and a `TypeResolverBuilder` is needed.
     * Intent is to allow early determination of cases where subtyping is completely denied (for example for security
     * reasons), or, conversely, allowed for allow subtypes (when base type guarantees that all subtypes are known to be
     * safe). Check can be thought of as both optimization (for latter case) and eager-fail (for former case) to give
     * better feedback.
     *
     * @param context Context for resolution: typically will be `DeserializationContext`
     *
     * @param baseType Nominal base type used for polymorphic handling: subtypes MUST be instances of this type and
     * assignment compatibility is verified by CirJackson core
     *
     * @return Determination of general validity of all subtypes of given base type; if [Validity.ALLOWED] returned, all
     * subtypes will automatically be accepted without further checks; is [Validity.DENIED] returned no subtyping
     * allowed at all (caller will usually throw an exception); otherwise (return [Validity.INDETERMINATE]) per subtype
     * validation calls are made for each new subclass encountered.
     */
    abstract fun validateBaseType(context: DatabindContext, baseType: KotlinType): Validity

    /**
     * Method called after intended class name for subtype has been read (and in case of minimal class name, expanded to
     * fully-qualified class name) but before attempt is made to look up actual [KClass] or [KotlinType]. Validator may
     * be able to determine validity of eventual type (and return [Validity.ALLOWED] or [Validity.DENIED]) or, if not
     * able to, can defer validation to actual resolved type by returning [Validity.INDETERMINATE].
     *
     * Validator may also choose to indicate denial by throwing a [DatabindException] (such as
     * [org.cirjson.cirjackson.databind.exception.InvalidTypeIdException])
     *
     * @param context Context for resolution: typically will be `DeserializationContext`
     *
     * @param baseType Nominal base type used for polymorphic handling: subtypes MUST be instances of this type and
     * assignment compatibility is verified by CirJackson core
     *
     * @param subClassName Name of class that will be resolved to [KClass] if (and only if) validity check is not
     * denied.
     *
     * @return Determination of validity of given class name, as a subtype of given base type: should NOT return `null`
     */
    abstract fun validateSubClassName(context: DatabindContext, baseType: KotlinType, subClassName: String): Validity

    /**
     * Method called after class name has been resolved to actual type, in cases where previous call to
     * [validateSubClassName] returned [Validity.INDETERMINATE]. Validator should be able to determine validity and
     * return appropriate [Validity] value, although it may also
     *
     * Validator may also choose to indicate denial by throwing a [DatabindException] (such as
     * [org.cirjson.cirjackson.databind.exception.InvalidTypeIdException])
     *
     * @param context Context for resolution: typically will be `DeserializationContext`
     *
     * @param baseType Nominal base type used for polymorphic handling: subtypes MUST be instances of this type and
     * assignment compatibility has been verified by CirJackson core
     *
     * @param subType Resolved subtype to validate
     *
     * @return Determination of validity of given class name, as a subtype of given base type: should NOT return `null`
     */
    abstract fun validateSubtype(context: DatabindContext, baseType: KotlinType, subType: KotlinType): Validity

    /**
     * Definition of return values to indicate determination regarding validity.
     */
    enum class Validity {

        /**
         * Value that indicates that KClass name or KClass is allowed for use without further checking
         */
        ALLOWED,

        /**
         * Value that indicates that KClass name or KClass is NOT allowed and no further checks are needed or allowed
         */
        DENIED,

        /**
         * Value that indicates that KClass name or KClass validity can not be confirmed by validator and further checks
         * are needed.
         *
         * Typically, if validator can not establish validity from Type ID or KClass (name), eventual determination will
         * be `DENIED`, for safety reasons.
         */
        INDETERMINATE
    }

    /**
     * Shared base class with partial implementation (with all validation calls returning [Validity.INDETERMINATE]) and
     * convenience methods for indicating failure reasons. Use of this base class is strongly recommended over directly
     * implement
     */
    abstract class Base : PolymorphicTypeValidator() {

        override fun validateBaseType(context: DatabindContext, baseType: KotlinType): Validity {
            return Validity.INDETERMINATE
        }

        override fun validateSubClassName(context: DatabindContext, baseType: KotlinType,
                subClassName: String): Validity {
            return Validity.INDETERMINATE
        }

        override fun validateSubtype(context: DatabindContext, baseType: KotlinType, subType: KotlinType): Validity {
            return Validity.INDETERMINATE
        }

    }

}