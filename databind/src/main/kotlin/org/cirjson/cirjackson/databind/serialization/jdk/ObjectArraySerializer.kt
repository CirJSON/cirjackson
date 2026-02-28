package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.standard.ArraySerializerBase
import org.cirjson.cirjackson.databind.serialization.standard.StandardContainerSerializer

/**
 * Generic serializer for Object arrays (`Array<Any?>`).
 */
@CirJacksonStandardImplementation
open class ObjectArraySerializer : ArraySerializerBase<Array<Any?>> {

    /**
     * Whether we are using static typing (using declared types, ignoring runtime type) or not for elements.
     */
    protected val myStaticTyping: Boolean

    /**
     * Declared type of element entries
     */
    protected val myElementType: KotlinType

    /**
     * Type serializer to use for values, if any.
     */
    protected val myValueTypeSerializer: TypeSerializer?

    /**
     * Value serializer to use, if it can be statically determined.
     */
    protected var myElementSerializer: ValueSerializer<Any>?

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor(elementType: KotlinType, staticTyping: Boolean, valueTypeSerializer: TypeSerializer?,
            elementSerializer: ValueSerializer<Any>?) : super(Array<Any?>::class) {
        myStaticTyping = staticTyping
        myElementType = elementType
        myValueTypeSerializer = valueTypeSerializer
        myElementSerializer = elementSerializer
    }

    constructor(source: ObjectArraySerializer, valueTypeSerializer: TypeSerializer?) : super(source) {
        myStaticTyping = source.myStaticTyping
        myElementType = source.myElementType
        myValueTypeSerializer = valueTypeSerializer
        myElementSerializer = source.myElementSerializer
    }

    @Suppress("UNCHECKED_CAST")
    constructor(source: ObjectArraySerializer, property: BeanProperty?, valueTypeSerializer: TypeSerializer?,
            valueSerializer: ValueSerializer<*>?, unwrapSingle: Boolean?) : super(source, property, unwrapSingle) {
        myStaticTyping = source.myStaticTyping
        myElementType = source.myElementType
        myValueTypeSerializer = valueTypeSerializer
        myElementSerializer = valueSerializer as ValueSerializer<Any>?
    }

    override fun withResolved(property: BeanProperty?, unwrapSingle: Boolean?): ValueSerializer<*> {
        return ObjectArraySerializer(this, property, myValueTypeSerializer, myElementSerializer, unwrapSingle)
    }

    override fun withValueTypeSerializerImplementation(
            valueTypeSerializer: TypeSerializer): StandardContainerSerializer<*> {
        return ObjectArraySerializer(myElementType, myStaticTyping, myValueTypeSerializer, myElementSerializer)
    }

    open fun withResolved(property: BeanProperty?, valueTypeSerializer: TypeSerializer?,
            valueSerializer: ValueSerializer<*>?, unwrapSingle: Boolean?): ValueSerializer<*> {
        if (myProperty === property && myElementSerializer === valueSerializer && myValueTypeSerializer === valueTypeSerializer && myUnwrapSingle == unwrapSingle) {
            return this
        }

        return ObjectArraySerializer(this, property, valueTypeSerializer, valueSerializer, unwrapSingle)
    }

    /*
     *******************************************************************************************************************
     * Postprocessing
     *******************************************************************************************************************
     */

    override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        val valueTypeSerializer = myValueTypeSerializer?.forProperty(provider, property)
        var serializer: ValueSerializer<*>? = null

        if (property != null) {
            val member = property.member
            val introspector = provider.annotationIntrospector!!

            if (member != null) {
                serializer =
                        provider.serializerInstance(member, introspector.findContentSerializer(provider.config, member))
            }
        }

        val format = findFormatOverrides(provider, property, handledType()!!)
        val unwrapSingle = format.getFeature(CirJsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)

        if (serializer == null) {
            serializer = myElementSerializer
        }

        serializer = findContextualConvertingSerializer(provider, property, serializer)

        if (serializer == null) {
            if (myStaticTyping && !myElementType.isJavaLangObject) {
                serializer = provider.findContentValueSerializer(myElementType, property)
            }
        }

        return withResolved(property, valueTypeSerializer, serializer, unwrapSingle)
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    override val contentType: KotlinType
        get() = myElementType

    override val contentSerializer: ValueSerializer<*>?
        get() = myElementSerializer

    override fun isEmpty(provider: SerializerProvider, value: Array<Any?>?): Boolean {
        return value!!.isEmpty()
    }

    override fun hasSingleElement(value: Array<Any?>): Boolean {
        return value.size == 1
    }

    /*
     *******************************************************************************************************************
     * Actual serialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    final override fun serialize(value: Array<Any?>, generator: CirJsonGenerator, serializers: SerializerProvider) {
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
    override fun serializeContents(value: Array<Any?>, generator: CirJsonGenerator, context: SerializerProvider) {
        val length = value.size

        if (length == 0) {
            return
        }

        if (myElementSerializer != null) {
            serializeContentsUsing(value, generator, context, myElementSerializer!!)
            return
        }

        if (myValueTypeSerializer != null) {
            serializeTypedContents(value, generator, context)
            return
        }

        var i = 0
        var element: Any? = null

        try {
            while (i < length) {
                element = value[i]

                if (element == null) {
                    context.defaultSerializeNullValue(generator)
                    i++
                    continue
                }

                val elementClass = element::class
                val serializer = myDynamicValueSerializers.serializerFor(elementClass)
                        ?: myElementType.takeIf { it.hasGenericTypes }?.let {
                            findAndAddDynamic(context, context.constructSpecializedType(myElementType, elementClass))
                        } ?: findAndAddDynamic(context, elementClass)
                serializer.serialize(element, generator, context)
                i++
            }
        } catch (e: Exception) {
            wrapAndThrow(context, e, element, i)
        }
    }

    @Throws(CirJacksonException::class)
    open fun serializeContentsUsing(value: Array<Any?>, generator: CirJsonGenerator, context: SerializerProvider,
            serializer: ValueSerializer<Any>) {
        val length = value.size
        val typeSerializer = myValueTypeSerializer

        var i = 0
        var element: Any? = null

        try {
            while (i < length) {
                element = value[i]

                if (element == null) {
                    context.defaultSerializeNullValue(generator)
                    i++
                    continue
                }

                if (typeSerializer == null) {
                    serializer.serialize(element, generator, context)
                } else {
                    serializer.serializeWithType(element, generator, context, typeSerializer)
                }

                i++
            }
        } catch (e: Exception) {
            wrapAndThrow(context, e, element, i)
        }
    }

    @Throws(CirJacksonException::class)
    open fun serializeTypedContents(value: Array<Any?>, generator: CirJsonGenerator, context: SerializerProvider) {
        val length = value.size
        var i = 0
        var element: Any? = null

        try {
            while (i < length) {
                element = value[i]

                if (element == null) {
                    context.defaultSerializeNullValue(generator)
                    i++
                    continue
                }

                val elementClass = element::class
                val serializer = myDynamicValueSerializers.serializerFor(elementClass) ?: findAndAddDynamic(context,
                        elementClass)
                serializer.serializeWithType(element, generator, context, myValueTypeSerializer!!)
                i++
            }
        } catch (e: Exception) {
            wrapAndThrow(context, e, element, i)
        }
    }

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        val arrayVisitor = visitor.expectArrayFormat(typeHint) ?: return
        val contentType = myElementType
        val valueSerializer =
                myElementSerializer ?: visitor.provider!!.findContentValueSerializer(contentType, myProperty)
        arrayVisitor.itemsFormat(valueSerializer, contentType)
    }

}