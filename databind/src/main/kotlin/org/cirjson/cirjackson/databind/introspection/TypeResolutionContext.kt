package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.type.TypeBindings
import org.cirjson.cirjackson.databind.type.TypeFactory
import kotlin.reflect.KType

/**
 * Interface that defines API used by members (like [AnnotatedMethod]) to dynamically resolve types they have.
 */
interface TypeResolutionContext {

    fun resolveType(type: KType): KotlinType

    class Basic(private val myTypeFactory: TypeFactory, private val myBindings: TypeBindings) : TypeResolutionContext {

        override fun resolveType(type: KType): KotlinType {
            return myTypeFactory.resolveMemberType(type, myBindings)
        }

    }

    /**
     * Stub implementation for the case where there are no bindings available (for example, for static methods and
     * fields)
     */
    class Empty(private val myTypeFactory: TypeFactory) : TypeResolutionContext {

        override fun resolveType(type: KType): KotlinType {
            return myTypeFactory.constructType(type)
        }

    }

}