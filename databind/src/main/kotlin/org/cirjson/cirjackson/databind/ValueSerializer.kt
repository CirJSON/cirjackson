package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitable
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper

abstract class ValueSerializer<T> : CirJsonFormatVisitable {

    /*
     *******************************************************************************************************************
     * Serialization methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    abstract fun serialize(value: T, generator: CirJsonGenerator, serializers: SerializerProvider)

    /*
     *******************************************************************************************************************
     * Default CirJsonFormatVisitable implementation
     *******************************************************************************************************************
     */

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Helper class
     *******************************************************************************************************************
     */

    abstract class None private constructor() : ValueSerializer<Any>()

}