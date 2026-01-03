package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.util.Snapshottable
import org.cirjson.cirjackson.databind.util.className
import java.io.Serializable
import kotlin.reflect.KClass

/**
 * Abstract class that defines API for objects that provide value to "inject" during deserialization.
 */
abstract class InjectableValues : Snapshottable<InjectableValues> {

    /**
     * Method called to find value identified by id `valueId` to inject as value of specified property during
     * deserialization, passing POJO instance in which value will be injected if it is available (will be available when
     * injected via field or setter; not available when injected via constructor or factory method argument).
     *
     * @param valueId Object that identifies value to inject; may be a simple name or more complex identifier object,
     * whatever provider needs
     * 
     * @param context Deserialization context
     * 
     * @param forProperty Bean property in which value is to be injected
     * 
     * @param beanInstance Bean instance that contains property to inject, if available; `null` if bean has not yet been
     * constructed.
     */
    abstract fun findInjectableValue(valueId: Any, context: DeserializationContext, forProperty: BeanProperty,
            beanInstance: Any?): Any?

    /*
     *******************************************************************************************************************
     * Standard implementation
     *******************************************************************************************************************
     */

    /**
     * Simple standard implementation which uses a simple Map to store values to inject, identified by simple String
     * keys.
     */
    open class Standard(protected val myValues: MutableMap<String, Any?>) : InjectableValues(), Serializable {

        constructor() : this(HashMap())

        open fun addValue(key: String, value: Any?): Standard {
            myValues[key] = value
            return this
        }

        open fun addValue(classKey: KClass<*>, value: Any?): Standard {
            myValues[classKey.qualifiedName!!] = value
            return this
        }

        override fun snapshot(): Standard {
            if (myValues.isEmpty()) {
                return Standard()
            }

            return Standard(HashMap(myValues))
        }

        override fun findInjectableValue(valueId: Any, context: DeserializationContext, forProperty: BeanProperty,
                beanInstance: Any?): Any? {
            if (valueId !is String) {
                return context.reportBadDefinition(valueId::class,
                        "Unrecognized inject value id type (${valueId.className}), expecting String")
            }

            val obj = myValues[valueId]

            if (obj == null && valueId !in myValues) {
                throw IllegalArgumentException(
                        "No injectable id with value '$valueId' found (for property '${forProperty.name}')")
            }

            return obj
        }

    }

}