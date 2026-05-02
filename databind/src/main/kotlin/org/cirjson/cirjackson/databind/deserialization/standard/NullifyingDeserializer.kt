package org.cirjson.cirjackson.databind.deserialization.standard

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.CirJsonTokenId
import org.cirjson.cirjackson.databind.DeserializationConfig
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer

/**
 * Bogus deserializer that will simply skip all content there is to map and returns Kotlin `null` reference.
 */
open class NullifyingDeserializer : StandardDeserializer<Any>(Any::class) {

    /*
     *******************************************************************************************************************
     * Deserializer API
     *******************************************************************************************************************
     */

    override fun supportsUpdate(config: DeserializationConfig): Boolean {
        return false
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        if (parser.hasToken(CirJsonToken.PROPERTY_NAME)) {
            while (true) {
                val token = parser.nextToken() ?: break

                if (token == CirJsonToken.END_OBJECT) {
                    break
                }

                parser.skipChildren()
            }
        } else {
            parser.skipChildren()
        }

        return null
    }

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        return when (parser.currentTokenId()) {
            CirJsonTokenId.ID_START_ARRAY, CirJsonTokenId.ID_START_OBJECT, CirJsonTokenId.ID_PROPERTY_NAME -> {
                typeDeserializer.deserializeTypedFromAny(
                        parser, context)
            }

            else -> {
                null
            }
        }
    }

    companion object {

        val INSTANCE = NullifyingDeserializer()

    }

}