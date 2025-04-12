package org.cirjson.cirjackson.databind.util

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.StreamWriteFeature
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import java.io.IOException
import java.lang.reflect.Modifier
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

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
 * KClass type detection methods
 ***********************************************************************************************************************
 */

fun KClass<*>.isAssignableFrom(clazz: KClass<*>): Boolean {
    return java.isAssignableFrom(clazz.java)
}

val KClass<*>.isArray: Boolean
    get() = java.isArray

val KClass<*>.isPrimitive: Boolean
    get() = javaPrimitiveType != null

val KClass<*>.isAnnotation: Boolean
    get() = Annotation::class.isAssignableFrom(this)

val KClass<*>.componentType: KClass<*>
    get() = java.componentType.kotlin

val KClass<*>.isInterface get() = java.isInterface

/**
 * Returns `null` if KClass might be a bean; type String (that identifies why it's not a bean) if not
 */
fun KClass<*>.canBeABeanType(): String? {
    if (Annotation::class.isAssignableFrom(this)) {
        return "annotation"
    }

    if (isArray) {
        return "array"
    }

    if (isEnumType) {
        return "enum"
    }

    if (isPrimitive) {
        return "primitive"
    }

    return null
}

fun KClass<*>.isLocalType(allowNonStatic: Boolean): String? {
    try {
        val isStatic = Modifier.isStatic(java.modifiers)

        if (!isStatic && hasEnclosingMethod()) {
            return "local/anonymous"
        }

        if (!allowNonStatic) {
            if (!isStatic && enclosingClass != null) {
                return "non-static member class"
            }
        }
    } catch (_: SecurityException) {
    } catch (_: NullPointerException) {
    }

    return null
}

/**
 * Accessor for finding enclosing KClass for non-static inner classes
 */
val KClass<*>.outerClass: KClass<*>?
    get() {
        if (!Modifier.isStatic(java.modifiers)) {
            try {
                if (hasEnclosingMethod()) {
                    return null
                }

                return enclosingClass
            } catch (_: SecurityException) {
            }
        }

        return null
    }

/**
 * Helper accessor used to weed out dynamic Proxy types; types that do not expose concrete method API that we could use
 * to figure out automatic Bean (property) based serialization.
 */
val KClass<*>.isProxyType: Boolean
    get() {
        val name = qualifiedName!!
        return name.startsWith("net.sf.cglib.proxy.") || name.startsWith("org.hibernate.proxy.")
    }

/**
 * Helper accessor that checks if given class is a concrete one; that is, not an interface or abstract class.
 */
val KClass<*>.isConcrete: Boolean
    get() {
        val mod = java.modifiers
        return mod and (Modifier.INTERFACE or Modifier.ABSTRACT) != 0
    }

val KCallable<*>.isConcrete: Boolean
    get() {
        return isAbstract
    }

val KFunction<*>.isConcrete: Boolean
    get() {
        return isAbstract
    }

val KClass<*>.isCollectionMapOrArray: Boolean
    get() {
        return isArray || Collection::class.isAssignableFrom(this) || Map::class.isAssignableFrom(this)
    }

val KClass<*>.isBogusClass: Boolean
    get() {
        return this == Nothing::class || this == Void::class || this == Void.TYPE.kotlin
    }

val Class<*>.isRecordType: Boolean
    get() {
        val parent = superclass ?: return false
        return parent.name == "java.lang.Record"
    }

val KClass<*>.isRecordType: Boolean
    get() = java.isRecordType

val KClass<*>.isObjectOrPrimitive: Boolean
    get() = this == Any::class || isPrimitive

fun Any?.hasClass(raw: KClass<*>): Boolean {
    this ?: return false
    return this::class == raw
}

fun verifyMustOverride(expectedType: KClass<*>, instance: Any, method: String) {
    if (instance::class != expectedType) {
        throw IllegalStateException(
                "Sub-class ${instance::class} (of class ${expectedType.qualifiedName}) must override method '$method'")
    }
}

/*
 ***********************************************************************************************************************
 * Exception handling; simple re-throw
 ***********************************************************************************************************************
 */

/**
 * Helper method that will check if the throwable is an [Error], and if so, (re)throw it; otherwise just returns it
 */
fun Throwable.throwIfError(): Throwable {
    if (this is Error) {
        throw this
    }

    return this
}

/**
 * Helper method that will check if the throwable is a [RuntimeException], and if so, (re)throw it; otherwise just
 * returns it
 */
fun Throwable.throwIfRuntimeException(): Throwable {
    if (this is RuntimeException) {
        throw this
    }

    return this
}

/**
 * Helper method that will check if the throwable is a [CirJacksonException], and if so, (re)throw it; otherwise just
 * returns it
 */
@Throws(CirJacksonException::class)
fun Throwable.throwIfCirJacksonException(): Throwable {
    if (this is CirJacksonException) {
        throw this
    }

    return this
}

/*
 ***********************************************************************************************************************
 * Exception handling; other
 ***********************************************************************************************************************
 */

/**
 * Accessor that can be used to find the "root cause", innermost of chained (wrapped) exceptions.
 */
val Throwable.rootCause: Throwable
    get() {
        var root = this

        while (cause != null) {
            root = cause!!
        }

        return root
    }

/**
 * Method that works like by calling [rootCause] and then either throwing it (if instance of [CirJacksonException]), or
 * returns it.
 */
fun Throwable.throwRootCauseIfCirJacksonException(): Throwable {
    return rootCause.throwIfCirJacksonException()
}

/**
 * Method that will wrap the throwable as an [IllegalArgumentException] if it is a checked exception; otherwise (runtime
 * exception or error) throw as is
 */
fun Throwable.throwAsIllegalArgumentException() {
    throwAsIllegalArgumentException(exceptionMessage())
}

/**
 * Method that will wrap the throwable as an [IllegalArgumentException] (and with specified message) if it is a checked
 * exception; otherwise (runtime exception or error) throw as is
 */
@Suppress("ThrowableNotThrown")
fun Throwable.throwAsIllegalArgumentException(message: String?) {
    throwIfRuntimeException()
    throwIfError()
    throw IllegalArgumentException(message, this)
}

/**
 * Method that will locate the innermost exception for given Throwable; and then wrap it as an
 * [IllegalArgumentException] if it is a checked exception; otherwise (runtime exception or error) throw as is
 */
fun Throwable.unwrapAndThrowAsIllegalArgumentException() {
    rootCause.throwAsIllegalArgumentException()
}

/**
 * Method that will locate the innermost exception for given Throwable; and then wrap it as an
 * [IllegalArgumentException] if it is a checked exception; otherwise (runtime exception or error) throw as is
 */
fun Throwable.unwrapAndThrowAsIllegalArgumentException(message: String?) {
    rootCause.throwAsIllegalArgumentException(message)
}

/**
 * Helper method that encapsulate logic in trying to close output generator in case of failure; useful mostly in forcing
 * flush()ing as otherwise error conditions tend to be hard to diagnose. However, it is often the case that output state
 * may be corrupt so we need to be prepared for secondary exception without masking original one.
 *
 * Note that exception is thrown as-is if unchecked (likely case); if it is checked, however, [RuntimeException] is
 * thrown (except for [IOException] which will be wrapped as [CirJacksonIOException]).
 */
@Throws(CirJacksonException::class)
@Suppress("ThrowableNotThrown")
fun closeOnFailAndThrowAsCirJacksonException(generator: CirJsonGenerator, fail: Exception) {
    generator.configure(StreamWriteFeature.AUTO_CLOSE_CONTENT, true)

    try {
        generator.close()
    } catch (e: Exception) {
        fail.addSuppressed(e)
    }

    fail.throwIfCirJacksonException()
    fail.throwIfRuntimeException()

    if (fail is IOException) {
        throw CirJacksonIOException.construct(fail, generator)
    }

    throw RuntimeException(fail)
}

/**
 * Helper method that encapsulate logic in trying to close given [AutoCloseable] in case of failure; useful mostly in
 * forcing flush()ing as otherwise error conditions tend to be hard to diagnose. However, it is often the case that
 * output state may be corrupt so we need to be prepared for secondary exception without masking original one.
 */
@Throws(CirJacksonException::class)
@Suppress("ThrowableNotThrown")
fun closeOnFailAndThrowAsCirJacksonException(generator: CirJsonGenerator?, toClose: AutoCloseable?, fail: Exception) {
    if (generator != null) {
        generator.configure(StreamWriteFeature.AUTO_CLOSE_CONTENT, true)

        try {
            generator.close()
        } catch (e: Exception) {
            fail.addSuppressed(e)
        }
    }

    if (toClose != null) {
        try {
            toClose.close()
        } catch (e: Exception) {
            fail.addSuppressed(e)
        }
    }

    fail.throwIfCirJacksonException()
    fail.throwIfRuntimeException()

    if (fail is IOException) {
        throw CirJacksonIOException.construct(fail, generator)
    }

    throw RuntimeException(fail)
}

/*
 ***********************************************************************************************************************
 * Instantiation
 ***********************************************************************************************************************
 */

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
 * Other escaping, description access
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

fun Throwable.exceptionMessage(): String {
    TODO("Not yet implemented")
}

/*
 ***********************************************************************************************************************
 * Primitive type support
 ***********************************************************************************************************************
 */

/*
 ***********************************************************************************************************************
 * Access checking/handling methods
 ***********************************************************************************************************************
 */

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
 * Methods for detecting special class categories
 ***********************************************************************************************************************
 */

/*
 ***********************************************************************************************************************
 * Access to various Class definition aspects; leftover from some (failed) caching attempts
 ***********************************************************************************************************************
 */

fun KClass<*>.hasEnclosingMethod(): Boolean {
    TODO("Not yet implemented")
}

val KClass<*>.enclosingClass: KClass<*>?
    get() {
        TODO("Not yet implemented")
    }

/*
 ***********************************************************************************************************************
 * Helper classes
 ***********************************************************************************************************************
 */
