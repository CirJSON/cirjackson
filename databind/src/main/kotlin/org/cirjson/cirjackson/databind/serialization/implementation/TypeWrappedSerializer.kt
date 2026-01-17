package org.cirjson.cirjackson.databind.serialization.implementation

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer

class TypeWrappedSerializer(private val myTypeSerializer: TypeSerializer, serializer: ValueSerializer<*>) :
        ValueSerializer<Any>() {

    override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
        TODO("Not yet implemented")
    }

}