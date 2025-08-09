package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.databind.ValueSerializer

abstract class SerializerFactory {

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