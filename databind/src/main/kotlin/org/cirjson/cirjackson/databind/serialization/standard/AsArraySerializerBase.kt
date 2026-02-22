package org.cirjson.cirjackson.databind.serialization.standard

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import kotlin.reflect.KClass

/**
 * Base class for serializers that will output contents as CirJSON arrays; typically serializers used for [Collection]
 * and array types.
 */
abstract class AsArraySerializerBase<T : Any> : StandardContainerSerializer<T> {

    protected val myElementType: KotlinType

    protected val myStaticTyping: Boolean

    /**
     * Setting for specific local override for "unwrap single element arrays": `true` for enable unwrapping, `false` for
     * preventing it, `null` for using global configuration.
     */
    protected val myUnwrapSingle: Boolean?

    /**
     * Type serializer used for values, if any.
     */
    protected val myValueTypeSerializer: TypeSerializer?

    /**
     * Value serializer to use, if it can be statically determined
     */
    protected val myElementSerializer: ValueSerializer<Any>?

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * Non-contextual, "blueprint" constructor typically called when the first instance is created, without knowledge of
     * property it was used via.
     */
    protected constructor(type: KClass<*>, elementType: KotlinType, staticTyping: Boolean,
            valueTypeSerializer: TypeSerializer?, elementSerializer: ValueSerializer<*>?) : this(type, elementType,
            staticTyping, valueTypeSerializer, elementSerializer, null)

    /**
     * General purpose constructor. Use contextual constructors, if possible.
     */
    @Suppress("UNCHECKED_CAST")
    protected constructor(type: KClass<*>, elementType: KotlinType, staticTyping: Boolean,
            valueTypeSerializer: TypeSerializer?, elementSerializer: ValueSerializer<*>?,
            unwrapSingle: Boolean?) : super(type) {
        myElementType = elementType
        myStaticTyping = staticTyping || elementType.isFinal
        myValueTypeSerializer = valueTypeSerializer
        myElementSerializer = elementSerializer as ValueSerializer<Any>?
        myUnwrapSingle = unwrapSingle
    }

    /**
     * General purpose constructor. Use contextual constructors, if possible.
     */
    @Suppress("UNCHECKED_CAST")
    protected constructor(type: KClass<*>, elementType: KotlinType, staticTyping: Boolean,
            valueTypeSerializer: TypeSerializer?, elementSerializer: ValueSerializer<*>?, unwrapSingle: Boolean?,
            property: BeanProperty?) : super(type, property) {
        myElementType = elementType
        myStaticTyping = staticTyping || elementType.isFinal
        myValueTypeSerializer = valueTypeSerializer
        myElementSerializer = elementSerializer as ValueSerializer<Any>?
        myUnwrapSingle = unwrapSingle
    }

    @Suppress("UNCHECKED_CAST")
    protected constructor(source: AsArraySerializerBase<*>, valueTypeSerializer: TypeSerializer?,
            elementSerializer: ValueSerializer<*>?, unwrapSingle: Boolean?, property: BeanProperty?) : super(source,
            property) {
        myElementType = source.myElementType
        myStaticTyping = source.myStaticTyping
        myValueTypeSerializer = valueTypeSerializer
        myElementSerializer = elementSerializer as ValueSerializer<Any>?
        myUnwrapSingle = unwrapSingle
    }

    abstract fun withResolved(property: BeanProperty?, valueTypeSerializer: TypeSerializer?,
            elementSerializer: ValueSerializer<*>?, unwrapSingle: Boolean?): AsArraySerializerBase<T>

    /*
     *******************************************************************************************************************
     * Postprocessing
     *******************************************************************************************************************
     */

    /**
     * This method is needed to resolve contextual annotations like per-property overrides, as well as do recursive call
     * to `createContextual` of content serializer, if known statically.
     */
    override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        var typeSerializer = myValueTypeSerializer

        if (typeSerializer != null) {
            typeSerializer = typeSerializer.forProperty(provider, property)
        }

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

        if (serializer == null) {
            serializer = myElementSerializer
        }

        serializer = findContextualConvertingSerializer(provider, property, serializer)

        if (serializer == null) {
            if (myStaticTyping && !myElementType.isJavaLangObject) {
                serializer = provider.findContentValueSerializer(myElementType, property)
            }
        }

        if (serializer !== myElementSerializer || property !== myProperty || typeSerializer !== myValueTypeSerializer || unwrapSingle != myUnwrapSingle) {
            return withResolved(property, typeSerializer, serializer, unwrapSingle)
        }

        return this
    }

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    override val contentType: KotlinType
        get() = myElementType

    override val contentSerializer: ValueSerializer<*>?
        get() = myElementSerializer

    /*
     *******************************************************************************************************************
     * Serialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: T, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                typeSerializer.typeId(value, CirJsonToken.START_ARRAY))
        generator.assignCurrentValue(value)
        serializeContents(value, generator, serializers)
        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

    @Throws(CirJacksonException::class)
    protected abstract fun serializeContents(value: T, generator: CirJsonGenerator, context: SerializerProvider)

    @Throws(CirJacksonException::class)
    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        val valueSerializer =
                myElementSerializer ?: visitor.provider!!.findContentValueSerializer(myElementType, myProperty)
        visitArrayFormat(visitor, typeHint, valueSerializer, myElementType)
    }

}