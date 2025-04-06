package org.cirjson.cirjackson.databind.cirjsontype

import org.cirjson.cirjackson.databind.DatabindContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.util.className
import java.io.Closeable
import java.io.Serializable
import kotlin.reflect.KClass

/**
 * [PolymorphicTypeValidator] that will only allow polymorphic handling if the base type is NOT one of potential
 * dangerous base types (see [isUnsafeBaseType] for specific list of such base types).
 *
 * This implementation is the default one used for annotation-based polymorphic deserialization. Default Typing requires
 * explicit registration of validator; while this implementation may be used users are recommended to either use a
 * custom implementation or subclass this implementation and override either [validateSubClassName] or [validateSubtype]
 * to implement use-case specific validation.
 *
 * Note that when using potentially unsafe base type like [Any] a custom implementation (or subtype with override) is
 * needed. Most commonly subclasses would override both [isUnsafeBaseType] and [isSafeSubtype]: the former to allow all
 * (or just more) base types, and the latter to add actual validation of subtype.
 */
open class DefaultBaseTypeLimitingValidator : PolymorphicTypeValidator() {

    override fun validateBaseType(context: DatabindContext, baseType: KotlinType): Validity {
        if (isUnsafeBaseType(context, baseType)) {
            return context.reportBadDefinition(baseType,
                    "Configured `PolymorphicTypeValidator` (of type ${this::class.className}) denies resolution of all subtypes of base type ${baseType.rawClass.className} as using too generic base type can open a security hole without checks on subtype: please configure a custom `PolymorphicTypeValidator` for this use case")
        }

        return Validity.INDETERMINATE
    }

    override fun validateSubClassName(context: DatabindContext, baseType: KotlinType, subClassName: String): Validity {
        return Validity.INDETERMINATE
    }

    override fun validateSubtype(context: DatabindContext, baseType: KotlinType, subType: KotlinType): Validity {
        return if (isSafeSubtype(context, baseType, subType)) {
            Validity.ALLOWED
        } else {
            Validity.DENIED
        }
    }

    /**
     * Helper method called to determine if the given base type is known to be problematic regarding possible "gadget
     * types".
     *
     * @param context Processing context (to give access to configuration)
     *
     * @param baseType Base type to test
     */
    protected open fun isUnsafeBaseType(context: DatabindContext, baseType: KotlinType): Boolean {
        return UnsafeBaseTypes.isUnsafeBaseType(baseType.rawClass)
    }

    /**
     * Helper called to determine whether given actual subtype is considered safe to process: this will only be called
     * if subtype was considered acceptable earlier.
     *
     * @param context Processing context (to give access to configuration)
     *
     * @param baseType Base type of subtype (validated earlier)
     *
     * @param subType Subtype to test
     */
    protected open fun isSafeSubtype(context: DatabindContext, baseType: KotlinType, subType: KotlinType): Boolean {
        return true
    }

    private object UnsafeBaseTypes {

        private val unsafe =
                setOf(Any::class.qualifiedName, Closeable::class.qualifiedName, AutoCloseable::class.qualifiedName,
                        Serializable::class.qualifiedName, Cloneable::class.qualifiedName, "java.util.logging.Handler",
                        "javax.naming.Referenceable", "javax.sql.DataSource")

        fun isUnsafeBaseType(valueType: KClass<*>): Boolean {
            return valueType.simpleName in unsafe
        }

    }

}