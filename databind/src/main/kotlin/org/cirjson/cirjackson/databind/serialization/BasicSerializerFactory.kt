package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.configuration.SerializerFactoryConfig

abstract class BasicSerializerFactory protected constructor(config: SerializerFactoryConfig?) : SerializerFactory() {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    final override fun withAdditionalSerializers(additional: Serializers): SerializerFactory {
        TODO("Not yet implemented")
    }

    final override fun withAdditionalKeySerializers(additional: Serializers): SerializerFactory {
        TODO("Not yet implemented")
    }

    final override fun withSerializerModifier(modifier: ValueSerializerModifier): SerializerFactory {
        TODO("Not yet implemented")
    }

    final override fun withNullKeySerializers(serializer: ValueSerializer<*>): SerializerFactory {
        TODO("Not yet implemented")
    }

    final override fun withNullValueSerializers(serializer: ValueSerializer<*>): SerializerFactory {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * SerializerFactory implementation
     *******************************************************************************************************************
     */

    override fun createKeySerializer(context: SerializerProvider, type: KotlinType): ValueSerializer<Any> {
        TODO("Not yet implemented")
    }

    override val defaultNullValueSerializer: ValueSerializer<Any>
        get() = TODO("Not yet implemented")

}