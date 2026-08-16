package org.cirjson.cirjackson.databind.module

import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.DeserializationConfig
import org.cirjson.cirjackson.databind.KeyDeserializer
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.deserialization.KeyDeserializers
import org.cirjson.cirjackson.databind.type.ClassKey
import kotlin.reflect.KClass

/**
 * Simple implementation [KeyDeserializers] which allows registration of deserializers based on raw (type erased class).
 * It can work well for basic bean and scalar type deserializers, but is not a good fit for handling generic types (like
 * [Maps][Map] and [Collections][Collection] or array types).
 * 
 * Unlike [SimpleSerializers], this class does not currently support generic mappings; all mappings must be to exact
 * declared deserialization type.
 */
open class SimpleKeyDeserializers : KeyDeserializers {

    protected var myClassMappings: HashMap<ClassKey, KeyDeserializer>? = null

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    open fun addKeyDeserializer(forType: KClass<*>, keyDeserializer: KeyDeserializer): SimpleKeyDeserializers {
        val classMappings = myClassMappings ?: HashMap<ClassKey, KeyDeserializer>().apply { myClassMappings = this }
        classMappings[ClassKey(forType)] = keyDeserializer
        return this
    }

    /*
     *******************************************************************************************************************
     * KeyDeserializers implementation
     *******************************************************************************************************************
     */

    override fun findKeyDeserializer(type: KotlinType, config: DeserializationConfig,
            beanDescription: BeanDescription): KeyDeserializer? {
        return myClassMappings?.get(ClassKey(type.rawClass))
    }

}