package org.cirjson.cirjackson.databind.serialization.cirjackson

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.CirJacksonSerializable
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardSerializer

/**
 * Generic handler for types that implement [CirJacksonSerializable].
 *
 * Note: given that this is used for anything that implements
 * interface, cannot be checked for direct class equivalence.
 */
@CirJacksonStandardImplementation
open class CirJacksonSerializableSerializer protected constructor() :
        StandardSerializer<CirJacksonSerializable>(CirJacksonSerializable::class) {

    override fun isEmpty(provider: SerializerProvider, value: CirJacksonSerializable?): Boolean {
        return (value as? CirJacksonSerializable.Base)?.isEmpty(provider) ?: false
    }

    @Throws(CirJacksonException::class)
    override fun serialize(value: CirJacksonSerializable, generator: CirJsonGenerator,
            serializers: SerializerProvider) {
        value.serialize(generator, serializers)
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: CirJacksonSerializable, generator: CirJsonGenerator,
            serializers: SerializerProvider, typeSerializer: TypeSerializer) {
        value.serializeWithType(generator, serializers, typeSerializer)
    }

    companion object {

        val INSTANCE = CirJacksonSerializableSerializer()

    }

}