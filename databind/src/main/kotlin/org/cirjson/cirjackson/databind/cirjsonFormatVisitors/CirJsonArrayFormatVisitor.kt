package org.cirjson.cirjackson.databind.cirjsonFormatVisitors

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider

interface CirJsonArrayFormatVisitor : CirJsonFormatVisitorWithSerializerProvider {

    /**
     * Visit method called for structured types, as well as possibly for leaf types (especially if handled by custom
     * serializers).
     *
     * @param handler Serializer used, to allow for further callbacks
     *
     * @param elementType Type of elements in CirJSON array value
     */
    fun itemsFormat(handler: CirJsonFormatVisitable, elementType: KotlinType)

    /**
     * Visit method that is called if the content type is a simple scalar type like [CirJsonFormatTypes.STRING] (but not
     * for structured types like [CirJsonFormatTypes.OBJECT] since they would be missing type information).
     */
    fun itemsFormat(format: CirJsonFormatTypes)

    /**
     * Default "empty" implementation, useful as the base to start on; especially as it is guaranteed to implement all
     * the method of the interface, even if new methods are getting added.
     */
    open class Base(override var provider: SerializerProvider? = null) : CirJsonArrayFormatVisitor {

        override fun itemsFormat(handler: CirJsonFormatVisitable, elementType: KotlinType) {}

        override fun itemsFormat(format: CirJsonFormatTypes) {}

    }

}