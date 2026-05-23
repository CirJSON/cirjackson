package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader
import org.cirjson.cirjackson.databind.deserialization.implementation.ValueInjector

open class BeanDeserializerBuilder {

    /*
     *******************************************************************************************************************
     * Accumulated information about properties
     *******************************************************************************************************************
     */

    protected var myInjectables: MutableList<ValueInjector>? = null

    protected var myValueInstantiator: ValueInstantiator? = null

    protected var myObjectIdReader: ObjectIdReader? = null

    protected var myAnySetter: SettableAnyProperty? = null

    /*
     *******************************************************************************************************************
     * Lifecycle: construction
     *******************************************************************************************************************
     */

    constructor(beanDescription: BeanDescription, context: DeserializationContext) {
    }

    /*
     *******************************************************************************************************************
     * Public accessors
     *******************************************************************************************************************
     */

    open val anySetter: SettableAnyProperty?
        get() = myAnySetter

    open val valueInstantiator: ValueInstantiator?
        get() = myValueInstantiator

    open val injectables: List<ValueInjector>?
        get() = myInjectables

    open val objectIdReader: ObjectIdReader?
        get() = myObjectIdReader

}