package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.configuration.SerializerFactoryConfig

abstract class BasicSerializerFactory protected constructor(config: SerializerFactoryConfig?) : SerializerFactory() {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun withAdditionalSerializers(additional: Serializers): SerializerFactory {
        TODO("Not yet implemented")
    }

    override fun withAdditionalKeySerializers(additional: Serializers): SerializerFactory {
        TODO("Not yet implemented")
    }

    override fun withSerializerModifier(modifier: ValueSerializerModifier): SerializerFactory {
        TODO("Not yet implemented")
    }

    override fun withNullKeySerializers(serializer: ValueSerializer<*>): SerializerFactory {
        TODO("Not yet implemented")
    }

    override fun withNullValueSerializers(serializer: ValueSerializer<*>): SerializerFactory {
        TODO("Not yet implemented")
    }

}