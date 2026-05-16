package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.introspection.AnnotatedConstructor
import org.cirjson.cirjackson.databind.util.unwrapAndThrowAsIllegalArgumentException
import kotlin.reflect.KFunction

/**
 * This subclass is used to handle special case of value being a non-static inner class. If so, we will have to use a
 * special alternative for default constructor; but otherwise can delegate to regular implementation.
 */
class InnerClassProperty : SettableBeanProperty.Delegating {

    /**
     * Constructor used when deserializing this property. Transient since there is no need to persist; only needed
     * during construction of objects.
     */
    @Transient
    private val myCreator: KFunction<*>

    /**
     * Serializable version of single-arg constructor we use for value instantiation.
     */
    private val myAnnotated: AnnotatedConstructor?

    constructor(delegate: SettableBeanProperty, constructor: KFunction<*>) : super(delegate) {
        myCreator = constructor
        myAnnotated = null
    }

    private constructor(delegate: SettableBeanProperty, annotatedConstructor: AnnotatedConstructor?) : super(delegate) {
        myCreator = annotatedConstructor?.annotated ?: throw IllegalArgumentException(
                "Missing constructor (broken JDK (de)serialization?)")
        myAnnotated = annotatedConstructor
    }

    override fun withDelegate(delegate: SettableBeanProperty): SettableBeanProperty {
        if (delegate === myCreator) {
            return this
        }

        return InnerClassProperty(delegate, myCreator)
    }

    /*
     *******************************************************************************************************************
     * Deserialization methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserializeAndSet(parser: CirJsonParser, context: DeserializationContext, instance: Any) {
        val token = parser.currentToken()

        val value = if (token == CirJsonToken.VALUE_NULL) {
            myValueDeserializer.getNullValue(context)
        } else if (myValueTypeDeserializer != null) {
            myValueDeserializer.deserializeWithType(parser, context, myValueTypeDeserializer)
        } else {
            try {
                myCreator.call(instance)!!
            } catch (e: Exception) {
                e.unwrapAndThrowAsIllegalArgumentException()
            }.also { myValueDeserializer.deserialize(parser, context, it) }
        }

        set(instance, value)
    }

    @Throws(CirJacksonException::class)
    override fun deserializeSetAndReturn(parser: CirJsonParser, context: DeserializationContext, instance: Any): Any? {
        return setAndReturn(instance, deserialize(parser, context))
    }

}