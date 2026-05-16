package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember

/**
 * [SettableBeanProperty] implementation that will try to access value of the property first, and if non-`null` value
 * found, pass that for update (using the [org.cirjson.cirjackson.databind.ValueDeserializer.deserialize] that takes in
 * the instance) instead of constructing a new value. This is necessary to support "merging" properties.
 * 
 * Note that there are many similarities to [SetterlessProperty], which predates this variant; and that one is even used
 * in cases where there is no mutator available.
 */
open class MergingSettableBeanProperty : SettableBeanProperty.Delegating {

    protected val myAccessor: AnnotatedMember

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor(delegate: SettableBeanProperty, accessor: AnnotatedMember) : super(delegate) {
        myAccessor = accessor
    }

    protected constructor(source: MergingSettableBeanProperty, delegate: SettableBeanProperty) : super(delegate) {
        myAccessor = source.myAccessor
    }

    override fun withDelegate(delegate: SettableBeanProperty): SettableBeanProperty {
        return MergingSettableBeanProperty(delegate, myAccessor)
    }

    /*
     *******************************************************************************************************************
     * Deserialization methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserializeAndSet(parser: CirJsonParser, context: DeserializationContext, instance: Any) {
        val oldValue = myAccessor.getValue(instance)

        val newValue = if (oldValue == null) {
            myDelegate.deserialize(parser, context)
        } else {
            myDelegate.deserializeWith(parser, context, oldValue)
        }

        if (newValue !== oldValue) {
            myDelegate.set(instance, newValue)
        }
    }

    @Throws(CirJacksonException::class)
    override fun deserializeSetAndReturn(parser: CirJsonParser, context: DeserializationContext, instance: Any): Any? {
        val oldValue = myAccessor.getValue(instance)

        val newValue = if (oldValue == null) {
            myDelegate.deserialize(parser, context)
        } else {
            myDelegate.deserializeWith(parser, context, oldValue)
        }

        return if (newValue !== oldValue && newValue != null) {
            myDelegate.setAndReturn(instance, newValue)
        } else {
            instance
        }
    }

    override fun set(instance: Any, value: Any?) {
        value?.also { myDelegate.set(instance, it) }
    }

    override fun setAndReturn(instance: Any, value: Any?): Any {
        return value?.let { myDelegate.setAndReturn(instance, it) } ?: instance
    }

    companion object {

        fun construct(delegate: SettableBeanProperty, accessor: AnnotatedMember): MergingSettableBeanProperty {
            return MergingSettableBeanProperty(delegate, accessor)
        }

    }

}