package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType

open class TypeBindings private constructor(names: Array<String>?, types: Array<KotlinType>?,
        private val myUnboundVariables: Array<String>?) {

    companion object {

        val NO_STRINGS = emptyArray<String>()

        val NO_TYPES = emptyArray<KotlinType>()

        val EMPTY = TypeBindings(null, null, null)

    }

}