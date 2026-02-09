package org.cirjson.cirjackson.databind.serialization.standard

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitable
import kotlin.reflect.KClass

abstract class StandardSerializer<T : Any> : ValueSerializer<T>, CirJsonFormatVisitable {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor(type: KClass<*>) : super() {
    }

    protected constructor(type: KotlinType) : super() {
    }

}