package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DatabindException
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.SettableAnyProperty
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader

open class PropertyValueBuffer(protected val myParser: CirJsonParser, protected val myContext: DeserializationContext,
        protected val myParametersNeeded: Int, protected val myObjectIdReader: ObjectIdReader?,
        anyParamSetter: SettableAnyProperty?) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    @Throws(DatabindException::class)
    open fun getParameters(properties: Array<SettableBeanProperty>): Array<Any?> {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Other methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    open fun readIdProperty(propertyName: String): Boolean {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    open fun handleIdValue(context: DeserializationContext, bean: Any): Any {
        TODO("Not yet implemented")
    }

    protected open fun buffered(): PropertyValue? {
        TODO("Not yet implemented")
    }

    internal fun bufferedInternal(): PropertyValue? {
        return buffered()
    }

    open fun assignParameter(property: SettableBeanProperty, value: Any?): Boolean {
        TODO("Not yet implemented")
    }

    open fun bufferProperty(property: SettableBeanProperty, value: Any?) {
        TODO("Not yet implemented")
    }

    open fun bufferAnyProperty(property: SettableAnyProperty, propertyName: String, value: Any?) {
        TODO("Not yet implemented")
    }

}