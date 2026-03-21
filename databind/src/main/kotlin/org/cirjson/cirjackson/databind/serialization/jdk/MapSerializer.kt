package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.PropertyFilter
import org.cirjson.cirjackson.databind.serialization.jdk.MapSerializer.Companion.MARKER_FOR_EMPTY
import org.cirjson.cirjackson.databind.serialization.standard.StandardContainerSerializer
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.*
import java.util.*

/**
 * Standard serializer implementation for serializing [Map] types.
 * 
 * Note: about the only configurable setting currently is ability to filter out entries with specified names.
 */
@CirJacksonStandardImplementation
open class MapSerializer : StandardContainerSerializer<Map<*, *>> {

    /*
     *******************************************************************************************************************
     * Basic information about referring property, type
     *******************************************************************************************************************
     */

    /**
     * Whether static types should be used for serialization of values or not (if not, dynamic runtime type is used)
     */
    protected val myValueTypeIsStatic: Boolean

    /**
     * Declared type of keys
     */
    protected val myKeyType: KotlinType

    /**
     * Declared type of contained values
     */
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
     * Set of entries to omit during serialization, if any
     */
    protected val myIgnoredEntries: Set<String>?

    /**
     * Set of entries to include during serialization, if `null`, it is ignored, empty will include nothing.
     */
    protected val myIncludedEntries: Set<String>?

    /**
     * ID of the property filter to use, if any; `null` if none.
     */
    protected val myFilterId: Any?

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

    /**
     * Helper object used for name-based filtering
     */
    protected val myInclusionChecker: IgnorePropertiesUtil.Checker?

    /*
     *******************************************************************************************************************
     * Config settings, other
     *******************************************************************************************************************
     */

    /**
     * Flag set if output is forced to be sorted by keys (usually due to annotation).
     */
    protected val mySortKeys: Boolean

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    protected constructor(ignoredEntries: Set<String>?, includedEntries: Set<String>?, keyType: KotlinType,
            valueType: KotlinType, valueTypeIsStatic: Boolean, valueTypeSerializer: TypeSerializer?,
            keySerializer: ValueSerializer<*>?, valueSerializer: ValueSerializer<*>?) : super(Map::class) {
        myIgnoredEntries = ignoredEntries?.takeUnless { it.isEmpty() }
        myIncludedEntries = includedEntries
        myKeyType = keyType
        myValueType = valueType
        myValueTypeIsStatic = valueTypeIsStatic
        myValueTypeSerializer = valueTypeSerializer
        myKeySerializer = keySerializer as ValueSerializer<Any>?
        myValueSerializer = valueSerializer as ValueSerializer<Any>?
        myFilterId = null
        mySortKeys = false
        mySuppressableValue = null
        mySuppressNulls = false
        myInclusionChecker = IgnorePropertiesUtil.buildCheckerIfNeeded(myIgnoredEntries, myIgnoredEntries)
    }

    @Suppress("UNCHECKED_CAST")
    protected constructor(source: MapSerializer, property: BeanProperty?, keySerializer: ValueSerializer<*>?,
            valueSerializer: ValueSerializer<*>?, ignoredEntries: Set<String>?, includedEntries: Set<String>?) : super(
            source, property) {
        myIgnoredEntries = ignoredEntries?.takeUnless { it.isEmpty() }
        myIncludedEntries = includedEntries
        myKeyType = source.myKeyType
        myValueType = source.myValueType
        myValueTypeIsStatic = source.myValueTypeIsStatic
        myValueTypeSerializer = source.myValueTypeSerializer
        myKeySerializer = keySerializer as ValueSerializer<Any>?
        myValueSerializer = valueSerializer as ValueSerializer<Any>?
        myFilterId = source.myFilterId
        mySortKeys = source.mySortKeys
        mySuppressableValue = source.mySuppressableValue
        mySuppressNulls = source.mySuppressNulls
        myInclusionChecker = IgnorePropertiesUtil.buildCheckerIfNeeded(myIgnoredEntries, myIgnoredEntries)
    }

    protected constructor(source: MapSerializer, valueTypeSerializer: TypeSerializer?, suppressableValue: Any?,
            suppressNulls: Boolean) : super(source) {
        myIgnoredEntries = source.myIgnoredEntries
        myIncludedEntries = source.myIncludedEntries
        myKeyType = source.myKeyType
        myValueType = source.myValueType
        myValueTypeIsStatic = source.myValueTypeIsStatic
        myValueTypeSerializer = valueTypeSerializer
        myKeySerializer = source.myKeySerializer
        myValueSerializer = source.myValueSerializer
        myFilterId = source.myFilterId
        mySortKeys = source.mySortKeys
        mySuppressableValue = suppressableValue
        mySuppressNulls = suppressNulls
        myInclusionChecker = source.myInclusionChecker
    }

    protected constructor(source: MapSerializer, filterId: Any?, sortKeys: Boolean) : super(source) {
        myIgnoredEntries = source.myIgnoredEntries
        myIncludedEntries = source.myIncludedEntries
        myKeyType = source.myKeyType
        myValueType = source.myValueType
        myValueTypeIsStatic = source.myValueTypeIsStatic
        myValueTypeSerializer = source.myValueTypeSerializer
        myKeySerializer = source.myKeySerializer
        myValueSerializer = source.myValueSerializer
        myFilterId = filterId
        mySortKeys = sortKeys
        mySuppressableValue = source.mySuppressableValue
        mySuppressNulls = source.mySuppressNulls
        myInclusionChecker = source.myInclusionChecker
    }

    override fun withValueTypeSerializerImplementation(valueTypeSerializer: TypeSerializer): MapSerializer {
        if (myValueTypeSerializer === valueTypeSerializer) {
            return this
        }

        ensureOverride("withValueTypeSerializerImplementation")
        return MapSerializer(this, valueTypeSerializer, mySuppressableValue, mySuppressNulls)
    }

    protected open fun withResolved(property: BeanProperty?, keySerializer: ValueSerializer<*>?,
            valueSerializer: ValueSerializer<*>?, ignoredEntries: Set<String>?, includedEntries: Set<String>?,
            sortKeys: Boolean): MapSerializer {
        ensureOverride("withResolved")
        val serializer = MapSerializer(this, property, keySerializer, valueSerializer, ignoredEntries, includedEntries)
        return serializer.takeUnless { it.mySortKeys != sortKeys } ?: MapSerializer(serializer, myFilterId, sortKeys)
    }

    override fun withFilterId(filterId: Any?): MapSerializer {
        if (myFilterId === filterId) {
            return this
        }

        ensureOverride("withFilterId")
        return MapSerializer(this, filterId, mySortKeys)
    }

    /**
     * Mutant factory for constructing an instance with different inclusion strategy for content (Map values).
     */
    protected open fun withContentInclusion(suppressableValue: Any?, suppressNulls: Boolean): MapSerializer {
        if (mySuppressableValue === suppressableValue && mySuppressNulls == suppressNulls) {
            return this
        }

        ensureOverride("withContentInclusion")
        return MapSerializer(this, myValueTypeSerializer, suppressableValue, suppressNulls)
    }

    internal fun withContentInclusionInternal(suppressableValue: Any?, suppressNulls: Boolean): MapSerializer {
        return withContentInclusion(suppressableValue, suppressNulls)
    }

    protected open fun ensureOverride(method: String) {
        verifyMustOverride(MapSerializer::class, this, method)
    }

    /*
     *******************************************************************************************************************
     * Postprocessing (contextualization)
     *******************************************************************************************************************
     */

    override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        var valueSerializer: ValueSerializer<*>? = null
        var keySerializer: ValueSerializer<*>? = null
        val introspector = provider.annotationIntrospector!!
        val config = provider.config
        val member = property?.member

        if (member != null) {
            keySerializer = provider.serializerInstance(member, introspector.findKeySerializer(config, member))
            valueSerializer = provider.serializerInstance(member, introspector.findContentSerializer(config, member))
        }

        valueSerializer = findContextualConvertingSerializer(provider, property, valueSerializer ?: myValueSerializer)

        if (valueSerializer == null) {
            if (myValueTypeIsStatic && !myValueType.isJavaLangObject) {
                valueSerializer = provider.findContentValueSerializer(myValueType, property)
            }
        }

        if (keySerializer == null) {
            keySerializer = myKeySerializer
        }

        keySerializer = keySerializer?.let { provider.handleSecondaryContextualization(it, property) }
                ?: provider.findKeySerializer(myKeyType, property)

        var ignoredEntries = myIgnoredEntries
        var includedEntries = myIncludedEntries
        var sortKeys = false

        if (member != null) {
            val newIgnoredEntries =
                    introspector.findPropertyIgnoralByName(config, member)!!.findIgnoredForSerialization()

            if (newIgnoredEntries.isNotEmpty()) {
                ignoredEntries = ignoredEntries?.let { HashSet(it) } ?: HashSet()
                (ignoredEntries as MutableSet<String>).addAll(newIgnoredEntries)
            }

            val newIncludedEntries = introspector.findPropertyInclusionByName(config, member)!!.included

            if (newIncludedEntries != null) {
                includedEntries = includedEntries?.let { HashSet(it) } ?: HashSet()
                (includedEntries as MutableSet<String>).addAll(newIncludedEntries)
            }

            sortKeys = introspector.findSerializationSortAlphabetically(config, member) ?: false
        }

        val format = findFormatOverrides(provider, property, Map::class)
        format.getFeature(CirJsonFormat.Feature.WRITE_SORTED_MAP_ENTRIES)?.also { sortKeys = it }

        var serializer =
                withResolved(property, keySerializer, valueSerializer, ignoredEntries, includedEntries, sortKeys)

        if (member != null) {
            val filterId = introspector.findFilterId(config, member)

            if (filterId != null) {
                serializer = serializer.withFilterId(filterId)
            }
        }

        val includeValue = findIncludeOverrides(provider, property, Map::class) ?: return serializer
        val include = includeValue.contentInclusion

        val (valueToSuppress, suppressNulls) = when (include) {
            CirJsonInclude.Include.USE_DEFAULTS -> {
                return serializer
            }

            CirJsonInclude.Include.NON_DEFAULT -> {
                var value = BeanUtil.getDefaultValue(myValueType)

                if (value != null && value::class.isArray) {
                    value = ArrayBuilders.getArrayComparator(value)
                }

                value to true
            }

            CirJsonInclude.Include.NON_ABSENT -> {
                MARKER_FOR_EMPTY.takeIf { myValueType.isReferenceType } to true
            }

            CirJsonInclude.Include.NON_EMPTY -> {
                MARKER_FOR_EMPTY to true
            }

            CirJsonInclude.Include.CUSTOM -> {
                provider.includeFilterInstance(null, includeValue.contentFilter)
                        .let { value -> value to (value?.let { provider.includeFilterSuppressNulls(it) } ?: true) }
            }

            CirJsonInclude.Include.NON_NULL -> {
                null to true
            }

            CirJsonInclude.Include.ALWAYS -> {
                null to false
            }
        }

        return serializer.withContentInclusion(valueToSuppress, suppressNulls)
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

    override fun isEmpty(provider: SerializerProvider, value: Map<*, *>?): Boolean {
        if (value!!.isEmpty()) {
            return true
        }

        val suppressableValue = mySuppressableValue

        if (suppressableValue == null && !mySuppressNulls) {
            return false
        }

        var valueSerializer = myValueSerializer
        val checkEmpty = suppressableValue === MARKER_FOR_EMPTY

        if (valueSerializer != null) {
            for (element in value.values) {
                if (element == null) {
                    if (mySuppressNulls) {
                        continue
                    }

                    return false
                }

                if (checkEmpty) {
                    if (!valueSerializer.isEmpty(provider, element)) {
                        return false
                    }
                } else if (suppressableValue == null || suppressableValue != element) {
                    return false
                }
            }

            return true
        }

        for (element in value.values) {
            if (element == null) {
                if (mySuppressNulls) {
                    continue
                }

                return false
            }

            valueSerializer = findSerializer(provider, element)

            if (checkEmpty) {
                if (!valueSerializer.isEmpty(provider, element)) {
                    return false
                }
            } else if (suppressableValue == null || suppressableValue != element) {
                return false
            }
        }

        return true
    }

    override fun hasSingleElement(value: Map<*, *>): Boolean {
        return value.size == 1
    }

    /*
     *******************************************************************************************************************
     * Extended API
     *******************************************************************************************************************
     */

    /**
     * Accessor for currently assigned key serializer. Note that this may return `null` during construction of
     * `MapSerializer`: dependencies are resolved during [createContextual] method (which can be overridden by custom
     * implementations), but for some dynamic types, it is possible that serializer is only resolved during actual
     * serialization.
     */
    open val keySerializer: ValueSerializer<*>?
        get() = myKeySerializer

    /*
     *******************************************************************************************************************
     * ValueSerializer implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun serialize(value: Map<*, *>, generator: CirJsonGenerator, serializers: SerializerProvider) {
        generator.writeStartObject(value)
        serializeWithoutTypeInfo(value, generator, serializers)
        generator.writeEndObject()
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: Map<*, *>, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        generator.assignCurrentValue(value)
        val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                typeSerializer.typeId(value, CirJsonToken.START_OBJECT))
        serializeWithoutTypeInfo(value, generator, serializers)
        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

    /*
     *******************************************************************************************************************
     * Secondary serialization methods
     *******************************************************************************************************************
     */

    /**
     * General-purpose serialization for contents without writing object type. Will suppress, filter and use custom
     * serializers.
     */
    @Throws(CirJacksonException::class)
    open fun serializeWithoutTypeInfo(value: Map<*, *>, generator: CirJsonGenerator, context: SerializerProvider) {
        if (value.isEmpty()) {
            return
        }

        var realValue = if (mySortKeys || context.isEnabled(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)) {
            orderEntries(value, generator, context)
        } else {
            value
        }

        var propertyFilter: PropertyFilter? = null

        if (myFilterId != null && findPropertyFilter(context, myFilterId, realValue).also {
                    propertyFilter = it
                } != null) {
            serializeFilteredEntries(realValue, generator, context, propertyFilter!!, mySuppressableValue)
        } else if (mySuppressableValue != null || mySuppressNulls) {
            serializeOptionalFields(realValue, generator, context, mySuppressableValue)
        } else if (myValueSerializer != null) {
            serializeEntriesUsing(realValue, generator, context, myValueSerializer!!)
        } else {
            serializeEntries(realValue, generator, context)
        }
    }

    /**
     * Internal access to [serializeWithoutTypeInfo] for `AnyGetterWriter`.
     */
    @Throws(CirJacksonException::class)
    internal fun serializeWithoutTypeInfoInternal(value: Map<*, *>, generator: CirJsonGenerator,
            context: SerializerProvider) {
        serializeWithoutTypeInfo(value, generator, context)
    }

    /**
     * General-purpose serialization for contents, where we do not necessarily know the value serialization, but we do
     * know that no value suppression is needed (which simplifies processing a bit).
     */
    @Throws(CirJacksonException::class)
    protected open fun serializeEntries(value: Map<*, *>, generator: CirJsonGenerator, context: SerializerProvider) {
        if (myValueTypeSerializer != null) {
            serializeTypedEntries(value, generator, context, null)
            return
        }

        val keySerializer = myKeySerializer
        var keyElement: Any? = null

        try {
            for (entry in value) {
                val valueElement = entry.value
                keyElement = entry.key

                if (keyElement == null) {
                    context.findNullKeySerializer(myKeyType, myProperty).serializeNullable(null, generator, context)
                } else {
                    if (myInclusionChecker?.shouldIgnore(keyElement) ?: false) {
                        continue
                    }

                    keySerializer!!.serialize(keyElement, generator, context)
                }

                if (valueElement == null) {
                    context.defaultSerializeNullValue(generator)
                    continue
                }

                val valueSerializer = myValueSerializer ?: findSerializer(context, valueElement)
                valueSerializer.serialize(valueElement, generator, context)
            }
        } catch (e: Exception) {
            wrapAndThrow(context, e, value, keyElement.toString())
        }
    }

    /**
     * General-purpose serialization for contents, where we do not necessarily know the value serialization, but we do
     * know that no value suppression is needed (which simplifies processing a bit).
     * 
     * NOTE: `public` only because it is called by code from `Guava` `TableSerializer`
     */
    @Throws(CirJacksonException::class)
    fun serializeEntriesPublic(value: Map<*, *>, generator: CirJsonGenerator, context: SerializerProvider) {
        serializeEntries(value, generator, context)
    }

    /**
     * Serialization method called when exclusion filtering needs to be applied.
     */
    @Throws(CirJacksonException::class)
    protected open fun serializeOptionalFields(value: Map<*, *>, generator: CirJsonGenerator,
            context: SerializerProvider, suppressableValue: Any?) {
        if (myValueTypeSerializer != null) {
            serializeTypedEntries(value, generator, context, suppressableValue)
            return
        }

        val checkEmpty = suppressableValue === MARKER_FOR_EMPTY

        for ((keyElement, valueElement) in value) {
            val keySerializer = if (keyElement == null) {
                context.findNullKeySerializer(myKeyType, myProperty)
            } else {
                if (myInclusionChecker?.shouldIgnore(keyElement) ?: false) {
                    continue
                }

                myKeySerializer!!
            }

            val valueSerializer = if (valueElement == null) {
                context.defaultNullValueSerializer
            } else {
                val serializer = myValueSerializer ?: findSerializer(context, valueElement)

                if (checkEmpty) {
                    if (serializer.isEmpty(context, valueElement)) {
                        continue
                    }
                } else if (suppressableValue != null) {
                    if (suppressableValue == valueElement) {
                        continue
                    }
                }

                serializer
            }

            try {
                if (keyElement == null) {
                    keySerializer.serializeNullable(null, generator, context)
                } else {
                    keySerializer.serialize(keyElement, generator, context)
                }

                if (valueElement == null) {
                    valueSerializer.serializeNullable(null, generator, context)
                } else {
                    valueSerializer.serialize(valueElement, generator, context)
                }
            } catch (e: Exception) {
                wrapAndThrow(context, e, value, keyElement.toString())
            }
        }
    }

    /**
     * Method called to serialize fields, when the value type is statically known, so that value serializer is passed
     * and does not need to be fetched from provider.
     */
    @Throws(CirJacksonException::class)
    protected open fun serializeEntriesUsing(value: Map<*, *>, generator: CirJsonGenerator, context: SerializerProvider,
            serializer: ValueSerializer<Any>) {
        val keySerializer = myKeySerializer
        val typeSerializer = myValueTypeSerializer

        for ((keyElement, valueElement) in value) {
            if (myInclusionChecker?.shouldIgnore(keyElement) ?: false) {
                continue
            }

            if (keyElement == null) {
                context.findNullKeySerializer(myKeyType, myProperty).serializeNullable(null, generator, context)
            } else {
                keySerializer!!.serialize(keyElement, generator, context)
            }

            if (valueElement == null) {
                context.defaultSerializeNullValue(generator)
                continue
            }

            try {
                if (typeSerializer == null) {
                    serializer.serialize(valueElement, generator, context)
                } else {
                    serializer.serializeWithType(valueElement, generator, context, typeSerializer)
                }
            } catch (e: Exception) {
                wrapAndThrow(context, e, value, keyElement.toString())
            }
        }
    }

    /**
     * Helper method used when we have a CirJSON Filter to use for potentially filtering out Map entries.
     */
    @Throws(CirJacksonException::class)
    protected open fun serializeFilteredEntries(value: Map<*, *>, generator: CirJsonGenerator,
            context: SerializerProvider, filter: PropertyFilter, suppressableValue: Any?) {
        val property = MapProperty(myValueTypeSerializer, myProperty)
        val checkEmpty = suppressableValue === MARKER_FOR_EMPTY

        for ((keyElement, valueElement) in value) {
            if (myInclusionChecker?.shouldIgnore(keyElement) ?: false) {
                continue
            }

            val keySerializer = if (keyElement == null) {
                context.findNullKeySerializer(myKeyType, myProperty)
            } else {
                myKeySerializer
            }

            val valueSerializer = if (valueElement == null) {
                if (mySuppressNulls) {
                    continue
                }

                context.defaultNullValueSerializer
            } else {
                val serializer = myValueSerializer ?: findSerializer(context, valueElement)

                if (checkEmpty) {
                    if (serializer.isEmpty(context, valueElement)) {
                        continue
                    }
                } else if (suppressableValue != null) {
                    if (suppressableValue == valueElement) {
                        continue
                    }
                }

                serializer
            }

            property.reset(keyElement, valueElement, keySerializer, valueSerializer)

            try {
                filter.serializeAsProperty(value, generator, context, property)
            } catch (e: Exception) {
                wrapAndThrow(context, e, value, keyElement.toString())
            }
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun serializeTypedEntries(value: Map<*, *>, generator: CirJsonGenerator, context: SerializerProvider,
            suppressableValue: Any?) {
        val checkEmpty = suppressableValue === MARKER_FOR_EMPTY

        for ((keyElement, valueElement) in value) {
            val keySerializer = if (keyElement == null) {
                context.findNullKeySerializer(myKeyType, myProperty)
            } else {
                if (myInclusionChecker?.shouldIgnore(keyElement) ?: false) {
                    continue
                }

                myKeySerializer!!
            }

            val valueSerializer = if (valueElement == null) {
                if (mySuppressNulls) {
                    continue
                }

                context.defaultNullValueSerializer
            } else {
                val serializer = myValueSerializer ?: findSerializer(context, valueElement)

                if (checkEmpty) {
                    if (serializer.isEmpty(context, valueElement)) {
                        continue
                    }
                } else if (suppressableValue != null) {
                    if (suppressableValue == valueElement) {
                        continue
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
                } else {
                    valueSerializer.serializeWithType(valueElement, generator, context, myValueTypeSerializer!!)
                }
            } catch (e: Exception) {
                wrapAndThrow(context, e, value, keyElement.toString())
            }
        }
    }

    /**
     * Helper method used when we have a CirJSON Filter to use AND contents are "any properties" of a POJO.
     *
     * @param bean Enclosing POJO that has any-getter used to obtain "any properties"
     */
    @Throws(CirJacksonException::class)
    protected open fun serializeFilteredAnyProperties(context: SerializerProvider, generator: CirJsonGenerator,
            bean: Any, value: Map<*, *>, filter: PropertyFilter, suppressableValue: Any?) {
        val property = MapProperty(myValueTypeSerializer, myProperty)
        val checkEmpty = suppressableValue === MARKER_FOR_EMPTY

        for ((keyElement, valueElement) in value) {
            if (myInclusionChecker?.shouldIgnore(keyElement) ?: false) {
                continue
            }

            val keySerializer = if (keyElement == null) {
                context.findNullKeySerializer(myKeyType, myProperty)
            } else {
                myKeySerializer
            }

            val valueSerializer = if (valueElement == null) {
                if (mySuppressNulls) {
                    continue
                }

                context.defaultNullValueSerializer
            } else {
                val serializer = myValueSerializer ?: findSerializer(context, valueElement)

                if (checkEmpty) {
                    if (serializer.isEmpty(context, valueElement)) {
                        continue
                    }
                } else if (suppressableValue != null) {
                    if (suppressableValue == valueElement) {
                        continue
                    }
                }

                serializer
            }

            property.reset(keyElement, valueElement, keySerializer, valueSerializer)

            try {
                filter.serializeAsProperty(bean, generator, context, property)
            } catch (e: Exception) {
                wrapAndThrow(context, e, value, keyElement.toString())
            }
        }
    }

    /**
     * Internal access to [serializeFilteredAnyProperties] for `AnyGetterWriter`.
     */
    @Throws(CirJacksonException::class)
    internal fun serializeFilteredAnyPropertiesInternal(context: SerializerProvider, generator: CirJsonGenerator,
            bean: Any, value: Map<*, *>, filter: PropertyFilter, suppressableValue: Any?) {
        serializeFilteredAnyProperties(context, generator, bean, value, filter, suppressableValue)
    }

    /*
     *******************************************************************************************************************
     * Schema related functionality
     *******************************************************************************************************************
     */

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitor.expectMapFormat(typeHint)?.apply {
            keyFormat(myKeySerializer!!, myKeyType)
            valueFormat(myValueSerializer ?: findAndAddDynamic(visitor.provider!!, myValueType), myValueType)
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun orderEntries(input: Map<*, *>, generator: CirJsonGenerator,
            context: SerializerProvider): Map<*, *> {
        if (input is SortedMap<*, *>) {
            return input
        }

        if (!hasNullKey(input)) {
            return TreeMap(input)
        }

        val result = TreeMap<Any?, Any?>()

        for ((key, value) in input) {
            if (key == null) {
                writeNullKeyedEntry(generator, context, value)
                continue
            }

            result[key] = value
        }

        return result
    }

    protected open fun hasNullKey(input: Map<*, *>): Boolean {
        return input is HashMap<*, *> && null in input
    }

    @Throws(CirJacksonException::class)
    protected open fun writeNullKeyedEntry(generator: CirJsonGenerator, context: SerializerProvider, value: Any?) {
        val keySerializer = context.findNullKeySerializer(myKeyType, myProperty)

        val valueSerializer = if (value == null) {
            if (mySuppressNulls) {
                return
            }

            context.defaultNullValueSerializer
        } else {
            val serializer = myValueSerializer ?: findSerializer(context, value)

            if (mySuppressableValue === MARKER_FOR_EMPTY) {
                if (serializer.isEmpty(context, value)) {
                    return
                }
            } else if (mySuppressableValue != null && mySuppressableValue == value) {
                return
            }

            serializer
        }

        try {
            keySerializer.serializeNullable(null, generator, context)

            if (value == null) {
                valueSerializer.serializeNullable(null, generator, context)
            } else {
                valueSerializer.serialize(value, generator, context)
            }
        } catch (e: Exception) {
            wrapAndThrow(context, e, value, "")
        }
    }

    private fun findSerializer(context: SerializerProvider, value: Any): ValueSerializer<Any> {
        val valueClass = value::class

        val valueSerializer = myDynamicValueSerializers.serializerFor(valueClass)

        if (valueSerializer != null) {
            return valueSerializer
        }

        return if (myValueType.hasGenericTypes()) {
            findAndAddDynamic(context, context.constructSpecializedType(myValueType, valueClass))
        } else {
            findAndAddDynamic(context, valueClass)
        }
    }

    companion object {

        val UNSPECIFIED_TYPE = TypeFactory.unknownType()

        val MARKER_FOR_EMPTY = CirJsonInclude.Include.NON_EMPTY

        fun construct(mapType: KotlinType?, staticValueType: Boolean, valueTypeSerializer: TypeSerializer?,
                keySerializer: ValueSerializer<*>?, valueSerializer: ValueSerializer<*>?, filterId: Any?,
                ignoredEntries: Set<String>?, includedEntries: Set<String>?): MapSerializer {
            val (keyType, valueType) = if (mapType == null) {
                UNSPECIFIED_TYPE to UNSPECIFIED_TYPE
            } else {
                val key = mapType.keyType!!

                val value = if (mapType.hasRawClass(Properties::class)) {
                    TypeFactory.unknownType()
                } else {
                    mapType.contentType!!
                }

                key to value
            }

            val realStaticValueType = if (!staticValueType) {
                valueType.isFinal
            } else if (valueType.rawClass == Any::class) {
                false
            } else {
                staticValueType
            }

            val serializer = MapSerializer(ignoredEntries, includedEntries, keyType, valueType, realStaticValueType,
                    valueTypeSerializer, keySerializer, valueSerializer)
            return filterId?.let { serializer.withFilterId(it) } ?: serializer
        }

    }

}