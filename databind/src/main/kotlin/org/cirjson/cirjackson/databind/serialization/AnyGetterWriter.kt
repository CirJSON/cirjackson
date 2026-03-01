package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.serialization.jdk.MapSerializer

/**
 * Class similar to [BeanPropertyWriter], but that will be used for serializing
 * [org.cirjson.cirjackson.annotations.CirJsonAnyGetter] annotated (Map) properties
 *
 * @property myAccessor Method (or field) that represents the "any getter"
 */
open class AnyGetterWriter(protected val myProperty: BeanProperty, protected val myAccessor: AnnotatedMember,
        serializer: ValueSerializer<*>) {

    @Suppress("UNCHECKED_CAST")
    protected var mySerializer = serializer as ValueSerializer<Any>

    protected var myMapSerializer = serializer as? MapSerializer

    open fun fixAccess(config: SerializationConfig) {
        myAccessor.fixAccess(config.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))
    }

    @Suppress("UNCHECKED_CAST")
    open fun resolve(context: SerializerProvider) {
        val serializer = context.handlePrimaryContextualization(mySerializer, myProperty) as ValueSerializer<*>
        mySerializer = serializer as ValueSerializer<Any>
        (serializer as? MapSerializer)?.let { myMapSerializer = it }
    }

    @Throws(Exception::class)
    open fun getAndSerialize(bean: Any, generator: CirJsonGenerator, context: SerializerProvider) {
        val value = myAccessor.getValue(bean) ?: return

        if (value !is Map<*, *>) {
            return context.reportBadDefinition(myProperty.type,
                    "Value returned by 'any-getter' ${myAccessor.name}() not Map but ${value::class.qualifiedName}")
        }

        if (myMapSerializer != null) {
            myMapSerializer!!.serializeWithoutTypeInfo(value, generator, context)
            return
        }

        mySerializer.serialize(value, generator, context)
    }

    @Throws(Exception::class)
    open fun getAndFilter(bean: Any, generator: CirJsonGenerator, context: SerializerProvider, filter: PropertyFilter) {
        val value = myAccessor.getValue(bean) ?: return

        if (value !is Map<*, *>) {
            return context.reportBadDefinition(myProperty.type,
                    "Value returned by 'any-getter' ${myAccessor.name}() not Map but ${value::class.qualifiedName}")
        }

        if (myMapSerializer != null) {
            myMapSerializer!!.serializeFilteredAnyPropertiesInternal(context, generator, bean, value, filter, null)
            return
        }

        mySerializer.serialize(value, generator, context)
    }

}