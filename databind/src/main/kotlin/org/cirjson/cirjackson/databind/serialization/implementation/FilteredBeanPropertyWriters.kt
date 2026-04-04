package org.cirjson.cirjackson.databind.serialization.implementation

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonObjectFormatVisitor
import org.cirjson.cirjackson.databind.serialization.BeanPropertyWriter
import org.cirjson.cirjackson.databind.util.NameTransformer
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import kotlin.reflect.KClass

/**
 * Object containing decorated [BeanPropertyWriters][BeanPropertyWriter] that will filter out properties that are not to
 * be included in currently active CirJsonView.
 */
object FilteredBeanPropertyWriters {

    fun constructViewBased(base: BeanPropertyWriter, viewsToInclude: Array<KClass<*>>): BeanPropertyWriter {
        return if (viewsToInclude.size == 1) {
            SingleView(base, viewsToInclude[0])
        } else {
            MultiView(base, viewsToInclude)
        }
    }

    /*
     *******************************************************************************************************************
     * Concrete subclasses
     *******************************************************************************************************************
     */

    private class SingleView(private val myDelegate: BeanPropertyWriter, private val myView: KClass<*>) :
            BeanPropertyWriter(myDelegate) {

        override fun rename(transformer: NameTransformer): BeanPropertyWriter {
            return SingleView(myDelegate.rename(transformer), myView)
        }

        override fun assignSerializer(serializer: ValueSerializer<Any>?) {
            myDelegate.assignSerializer(serializer)
        }

        override fun assignNullSerializer(serializer: ValueSerializer<Any>?) {
            myDelegate.assignNullSerializer(serializer)
        }

        override fun serializeAsProperty(value: Any, generator: CirJsonGenerator, provider: SerializerProvider) {
            if (provider.activeView?.let { myView.isAssignableFrom(it) } ?: true) {
                myDelegate.serializeAsProperty(value, generator, provider)
            } else {
                myDelegate.serializeAsOmittedProperty(value, generator, provider)
            }
        }

        override fun serializeAsElement(value: Any, generator: CirJsonGenerator, provider: SerializerProvider) {
            if (provider.activeView?.let { myView.isAssignableFrom(it) } ?: true) {
                myDelegate.serializeAsElement(value, generator, provider)
            } else {
                myDelegate.serializeAsOmittedElement(value, generator, provider)
            }
        }

        override fun depositSchemaProperty(objectVisitor: CirJsonObjectFormatVisitor, provider: SerializerProvider) {
            if (provider.activeView?.let { myView.isAssignableFrom(it) } ?: true) {
                super.depositSchemaProperty(objectVisitor, provider)
            }
        }

    }

    private class MultiView(private val myDelegate: BeanPropertyWriter, private val myViews: Array<KClass<*>>) :
            BeanPropertyWriter(myDelegate) {

        override fun rename(transformer: NameTransformer): BeanPropertyWriter {
            return MultiView(myDelegate.rename(transformer), myViews)
        }

        override fun assignSerializer(serializer: ValueSerializer<Any>?) {
            myDelegate.assignSerializer(serializer)
        }

        override fun assignNullSerializer(serializer: ValueSerializer<Any>?) {
            myDelegate.assignNullSerializer(serializer)
        }

        override fun serializeAsProperty(value: Any, generator: CirJsonGenerator, provider: SerializerProvider) {
            if (inView(provider.activeView)) {
                myDelegate.serializeAsProperty(value, generator, provider)
            } else {
                myDelegate.serializeAsOmittedProperty(value, generator, provider)
            }
        }

        override fun serializeAsElement(value: Any, generator: CirJsonGenerator, provider: SerializerProvider) {
            if (inView(provider.activeView)) {
                myDelegate.serializeAsElement(value, generator, provider)
            } else {
                myDelegate.serializeAsOmittedElement(value, generator, provider)
            }
        }

        override fun depositSchemaProperty(objectVisitor: CirJsonObjectFormatVisitor, provider: SerializerProvider) {
            if (inView(provider.activeView)) {
                super.depositSchemaProperty(objectVisitor, provider)
            }
        }

        private fun inView(activeView: KClass<*>?): Boolean {
            activeView ?: return true

            for (view in myViews) {
                if (view.isAssignableFrom(activeView)) {
                    return true
                }
            }

            return false
        }

    }

}