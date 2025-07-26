package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.serialization.Serializers
import org.cirjson.cirjackson.databind.serialization.ValueSerializerModifier

class SerializerFactoryConfig private constructor(allAdditionalSerializers: Array<Serializers>?,
        allAdditionalKeySerializers: Array<Serializers>?, modifiers: Array<ValueSerializerModifier>?,
        val nullKeySerializer: ValueSerializer<Any>, val nullValueSerializer: ValueSerializer<Any>) {
}