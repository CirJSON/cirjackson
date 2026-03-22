package org.cirjson.cirjackson.databind.serialization.cirjackson

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardSerializer
import org.cirjson.cirjackson.databind.util.TokenBuffer

/**
 * We also want to directly support serialization of [TokenBuffer]; and since it is part of core package, it cannot
 * implement [org.cirjson.cirjackson.databind.CirJacksonSerializable] (which is only included in the mapper package).
 */
@CirJacksonStandardImplementation
open class TokenBufferSerializer : StandardSerializer<TokenBuffer>(TokenBuffer::class) {

    @Throws(CirJacksonException::class)
    override fun serialize(value: TokenBuffer, generator: CirJsonGenerator, serializers: SerializerProvider) {
        value.serialize(generator)
    }

    /**
     * Implementing typed output for contents of a TokenBuffer is very tricky, since we do not know for sure what its
     * contents might look like (or, rather, we do know when serializing, but not necessarily when deserializing!). One
     * possibility would be to check the current token, and use that to determine if we would output CirJSON Array,
     * Object, or scalar value.
     *
     * Note that we just claim it is scalar; this should work ok and is simpler than doing introspection on both
     * serialization and deserialization.
     */
    @Throws(CirJacksonException::class)
    final override fun serializeWithType(value: TokenBuffer, generator: CirJsonGenerator,
            serializers: SerializerProvider, typeSerializer: TypeSerializer) {
        val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                typeSerializer.typeId(value, CirJsonToken.VALUE_EMBEDDED_OBJECT))
        serialize(value, generator, serializers)
        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitor.expectAnyFormat(typeHint)
    }

}