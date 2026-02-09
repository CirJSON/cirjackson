package org.cirjson.cirjackson.databind.serialization.bean

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.serialization.BeanPropertyWriter
import org.cirjson.cirjackson.databind.serialization.BeanSerializerBuilder
import org.cirjson.cirjackson.databind.serialization.standard.StandardSerializer

abstract class BeanSerializerBase : StandardSerializer<Any> {

    /*
     *******************************************************************************************************************
     * Lifecycle: constructors
     *******************************************************************************************************************
     */

    protected constructor(type: KotlinType, builder: BeanSerializerBuilder, properties: Array<BeanPropertyWriter>,
            filteredProperties: Array<BeanPropertyWriter>?) : super(type) {
    }

}