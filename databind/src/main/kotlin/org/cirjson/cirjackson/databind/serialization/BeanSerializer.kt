package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.serialization.bean.BeanSerializerBase

@CirJacksonStandardImplementation
open class BeanSerializer : BeanSerializerBase {

    /*
     *******************************************************************************************************************
     * Lifecycle: constructors
     *******************************************************************************************************************
     */

    constructor(type: KotlinType, builder: BeanSerializerBuilder?, properties: Array<BeanPropertyWriter>,
            filteredProperties: Array<BeanPropertyWriter>?) : super(type, builder, properties, filteredProperties)

    /*
     *******************************************************************************************************************
     * ValueSerializer implementation that differs between implementations
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
        TODO("Not yet implemented")
    }

    companion object {

        fun createDummy(forType: KotlinType, builder: BeanSerializerBuilder?): BeanSerializer {
            TODO("Not yet implemented")
        }

    }

}