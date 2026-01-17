package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.configuration.SerializerFactoryConfig

open class BeanSerializerFactory protected constructor(config: SerializerFactoryConfig?) :
        BasicSerializerFactory(config) {

    /*
     *******************************************************************************************************************
     * SerializerFactory implementation
     *******************************************************************************************************************
     */

    override fun createSerializer(context: SerializerProvider, baseType: KotlinType, beanDescription: BeanDescription,
            formatOverride: CirJsonFormat.Value?): ValueSerializer<Any> {
        TODO("Not yet implemented")
    }

    companion object {

        val INSTANCE = BeanSerializerFactory(null)

    }

}