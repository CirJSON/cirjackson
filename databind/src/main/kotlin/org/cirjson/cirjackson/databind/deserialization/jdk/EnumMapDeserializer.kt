package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.CirJsonTokenId
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.NullValueProvider
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.deserialization.bean.PropertyBasedCreator
import org.cirjson.cirjackson.databind.deserialization.standard.ContainerDeserializerBase
import org.cirjson.cirjackson.databind.type.LogicalType
import java.util.*
import kotlin.reflect.KClass

/**
 * Deserializer for [EnumMap] values.
 * 
 * Note: casting within this class is all messed up -- just could not figure out a way to properly deal with recursive
 * definition of `EnumMap<K : Enum<K>, V>`
 */
@Suppress("UNCHECKED_CAST")
open class EnumMapDeserializer : ContainerDeserializerBase<EnumMap<*, *>> {

    protected val myEnumClass: KClass<*>

    private val myKeyDeserializer: KeyDeserializer?

    private val myValueDeserializer: ValueDeserializer<Any>?

    /**
     * If value instances have polymorphic type information, this is the type deserializer that can handle it
     */
    protected val myValueTypeDeserializer: TypeDeserializer?

    protected val myValueInstantiator: ValueInstantiator?

    /**
     * Deserializer that is used iff delegate-based creator is to be used for deserializing from CirJSON Object.
     */
    protected var myDelegateDeserializer: ValueDeserializer<Any>? = null

    /**
     * If the Map is to be instantiated using non-default constructor or factory method that takes one or more named
     * properties as argument(s), this creator is used for instantiation.
     */
    protected var myPropertyBasedCreator: PropertyBasedCreator? = null

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor(mapType: KotlinType, valueInstantiator: ValueInstantiator?, keyDeserializer: KeyDeserializer?,
            valueDeserializer: ValueDeserializer<*>?, valueTypeDeserializer: TypeDeserializer?,
            nullValueProvider: NullValueProvider?) : super(mapType, nullValueProvider, null) {
        myEnumClass = mapType.keyType!!.rawClass
        myKeyDeserializer = keyDeserializer
        myValueDeserializer = valueDeserializer as ValueDeserializer<Any>?
        myValueTypeDeserializer = valueTypeDeserializer
        myValueInstantiator = valueInstantiator
    }

    protected constructor(base: EnumMapDeserializer, keyDeserializer: KeyDeserializer?,
            valueDeserializer: ValueDeserializer<*>?, valueTypeDeserializer: TypeDeserializer?,
            nullValueProvider: NullValueProvider?) : super(base, nullValueProvider, null) {
        myEnumClass = base.myEnumClass
        myKeyDeserializer = keyDeserializer
        myValueDeserializer = valueDeserializer as ValueDeserializer<Any>?
        myValueTypeDeserializer = valueTypeDeserializer
        myValueInstantiator = base.myValueInstantiator
        myDelegateDeserializer = base.myDelegateDeserializer
        myPropertyBasedCreator = base.myPropertyBasedCreator
    }

    open fun withResolved(keyDeserializer: KeyDeserializer?, valueDeserializer: ValueDeserializer<*>?,
            valueTypeDeserializer: TypeDeserializer?, nullValueProvider: NullValueProvider?): EnumMapDeserializer {
        if (myKeyDeserializer === keyDeserializer && myValueDeserializer === valueDeserializer &&
                myValueTypeDeserializer === valueTypeDeserializer && myNullProvider === nullValueProvider) {
            return this
        }

        return EnumMapDeserializer(this, keyDeserializer, valueDeserializer, valueTypeDeserializer, nullValueProvider)
    }

    /*
     *******************************************************************************************************************
     * Validation, post-processing (ResolvableDeserializer)
     *******************************************************************************************************************
     */

    override fun resolve(context: DeserializationContext) {
        if (myValueInstantiator == null) {
            return
        }

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
        } else if (myValueInstantiator.canCreateFromObjectWith()) {
            val creatorProperties = myValueInstantiator.getFromObjectArguments(context.config)!!
            myPropertyBasedCreator = PropertyBasedCreator.construct(context, myValueInstantiator, creatorProperties,
                    context.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES))
        }
    }

    /**
     * Method called to finalize setup of this deserializer, when it is known for which property deserializer is needed
     * for.
     */
    override fun createContextual(context: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        val keyDeserializer = myKeyDeserializer ?: context.findKeyDeserializer(myContainerType.keyType!!, property)
        val valueType = myContainerType.contentType!!
        val valueDeserializer =
                myValueDeserializer?.let { context.handleSecondaryContextualization(it, property, valueType) }
                        ?: context.findContextualValueDeserializer(valueType, property)
        val valueTypeDeserializer = myValueTypeDeserializer?.forProperty(property)
        val nullValueProvider = findContentNullProvider(context, property, valueDeserializer)
        return withResolved(keyDeserializer, valueDeserializer, valueTypeDeserializer, nullValueProvider)
    }

    /**
     * Because of costs associated with constructing Enum resolvers, let's cache instances by default.
     */
    override val isCacheable: Boolean
        get() = myValueDeserializer == null && myKeyDeserializer == null && myValueTypeDeserializer == null

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

    override val valueInstantiator: ValueInstantiator?
        get() = myValueInstantiator

    override fun getEmptyValue(context: DeserializationContext): Any? {
        return constructMap(context)
    }

    /*
     *******************************************************************************************************************
     * ValueDeserializer implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): EnumMap<*, *>? {
        return if (myPropertyBasedCreator != null) {
            deserializeUsingProperties(parser, context)
        } else if (myDelegateDeserializer != null) {
            myValueInstantiator!!.createUsingDelegate(context,
                    myDelegateDeserializer!!.deserialize(parser, context)) as EnumMap<*, *>?
        } else {
            when (parser.currentTokenId()) {
                CirJsonTokenId.ID_START_OBJECT, CirJsonTokenId.ID_END_OBJECT, CirJsonTokenId.ID_PROPERTY_NAME -> deserialize(
                        parser, context, constructMap(context)!!)

                CirJsonTokenId.ID_STRING -> deserializeFromString(parser, context)

                CirJsonTokenId.ID_START_ARRAY -> deserializeFromArray(parser, context)

                else -> context.handleUnexpectedToken(getValueType(context), parser) as EnumMap<*, *>?
            }
        }
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext,
            intoValue: EnumMap<*, *>): EnumMap<*, *>? {
        parser.assignCurrentValue(intoValue)

        var keyString = if (parser.isExpectedStartObjectToken) {
            parser.nextName()
        } else {
            val token = parser.currentToken()

            if (token != CirJsonToken.PROPERTY_NAME) {
                if (token == CirJsonToken.END_OBJECT) {
                    return intoValue
                }

                return context.reportWrongTokenException(this, CirJsonToken.PROPERTY_NAME, null)
            }

            parser.currentName()
        }

        val keyDeserializer = myKeyDeserializer!!
        val typeDeserializer = myValueTypeDeserializer
        val nullProvider = myNullProvider!!
        val valueDeserializer = myValueDeserializer!!

        while (keyString != null) {
            val key = keyDeserializer.deserializeKey(keyString, context) as Enum<*>?
            val token = parser.nextToken()

            if (key == null) {
                if (!context.isEnabled(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)) {
                    return context.handleWeirdStringValue(myEnumClass, keyString,
                            "value not one of declared Enum instance names for ${myContainerType.keyType}") as EnumMap<*, *>?
                }

                parser.skipChildren()
                keyString = parser.nextName()
                continue
            }

            val value = try {
                if (token == CirJsonToken.VALUE_NULL) {
                    if (mySkipNullValues) {
                        keyString = parser.nextName()
                        continue
                    }

                    nullProvider.getNullValue(context)
                } else if (typeDeserializer == null) {
                    valueDeserializer.deserialize(parser, context)
                } else {
                    valueDeserializer.deserializeWithType(parser, context, typeDeserializer)
                }
            } catch (e: Exception) {
                wrapAndThrow(context, e, intoValue, keyString)
            }

            (intoValue as MutableMap<Enum<*>, Any?>)[key] = value
            keyString = parser.nextName()
        }

        return intoValue
    }

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        return typeDeserializer.deserializeTypedFromObject(parser, context)
    }

    protected open fun constructMap(context: DeserializationContext): EnumMap<*, *>? {
        return if (myValueInstantiator == null) {
            EnumMap<DeserializationFeature, Any?>(myEnumClass.java as Class<DeserializationFeature>)
        } else if (!myValueInstantiator.canCreateUsingDefault()) {
            context.handleMissingInstantiator(handledType(), valueInstantiator, null,
                    "no default constructor found") as EnumMap<*, *>
        } else {
            myValueInstantiator.createUsingDefault(context) as EnumMap<*, *>?
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeUsingProperties(parser: CirJsonParser,
            context: DeserializationContext): EnumMap<*, *>? {
        val creator = myPropertyBasedCreator!!
        val buffer = creator.startBuilding(parser, context, null)

        var keyName = if (parser.isExpectedStartObjectToken) {
            parser.nextName()
        } else if (parser.hasToken(CirJsonToken.PROPERTY_NAME)) {
            parser.currentName()
        } else {
            null
        }

        val keyDeserializer = myKeyDeserializer!!
        val typeDeserializer = myValueTypeDeserializer
        val nullProvider = myNullProvider!!
        val valueDeserializer = myValueDeserializer!!

        while (keyName != null) {
            val token = parser.nextToken()
            val property = creator.findCreatorProperty(keyName)

            if (property != null) {
                if (!buffer.assignParameter(property, property.deserialize(parser, context))) {
                    keyName = parser.nextName()
                    continue
                }

                parser.nextToken()

                val result = try {
                    creator.build(context, buffer) as EnumMap<*, *>
                } catch (e: Exception) {
                    wrapAndThrow(context, e, myContainerType.rawClass, keyName)
                }

                return deserialize(parser, context, result)
            }

            val key = keyDeserializer.deserializeKey(keyName, context) as Enum<*>?

            if (key == null) {
                if (!context.isEnabled(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)) {
                    return context.handleWeirdStringValue(myEnumClass, keyName,
                            "value not one of declared Enum instance names for ${myContainerType.keyType}") as EnumMap<*, *>?
                }

                parser.nextToken()
                parser.skipChildren()
                keyName = parser.nextName()
                continue
            }

            val value = try {
                if (token == CirJsonToken.VALUE_NULL) {
                    if (mySkipNullValues) {
                        keyName = parser.nextName()
                        continue
                    }

                    nullProvider.getNullValue(context)
                } else if (typeDeserializer == null) {
                    valueDeserializer.deserialize(parser, context)
                } else {
                    valueDeserializer.deserializeWithType(parser, context, typeDeserializer)
                }
            } catch (e: Exception) {
                wrapAndThrow(context, e, myContainerType.rawClass, keyName)
            }

            buffer.bufferMapProperty(key, value)
        }

        try {
            return creator.build(context, buffer) as EnumMap<*, *>?
        } catch (e: Exception) {
            wrapAndThrow(context, e, myContainerType.rawClass, keyName)
        }
    }

}