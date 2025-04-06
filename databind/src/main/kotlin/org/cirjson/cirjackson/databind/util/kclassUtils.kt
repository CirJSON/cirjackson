package org.cirjson.cirjackson.databind.util

import kotlin.reflect.KClass

/*
 ***********************************************************************************************************************
 * Methods that deal with inheritance
 ***********************************************************************************************************************
 */

val KClass<*>.superclass: KClass<*>?
    get() = java.superclass?.kotlin

val KClass<*>.interfaces: List<KClass<*>>
    get() = java.interfaces.map { it.kotlin }

fun KClass<*>?.findRawSuperTypes(endBefore: KClass<*>?, addClassItself: Boolean): List<KClass<*>> {
    if (this == null || this == endBefore || this == Any::class) {
        return emptyList()
    }

    val result = ArrayList<KClass<*>>(8)
    addRawSuperTypes(this, endBefore, result, addClassItself)
    return result
}

/**
 * Method for finding all super classes (but not super interfaces) of given class, starting with the immediate super
 * class and ending in the most distant one. KClass itself is included if `addClassItself` is `true`.
 *
 * NOTE: mostly/only called to resolve mix-ins as that's where we do not care about fully-resolved types, just associated annotations.
 */
fun KClass<*>?.findSuperClasses(endBefore: KClass<*>?, addClassItself: Boolean): List<KClass<*>> {
    if (this == null || this == endBefore) {
        return emptyList()
    }

    val result = ArrayList<KClass<*>>(8)
    var clazz = this

    if (addClassItself) {
        result.add(clazz)
    }

    while (clazz!!.superclass?.also { clazz = it } != null) {
        if (clazz!! == endBefore) {
            break
        }

        result.add(clazz!!)
    }

    return result
}

private fun addRawSuperTypes(clazz: KClass<*>?, endBefore: KClass<*>?, result: MutableCollection<KClass<*>>,
        addClassItself: Boolean) {
    if (clazz == null || clazz == endBefore || clazz == Any::class) {
        return
    }

    if (addClassItself) {
        if (clazz in result) {
            return
        }

        result.add(clazz)
    }

    for (interfaze in clazz.interfaces) {
        addRawSuperTypes(interfaze, endBefore, result, true)
    }

    addRawSuperTypes(clazz.superclass, endBefore, result, true)
}

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

    return "`$this`"
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
