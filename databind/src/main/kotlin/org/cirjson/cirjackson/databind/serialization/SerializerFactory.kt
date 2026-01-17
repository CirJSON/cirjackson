package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer

abstract class SerializerFactory {

    /*
     *******************************************************************************************************************
     * Basic API
     *******************************************************************************************************************
     */

    abstract fun createSerializer(context: SerializerProvider, baseType: KotlinType, beanDescription: BeanDescription,
            formatOverride: CirJsonFormat.Value?): ValueSerializer<Any>

    abstract fun createKeySerializer(context: SerializerProvider, type: KotlinType): ValueSerializer<Any>

    abstract val defaultNullValueSerializer: ValueSerializer<Any>

    /*
     *******************************************************************************************************************
     * Mutant factories for registering additional configuration
     *******************************************************************************************************************
     */

    abstract fun withAdditionalSerializers(additional: Serializers): SerializerFactory

    abstract fun withAdditionalKeySerializers(additional: Serializers): SerializerFactory

    abstract fun withSerializerModifier(modifier: ValueSerializerModifier): SerializerFactory

    abstract fun withNullKeySerializers(serializer: ValueSerializer<*>): SerializerFactory

    abstract fun withNullValueSerializers(serializer: ValueSerializer<*>): SerializerFactory

}