package org.cirjson.cirjackson.databind.deserialization.cirjackson

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.deserialization.standard.StandardScalarDeserializer
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.TokenBuffer

/**
 * We also want to directly support deserialization of [TokenBuffer].
 *
 * Note that we use scalar deserializer base just because we claim to be of scalar for type information inclusion
 * purposes; actual underlying content can be of any (Object, Array, scalar) type.
 */
@CirJacksonStandardImplementation
open class TokenBufferDeserializer : StandardScalarDeserializer<TokenBuffer>(TokenBuffer::class) {

    override fun logicalType(): LogicalType {
        return LogicalType.UNTYPED
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): TokenBuffer? {
        return context.bufferForInputBuffering(parser).deserialize(parser, context)
    }

}