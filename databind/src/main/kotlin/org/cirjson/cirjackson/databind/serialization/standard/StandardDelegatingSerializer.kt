package org.cirjson.cirjackson.databind.serialization.standard

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.util.Converter

open class StandardDelegatingSerializer : StandardSerializer<Any> {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor(converter: Converter<*, *>) : super(Any::class) {
    }

    constructor(converter: Converter<Any, *>, delegateType: KotlinType, valueSerializer: ValueSerializer<*>?,
            property: BeanProperty?) : super(delegateType) {
    }

    /*
     *******************************************************************************************************************
     * Serialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
        TODO("Not yet implemented")
    }

}