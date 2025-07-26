package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.databind.configuration.DeserializerFactoryConfig

open class BeanDeserializerFactory constructor(config: DeserializerFactoryConfig) : BasicDeserializerFactory(config) {

    companion object {

        val INSTANCE = BeanDeserializerFactory(DeserializerFactoryConfig())

    }

}