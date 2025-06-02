package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator

abstract class ValueSerializer<T> {

    /*
     *******************************************************************************************************************
     * Serialization methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    abstract fun serialize(value: T, generator: CirJsonGenerator, serializers: SerializerProvider)

    abstract class None private constructor() : ValueSerializer<Any>()

}