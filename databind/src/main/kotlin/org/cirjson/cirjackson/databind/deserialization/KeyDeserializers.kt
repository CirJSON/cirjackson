package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.DeserializationConfig
import org.cirjson.cirjackson.databind.KeyDeserializer
import org.cirjson.cirjackson.databind.KotlinType

/**
 * Interface that defines API for simple extensions that can provide additional deserializers
 * for deserializer Map keys of various types, from CirJSON property names.
 * Access is by a single callback method; instance is to either return
 * a configured [KeyDeserializer] for specified type, or null to indicate that it
 * does not support handling of the type. In the latter case, further calls can be made
 * for other providers; in the former case returned key deserializer is used for handling
 * of the specified type's key instances.
 */
interface KeyDeserializers {

    fun findKeyDeserializer(type: KotlinType, config: DeserializationConfig,
            beanDescription: BeanDescription): KeyDeserializer?

}