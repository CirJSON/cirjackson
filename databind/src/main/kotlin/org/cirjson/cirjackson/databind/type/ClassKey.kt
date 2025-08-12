package org.cirjson.cirjackson.databind.type

import kotlin.reflect.KClass

/**
 * Key class, used as an efficient and accurate key for locating per-class values, such as
 * [ValueSerializers][org.cirjson.cirjackson.databind.ValueSerializer].
 *
 * The reason for having a separate key class instead of directly using [KClass] as key is mostly to allow for
 * redefining `hashCode` method -- for some strange reason, [KClass] does not redefine [Any.hashCode] and thus uses
 * identity hash, which is pretty slow. This makes key access using [KClass] unnecessarily slow.
 *
 * Note: since class is not strictly immutable, caller must know what it is doing, if changing field values.
 */
class ClassKey(private var myClass: KClass<*>) : Comparable<ClassKey> {

    private val myClassName: String = myClass.qualifiedName!!

    private val myHashCode = myClassName.hashCode()

    /*
     *******************************************************************************************************************
     * Comparable
     *******************************************************************************************************************
     */

    override operator fun compareTo(other: ClassKey): Int {
        return myClassName.compareTo(other.myClassName)
    }

    /*
     *******************************************************************************************************************
     * Standard methods
     *******************************************************************************************************************
     */

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is ClassKey) {
            return false
        }

        return myClass == other.myClass
    }

    override fun hashCode(): Int {
        return myHashCode
    }

    override fun toString(): String {
        return myClassName
    }

}