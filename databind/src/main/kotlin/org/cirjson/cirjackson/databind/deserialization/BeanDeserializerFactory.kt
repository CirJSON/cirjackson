package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.configuration.DeserializerFactoryConfig
import kotlin.reflect.KClass

open class BeanDeserializerFactory constructor(config: DeserializerFactoryConfig) : BasicDeserializerFactory(config) {

    /*
     *******************************************************************************************************************
     * DeserializerFactory API implementation
     *******************************************************************************************************************
     */

    override fun createBeanDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<Any>? {
        TODO("Not yet implemented")
    }

    override fun createBuilderBasedDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription, builderClass: KClass<*>): ValueDeserializer<Any> {
        TODO("Not yet implemented")
    }

    companion object {

        val INSTANCE = BeanDeserializerFactory(DeserializerFactoryConfig())

    }

}