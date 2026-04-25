package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.deserialization.standard.StandardDeserializer

abstract class BeanDeserializerBase : StandardDeserializer<Any>, ValueInstantiator.Gettable {

    /*
     *******************************************************************************************************************
     * Lifecycle, construction, initialization
     *******************************************************************************************************************
     */

    protected constructor(source: BeanDeserializerBase, ignoreAllUnknown: Boolean) : super(source) {
    }

}