package org.cirjson.cirjackson.databind.util

import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.databind.KotlinType
import java.util.*
import kotlin.reflect.KClass

/**
 * Helper class that contains functionality needed by both serialization and deserialization side.
 */
object BeanUtil {

    /*
     *******************************************************************************************************************
     * Value defaulting helpers
     *******************************************************************************************************************
     */

    /**
     * Accessor used to find out "default value" to use for comparing values to serialize, to determine whether to
     * exclude value from serialization with inclusion type of [CirJsonInclude.Include.NON_DEFAULT].
     *
     * Default logic is such that for primitives and wrapper types for primitives, expected defaults (e.g., `0` for
     * `Int`) are returned; for Strings, empty String, and for structured (Maps, Collections, arrays) and reference
     * types, criteria [CirJsonInclude.Include.NON_DEFAULT] is used.
     */
    fun getDefaultValue(type: KotlinType): Any? {
        val clazz = type.rawClass

        if (clazz.isPrimitive) {
            return clazz.defaultValue()
        }

        if (type.isContainerType || type.isReferenceType) {
            return CirJsonInclude.Include.NON_EMPTY
        }

        if (clazz == String::class) {
            return ""
        }

        if (type.isTypeOrSubTypeOf(Date::class)) {
            return Date(0L)
        }

        if (type.isTypeOrSubTypeOf(Calendar::class)) {
            return GregorianCalendar().apply { timeInMillis = 0 }
        }

        return null
    }

    /*
     *******************************************************************************************************************
     * Package-specific type detection for error handling
     *******************************************************************************************************************
     */

    /**
     * Helper method called by [org.cirjson.cirjackson.databind.deserialization.BeanDeserializerFactory] and
     * [org.cirjson.cirjackson.databind.serialization.BeanSerializerFactory] to check if given unrecognized type (to be
     * (de)serialized as general POJO) is one of "well-known" types for which there would be a datatype module; and if
     * so, return the appropriate failure message to give to caller.
     */
    fun checkUnsupportedType(type: KotlinType): String? {
        val className = type.rawClass.qualifiedName!!

        val (typeName, moduleName) = if (isTimeClass(className)) {
            if (className.indexOf('.', 10) >= 0) {
                return null
            }

            "Java 8 date/time" to "org.cirjson.cirjackson.datatype:cirjackson-datatype-jsr310"
        } else if (isJodaTimeClass(className)) {
            "Joda date/time" to "org.cirjson.cirjackson.datatype:cirjackson-datatype-joda"
        } else {
            return null
        }

        return "$typeName type ${type.typeDescription} not supported by default: add Module \"$moduleName\" to enable handling"
    }

    fun isTimeClass(rawType: KClass<*>): Boolean {
        return isTimeClass(rawType.qualifiedName!!)
    }

    private fun isTimeClass(className: String): Boolean {
        return className.startsWith("java.time.")
    }

    fun isJodaTimeClass(rawType: KClass<*>): Boolean {
        return isJodaTimeClass(rawType.qualifiedName!!)
    }

    private fun isJodaTimeClass(className: String): Boolean {
        return className.startsWith("org.joda.time.")
    }

}