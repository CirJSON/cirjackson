package org.cirjson.cirjackson.databind.util

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

/**
 * Key that offers two "modes"; one with raw class, as used for cases where raw class type is available (for example,
 * when using runtime type); and one with full generics-including.
 */
open class TypeKey {

    protected var myHashCode: Int

    var rawType: KClass<*>?
        protected set

    var type: KotlinType?
        protected set

    /**
     * Indicator of whether serializer stored has a type serializer wrapper around it or not; if not, it is "untyped"
     * serializer; if it has, it is "typed"
     */
    var isTyped: Boolean
        protected set

    constructor() {
        myHashCode = 0
        rawType = null
        type = null
        isTyped = false
    }

    constructor(source: TypeKey) {
        myHashCode = source.myHashCode
        rawType = source.rawType
        type = source.type
        isTyped = source.isTyped
    }

    constructor(key: KClass<*>, typed: Boolean) {
        rawType = key
        type = null
        isTyped = typed
        myHashCode = if (typed) typedHash(key) else untypedHash(key)
    }

    constructor(key: KotlinType, typed: Boolean) {
        type = key
        rawType = null
        isTyped = typed
        myHashCode = if (typed) typedHash(key) else untypedHash(key)
    }

    fun resetTyped(clazz: KClass<*>) {
        rawType = clazz
        type = null
        isTyped = true
        myHashCode = typedHash(clazz)
    }

    fun resetUntyped(clazz: KClass<*>) {
        rawType = clazz
        type = null
        isTyped = false
        myHashCode = untypedHash(clazz)
    }

    fun resetTyped(type: KotlinType) {
        this.type = type
        rawType = null
        isTyped = true
        myHashCode = typedHash(type)
    }

    fun resetUntyped(type: KotlinType) {
        this.type = type
        rawType = null
        isTyped = false
        myHashCode = untypedHash(type)
    }

    override fun hashCode(): Int {
        return myHashCode
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is TypeKey) {
            return false
        }

        if (isTyped != other.isTyped) {
            return false
        }

        if (rawType != other.rawType) {
            return false
        }

        if (type != other.type) {
            return false
        }

        return true
    }

    override fun toString(): String {
        if (rawType != null) {
            return "{class: ${rawType!!.qualifiedName}, typed? $isTyped}"
        }

        return "{class: $type, typed? $isTyped}"
    }

    companion object {

        fun untypedHash(clazz: KClass<*>): Int {
            return clazz.qualifiedName!!.hashCode()
        }

        fun typedHash(clazz: KClass<*>): Int {
            return clazz.qualifiedName!!.hashCode() + 1
        }

        fun untypedHash(type: KotlinType): Int {
            return type.hashCode() - 1
        }

        fun typedHash(type: KotlinType): Int {
            return type.hashCode() - 3
        }

    }

}