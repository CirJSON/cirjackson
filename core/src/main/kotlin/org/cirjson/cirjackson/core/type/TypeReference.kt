package org.cirjson.cirjackson.core.type

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * This generic abstract class is used for obtaining full generics type information by sub-classing; it must be
 * converted to [ResolvedType] implementation (implemented by `KotlinType` from "databind" bundle) to be used. Class is
 * based on ideas from [Super Type Tokens](http://gafter.blogspot.com/2006/12/super-type-tokens.html). Additional idea
 * (from a suggestion made in comments of the article) is to require bogus implementation of `Comparable` (any such
 * generic interface would do, as long as it forces a method with generic type to be implemented). to ensure that a Type
 * argument is indeed given.
 *
 * Usage is by sub-classing: here is one way to instantiate reference to generic type `List<Int>`:
 * ```
 *  val ref = object :  TypeReference<List<Int>>() { };
 * ```
 * which can be passed to methods that accept TypeReference, or resolved using `TypeFactory` to obtain [ResolvedType].
 */
abstract class TypeReference<T> : Comparable<TypeReference<T>> {

    val type: Type

    init {
        val superclass = javaClass.genericSuperclass

        if (superclass is Class<*>) {
            throw IllegalArgumentException("Internal error: TypeReference constructed without actual type information")
        }

        type = (superclass as ParameterizedType).actualTypeArguments[0]
    }

    /**
     * The only reason we define this method (and require implementation of `Comparable`) is to prevent constructing a
     * reference without type information.
     */
    override fun compareTo(other: TypeReference<T>): Int {
        return 0
    }

}