package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.serialization.bean.BeanSerializerBase
import org.cirjson.cirjackson.databind.serialization.implementation.ObjectIdWriter
import org.cirjson.cirjackson.databind.util.NameTransformer

@CirJacksonStandardImplementation
open class BeanSerializer : BeanSerializerBase {

    /*
     *******************************************************************************************************************
     * Lifecycle: constructors
     *******************************************************************************************************************
     */

    constructor(type: KotlinType, builder: BeanSerializerBuilder?, properties: Array<BeanPropertyWriter>,
            filteredProperties: Array<BeanPropertyWriter?>?) : super(type, builder, properties, filteredProperties)

    /*
     *******************************************************************************************************************
     * Lifecycle: factory methods, fluent factories
     *******************************************************************************************************************
     */

    override fun withObjectIdWriter(objectIdWriter: ObjectIdWriter?): BeanSerializerBase {
        TODO("Not yet implemented")
    }

    override fun withByNameInclusion(toIgnore: Set<String>?, toInclude: Set<String>?): BeanSerializerBase {
        TODO("Not yet implemented")
    }

    override fun asArraySerializer(): BeanSerializerBase {
        TODO("Not yet implemented")
    }

    override fun withFilterId(filterId: Any?): BeanSerializerBase {
        TODO("Not yet implemented")
    }

    override fun withProperties(properties: Array<BeanPropertyWriter>,
            filteredProperties: Array<BeanPropertyWriter?>?): BeanSerializerBase {
        TODO("Not yet implemented")
    }

    override fun unwrappingSerializer(unwrapper: NameTransformer): BeanSerializerBase {
        TODO("Not yet implemented")
    }

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