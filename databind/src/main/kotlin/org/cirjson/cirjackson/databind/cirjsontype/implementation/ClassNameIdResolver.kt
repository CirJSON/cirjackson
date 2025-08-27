package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.databind.DatabindContext
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.cirjsontype.PolymorphicTypeValidator
import org.cirjson.cirjackson.databind.util.findEnumType
import org.cirjson.cirjackson.databind.util.isEnumType
import org.cirjson.cirjackson.databind.util.superclass
import java.util.*
import kotlin.reflect.KClass

/**
 * [org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver] implementation that converts between fully-qualified
 * class names and (CirJSON) Strings.
 */
open class ClassNameIdResolver(baseType: KotlinType, protected val mySubTypeValidator: PolymorphicTypeValidator) :
        TypeIdResolverBase(baseType) {

    override val mechanism: CirJsonTypeInfo.Id
        get() = CirJsonTypeInfo.Id.CLASS

    override fun idFromValue(context: DatabindContext, value: Any?): String? {
        return idFrom(context, value!!, value::class)
    }

    override fun idFromValueAndType(context: DatabindContext, value: Any?, suggestedType: KClass<*>?): String? {
        return idFrom(context, value, suggestedType!!)
    }

    @Throws(CirJacksonException::class)
    override fun typeFromId(context: DatabindContext, id: String): KotlinType? {
        return internalTypeFromId(context, id)
    }

    @Throws(CirJacksonException::class)
    protected open fun internalTypeFromId(context: DatabindContext, id: String): KotlinType? {
        val type = context.resolveAndValidateSubType(myBaseType!!, id, mySubTypeValidator)

        if (type == null && context is DeserializationContext) {
            return context.handleUnknownTypeId(myBaseType, id, this, "no such class found")
        }

        return type
    }

    /*
     *******************************************************************************************************************
     * Internal methods
     *******************************************************************************************************************
     */

    protected open fun idFrom(context: DatabindContext, value: Any?, clazz: KClass<*>): String {
        var realClass = clazz

        if (realClass.isEnumType) {
            if (!realClass.java.isEnum) {
                realClass = realClass.superclass!!
            }
        }

        val string = realClass.qualifiedName!!

        if (!string.startsWith(JAVA_UTIL_PKG)) {
            return string
        }

        return if (value is EnumSet<*>) {
            val enumClass = value.findEnumType()
            context.typeFactory.constructCollectionType(EnumSet::class, enumClass).toCanonical()
        } else if (value is EnumMap<*, *>) {
            val enumClass = value.findEnumType()
            val valueClass = Any::class
            context.typeFactory.constructMapType(EnumMap::class, enumClass, valueClass).toCanonical()
        } else {
            string
        }
    }

    override val descriptionForKnownTypeIds: String?
        get() = "{class name used as type id}"

    companion object {

        const val JAVA_UTIL_PKG = "java.util."

        fun construct(baseType: KotlinType, polymorphicTypeValidator: PolymorphicTypeValidator): ClassNameIdResolver {
            return ClassNameIdResolver(baseType, polymorphicTypeValidator)
        }

    }

}