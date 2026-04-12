package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DatabindException
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.SettableAnyProperty
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader

open class PropertyValueBuffer(protected val myParser: CirJsonParser, protected val myContext: DeserializationContext,
        protected val myObjectIdReader: ObjectIdReader?, anyParamSetter: SettableAnyProperty?) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    @Throws(DatabindException::class)
    open fun getParameters(properties: Array<SettableBeanProperty>): Array<Any?> {
        TODO("Not yet implemented")
    }

}