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

val KClass<*>.isArray: Boolean
    get() = java.isArray

val KClass<*>.componentType: KClass<*>
    get() = java.componentType.kotlin

val KClass<*>.isInterface get() = java.isInterface

fun KClass<*>.isAssignableFrom(clazz: KClass<*>): Boolean {
    return java.isAssignableFrom(clazz.java)
}

/*
 ***********************************************************************************************************************
 * Type name, name, desc handling methods
 ***********************************************************************************************************************
 */

/**
 * Helper accessor used to construct appropriate description when passed either type (KClass) or an instance; in latter
 * case, KClass of instance is to be used.
 */
val Any?.className: String
    get() {
        this ?: return "[null]"
        return (this as? KClass<*> ?: this::class).name
    }

/**
 * Returns either `cls.getName()` (if `cls` not null),
 * or `"[null]"` if `cls` is null.
 */
@Suppress("KDocUnresolvedReference")
val KClass<*>?.name: String
    get() {
        this ?: return "[null]"

        var clazz: KClass<*> = this
        var index = 0

        while (clazz.isArray) {
            ++index
            clazz = clazz.componentType
        }

        val name = if (clazz.isPrimitive) clazz.simpleName!! else clazz.qualifiedName!!

        if (index == 0) {
            return name.backticked()
        }

        val stringBuilder = StringBuilder(name)

        do {
            stringBuilder.append("[]")
        } while (--index > 0)

        return stringBuilder.toString().backticked()
    }

/*
 ***********************************************************************************************************************
 * Class name, description access
 ***********************************************************************************************************************
 */

/**
 * Returns either ``text`` (backtick-quoted) or `"[null]"`.
 */
@Suppress("KDocUnresolvedReference")
fun String?.backticked(): String {
    this ?: return "[null]"

    return "'$this'"
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
