package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.databind.configuration.SerializerFactoryConfig

open class BeanSerializerFactory protected constructor(config: SerializerFactoryConfig?) :
        BasicSerializerFactory(config) {

    companion object {

        val INSTANCE = BeanSerializerFactory(null)

    }

}