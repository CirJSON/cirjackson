package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.util.Snapshottable

abstract class InjectableValues : Snapshottable<InjectableValues> {

    abstract fun findInjectableValue(valueId: Any, context: DeserializationContext, forProperty: BeanProperty,
            beanInstance: Any?): Any?

}