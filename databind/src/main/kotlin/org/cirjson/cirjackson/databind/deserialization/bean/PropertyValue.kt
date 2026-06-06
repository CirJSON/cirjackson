package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.core.CirJacksonException

abstract class PropertyValue protected constructor(val next: PropertyValue?, value: Any) {

    @Throws(CirJacksonException::class)
    abstract fun assign(bean: Any)

}