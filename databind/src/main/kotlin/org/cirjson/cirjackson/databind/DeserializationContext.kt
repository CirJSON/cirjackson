package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.CirJsonParser
import kotlin.reflect.KClass

abstract class DeserializationContext : DatabindContext() {

    val parser: CirJsonParser?
        get() = TODO("Not implemented")

    fun hasExplicitDeserializerFor(valueType: KClass<*>): Boolean {
        TODO("Not yet implemented")
    }

}