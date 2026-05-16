package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationConfig
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty

/**
 * Wrapper property that is used to handle managed (forward) properties Basically just needs to delegate first to actual
 * forward property, and then to back property.
 *
 * @property myIsContainer Flag that indicates whether property to handle is a container type (array, Collection, Map)
 * or not.
 */
class ManagedReferenceProperty(forward: SettableBeanProperty, private val myReferenceName: String,
        private val myBackProperty: SettableBeanProperty, private val myIsContainer: Boolean) :
        SettableBeanProperty.Delegating(forward) {

    override fun withDelegate(delegate: SettableBeanProperty): SettableBeanProperty {
        throw IllegalStateException("Should never try to reset delegate")
    }

    override fun fixAccess(config: DeserializationConfig) {
        myDelegate.fixAccess(config)
        myBackProperty.fixAccess(config)
    }

    /*
     *******************************************************************************************************************
     * SettableBeanProperty implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserializeAndSet(parser: CirJsonParser, context: DeserializationContext, instance: Any) {
        set(instance, myDelegate.deserialize(parser, context))
    }

    @Throws(CirJacksonException::class)
    override fun deserializeSetAndReturn(parser: CirJsonParser, context: DeserializationContext, instance: Any): Any {
        return setAndReturn(instance, deserialize(parser, context))
    }

    override fun set(instance: Any, value: Any?) {
        setAndReturn(instance, value)
    }

    override fun setAndReturn(instance: Any, value: Any?): Any {
        value ?: return myDelegate.setAndReturn(instance, null)

        if (myIsContainer) {
            when (value) {
                is Array<*> -> {
                    for (item in value) {
                        item?.let { myBackProperty.set(it, instance) }
                    }
                }

                is Collection<*> -> {
                    for (item in value) {
                        item?.let { myBackProperty.set(it, instance) }
                    }
                }

                is Map<*, *> -> {
                    for (item in value.values) {
                        item?.let { myBackProperty.set(it, instance) }
                    }
                }

                else -> {
                    throw IllegalStateException(
                            "Unsupported container type (${value::class.qualifiedName}) when resolving reference '$myReferenceName'")
                }
            }
        } else {
            myBackProperty.set(value, instance)
        }

        return myDelegate.setAndReturn(instance, value)
    }

}