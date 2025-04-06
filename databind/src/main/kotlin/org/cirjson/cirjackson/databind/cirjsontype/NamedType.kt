package org.cirjson.cirjackson.databind.cirjsontype

import kotlin.reflect.KClass

class NamedType(val type: KClass<*>, name: String?) {

    var name: String? = null
        get() = field
        set(value) {
            field = value?.takeUnless { it.isEmpty() }
        }

    private val myHashCode = type.qualifiedName!!.hashCode() + name.hashCode()

    init {
        this.name = name
    }

    constructor(type: KClass<*>) : this(type, null)

    fun hasName() = name != null

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is NamedType) {
            return false
        }

        return type == other.type && name == other.name
    }

    override fun hashCode(): Int {
        return myHashCode
    }

    override fun toString(): String {
        return "[NamedType, class ${type.qualifiedName}, name: ${name?.let { "'$it'" } ?: "null"}]"
    }

}