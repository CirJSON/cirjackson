package org.cirjson.cirjackson.databind.deserialization.standard

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationContext

open class NullifyingDeserializer : StandardDeserializer<Any>(Any::class) {

    /*
     *******************************************************************************************************************
     * Deserializer API
     *******************************************************************************************************************
     */

    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        TODO("Not yet implemented")
    }

    companion object {

        val INSTANCE = NullifyingDeserializer()

    }

}