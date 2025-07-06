package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KType

class TypeFactory {

    /*
     *******************************************************************************************************************
     * Public general-purpose factory methods
     *******************************************************************************************************************
     */

    fun constructType(type: KType): KotlinType {
        TODO("Not yet implemented")
    }

    fun resolveMemberType(type: KType, contextBindings: TypeBindings): KotlinType {
        TODO("Not yet implemented")
    }

    companion object {

        const val DEFAULT_MAX_CACHE_SIZE = 200

        /**
         * Method for constructing a marker type that indicates missing generic type information, which is handled same
         * as simple type for `Any`.
         */
        fun unknownType(): KotlinType {
            TODO("Not yet implemented")
        }

    }

}