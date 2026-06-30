package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.*
import org.cirjson.cirjackson.databind.deserialization.bean.PropertyBasedCreator
import org.cirjson.cirjackson.databind.deserialization.standard.ContainerDeserializerBase
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.IgnorePropertiesUtil
import kotlin.reflect.KClass

/**
 * Basic deserializer that can take JSON "Object" structure and construct a [Map] instance, with typed contents.
 * 
 * Note: for untyped content (one indicated by passing `Any::class` as the type), [UntypedObjectDeserializer] is used
 * instead. It can also construct [Maps][Map], but not with specific POJO types, only other containers and
 * primitives/wrappers.
 */
@CirJacksonStandardImplementation
open class MapDeserializer : ContainerDeserializerBase<MutableMap<Any?, Any?>> {

    /**
     * Key deserializer to use; either passed via constructor (when indicated by annotations), or resolved when
     * [resolve] is called;
     */
    protected val myKeyDeserializer: KeyDeserializer?

    /**
     * Flag set to indicate that the key type is [String] (or [Any], for which String is acceptable), **and** that the
     * default CirJackson key deserializer would be used. If both are true, can optimize handling.
     */
    protected var myStandardStringKey: Boolean

    /**
     * Value deserializer.
     */
    protected val myValueDeserializer: ValueDeserializer<Any>?

    /**
     * If value instances have polymorphic type information, this is the type deserializer that can handle it
     */
    protected val myValueTypeDeserializer: TypeDeserializer?

    protected val myValueInstantiator: ValueInstantiator

    /**
     * Deserializer that is used iff delegate-based creator is to be used for deserializing from CirJSON Object.
     */
    protected var myDelegateDeserializer: ValueDeserializer<Any>?

    /**
     * If the Map is to be instantiated using non-default constructor or factory method that takes one or more named
     * properties as argument(s), this creator is used for instantiation.
     */
    protected var myPropertyBasedCreator: PropertyBasedCreator?

    protected val myHasDefaultCreator: Boolean

    protected var myIgnorableProperties: Set<String>?

    protected var myIncludableProperties: Set<String>?

    /**
     * Helper object used for name-based filtering
     */
    protected var myInclusionChecker: IgnorePropertiesUtil.Checker?

    /**
     * Flag used to check, whether the
     * [StreamReadCapability.DUPLICATE_PROPERTIES][org.cirjson.cirjackson.core.StreamReadCapability.DUPLICATE_PROPERTIES]
     * can be applied, because the Map has declared value type of `Any`.
     */
    protected var myCheckDuplicateSquash: Boolean

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor(mapType: KotlinType, valueInstantiator: ValueInstantiator, keyDeserializer: KeyDeserializer?,
            valueDeserializer: ValueDeserializer<Any>?, valueTypeDeserializer: TypeDeserializer?) : super(mapType, null,
            null) {
        myKeyDeserializer = keyDeserializer
        myStandardStringKey = isStandardKeyDeserializer(mapType, keyDeserializer)
        myValueDeserializer = valueDeserializer
        myValueTypeDeserializer = valueTypeDeserializer
        myValueInstantiator = valueInstantiator
        myDelegateDeserializer = null
        myPropertyBasedCreator = null
        myHasDefaultCreator = valueInstantiator.canCreateUsingDefault()
        myIgnorableProperties = null
        myIncludableProperties = null
        myInclusionChecker = null
        myCheckDuplicateSquash = mapType.contentType!!.hasRawClass(Any::class)
    }

    protected constructor(source: MapDeserializer) : super(source) {
        myKeyDeserializer = source.myKeyDeserializer
        myStandardStringKey = source.myStandardStringKey
        myValueDeserializer = source.myValueDeserializer
        myValueTypeDeserializer = source.myValueTypeDeserializer
        myValueInstantiator = source.myValueInstantiator
        myDelegateDeserializer = source.myDelegateDeserializer
        myPropertyBasedCreator = source.myPropertyBasedCreator
        myHasDefaultCreator = source.myHasDefaultCreator
        myIgnorableProperties = source.myIgnorableProperties
        myIncludableProperties = source.myIncludableProperties
        myInclusionChecker = source.myInclusionChecker
        myCheckDuplicateSquash = source.myCheckDuplicateSquash
    }

    protected constructor(source: MapDeserializer, keyDeserializer: KeyDeserializer?,
            valueDeserializer: ValueDeserializer<Any>?, valueTypeDeserializer: TypeDeserializer?,
            nullValueProvider: NullValueProvider?, ignorableProperties: Set<String>?) : this(source, keyDeserializer,
            valueDeserializer, valueTypeDeserializer, nullValueProvider, ignorableProperties, null)

    protected constructor(source: MapDeserializer, keyDeserializer: KeyDeserializer?,
            valueDeserializer: ValueDeserializer<Any>?, valueTypeDeserializer: TypeDeserializer?,
            nullValueProvider: NullValueProvider?, ignorableProperties: Set<String>?,
            includableProperties: Set<String>?) : super(source, nullValueProvider, null) {
        myKeyDeserializer = keyDeserializer
        myStandardStringKey = isStandardKeyDeserializer(myContainerType, keyDeserializer)
        myValueDeserializer = valueDeserializer
        myValueTypeDeserializer = valueTypeDeserializer
        myValueInstantiator = source.myValueInstantiator
        myDelegateDeserializer = source.myDelegateDeserializer
        myPropertyBasedCreator = source.myPropertyBasedCreator
        myHasDefaultCreator = source.myHasDefaultCreator
        myIgnorableProperties = ignorableProperties
        myIncludableProperties = includableProperties
        myInclusionChecker = IgnorePropertiesUtil.buildCheckerIfNeeded(includableProperties, includableProperties)
        myCheckDuplicateSquash = source.myCheckDuplicateSquash
    }

    /**
     * Fluent factory method used to create a copy with slightly different settings. When subclassing, MUST be
     * overridden.
     */
    protected open fun withResolved(keyDeserializer: KeyDeserializer?, valueDeserializer: ValueDeserializer<Any>?,
            valueTypeDeserializer: TypeDeserializer?, nullValueProvider: NullValueProvider?,
            ignorableProperties: Set<String>?): MapDeserializer {
        return withResolved(keyDeserializer, valueDeserializer, valueTypeDeserializer, nullValueProvider,
                ignorableProperties, myIgnorableProperties)
    }

    /**
     * Fluent factory method used to create a copy with slightly different settings. When subclassing, MUST be
     * overridden.
     */
    protected open fun withResolved(keyDeserializer: KeyDeserializer?, valueDeserializer: ValueDeserializer<Any>?,
            valueTypeDeserializer: TypeDeserializer?, nullValueProvider: NullValueProvider?,
            ignorableProperties: Set<String>?, includableProperties: Set<String>?): MapDeserializer {
        if (myKeyDeserializer === keyDeserializer && myValueDeserializer === valueDeserializer &&
                myValueTypeDeserializer === valueTypeDeserializer && myNullProvider === nullValueProvider &&
                myIgnorableProperties === ignorableProperties && myIncludableProperties === includableProperties) {
            return this
        }

        return MapDeserializer(this, keyDeserializer, valueDeserializer, valueTypeDeserializer, nullValueProvider,
                ignorableProperties, includableProperties)
    }

    /**
     * Helper method used to check whether we can just use the default key deserialization, where CirJSON String becomes
     * Kotlin String.
     */
    protected fun isStandardKeyDeserializer(mapType: KotlinType, keyDeserializer: KeyDeserializer?): Boolean {
        keyDeserializer ?: return true
        val keyType = mapType.keyType ?: return true
        val rawKeyType = keyType.rawClass
        return (rawKeyType == String::class || rawKeyType == Any::class) && isDefaultKeySerializer(keyDeserializer)
    }

    open var ignorableProperties: Set<String>?
        get() = throw UnsupportedOperationException()
        set(ignorableProperties) {
            myIgnorableProperties = ignorableProperties?.takeUnless { it.isEmpty() }
            myInclusionChecker =
                    IgnorePropertiesUtil.buildCheckerIfNeeded(myIgnorableProperties, myIncludableProperties)
        }

    open var includableProperties: Set<String>?
        get() = throw UnsupportedOperationException()
        set(includableProperties) {
            myIncludableProperties = includableProperties?.takeUnless { it.isEmpty() }
            myInclusionChecker =
                    IgnorePropertiesUtil.buildCheckerIfNeeded(myIgnorableProperties, myIncludableProperties)
        }

    /*
     *******************************************************************************************************************
     * Validation, post-processing (ResolvableDeserializer)
     *******************************************************************************************************************
     */

    override fun resolve(context: DeserializationContext) {
        if (myValueInstantiator.canCreateUsingDelegate()) {
            val delegateType =
                    myValueInstantiator.getDelegateType(context.config) ?: return context.reportBadDefinition(
                            myContainerType,
                            "Invalid delegate-creator definition for $myContainerType: value instantiator (${myValueInstantiator::class.qualifiedName}) returned true for 'canCreateUsingDelegate()', but null for 'getDelegateType()'")
            myDelegateDeserializer = findDeserializer(context, delegateType, null)
        } else if (myValueInstantiator.canCreateUsingArrayDelegate()) {
            val delegateType =
                    myValueInstantiator.getArrayDelegateType(context.config) ?: return context.reportBadDefinition(
                            myContainerType,
                            "Invalid delegate-creator definition for $myContainerType: value instantiator (${myValueInstantiator::class.qualifiedName}) returned true for 'canCreateUsingArrayDelegate()', but null for 'getArrayDelegateType()'")
            myDelegateDeserializer = findDeserializer(context, delegateType, null)
        }

        if (myValueInstantiator.canCreateFromObjectWith()) {
            val creatorProperties = myValueInstantiator.getFromObjectArguments(context.config)!!
            myPropertyBasedCreator = PropertyBasedCreator.construct(context, myValueInstantiator, creatorProperties,
                    context.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES))
        }

        myStandardStringKey = isStandardKeyDeserializer(myContainerType, myKeyDeserializer)
    }

    /**
     * Method called to finalize setup of this deserializer, when it is known for which property deserializer is needed
     * for.
     */
    @Suppress("UNCHECKED_CAST")
    override fun createContextual(context: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        val keyDeserializer =
                myKeyDeserializer?.let { (it as? ContextualKeyDeserializer)?.createContextual(context, property) ?: it }
                        ?: context.findKeyDeserializer(myContainerType.keyType!!, property)
        val valueType = myContainerType.contentType!!
        val valueDeserializer =
                (property?.let { findConvertingContentDeserializer(context, property, myValueDeserializer) }
                        ?: myValueDeserializer)?.let {
                    context.handleSecondaryContextualization(it, property, valueType) as ValueDeserializer<Any>?
                } ?: context.findContextualValueDeserializer(valueType, property)
        val valueTypeDeserializer = myValueTypeDeserializer?.forProperty(property)
        val nullValueProvider = findContentNullProvider(context, property, valueDeserializer)
        var ignorableProperties = myIgnorableProperties
        var includableProperties = myIncludableProperties
        val introspector = context.annotationIntrospector

        if (introspector == null || property == null) {
            return withResolved(keyDeserializer, valueDeserializer, valueTypeDeserializer, nullValueProvider,
                    ignorableProperties, includableProperties)
        }

        val member = property.member ?: return withResolved(keyDeserializer, valueDeserializer, valueTypeDeserializer,
                nullValueProvider, ignorableProperties, includableProperties)
        val ignorals = introspector.findPropertyIgnoralByName(context.config, member)

        if (ignorals != null) {
            val ignorableToAdd = ignorals.findIgnoredForDeserialization()

            if (ignorableToAdd.isNotEmpty()) {
                ignorableProperties = ignorableProperties?.let { HashSet(it) } ?: HashSet()
                (ignorableProperties as MutableSet<String>).addAll(ignorableToAdd)
            }
        }

        val inclusions = introspector.findPropertyInclusionByName(context.config, member)
        val includableToAdd = inclusions?.included

        if (includableToAdd != null) {
            includableProperties = if (includableProperties == null) {
                HashSet(includableToAdd)
            } else {
                HashSet<String>().also {
                    for (string in includableToAdd) {
                        it.add(string)
                    }
                }
            }
        }

        return withResolved(keyDeserializer, valueDeserializer, valueTypeDeserializer, nullValueProvider,
                ignorableProperties, includableProperties)
    }

    /**
     * Turns out that these are expensive enough to create so that caching does make sense.
     * 
     * IMPORTANT: but, note, that instances CAN NOT BE CACHED if there is a value type deserializer. It is also possible
     * that some other settings could make deserializers un-cacheable; but on the other hand, caching can make a big
     * positive difference with performance... so it's a hard choice.
     */
    override val isCacheable: Boolean
        get() = myValueDeserializer == null && myKeyDeserializer == null && myValueTypeDeserializer == null &&
                myIgnorableProperties == null && myIncludableProperties == null

    override fun logicalType(): LogicalType {
        return LogicalType.MAP
    }

    /*
     *******************************************************************************************************************
     * ContainerDeserializerBase implementation
     *******************************************************************************************************************
     */

    override val contentDeserializer: ValueDeserializer<Any>?
        get() = myValueDeserializer

    override val valueInstantiator: ValueInstantiator
        get() = myValueInstantiator

    /*
     *******************************************************************************************************************
     * ValueDeserializer implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): MutableMap<Any?, Any?>? {
        return if (myPropertyBasedCreator != null) {
            deserializeUsingCreator(parser, context)
        } else if (myDelegateDeserializer != null) {
            myValueInstantiator.createUsingDelegate(context,
                    myDelegateDeserializer!!.deserialize(parser, context)) as MutableMap<Any?, Any?>?
        } else if (!myHasDefaultCreator) {
            context.handleMissingInstantiator(mapClass, valueInstantiator, parser,
                    "no default constructor found") as MutableMap<Any?, Any?>?
        } else {
            when (parser.currentTokenId()) {
                CirJsonTokenId.ID_START_OBJECT, CirJsonTokenId.ID_END_OBJECT, CirJsonTokenId.ID_PROPERTY_NAME -> {
                    val result = myValueInstantiator.createUsingDefault(context)!! as MutableMap<Any?, Any?>

                    if (myStandardStringKey) {
                        readAndBindStringKeyMap(parser, context, result)
                    } else {
                        readAndBind(parser, context, result)
                    }
                }

                CirJsonTokenId.ID_STRING -> {
                    deserializeFromString(parser, context)
                }

                CirJsonTokenId.ID_START_ARRAY -> {
                    deserializeFromArray(parser, context)
                }

                else -> {
                    context.handleUnexpectedToken(getValueType(context), parser) as MutableMap<Any?, Any?>?
                }
            }
        }
    }

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext,
            intoValue: MutableMap<Any?, Any?>): MutableMap<Any?, Any?>? {
        parser.assignCurrentValue(intoValue)

        val token = parser.currentToken()

        return if (token != CirJsonToken.START_OBJECT && token != CirJsonToken.PROPERTY_NAME) {
            context.handleUnexpectedToken(getValueType(context), parser) as MutableMap<Any?, Any?>?
        } else if (myStandardStringKey) {
            readAndUpdateStringKeyMap(parser, context, intoValue)
            intoValue
        } else {
            readAndUpdate(parser, context, intoValue)
            intoValue
        }
    }

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        return typeDeserializer.deserializeTypedFromObject(parser, context)
    }

    /*
     *******************************************************************************************************************
     * Other public accessors
     *******************************************************************************************************************
     */

    val mapClass: KClass<*>
        get() = myContainerType.rawClass

    /*
     *******************************************************************************************************************
     * Internal methods, non-merging deserialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun readAndBind(parser: CirJsonParser, context: DeserializationContext,
            result: MutableMap<Any?, Any?>): MutableMap<Any?, Any?> {
        val keyDeserializer = myKeyDeserializer!!
        val nullValueProvider = myNullProvider!!
        val valueDeserializer = myValueDeserializer!!
        val typeDeserializer = myValueTypeDeserializer

        val useObjectId = valueDeserializer.getObjectIdReader(context) != null

        val accumulator = if (useObjectId) {
            MapReferringAccumulator(myContainerType.contentType!!.rawClass, result)
        } else {
            null
        }

        var keyString = if (parser.isExpectedStartObjectToken) {
            parser.nextName()
        } else {
            val token = parser.currentToken()

            if (token != CirJsonToken.PROPERTY_NAME) {
                if (token != CirJsonToken.END_OBJECT) {
                    return context.reportWrongTokenException(this, CirJsonToken.PROPERTY_NAME, null)
                }

                return result
            }

            parser.currentName()
        }

        while (keyString != null) {
            val key = keyDeserializer.deserializeKey(keyString, context)
            val token = parser.nextToken()

            if (myInclusionChecker?.shouldIgnore(keyString) ?: false) {
                parser.skipChildren()
                keyString = parser.nextName()
                continue
            }

            try {
                val value = if (token == CirJsonToken.VALUE_NULL) {
                    if (mySkipNullValues) {
                        keyString = parser.nextName()
                        continue
                    }

                    nullValueProvider.getNullValue(context)
                } else if (typeDeserializer == null) {
                    valueDeserializer.deserialize(parser, context)
                } else {
                    valueDeserializer.deserializeWithType(parser, context, typeDeserializer)
                }

                if (accumulator != null) {
                    accumulator[key] = value
                } else {
                    val oldValue = result.put(key, value)

                    if (oldValue != null) {
                        squashDuplicates(context, result, key, oldValue, value)
                    }
                }
            } catch (reference: UnresolvedForwardReferenceException) {
                handleUnresolvedReference(context, accumulator, key, reference)
            } catch (e: Exception) {
                wrapAndThrow(context, e, result, keyString)
            }

            keyString = parser.nextName()
        }

        return result
    }

    /**
     * Optimized method used when keys can be deserialized as plain old [Strings][String], and there is no custom
     * deserialized specified.
     */
    @Throws(CirJacksonException::class)
    protected fun readAndBindStringKeyMap(parser: CirJsonParser, context: DeserializationContext,
            result: MutableMap<Any?, Any?>): MutableMap<Any?, Any?> {
        val nullValueProvider = myNullProvider!!
        val valueDeserializer = myValueDeserializer!!
        val typeDeserializer = myValueTypeDeserializer

        val useObjectId = valueDeserializer.getObjectIdReader(context) != null

        val accumulator = if (useObjectId) {
            MapReferringAccumulator(myContainerType.contentType!!.rawClass, result)
        } else {
            null
        }

        var key = if (parser.isExpectedStartObjectToken) {
            parser.nextName()
        } else {
            val token = parser.currentToken()

            if (token != CirJsonToken.PROPERTY_NAME) {
                if (token != CirJsonToken.END_OBJECT) {
                    return context.reportWrongTokenException(this, CirJsonToken.PROPERTY_NAME, null)
                }

                return result
            }

            parser.currentName()
        }

        while (key != null) {
            val token = parser.nextToken()

            if (myInclusionChecker?.shouldIgnore(token) ?: false) {
                parser.skipChildren()
                key = parser.nextName()
                continue
            }

            try {
                val value = if (token == CirJsonToken.VALUE_NULL) {
                    if (mySkipNullValues) {
                        key = parser.nextName()
                        continue
                    }

                    nullValueProvider.getNullValue(context)
                } else if (typeDeserializer == null) {
                    valueDeserializer.deserialize(parser, context)
                } else {
                    valueDeserializer.deserializeWithType(parser, context, typeDeserializer)
                }

                if (accumulator != null) {
                    accumulator[key] = value
                } else {
                    val oldValue = result.put(key, value)

                    if (oldValue != null) {
                        squashDuplicates(context, result, key, oldValue, value)
                    }
                }
            } catch (reference: UnresolvedForwardReferenceException) {
                handleUnresolvedReference(context, accumulator, key, reference)
            } catch (e: Exception) {
                wrapAndThrow(context, e, result, key)
            }

            key = parser.nextName()
        }

        return result
    }

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    protected open fun deserializeUsingCreator(parser: CirJsonParser,
            context: DeserializationContext): MutableMap<Any?, Any?>? {
        val creator = myPropertyBasedCreator!!
        val buffer = creator.startBuilding(parser, context, null)

        val keyDeserializer = myKeyDeserializer!!
        val nullValueProvider = myNullProvider!!
        val valueDeserializer = myValueDeserializer!!
        val typeDeserializer = myValueTypeDeserializer

        var keyString = if (parser.isExpectedStartObjectToken) {
            parser.nextName()
        } else if (parser.hasToken(CirJsonToken.PROPERTY_NAME)) {
            parser.currentName()
        } else {
            null
        }

        while (keyString != null) {
            val token = parser.nextToken()

            if (myInclusionChecker?.shouldIgnore(token) ?: false) {
                parser.skipChildren()
                keyString = parser.nextName()
                continue
            }

            val property = creator.findCreatorProperty(keyString)

            if (property != null) {
                if (!buffer.assignParameter(property, property.deserialize(parser, context))) {
                    keyString = parser.nextName()
                    continue
                }

                parser.nextToken()
                val result = try {
                    creator.build(context, buffer)!! as MutableMap<Any?, Any?>
                } catch (e: Exception) {
                    wrapAndThrow(context, e, myContainerType.rawClass, keyString)
                }

                return readAndBind(parser, context, result)
            }

            val key = keyDeserializer.deserializeKey(keyString, context)

            val value = try {
                if (token == CirJsonToken.VALUE_NULL) {
                    if (mySkipNullValues) {
                        keyString = parser.nextName()
                        continue
                    }

                    nullValueProvider.getNullValue(context)
                } else if (typeDeserializer == null) {
                    valueDeserializer.deserialize(parser, context)
                } else {
                    valueDeserializer.deserializeWithType(parser, context, typeDeserializer)
                }
            } catch (e: Exception) {
                wrapAndThrow(context, e, myContainerType.rawClass, keyString)
            }

            buffer.bufferMapProperty(key, value)
        }

        try {
            return creator.build(context, buffer) as MutableMap<Any?, Any?>?
        } catch (reference: UnresolvedForwardReferenceException) {
            wrapAndThrow(context, reference, myContainerType.rawClass, keyString)
        }
    }

    /*
     *******************************************************************************************************************
     * Internal methods, merging deserialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun readAndUpdate(parser: CirJsonParser, context: DeserializationContext,
            result: MutableMap<Any?, Any?>) {
        val keyDeserializer = myKeyDeserializer!!
        val nullValueProvider = myNullProvider!!
        val valueDeserializer = myValueDeserializer!!
        val typeDeserializer = myValueTypeDeserializer

        var keyString = if (parser.isExpectedStartObjectToken) {
            parser.nextName()
        } else {
            val token = parser.currentToken()

            if (token != CirJsonToken.PROPERTY_NAME) {
                if (token != CirJsonToken.END_OBJECT) {
                    return context.reportWrongTokenException(this, CirJsonToken.PROPERTY_NAME, null)
                }

                return
            }

            parser.currentName()
        }

        while (keyString != null) {
            val key = keyDeserializer.deserializeKey(keyString, context)
            val token = parser.nextToken()

            if (myInclusionChecker?.shouldIgnore(token) ?: false) {
                parser.skipChildren()
                keyString = parser.nextName()
                continue
            }

            try {
                if (token == CirJsonToken.VALUE_NULL) {
                    if (!mySkipNullValues) {
                        result[key] = nullValueProvider.getNullValue(context)
                    }

                    keyString = parser.nextName()
                    continue
                }

                val oldValue = result[key]

                val value = if (oldValue != null) {
                    if (typeDeserializer == null) {
                        valueDeserializer.deserialize(parser, context, oldValue)
                    } else {
                        valueDeserializer.deserializeWithType(parser, context, typeDeserializer, oldValue)
                    }
                } else if (typeDeserializer == null) {
                    valueDeserializer.deserialize(parser, context)
                } else {
                    valueDeserializer.deserializeWithType(parser, context, typeDeserializer)
                }

                if (value !== oldValue) {
                    result[key] = value
                }
            } catch (e: Exception) {
                wrapAndThrow(context, e, myContainerType.rawClass, keyString)
            }
        }
    }

    /**
     * Optimized method used when keys can be deserialized as plain old [Strings][String], and there is no custom
     * deserialized specified.
     */
    @Throws(CirJacksonException::class)
    protected fun readAndUpdateStringKeyMap(parser: CirJsonParser, context: DeserializationContext,
            result: MutableMap<Any?, Any?>) {
        val nullValueProvider = myNullProvider!!
        val valueDeserializer = myValueDeserializer!!
        val typeDeserializer = myValueTypeDeserializer

        var key = if (parser.isExpectedStartObjectToken) {
            parser.nextName()
        } else {
            val token = parser.currentToken()

            if (token != CirJsonToken.PROPERTY_NAME) {
                if (token != CirJsonToken.END_OBJECT) {
                    return context.reportWrongTokenException(this, CirJsonToken.PROPERTY_NAME, null)
                }

                return
            }

            parser.currentName()
        }

        while (key != null) {
            val token = parser.nextToken()

            if (myInclusionChecker?.shouldIgnore(token) ?: false) {
                parser.skipChildren()
                key = parser.nextName()
                continue
            }

            try {
                if (token == CirJsonToken.VALUE_NULL) {
                    if (!mySkipNullValues) {
                        result[key] = nullValueProvider.getNullValue(context)
                    }

                    key = parser.nextName()
                    continue
                }

                val oldValue = result[key]

                val value = if (oldValue != null) {
                    if (typeDeserializer == null) {
                        valueDeserializer.deserialize(parser, context, oldValue)
                    } else {
                        valueDeserializer.deserializeWithType(parser, context, typeDeserializer, oldValue)
                    }
                } else if (typeDeserializer == null) {
                    valueDeserializer.deserialize(parser, context)
                } else {
                    valueDeserializer.deserializeWithType(parser, context, typeDeserializer)
                }

                if (value !== oldValue) {
                    result[key] = value
                }
            } catch (e: Exception) {
                wrapAndThrow(context, e, myContainerType.rawClass, key)
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Internal methods, other
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    protected open fun squashDuplicates(context: DeserializationContext, result: MutableMap<Any?, Any?>, key: Any?,
            oldValue: Any?, newValue: Any?) {
        if (!myCheckDuplicateSquash || !context.isEnabled(StreamReadCapability.DUPLICATE_PROPERTIES)) {
            return
        }

        if (oldValue is List<*>) {
            (oldValue as? MutableList<Any?> ?: oldValue.toMutableList()).add(newValue)
            result[key] = oldValue
        } else {
            result[key] = arrayListOf(oldValue, newValue)
        }
    }

    @Throws(CirJacksonException::class)
    private fun handleUnresolvedReference(context: DeserializationContext, accumulator: MapReferringAccumulator?,
            key: Any?, reference: UnresolvedForwardReferenceException) {
        accumulator ?: return context.reportInputMismatch(this,
                "Unresolved forward reference but no identity info: $reference")
        val referring = accumulator.handleUnresolvedReference(reference, key)
        reference.readableObjectId!!.appendReferring(referring)
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    private class MapReferringAccumulator(private val myValueType: KClass<*>,
            private val myResult: MutableMap<Any?, Any?>) {

        /**
         * A list of [MapReferring] to maintain ordering.
         */
        private val myAccumulator = ArrayList<MapReferring>()

        operator fun set(key: Any?, value: Any?) {
            if (myAccumulator.isEmpty()) {
                myResult[key] = value
            } else {
                val referring = myAccumulator.last()
                referring.next[myAccumulator.lastIndex] = key
            }
        }

        fun handleUnresolvedReference(reference: UnresolvedForwardReferenceException,
                key: Any?): ReadableObjectId.Referring {
            val id = MapReferring(this, reference, myValueType, key)
            myAccumulator.add(id)
            return id
        }

        @Throws(CirJacksonException::class)
        fun resolveForwardReference(id: Any, value: Any?) {
            val iterator = myAccumulator.iterator()
            var previous = myResult

            while (iterator.hasNext()) {
                val referring = iterator.next()

                if (referring.hasId(id)) {
                    iterator.remove()
                    previous[referring.key] = value
                    previous.putAll(referring.next)
                    return
                }

                previous = referring.next
            }

            throw IllegalArgumentException(
                    "Trying to resolve a forward reference with id [$id] that wasn't previously seen as unresolved.")
        }

    }

    /**
     * Helper class to maintain processing order of value. The resolved object associated with [key] comes before the
     * values in [next].
     */
    private class MapReferring(private val myParent: MapReferringAccumulator,
            reference: UnresolvedForwardReferenceException, valueType: KClass<*>, val key: Any?) :
            ReadableObjectId.Referring(reference, valueType) {

        val next = LinkedHashMap<Any?, Any?>()

        @Throws(CirJacksonException::class)
        override fun handleResolvedForwardReference(id: Any, value: Any?) {
            myParent.resolveForwardReference(id, value)
        }

    }

}