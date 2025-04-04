package org.cirjson.cirjackson.databind.util

import kotlin.reflect.KClass

/*
 ***********************************************************************************************************************
 * Class type detection methods
 ***********************************************************************************************************************
 */

val Class<*>.isRecordType: Boolean
    get() {
        val parent = superclass ?: return false
        return parent.name == "java.lang.Record"
    }

val KClass<*>.isRecordType: Boolean
    get() = java.isRecordType

val KClass<*>.isInterface get() = java.isInterface

fun KClass<*>.isAssignableFrom(clazz: KClass<*>): Boolean {
    return java.isAssignableFrom(clazz.java)
}

/*
 ***********************************************************************************************************************
 * Class name, description access
 ***********************************************************************************************************************
 */

fun String?.quotedOr(forNull: String): String {
    return this?.let { "\"$it\"" } ?: forNull
}

/*
 ***********************************************************************************************************************
 * Enum type detection
 ***********************************************************************************************************************
 */

/**
 * Helper accessor that encapsulates reliable check on whether given raw type "is an Enum", that is, is or extends
 * [Enum].
 */
val Class<*>.isEnumType: Boolean
    get() = Enum::class.java.isAssignableFrom(this)

val KClass<*>.isEnumType: Boolean
    get() = java.isEnumType

/*
 ***********************************************************************************************************************
 * Primitive type detection
 ***********************************************************************************************************************
 */

val KClass<*>.isPrimitive: Boolean
    get() = javaPrimitiveType != null
