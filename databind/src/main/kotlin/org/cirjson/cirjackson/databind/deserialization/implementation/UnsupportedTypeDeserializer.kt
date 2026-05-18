package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.deserialization.standard.StandardDeserializer
import org.cirjson.cirjackson.databind.util.isAssignableFrom

/**
 * Special bogus "serializer" that will throw [org.cirjson.cirjackson.databind.exception.MismatchedInputException] if an
 * attempt is made to deserialize a value. This is used for "known unknown" types: types that we can recognize but can
 * not support easily (or support known to be added via extension module).
 *
 * NOTE: does allow deserialization from [CirJsonToken.VALUE_EMBEDDED_OBJECT] if type matches (or is `null`).
 */
open class UnsupportedTypeDeserializer(protected val myType: KotlinType, protected val myMessage: String) :
        StandardDeserializer<Any>(myType) {

    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        if (parser.currentToken() != CirJsonToken.VALUE_EMBEDDED_OBJECT) {
            return context.reportBadDefinition(myType, myMessage)
        }

        val value = parser.embeddedObject ?: return null

        if (!myType.rawClass.isAssignableFrom(value::class)) {
            return context.reportBadDefinition(myType, myMessage)
        }

        return value
    }

}