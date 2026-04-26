package org.cirjson.cirjackson.databind.external.jdk8

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.CirJsonTokenId
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.DeserializationFeature
import org.cirjson.cirjackson.databind.configuration.CoercionAction
import org.cirjson.cirjackson.databind.type.LogicalType
import java.util.*

open class OptionalDoubleDeserializer :
        BaseScalarOptionalDeserializer<OptionalDouble>(OptionalDouble::class, OptionalDouble.empty()) {

    override fun logicalType(): LogicalType {
        return LogicalType.FLOAT
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): OptionalDouble? {
        if (parser.hasToken(CirJsonToken.VALUE_NUMBER_FLOAT)) {
            return OptionalDouble.of(parser.doubleValue)
        }

        return when (parser.currentTokenId()) {
            CirJsonTokenId.ID_STRING -> {
                var text = parser.text!!
                val specialValue = checkDoubleSpecialValue(text)

                if (specialValue != null) {
                    return OptionalDouble.of(specialValue)
                }

                val action = checkFromStringCoercion(context, text)

                if (action == CoercionAction.AS_NULL) {
                    return getNullValue(context)
                } else if (action == CoercionAction.AS_EMPTY) {
                    return getEmptyValue(context) as OptionalDouble?
                }

                text = text.trim()

                if (checkTextualNull(context, text)) {
                    myEmpty
                } else {
                    OptionalDouble.of(parseDoublePrimitive(parser, context, text))
                }
            }

            CirJsonTokenId.ID_NUMBER_INT -> {
                OptionalDouble.of(parser.doubleValue)
            }

            CirJsonTokenId.ID_NULL -> {
                myEmpty
            }

            CirJsonTokenId.ID_START_ARRAY -> {
                if (!context.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
                    return context.handleUnexpectedToken(getValueType(context), parser) as OptionalDouble?
                }

                parser.nextToken()
                val parsed = deserialize(parser, context)
                verifyEndArrayForSingle(parser, context)
                parsed
            }

            else -> {
                context.handleUnexpectedToken(getValueType(context), parser) as OptionalDouble?
            }
        }
    }

    companion object {

        val INSTANCE = OptionalDoubleDeserializer()

    }

}