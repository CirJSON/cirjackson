package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.databind.deserialization.SettableAnyProperty
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty

/**
 * Base class for property values that need to be buffered during deserialization.
 */
abstract class PropertyValue protected constructor(val next: PropertyValue?, value: Any?) {

    /**
     * Value to assign when POJO has been instantiated.
     */
    @set:Throws(CirJacksonException::class)
    open var value = value
        set(value) {
            throw UnsupportedOperationException("Should not be called by this type ${this::class.qualifiedName}")
        }

    /**
     * Method called to assign stored value of this property to specified bean instance
     */
    @Throws(CirJacksonException::class)
    abstract fun assign(bean: Any)

    /*
     *******************************************************************************************************************
     * Concrete property value classes
     *******************************************************************************************************************
     */

    /**
     * Property value that used when assigning value to property using a setter method or direct field access.
     */
    internal class RegularProperty(next: PropertyValue?, value: Any?, val property: SettableBeanProperty) :
            PropertyValue(next, value) {

        @Throws(CirJacksonException::class)
        override fun assign(bean: Any) {
            property.set(bean, value)
        }

    }

    /**
     * Property value type used when storing entries to be added to a POJO using "any setter" (method that takes name
     * and value arguments, allowing setting multiple different properties using single method).
     */
    internal class AnyProperty(next: PropertyValue?, value: Any?, val property: SettableAnyProperty,
            val propertyName: String) : PropertyValue(next, value) {

        @Throws(CirJacksonException::class)
        override fun assign(bean: Any) {
            property.set(bean, propertyName, value)
        }

    }

    /**
     * Property value type used when storing entries to be added to a Map.
     */
    internal class MapProperty(next: PropertyValue?, value: Any?, val key: Any?) : PropertyValue(next, value) {

        @Throws(CirJacksonException::class)
        @Suppress("UNCHECKED_CAST")
        override fun assign(bean: Any) {
            (bean as MutableMap<Any?, Any?>)[key] = value
        }

    }

    /**
     * Property value type used when storing entries to be passed to constructor of POJO using "any-setter".
     */
    internal class AnyParameterProperty(next: PropertyValue?, value: Any?, val property: SettableAnyProperty,
            val propertyName: String) : PropertyValue(next, value) {

        @set:Throws(CirJacksonException::class)
        override var value: Any?
            get() = super.value
            set(value) {
                property.set(value!!, propertyName, this.value)
            }

        @Throws(CirJacksonException::class)
        override fun assign(bean: Any) {
            // No op
        }

    }

}