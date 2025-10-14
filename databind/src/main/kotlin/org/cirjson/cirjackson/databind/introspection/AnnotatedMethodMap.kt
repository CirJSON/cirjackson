package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.util.emptyIterator
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

/**
 * Simple helper class used to keep track of collection of [AnnotatedMethods][AnnotatedMethod], accessible by lookup.
 * Lookup is usually needed for augmenting and overriding annotations.
 */
class AnnotatedMethodMap : Iterable<AnnotatedMethod> {

    private val myMethods: Map<MemberKey, AnnotatedMethod>?

    constructor() {
        myMethods = null
    }

    constructor(methods: Map<MemberKey, AnnotatedMethod>?) {
        myMethods = methods
    }

    val size: Int
        get() = myMethods?.size ?: 0

    fun find(name: String, parameterTypes: Array<KClass<*>>): AnnotatedMethod? {
        return myMethods?.get(MemberKey(name, parameterTypes))
    }

    fun find(method: KFunction<*>): AnnotatedMethod? {
        return myMethods?.get(MemberKey(method.javaMethod!!))
    }

    /*
     *******************************************************************************************************************
     * Iterable implementation (for iterating over values)
     *******************************************************************************************************************
     */

    override fun iterator(): Iterator<AnnotatedMethod> {
        return myMethods?.values?.iterator() ?: emptyIterator()
    }

}