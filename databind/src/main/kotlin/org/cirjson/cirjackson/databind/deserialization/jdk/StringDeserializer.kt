package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.standard.StandardScalarDeserializer
import org.cirjson.cirjackson.databind.type.LogicalType

@CirJacksonStandardImplementation
open class StringDeserializer : StandardScalarDeserializer<String>(String::class) {

    override fun logicalType(): LogicalType {
        return LogicalType.TEXTUAL
    }

    override val isCacheable: Boolean
        get() = true

    override fun getEmptyValue(context: DeserializationContext): Any {
        return ""
    }

    override fun getNullValue(context: DeserializationContext): Any? {
        return null
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): String? {
        return if (parser.hasToken(CirJsonToken.VALUE_STRING)) {
            parser.text!!
        } else if (parser.hasToken(CirJsonToken.START_ARRAY)) {
            deserializeFromArray(parser, context)
        } else {
            parseString(parser, context, this)
        }
    }

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        return deserialize(parser, context)
    }

}