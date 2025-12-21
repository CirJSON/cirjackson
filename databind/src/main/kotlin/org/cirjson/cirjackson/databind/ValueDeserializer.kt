package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.deserialization.NullValueProvider
import org.cirjson.cirjackson.databind.util.AccessPattern

abstract class ValueDeserializer<T> : NullValueProvider {

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    open fun resolve(context: DeserializationContext) {
        // No-op
    }

    /*
     *******************************************************************************************************************
     * Main deserialization methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    abstract fun deserialize(parser: CirJsonParser, context: DeserializationContext): T?

    /*
     *******************************************************************************************************************
     * Introspection methods for figuring out configuration/setup of this deserializer instance and/or type it handles
     *******************************************************************************************************************
     */

    open val isCacheable: Boolean
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Default NullValueProvider implementation
     *******************************************************************************************************************
     */

    override fun getNullValue(context: DeserializationContext?): Any? {
        TODO("Not yet implemented")
    }

    override val nullAccessPattern: AccessPattern
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    abstract class None private constructor() : ValueDeserializer<Any>()

}