package org.cirjson.cirjackson.databind.cirjsonFormatVisitors

import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider

/**
 * Visitor called when properties of a type that maps to CirJSON Object are being visited: this usually means POJOs, but
 * sometimes other types use it too (like [java.util.EnumMap]).
 */
interface CirJsonObjectFormatVisitor : CirJsonFormatVisitorWithSerializerProvider {

    /**
     * Callback method called when a POJO property is being traversed.
     */
    fun property(writer: BeanProperty)

    /**
     * Callback method called when a non-POJO property (typically something like an Enum entry of [java.util.EnumMap]
     * type) is being traversed. With POJOs, [property] is called instead.
     */
    fun property(name: String, handler: CirJsonFormatVisitable, propertyTypeHint: KotlinType)

    /**
     * Callback method called when an optional POJO property is being traversed.
     */
    fun optionalProperty(writer: BeanProperty)

    /**
     * Callback method called when an optional non-POJO property (typically something like an Enum entry of
     * [java.util.EnumMap] type) is being traversed. With POJOs, [optionalProperty] is called instead.
     */
    fun optionalProperty(name: String, handler: CirJsonFormatVisitable, propertyTypeHint: KotlinType)

    /**
     * Default "empty" implementation, useful as the base to start on; especially as it is guaranteed to implement all
     * the method of the interface, even if new methods are getting added.
     */
    open class Base(override var provider: SerializerProvider? = null) : CirJsonObjectFormatVisitor {

        override fun property(writer: BeanProperty) {}

        override fun property(name: String, handler: CirJsonFormatVisitable, propertyTypeHint: KotlinType) {}

        override fun optionalProperty(writer: BeanProperty) {}

        override fun optionalProperty(name: String, handler: CirJsonFormatVisitable, propertyTypeHint: KotlinType) {}

    }

}