package org.cirjson.cirjackson.databind.cirjsonFormatVisitors

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider

interface CirJsonMapFormatVisitor : CirJsonFormatVisitorWithSerializerProvider {

    /**
     * Visit method called to indicate the keys' type of the Map type being visited
     *
     * @param handler Serializer used, to allow for further callbacks
     *
     * @param keyType Type of key elements in the Map type
     */
    fun keyFormat(handler: CirJsonFormatVisitable, keyType: KotlinType)

    /**
     * Visit method called after [keyFormat] to allow visiting of the value type
     *
     * @param handler Serializer used, to allow for further callbacks
     *
     * @param valueType Type of value elements in the Map type
     */
    fun valueFormat(handler: CirJsonFormatVisitable, valueType: KotlinType)

    /**
     * Default "empty" implementation, useful as the base to start on; especially as it is guaranteed to implement all
     * the method of the interface, even if new methods are getting added.
     */
    open class Base(override var provider: SerializerProvider? = null) : CirJsonMapFormatVisitor {

        override fun keyFormat(handler: CirJsonFormatVisitable, keyType: KotlinType) {}

        override fun valueFormat(handler: CirJsonFormatVisitable, valueType: KotlinType) {}

    }

}