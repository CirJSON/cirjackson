package org.cirjson.cirjackson.databind.serialization.standard

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.implementation.PropertySerializerMap
import org.cirjson.cirjackson.databind.util.Converter
import org.cirjson.cirjackson.databind.util.verifyMustOverride
import kotlin.reflect.KClass

/**
 * Serializer implementation where given Java type is first converted to an intermediate "delegate type" (using a
 * configured [Converter], and then this delegate value is serialized by CirJackson.
 * 
 * Note that although types may be related, they must not be same; trying to do this will result in an exception.
 */
open class StandardDelegatingSerializer : StandardSerializer<Any> {

    protected val myProperty: BeanProperty?

    protected val myConverter: Converter<Any, *>

    /**
     * Fully resolved delegate type with generic information, if any available.
     */
    protected val myDelegateType: KotlinType?

    /**
     * Underlying serializer for type converted to.
     */
    protected val myDelegateSerializer: ValueSerializer<Any>?

    /**
     * If delegate serializer needs to be accessed dynamically (non-final type, static type not forced), this data
     * structure helps with efficient lookups.
     */
    protected var myDynamicValueSerializers = PropertySerializerMap.emptyForProperties()

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    constructor(converter: Converter<*, *>) : super(Any::class) {
        myConverter = converter as Converter<Any, *>
        myDelegateType = null
        myDelegateSerializer = null
        myProperty = null
    }

    @Suppress("UNCHECKED_CAST")
    constructor(clazz: KClass<*>, converter: Converter<*, *>) : super(clazz) {
        myConverter = converter as Converter<Any, *>
        myDelegateType = null
        myDelegateSerializer = null
        myProperty = null
    }

    @Suppress("UNCHECKED_CAST")
    constructor(converter: Converter<Any, *>, delegateType: KotlinType, valueSerializer: ValueSerializer<*>?,
            property: BeanProperty?) : super(delegateType) {
        myConverter = converter
        myDelegateType = delegateType
        myDelegateSerializer = valueSerializer as ValueSerializer<Any>?
        myProperty = property
    }

    /**
     * Method used for creating resolved contextual instances. Must be overridden when subclassing.
     */
    protected open fun withDelegate(converter: Converter<Any, *>, delegateType: KotlinType,
            valueSerializer: ValueSerializer<*>?, property: BeanProperty?): StandardDelegatingSerializer {
        verifyMustOverride(StandardDelegatingSerializer::class, this, "withDelegate")
        return StandardDelegatingSerializer(converter, delegateType, this, property)
    }

    /*
     *******************************************************************************************************************
     * Contextualization
     *******************************************************************************************************************
     */

    override fun resolve(provider: SerializerProvider) {
        myDelegateSerializer?.resolve(provider)
    }

    override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        var delegateSerializer = myDelegateSerializer
        var delegateType = myDelegateType

        if (delegateSerializer == null) {
            if (delegateType == null) {
                delegateType = myConverter.getOutputType(provider.typeFactory)
            }

            if (!delegateType.isJavaLangObject) {
                delegateSerializer = provider.findValueSerializer(delegateType)
            }
        }

        if (delegateSerializer != null) {
            delegateSerializer = provider.handleSecondaryContextualization(delegateSerializer, property)
        }

        if (delegateSerializer === myDelegateSerializer && delegateType === myDelegateType && property === myProperty) {
            return this
        }

        return withDelegate(myConverter, delegateType!!, delegateSerializer, property)
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    protected open val converter: Converter<Any, *>
        get() = myConverter

    override val delegatee: ValueSerializer<*>?
        get() = myDelegateSerializer

    /*
     *******************************************************************************************************************
     * Serialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
        val delegateValue = convertValue(value)

        if (delegateValue == null) {
            serializers.defaultSerializeNullValue(generator)
            return
        }

        val serializer = myDelegateSerializer ?: findSerializer(delegateValue, serializers)
        serializer.serialize(delegateValue, generator, serializers)
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val delegateValue = convertValue(value)

        if (delegateValue == null) {
            serializers.defaultSerializeNullValue(generator)
            return
        }

        val serializer = myDelegateSerializer ?: findSerializer(delegateValue, serializers)
        serializer.serializeWithType(delegateValue, generator, serializers, typeSerializer)
    }

    override fun isEmpty(provider: SerializerProvider, value: Any?): Boolean {
        val delegateValue = convertValue(value!!) ?: return true
        val serializer = myDelegateSerializer ?: findSerializer(value, provider)
        return serializer.isEmpty(provider, delegateValue)
    }

    /*
     *******************************************************************************************************************
     * Schema functionality
     *******************************************************************************************************************
     */

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        myDelegateSerializer?.acceptCirJsonFormatVisitor(visitor, typeHint)
    }

    /*
     *******************************************************************************************************************
     * Overridable methods
     *******************************************************************************************************************
     */

    /**
     * Method called to convert from source Java value into delegate value (which will be serialized using standard
     * CirJackson serializer for delegate type)
     *
     * The default implementation uses configured [Converter] to do conversion.
     *
     * @param value Value to convert
     *
     * @return Result of conversion
     */
    protected open fun convertValue(value: Any): Any? {
        return myConverter.convert(value)
    }

    /**
     * Helper method used for locating serializer to use in dynamic use case, where actual type value gets converted to
     * is not specified beyond basic [Any], and where serializer needs to be located dynamically based on actual value
     * type.
     */
    protected open fun findSerializer(value: Any, context: SerializerProvider): ValueSerializer<Any> {
        val valueClass = value::class
        val result = myDynamicValueSerializers.findAndAddSecondarySerializer(valueClass, context, myProperty)

        if (myDynamicValueSerializers !== result.map) {
            myDynamicValueSerializers = result.map
        }

        return result.serializer
    }

}