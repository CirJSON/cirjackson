package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType

class TypeFactory {

    companion object {

        /**
         * Method for constructing a marker type that indicates missing generic type information, which is handled same
         * as simple type for `Any`.
         */
        fun unknownType(): KotlinType {
            TODO("Not yet implemented")
        }

    }

}