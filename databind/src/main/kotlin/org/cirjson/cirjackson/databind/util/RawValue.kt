package org.cirjson.cirjackson.databind.util

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.CirJacksonSerializable
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer

open class RawValue : CirJacksonSerializable {

    override fun serialize(generator: CirJsonGenerator, serializers: SerializerProvider) {
        TODO("Not yet implemented")
    }

    override fun serialize(generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        TODO("Not yet implemented")
    }

}