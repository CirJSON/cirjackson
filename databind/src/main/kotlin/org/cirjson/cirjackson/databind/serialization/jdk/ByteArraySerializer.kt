package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatTypes
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardSerializer

/**
 * Unlike other integral number array serializers, we do not just print out byte values as numbers. Instead, we assume
 * that it would make more sense to output content as base64 encoded bytes (using default base64 encoding).
 *
 * NOTE: since it is NOT serialized as an array, cannot use AsArraySerializer as base
 */
@CirJacksonStandardImplementation
open class ByteArraySerializer : StandardSerializer<ByteArray>(ByteArray::class) {

    override fun isEmpty(provider: SerializerProvider, value: ByteArray?): Boolean {
        return value!!.isEmpty()
    }

    @Throws(CirJacksonException::class)
    override fun serialize(value: ByteArray, generator: CirJsonGenerator, serializers: SerializerProvider) {
        generator.writeBinary(serializers.config.base64Variant, value, 0, value.size)
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: ByteArray, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                typeSerializer.typeId(value, CirJsonToken.VALUE_EMBEDDED_OBJECT))
        generator.writeBinary(serializers.config.base64Variant, value, 0, value.size)
        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitor.expectArrayFormat(typeHint)?.itemsFormat(CirJsonFormatTypes.INTEGER)
    }

}