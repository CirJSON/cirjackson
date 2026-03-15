package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.jdk.MapEntrySerializer.Companion.MARKER_FOR_EMPTY
import org.cirjson.cirjackson.databind.serialization.standard.StandardContainerSerializer
import org.cirjson.cirjackson.databind.util.ArrayBuilders
import org.cirjson.cirjackson.databind.util.BeanUtil
import org.cirjson.cirjackson.databind.util.isArray

@CirJacksonStandardImplementation
open class MapEntrySerializer : StandardContainerSerializer<Map.Entry<*, *>> {

    /**
     * Whether static types should be used for serialization of values or not (if not, dynamic runtime type is used)
     */
    protected val myValueTypeIsStatic: Boolean

    protected val myEntryType: KotlinType

    protected val myKeyType: KotlinType

    protected val myValueType: KotlinType

    /*
     *******************************************************************************************************************
     * Serializers used
     *******************************************************************************************************************
     */

    /**
     * Key serializer to use, if it can be statically determined
     */
    protected var myKeySerializer: ValueSerializer<Any>?

    /**
     * Value serializer to use, if it can be statically determined
     */
    protected var myValueSerializer: ValueSerializer<Any>?

    /**
     * Type identifier serializer used for values, if any.
     */
    protected val myValueTypeSerializer: TypeSerializer?

    /*
     *******************************************************************************************************************
     * Config settings, filtering
     *******************************************************************************************************************
     */

    /**
     * Value that indicates suppression mechanism to use for **values contained**; either "filter" (of which `equals()`
     * is called), or marker value of [MARKER_FOR_EMPTY], or `null` to indicate no filtering for non-`null` values. Note
     * that inclusion value for Map instance itself is handled by caller (POJO property that refers to the Map value).
     */
    protected val mySuppressableValue: Any?

    /**
     * Flag that indicates what to do with `null` values, distinct from handling of [mySuppressableValue]
     */
    protected val mySuppressNulls: Boolean

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    constructor(type: KotlinType, keyType: KotlinType, valueType: KotlinType, staticTyping: Boolean,
            valueTypeSerializer: TypeSerializer?, property: BeanProperty?) : super(type, property) {
        myEntryType = type
        myKeyType = keyType
        myValueType = valueType
        myValueTypeIsStatic = staticTyping
        myKeySerializer = null
        myValueSerializer = null
        myValueTypeSerializer = valueTypeSerializer
        mySuppressableValue = null
        mySuppressNulls = false
    }

    @Suppress("UNCHECKED_CAST")
    protected constructor(source: MapEntrySerializer, property: BeanProperty?, valueTypeSerializer: TypeSerializer?,
            keySerializer: ValueSerializer<*>?, valueSerializer: ValueSerializer<*>?, suppressableValue: Any?,
            suppressNulls: Boolean) : super(source, property) {
        myEntryType = source.myEntryType
        myKeyType = source.myKeyType
        myValueType = source.myValueType
        myValueTypeIsStatic = source.myValueTypeIsStatic
        myKeySerializer = keySerializer as ValueSerializer<Any>?
        myValueSerializer = valueSerializer as ValueSerializer<Any>?
        myValueTypeSerializer = valueTypeSerializer
        mySuppressableValue = suppressableValue
        mySuppressNulls = suppressNulls
    }

    override fun withValueTypeSerializerImplementation(
            valueTypeSerializer: TypeSerializer): StandardContainerSerializer<*> {
        return MapEntrySerializer(this, myProperty, valueTypeSerializer, myKeySerializer, myValueSerializer,
                mySuppressableValue, mySuppressNulls)
    }

    open fun withResolved(property: BeanProperty?, keySerializer: ValueSerializer<*>?,
            valueSerializer: ValueSerializer<*>?, suppressableValue: Any?, suppressNulls: Boolean): MapEntrySerializer {
        return MapEntrySerializer(this, property, myValueTypeSerializer, keySerializer, valueSerializer,
                suppressableValue, suppressNulls)
    }

    open fun withContentInclusion(suppressableValue: Any?, suppressNulls: Boolean): MapEntrySerializer {
        if (mySuppressableValue === suppressableValue && mySuppressNulls == suppressNulls) {
            return this
        }

        return MapEntrySerializer(this, myProperty, myValueTypeSerializer, myKeySerializer, myValueSerializer,
                suppressableValue, suppressNulls)
    }

    override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        var valueSerializer: ValueSerializer<*>? = null
        var keySerializer: ValueSerializer<*>? = null
        val introspector = provider.annotationIntrospector
        val member = property?.member

        if (member != null && introspector != null) {
            keySerializer = provider.serializerInstance(member, introspector.findKeySerializer(provider.config, member))
            valueSerializer =
                    provider.serializerInstance(member, introspector.findContentSerializer(provider.config, member))
        }

        if (valueSerializer == null) {
            valueSerializer = myValueSerializer
        }

        valueSerializer = findContextualConvertingSerializer(provider, property, valueSerializer)
                ?: if (myValueTypeIsStatic && !myValueType.isJavaLangObject) {
                    provider.findContentValueSerializer(myValueType, property)
                } else {
                    null
                }

        if (keySerializer == null) {
            keySerializer = myKeySerializer?.let { provider.handleSecondaryContextualization(it, property) }
                    ?: provider.findKeySerializer(myKeyType, property)
        }

        var valueToSuppress = mySuppressableValue
        var suppressNulls = mySuppressNulls

        property ?: return withResolved(null, keySerializer, valueSerializer, valueToSuppress, suppressNulls)
        val includeValue =
                property.findPropertyInclusion(provider.config, null) ?: return withResolved(property, keySerializer,
                        valueSerializer, valueToSuppress, suppressNulls)
        val include = includeValue.contentInclusion

        when (include) {
            CirJsonInclude.Include.USE_DEFAULTS -> {
                return withResolved(property, keySerializer, valueSerializer, valueToSuppress, suppressNulls)
            }

            CirJsonInclude.Include.NON_DEFAULT -> {
                suppressNulls = true
                valueToSuppress = BeanUtil.getDefaultValue(myValueType)

                if (valueToSuppress != null && valueToSuppress::class.isArray) {
                    valueToSuppress = ArrayBuilders.getArrayComparator(valueToSuppress)
                }
            }

            CirJsonInclude.Include.NON_ABSENT -> {
                suppressNulls = true
                valueToSuppress = MARKER_FOR_EMPTY.takeIf { myValueType.isReferenceType }
            }

            CirJsonInclude.Include.NON_EMPTY -> {
                suppressNulls = true
                valueToSuppress = MARKER_FOR_EMPTY
            }

            CirJsonInclude.Include.CUSTOM -> {
                valueToSuppress = provider.includeFilterInstance(null, includeValue.contentFilter)
                suppressNulls = valueToSuppress?.let { provider.includeFilterSuppressNulls(it) } ?: true
            }

            CirJsonInclude.Include.NON_NULL -> {
                suppressNulls = true
                valueToSuppress = null
            }

            CirJsonInclude.Include.ALWAYS -> {
                suppressNulls = false
                valueToSuppress = null
            }
        }

        return withResolved(property, keySerializer, valueSerializer, valueToSuppress, suppressNulls)
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    override val contentType: KotlinType
        get() = myValueType

    override val contentSerializer: ValueSerializer<*>?
        get() = myValueSerializer

    override fun hasSingleElement(value: Map.Entry<*, *>): Boolean {
        return true
    }

    override fun isEmpty(provider: SerializerProvider, value: Map.Entry<*, *>?): Boolean {
        val entryValue = value!!.value ?: return mySuppressNulls

        if (mySuppressableValue == null) {
            return false
        }

        val valueSerializer = myValueSerializer ?: entryValue::class.let {
            myDynamicValueSerializers.serializerFor(it) ?: findAndAddDynamic(provider, it)
        }

        if (mySuppressableValue === MARKER_FOR_EMPTY) {
            return valueSerializer.isEmpty(provider, entryValue)
        }

        return mySuppressableValue == entryValue
    }

    /*
     *******************************************************************************************************************
     * Serialization methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun serialize(value: Map.Entry<*, *>, generator: CirJsonGenerator, serializers: SerializerProvider) {
        generator.writeStartObject(value)
        serializeDynamic(value, generator, serializers)
        generator.writeEndObject()
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: Map.Entry<*, *>, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        generator.assignCurrentValue(value)
        val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                typeSerializer.typeId(value, CirJsonToken.START_OBJECT))
        serializeDynamic(value, generator, serializers)
        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

    @Throws(CirJacksonException::class)
    protected open fun serializeDynamic(value: Map.Entry<*, *>, generator: CirJsonGenerator,
            context: SerializerProvider) {
        val valueTypeSerializer = myValueTypeSerializer

        val keyElement = value.key
        val keySerializer =
                keyElement?.let { myKeySerializer!! } ?: context.findNullKeySerializer(myKeyType, myProperty)

        val valueElement = value.value

        val valueSerializer = if (valueElement == null) {
            if (mySuppressNulls) {
                return
            }

            context.defaultNullValueSerializer
        } else {
            val serializer = myValueSerializer ?: valueElement::class.let {
                myDynamicValueSerializers.serializerFor(it) ?: if (myValueType.hasGenericTypes()) {
                    findAndAddDynamic(context, context.constructSpecializedType(myValueType, it))
                } else {
                    findAndAddDynamic(context, it)
                }
            }

            if (mySuppressableValue != null) {
                if (mySuppressableValue === MARKER_FOR_EMPTY) {
                    if (serializer.isEmpty(context, valueElement)) {
                        return
                    }
                }

                if (mySuppressableValue == valueElement) {
                    return
                }
            }

            serializer
        }

        if (keyElement == null) {
            keySerializer.serializeNullable(null, generator, context)
        } else {
            keySerializer.serialize(keyElement, generator, context)
        }

        try {
            if (valueElement == null) {
                valueSerializer.serializeNullable(null, generator, context)
            } else if (valueTypeSerializer == null) {
                valueSerializer.serialize(valueElement, generator, context)
            } else {
                valueSerializer.serializeWithType(valueElement, generator, context, valueTypeSerializer)
            }
        } catch (e: Exception) {
            wrapAndThrow(context, e, value, keyElement.toString())
        }
    }

    companion object {

        val MARKER_FOR_EMPTY = CirJsonInclude.Include.NON_EMPTY

    }

}