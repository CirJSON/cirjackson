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

open class OptionalLongDeserializer :
        BaseScalarOptionalDeserializer<OptionalLong>(OptionalLong::class, OptionalLong.empty()) {

    override fun logicalType(): LogicalType {
        return LogicalType.INTEGER
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): OptionalLong? {
        if (parser.hasToken(CirJsonToken.VALUE_NUMBER_INT)) {
            return OptionalLong.of(parser.longValue)
        }

        return when (parser.currentTokenId()) {
            CirJsonTokenId.ID_STRING -> {
                var text = parser.text!!
                val action = checkFromStringCoercion(context, text)

                if (action == CoercionAction.AS_NULL) {
                    return getNullValue(context)
                } else if (action == CoercionAction.AS_EMPTY) {
                    return getEmptyValue(context) as OptionalLong?
                }

                text = text.trim()

                if (checkTextualNull(context, text)) {
                    getNullValue(context)
                } else {
                    OptionalLong.of(parseLongPrimitive(parser, context, text))
                }
            }

            CirJsonTokenId.ID_NUMBER_FLOAT -> {
                val action = checkFloatToIntCoercion(parser, context, myValueClass)

                if (action == CoercionAction.AS_NULL) {
                    getNullValue(context)
                } else if (action == CoercionAction.AS_EMPTY) {
                    getEmptyValue(context) as OptionalLong?
                } else {
                    OptionalLong.of(parser.valueAsLong)
                }
            }

            CirJsonTokenId.ID_NULL -> {
                myEmpty
            }

            CirJsonTokenId.ID_START_ARRAY -> {
                if (!context.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
                    return context.handleUnexpectedToken(getValueType(context), parser) as OptionalLong?
                }

                parser.nextToken()
                val parsed = deserialize(parser, context)
                verifyEndArrayForSingle(parser, context)
                parsed
            }

            else -> {
                context.handleUnexpectedToken(getValueType(context), parser) as OptionalLong?
            }
        }
    }

    companion object {

        val INSTANCE = OptionalLongDeserializer()

    }

}