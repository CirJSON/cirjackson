package org.cirjson.cirjackson.databind.introspection

import java.lang.reflect.Constructor
import java.lang.reflect.Method
import kotlin.reflect.KClass

/**
 * Helper class needed to be able to efficiently access class member functions ([Methods][Method] and
 * [Constructors][Constructor]) in [Maps][Map].
 */
class MemberKey(val name: String, argumentTypes: Array<KClass<*>>?) {

    private val myArgumentTypes = argumentTypes ?: NO_CLASSES

    constructor(method: Method) : this(method.name, method.parameterTypes.map { it.kotlin }.toTypedArray())

    constructor(constructor: Constructor<*>) : this("", constructor.parameterTypes.map { it.kotlin }.toTypedArray())

    val argumentCount: Int
        get() = myArgumentTypes.size

    override fun toString(): String {
        return "$name(${myArgumentTypes.size}-args)"
    }

    override fun hashCode(): Int {
        return name.hashCode() + myArgumentTypes.size
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other == null) {
            return false
        }

        if (other !is MemberKey) {
            return false
        }

        if (name != other.name) {
            return false
        }

        val otherArguments = other.myArgumentTypes
        val length = myArgumentTypes.size

        if (length != otherArguments.size) {
            return false
        }

        for (i in 0 until length) {
            val type1 = myArgumentTypes[i]
            val type2 = otherArguments[i]

            if (type1 != type2) {
                return false
            }
        }

        return true
    }

    companion object {

        private val NO_CLASSES = emptyArray<KClass<*>>()

    }

}