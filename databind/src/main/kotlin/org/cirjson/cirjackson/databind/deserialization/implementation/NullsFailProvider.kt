package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.deserialization.NullValueProvider
import org.cirjson.cirjackson.databind.exception.InvalidNullException
import org.cirjson.cirjackson.databind.util.AccessPattern

/**
 * Simple [NullValueProvider] that will always throw an [InvalidNullException] when a `null` is encountered.
 */
open class NullsFailProvider protected constructor(protected val myName: PropertyName?,
        protected val myType: KotlinType?) : NullValueProvider {

    override val nullAccessPattern: AccessPattern
        get() = AccessPattern.DYNAMIC

    override fun getNullValue(context: DeserializationContext): Any? {
        throw InvalidNullException.from(context, myName, myType)
    }

    companion object {

        fun constructForProperty(property: BeanProperty): NullsFailProvider {
            return constructForProperty(property, property.type)
        }

        fun constructForProperty(property: BeanProperty, type: KotlinType?): NullsFailProvider {
            return NullsFailProvider(property.fullName, type)
        }

        fun constructForRootValue(type: KotlinType?): NullsFailProvider {
            return NullsFailProvider(null, type)
        }

    }

}