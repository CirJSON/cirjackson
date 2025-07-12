package org.cirjson.cirjackson.databind.deserialization.kotlin

import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.DeserializationConfig
import org.cirjson.cirjackson.databind.KeyDeserializer
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.deserialization.KeyDeserializers

class KotlinKeyDeserializers : KeyDeserializers {

    /*
     *******************************************************************************************************************
     * KeyDeserializers implementation
     *******************************************************************************************************************
     */

    override fun findKeyDeserializer(type: KotlinType, config: DeserializationConfig,
            beanDescription: BeanDescription): KeyDeserializer? {
        TODO("Not yet implemented")
    }

}