package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember

/**
 * Class that encapsulates details of value injection that occurs before deserialization of a POJO. Details include
 * information needed to find injectable value (logical id) as well as method used for assigning value (setter or
 * field).
 */
open class ValueInjector(propertyName: PropertyName, type: KotlinType, mutator: AnnotatedMember,
        protected val myValueId: Any) :
        BeanProperty.Standard(propertyName, type, null, mutator, PropertyMetadata.STANDARD_OPTIONAL) {

    @Throws(CirJacksonException::class)
    open fun findValue(context: DeserializationContext, beanInstance: Any): Any {
        return context.findInjectableValue(myValueId, this, beanInstance)!!
    }

    @Throws(CirJacksonException::class)
    open fun inject(context: DeserializationContext, beanInstance: Any) {
        member!!.setValue(beanInstance, findValue(context, beanInstance))
    }

}