package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonArrayFormatVisitor
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardSerializer
import kotlin.reflect.KClass

/**
 * Intermediate base class for Lists, Collections and Arrays that contain static (non-dynamic) value types.
 */
abstract class StaticListSerializerBase<T : Collection<*>> : StandardSerializer<T> {

    /**
     * Setting for specific local override for "unwrap single element arrays": `true` for enable unwrapping, `false` for
     * preventing it, `null` for using global configuration.
     */
    protected val myUnwrapSingle: Boolean?

    protected constructor(type: KClass<*>) : super(type) {
        myUnwrapSingle = null
    }

    protected constructor(source: StaticListSerializerBase<*>, unwrapSingle: Boolean?) : super(source) {
        myUnwrapSingle = unwrapSingle
    }

    protected abstract fun withResolved(property: BeanProperty?, unwrapSingle: Boolean?): ValueSerializer<*>

    /*
     *******************************************************************************************************************
     * Postprocessing
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        var serializer: ValueSerializer<*>? = null

        if (property != null) {
            val introspector = provider.annotationIntrospector!!
            val member = property.member

            if (member != null) {
                serializer =
                        provider.serializerInstance(member, introspector.findContentSerializer(provider.config, member))
            }
        }

        val format = findFormatOverrides(provider, property, handledType()!!)
        val unwrapSingle = format.getFeature(CirJsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)

        serializer = findContextualConvertingSerializer(provider, property, serializer)
                ?: provider.findContentValueSerializer(String::class, property)

        return if (!isDefaultSerializer(serializer)) {
            CollectionSerializer(provider.constructType(String::class)!!, true, null,
                    serializer as ValueSerializer<Any>)
        } else if (unwrapSingle == myUnwrapSingle) {
            this
        } else {
            withResolved(property, unwrapSingle)
        }
    }

    override fun isEmpty(provider: SerializerProvider, value: T?): Boolean {
        return value.isNullOrEmpty()
    }

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitor.expectArrayFormat(typeHint)?.also { acceptContentVisitor(it) }
    }

    /*
     *******************************************************************************************************************
     * Abstract methods for subclasses to implement
     *******************************************************************************************************************
     */

    protected abstract fun contentSchema(): CirJsonNode

    protected abstract fun acceptContentVisitor(visitor: CirJsonArrayFormatVisitor)

    abstract override fun serializeWithType(value: T, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer)

}