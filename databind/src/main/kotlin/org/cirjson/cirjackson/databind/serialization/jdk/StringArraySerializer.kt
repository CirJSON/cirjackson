package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatTypes
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.ArraySerializerBase
import org.cirjson.cirjackson.databind.serialization.standard.StandardContainerSerializer
import org.cirjson.cirjackson.databind.type.TypeFactory

/**
 * Standard serializer used for `Array<String?>` values.
 */
@CirJacksonStandardImplementation
open class StringArraySerializer : ArraySerializerBase<Array<String?>> {

    /**
     * Value serializer to use, if it's not the standard one (if it is we can optimize serialization a lot)
     */
    protected val myElementSerializer: ValueSerializer<Any>?

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor() : super(Array<String?>::class) {
        myElementSerializer = null
    }

    @Suppress("UNCHECKED_CAST")
    constructor(source: StringArraySerializer, property: BeanProperty?, valueSerializer: ValueSerializer<*>?,
            unwrapSingle: Boolean?) : super(source, property, unwrapSingle) {
        myElementSerializer = valueSerializer as ValueSerializer<Any>?
    }

    override fun withResolved(property: BeanProperty?, unwrapSingle: Boolean?): ValueSerializer<*> {
        return StringArraySerializer(this, property, myElementSerializer, unwrapSingle)
    }

    /**
     * Strings never add type info; hence, even if type serializer is suggested, so it is ignored
     */
    override fun withValueTypeSerializerImplementation(
            valueTypeSerializer: TypeSerializer): StandardContainerSerializer<*> {
        return this
    }

    /*
     *******************************************************************************************************************
     * Postprocessing
     *******************************************************************************************************************
     */

    override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        var serializer: ValueSerializer<*>? = null

        if (property != null) {
            val member = property.member
            val introspector = provider.annotationIntrospector!!

            if (member != null) {
                serializer =
                        provider.serializerInstance(member, introspector.findContentSerializer(provider.config, member))
            }
        }

        val unwrapSingle = findFormatFeature(provider, property, Array<String?>::class,
                CirJsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)

        if (serializer == null) {
            serializer = myElementSerializer
        }

        serializer = findContextualConvertingSerializer(provider, property, serializer)
                ?: provider.findContentValueSerializer(String::class, property)

        if (isDefaultSerializer(serializer)) {
            serializer = null
        }

        if (serializer == myElementSerializer && unwrapSingle == myUnwrapSingle) {
            return this
        }

        return StringArraySerializer(this, property, serializer, unwrapSingle)
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    override val contentType: KotlinType
        get() = VALUE_TYPE

    override val contentSerializer: ValueSerializer<*>?
        get() = myElementSerializer

    override fun isEmpty(provider: SerializerProvider, value: Array<String?>?): Boolean {
        return value!!.isEmpty()
    }

    override fun hasSingleElement(value: Array<String?>): Boolean {
        return value.size == 1
    }

    /*
     *******************************************************************************************************************
     * Actual serialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    final override fun serialize(value: Array<String?>, generator: CirJsonGenerator, serializers: SerializerProvider) {
        val length = value.size

        if (length == 1) {
            if (myUnwrapSingle ?: serializers.isEnabled(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)) {
                serializeContents(value, generator, serializers)
                return
            }
        }

        generator.writeStartArray(value, length)
        serializeContents(value, generator, serializers)
        generator.writeEndArray()
    }

    @Throws(CirJacksonException::class)
    override fun serializeContents(value: Array<String?>, generator: CirJsonGenerator, context: SerializerProvider) {
        if (value.isEmpty()) {
            return
        }

        if (myElementSerializer != null) {
            serializeContentsSlow(value, generator, context, myElementSerializer)
            return
        }

        for (string in value) {
            if (string == null) {
                generator.writeNull()
            } else {
                generator.writeString(string)
            }
        }
    }

    @Throws(CirJacksonException::class)
    private fun serializeContentsSlow(value: Array<String?>, generator: CirJsonGenerator, context: SerializerProvider,
            serializer: ValueSerializer<Any>) {
        for (string in value) {
            if (string == null) {
                context.defaultSerializeNullValue(generator)
            } else {
                serializer.serialize(string, generator, context)
            }
        }
    }

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitArrayFormat(visitor, typeHint, CirJsonFormatTypes.STRING)
    }

    companion object {

        private val VALUE_TYPE = TypeFactory.DEFAULT_INSTANCE.constructType(Array<String?>::class.java)

        val INSTANCE = StringArraySerializer()

    }

}