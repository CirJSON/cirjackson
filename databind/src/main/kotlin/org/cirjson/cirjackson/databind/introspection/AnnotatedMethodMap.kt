package org.cirjson.cirjackson.databind.introspection

import kotlin.reflect.KClass

class AnnotatedMethodMap : Iterable<AnnotatedMethod> {

    val size: Int
        get() = TODO("Not yet implemented")

    fun find(name: String, parameterTypes: Array<KClass<*>>): AnnotatedMethod? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Iterable implementation (for iterating over values)
     *******************************************************************************************************************
     */

    override fun iterator(): Iterator<AnnotatedMethod> {
        TODO("Not yet implemented")
    }

}