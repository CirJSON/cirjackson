package org.cirjson.cirjackson.databind.deserialization.standard

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.util.Converter

open class StandardConvertingDeserializer<T> : StandardDeserializer<T> {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor(converter: Converter<Any, T>, delegateType: KotlinType,
            delegateDeserializer: ValueDeserializer<*>?) : super(delegateType) {
    }

    /*
     *******************************************************************************************************************
     * Deserialization
     *******************************************************************************************************************
     */

    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): T? {
        TODO("Not yet implemented")
    }

}