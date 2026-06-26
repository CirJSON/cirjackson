package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.CirJsonTokenId
import org.cirjson.cirjackson.core.symbols.PropertyNameMatcher
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.configuration.CoercionAction
import org.cirjson.cirjackson.databind.deserialization.BeanDeserializerBuilder
import org.cirjson.cirjackson.databind.deserialization.ReadableObjectId
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.UnresolvedForwardReferenceException
import org.cirjson.cirjackson.databind.deserialization.implementation.ExternalTypeHandler
import org.cirjson.cirjackson.databind.deserialization.implementation.MethodProperty
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader
import org.cirjson.cirjackson.databind.deserialization.implementation.UnwrappedPropertyHandler
import org.cirjson.cirjackson.databind.util.*
import kotlin.reflect.KClass

/**
 * Deserializer class that can deserialize instances of arbitrary bean objects, usually from CirJSON Object structs.
 */
open class BeanDeserializer : BeanDeserializerBase {

    /**
     * Lazily constructed exception used as root cause if reporting problem with creator method that returns `null`
     * (which is not allowed)
     */
    @Transient
    protected var myNullFromCreator: Exception? = null

    protected var myPropertyNameMatcher: PropertyNameMatcher? = null

    protected var myPropertiesByIndex: Array<SettableBeanProperty>? = null

    /**
     * State marker we need in order to avoid infinite recursion for some cases (not very clean, alas, but has to do for
     * now)
     */
    @Transient
    @Volatile
    protected var myCurrentlyTransforming: NameTransformer? = null

    /*
     *******************************************************************************************************************
     * Lifecycle, constructors
     *******************************************************************************************************************
     */

    /**
     * Constructor used by [BeanDeserializerBuilder].
     */
    constructor(builder: BeanDeserializerBuilder, beanDescription: BeanDescription, properties: BeanPropertyMap,
            backReferences: Map<String, SettableBeanProperty>?, ignorableProperties: Set<String>?,
            ignoreAllUnknown: Boolean, includableProperties: Set<String>?, hasViews: Boolean) : super(builder,
            beanDescription, properties, backReferences, ignorableProperties, ignoreAllUnknown, includableProperties,
            hasViews)

    /**
     * Copy-constructor that can be used by subclasses to allow copy-on-write style copying of settings of an existing
     * instance.
     */
    protected constructor(source: BeanDeserializer) : super(source, source.myIgnoreAllUnknown) {
        myPropertyNameMatcher = source.myPropertyNameMatcher
        myPropertiesByIndex = source.myPropertiesByIndex
    }

    protected constructor(source: BeanDeserializer, ignoreAllUnknown: Boolean) : super(source, ignoreAllUnknown) {
        myPropertyNameMatcher = source.myPropertyNameMatcher
        myPropertiesByIndex = source.myPropertiesByIndex
    }

    protected constructor(source: BeanDeserializer, unwrapHandler: UnwrappedPropertyHandler?,
            renamedProperties: BeanPropertyMap?, ignoreAllUnknown: Boolean) : super(source, unwrapHandler,
            renamedProperties, ignoreAllUnknown) {
        myPropertyNameMatcher = myBeanProperties!!.nameMatcher
        myPropertiesByIndex = myBeanProperties.nameMatcherProperties
    }

    protected constructor(source: BeanDeserializer, objectIdReader: ObjectIdReader?) : super(source, objectIdReader) {
        myPropertyNameMatcher = source.myPropertyNameMatcher
        myPropertiesByIndex = source.myPropertiesByIndex
    }

    protected constructor(source: BeanDeserializer, ignorableProperties: Set<String>?,
            includableProperties: Set<String>?) : super(source, ignorableProperties, includableProperties) {
        myPropertyNameMatcher = source.myPropertyNameMatcher
        myPropertiesByIndex = source.myPropertiesByIndex
    }

    protected constructor(source: BeanDeserializer, beanProperties: BeanPropertyMap?) : super(source, beanProperties) {
        myPropertyNameMatcher = myBeanProperties!!.nameMatcher
        myPropertiesByIndex = myBeanProperties.nameMatcherProperties
    }

    /*
     *******************************************************************************************************************
     * Lifecycle, mutant factories
     *******************************************************************************************************************
     */

    override fun unwrappingDeserializer(context: DeserializationContext,
            unwrapper: NameTransformer): ValueDeserializer<Any> {
        if (this::class != BeanDeserializer::class) {
            return this
        } else if (myCurrentlyTransforming === unwrapper) {
            return this
        }

        myCurrentlyTransforming = unwrapper

        try {
            val unwrapHandler = myUnwrappedPropertyHandler?.renameAll(context, unwrapper)
            return BeanDeserializer(this, unwrapHandler, myBeanProperties!!.renameAll(context, unwrapper), true)
        } finally {
            myCurrentlyTransforming = null
        }
    }

    override fun withObjectIdReader(objectIdReader: ObjectIdReader?): BeanDeserializer {
        return BeanDeserializer(this, objectIdReader)
    }

    override fun withByNameInclusion(ignorableProperties: Set<String>?,
            includableProperties: Set<String>?): BeanDeserializer {
        return BeanDeserializer(this, ignorableProperties, includableProperties)
    }

    override fun withIgnoreAllUnknown(ignoreAllUnknown: Boolean): BeanDeserializerBase {
        return BeanDeserializer(this, ignoreAllUnknown)
    }

    override fun withBeanProperties(beanProperties: BeanPropertyMap?): BeanDeserializerBase {
        return BeanDeserializer(this, beanProperties)
    }

    override fun asArrayDeserializer(): BeanDeserializerBase {
        return BeanAsArrayDeserializer(this, myBeanProperties!!.primaryProperties.nullable())
    }

    /*
     *******************************************************************************************************************
     * Lifecycle, initialization
     *******************************************************************************************************************
     */

    override fun initializeNameMatcher(context: DeserializationContext) {
        myBeanProperties!!.initMatcher(context.tokenStreamFactory())
        myPropertyNameMatcher = myBeanProperties.nameMatcher
        myPropertiesByIndex = myBeanProperties.nameMatcherProperties
    }

    /*
     *******************************************************************************************************************
     * ValueDeserializer implementation
     *******************************************************************************************************************
     */

    /**
     * Main deserialization method for bean-based objects (POJOs).
     */
    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        if (!parser.isExpectedStartObjectToken) {
            return deserializeOther(parser, context, parser.currentToken())
        } else if (myVanillaProcessing) {
            return vanillaDeserialize(parser, context)
        }

        parser.nextToken()

        return if (myObjectIdReader != null) {
            deserializeWithObjectId(parser, context)
        } else {
            deserializeFromObject(parser, context)
        }
    }

    @Throws(CirJacksonException::class)
    protected fun deserializeOther(parser: CirJsonParser, context: DeserializationContext, token: CirJsonToken?): Any? {
        token ?: return context.handleUnexpectedToken(getValueType(context), parser)

        return when (token) {
            CirJsonToken.VALUE_STRING -> deserializeFromString(parser, context)

            CirJsonToken.VALUE_NUMBER_INT -> deserializeFromNumber(parser, context)

            CirJsonToken.VALUE_NUMBER_FLOAT -> deserializeFromDouble(parser, context)

            CirJsonToken.VALUE_EMBEDDED_OBJECT -> deserializeFromEmbedded(parser, context)

            CirJsonToken.VALUE_TRUE, CirJsonToken.VALUE_FALSE -> deserializeFromBoolean(parser, context)

            CirJsonToken.VALUE_NULL -> deserializeFromNull(parser, context)

            CirJsonToken.START_ARRAY -> deserializeFromArray(parser, context)

            CirJsonToken.PROPERTY_NAME, CirJsonToken.END_OBJECT -> if (myVanillaProcessing) {
                vanillaDeserialize(parser, context)
            } else if (myObjectIdReader != null) {
                deserializeWithObjectId(parser, context)
            } else {
                deserializeFromObject(parser, context)
            }

            else -> context.handleUnexpectedToken(getValueType(context), parser)
        }
    }

    /**
     * Secondary deserialization method, called in cases where POJO instance is created as part of deserialization,
     * potentially after collecting some or all of the properties to set.
     */
    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext, intoValue: Any): Any? {
        parser.assignCurrentValue(intoValue)

        myInjectables?.also { injectValues(context, intoValue) }

        if (myUnwrappedPropertyHandler != null) {
            return deserializeWithUnwrapped(parser, context, intoValue)
        } else if (myExternalTypeIdHandler != null) {
            return deserializeWithExternalTypeId(parser, context, intoValue)
        }

        val propertyName = if (parser.isExpectedStartObjectToken) {
            parser.nextName() ?: return intoValue
        } else if (parser.hasTokenId(CirJsonTokenId.ID_PROPERTY_NAME)) {
            parser.currentName()!!
        } else {
            return intoValue
        }

        if (myNeedViewProcessing) {
            val view = context.activeView

            if (view != null) {
                return deserializeWithView(parser, context, intoValue, view)
            }
        }

        val propertyNameMatcher = myPropertyNameMatcher!!
        var index = propertyNameMatcher.matchName(propertyName)

        while (index >= 0) {
            parser.nextToken()
            val property = myPropertiesByIndex!![index]

            try {
                property.deserializeAndSet(parser, context, intoValue)
            } catch (e: Exception) {
                wrapAndThrow(e, intoValue, property.name, context)
            }

            index = parser.nextNameMatch(propertyNameMatcher)
        }

        return if (index == PropertyNameMatcher.MATCH_END_OBJECT) {
            intoValue
        } else if (index == PropertyNameMatcher.MATCH_UNKNOWN_NAME) {
            vanillaDeserializeWithUnknown(parser, context, intoValue, parser.currentName()!!)
        } else {
            handleUnexpectedWithin(parser, context, intoValue)
        }
    }

    /*
     *******************************************************************************************************************
     * Concrete deserialization methods
     *******************************************************************************************************************
     */

    /**
     * Streamlined version that is only used when no "special" features are enabled, and when current logical token is
     * [CirJsonToken.START_OBJECT] (or equivalent).
     */
    @Throws(CirJacksonException::class)
    private fun vanillaDeserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        val bean = myValueInstantiator.createUsingDefault(context)!!
        parser.assignCurrentValue(bean)
        val propertyNameMatcher = myPropertyNameMatcher!!
        val propertiesByIndex = myPropertiesByIndex!!

        var index = parser.nextNameMatch(propertyNameMatcher)

        while (index >= 0) {
            parser.nextToken()
            var property = propertiesByIndex[index]

            try {
                property.deserializeAndSet(parser, context, bean)
            } catch (e: Exception) {
                wrapAndThrow(e, bean, property.name, context)
            }

            index = parser.nextNameMatch(propertyNameMatcher)

            if (index < 0) {
                break
            }

            parser.nextToken()
            property = propertiesByIndex[index]

            try {
                property.deserializeAndSet(parser, context, bean)
            } catch (e: Exception) {
                wrapAndThrow(e, bean, property.name, context)
            }

            index = parser.nextNameMatch(propertyNameMatcher)

            if (index < 0) {
                break
            }

            parser.nextToken()
            property = propertiesByIndex[index]

            try {
                property.deserializeAndSet(parser, context, bean)
            } catch (e: Exception) {
                wrapAndThrow(e, bean, property.name, context)
            }

            index = parser.nextNameMatch(propertyNameMatcher)

            if (index < 0) {
                break
            }

            parser.nextToken()
            property = propertiesByIndex[index]

            try {
                property.deserializeAndSet(parser, context, bean)
            } catch (e: Exception) {
                wrapAndThrow(e, bean, property.name, context)
            }

            index = parser.nextNameMatch(propertyNameMatcher)
        }

        return if (index == PropertyNameMatcher.MATCH_END_OBJECT) {
            bean
        } else if (index == PropertyNameMatcher.MATCH_UNKNOWN_NAME) {
            vanillaDeserializeWithUnknown(parser, context, bean, parser.currentName()!!)
        } else {
            handleUnexpectedWithin(parser, context, bean)
        }
    }

    /**
     * Streamlined version that is only used when no "special" features are enabled.
     */
    @Throws(CirJacksonException::class)
    private fun vanillaDeserialize(parser: CirJsonParser, context: DeserializationContext, token: CirJsonToken): Any? {
        val bean = myValueInstantiator.createUsingDefault(context)!!

        if (token != CirJsonToken.PROPERTY_NAME) {
            return bean
        }

        parser.assignCurrentValue(token)
        val propertyNameMatcher = myPropertyNameMatcher!!
        val propertiesByIndex = myPropertiesByIndex!!

        var index = parser.nextNameMatch(propertyNameMatcher)

        while (index >= 0) {
            parser.nextToken()
            var property = propertiesByIndex[index]

            try {
                property.deserializeAndSet(parser, context, bean)
            } catch (e: Exception) {
                wrapAndThrow(e, bean, property.name, context)
            }

            index = parser.nextNameMatch(propertyNameMatcher)

            if (index < 0) {
                break
            }

            parser.nextToken()
            property = propertiesByIndex[index]

            try {
                property.deserializeAndSet(parser, context, bean)
            } catch (e: Exception) {
                wrapAndThrow(e, bean, property.name, context)
            }

            index = parser.nextNameMatch(propertyNameMatcher)
        }

        return if (index == PropertyNameMatcher.MATCH_END_OBJECT) {
            bean
        } else if (index == PropertyNameMatcher.MATCH_UNKNOWN_NAME) {
            vanillaDeserializeWithUnknown(parser, context, bean, parser.currentName()!!)
        } else {
            handleUnexpectedWithin(parser, context, bean)
        }
    }

    @Throws(CirJacksonException::class)
    private fun vanillaDeserializeWithUnknown(parser: CirJsonParser, context: DeserializationContext, bean: Any,
            propertyName: String): Any? {
        parser.nextToken()
        handleUnknownVanilla(parser, context, bean, propertyName)
        val propertyNameMatcher = myPropertyNameMatcher!!
        val propertiesByIndex = myPropertiesByIndex!!

        while (true) {
            val index = parser.nextNameMatch(propertyNameMatcher)

            if (index >= 0) {
                parser.nextToken()

                try {
                    propertiesByIndex[index].deserializeAndSet(parser, context, bean)
                } catch (e: Exception) {
                    wrapAndThrow(e, bean, parser.currentName(), context)
                }

                continue
            }

            if (index == PropertyNameMatcher.MATCH_END_OBJECT) {
                return bean
            } else if (index != PropertyNameMatcher.MATCH_UNKNOWN_NAME) {
                return handleUnexpectedWithin(parser, context, bean)
            }

            parser.nextToken()
            handleUnknownVanilla(parser, context, bean, parser.currentName()!!)
        }
    }

    /**
     * General version used when handling needs more advanced features.
     */
    @Throws(CirJacksonException::class)
    override fun deserializeFromObject(parser: CirJsonParser, context: DeserializationContext): Any? {
        if (myObjectIdReader?.maySerializeAsObject() ?: false) {
            if (parser.hasTokenId(CirJsonTokenId.ID_PROPERTY_NAME) &&
                    myObjectIdReader.isValidReferencePropertyName(parser.currentName()!!, parser)) {
                return deserializeFromObjectId(parser, context)
            }
        }

        if (myNonStandardCreation) {
            return if (myUnwrappedPropertyHandler != null) {
                deserializeWithUnwrapped(parser, context)
            } else if (myExternalTypeIdHandler != null) {
                deserializeWithExternalTypeId(parser, context)
            } else {
                deserializeFromObjectUsingNonDefault(parser, context)
            }
        }

        val bean = myValueInstantiator.createUsingDefault(context)!!
        parser.assignCurrentValue(parser)

        val id = parser.objectId

        if (id != null) {
            handleTypedObjectId(parser, context, bean, id)
        }

        if (myObjectIdReader != null && parser.hasTokenId(CirJsonTokenId.ID_END_OBJECT)) {
            return context.reportUnresolvedObjectId(myObjectIdReader, bean)
        }

        myInjectables?.also { injectValues(context, bean) }

        if (!parser.hasTokenId(CirJsonTokenId.ID_PROPERTY_NAME)) {
            return bean
        }

        if (myNeedViewProcessing) {
            val activeView = context.activeView

            if (activeView != null) {
                return deserializeWithView(parser, context, bean, activeView)
            }
        }

        val propertyNameMatcher = myPropertyNameMatcher!!
        val propertiesByIndex = myPropertiesByIndex!!

        var index = parser.currentNameMatch(propertyNameMatcher)

        while (true) {
            if (index >= 0) {
                parser.nextToken()

                try {
                    propertiesByIndex[index].deserializeAndSet(parser, context, bean)
                } catch (e: Exception) {
                    wrapAndThrow(e, bean, parser.currentName(), context)
                }

                index = parser.nextNameMatch(propertyNameMatcher)
                continue
            }

            if (index == PropertyNameMatcher.MATCH_END_OBJECT) {
                return bean
            } else if (index != PropertyNameMatcher.MATCH_UNKNOWN_NAME) {
                return handleUnexpectedWithin(parser, context, bean)
            }

            parser.nextToken()
            handleUnknownVanilla(parser, context, bean, parser.currentName()!!)
            index = parser.nextNameMatch(propertyNameMatcher)
        }
    }

    /**
     * Method called to deserialize bean using "property-based creator": this means that a non-default constructor or
     * factory method is called, and then possibly other setters. The trick is that values for creator method need to be
     * buffered, first; and due to non-guaranteed ordering possibly some other properties as well.
     */
    @Throws(CirJacksonException::class)
    override fun deserializeUsingPropertyBased(parser: CirJsonParser, context: DeserializationContext): Any? {
        val creator = myPropertyBasedCreator!!
        val buffer = myAnySetter?.let { anySetter ->
            creator.startBuildingWithAnySetter(parser, context, myObjectIdReader, anySetter)
        } ?: creator.startBuilding(parser, context, myObjectIdReader)
        var unknown: TokenBuffer? = null
        val activeView = context.takeIf { myNeedViewProcessing }?.activeView

        var token = parser.currentToken()
        var referrings: MutableList<BeanReferring>? = null
        val propertyNameMatcher = myPropertyNameMatcher!!
        val propertiesByIndex = myPropertiesByIndex!!

        while (token == CirJsonToken.PROPERTY_NAME) {
            val propertyName = parser.currentName()!!
            parser.nextToken()
            val creatorProperty = creator.findCreatorProperty(propertyName)

            if (buffer.readIdProperty(propertyName) && creatorProperty == null) {
                token = parser.nextToken()
                continue
            }

            if (creatorProperty != null) {
                if (activeView != null && !creatorProperty.visibleInView(activeView)) {
                    parser.skipChildren()
                    token = parser.nextToken()
                    continue
                }

                val value = deserializeWithErrorWrapping(parser, context, creatorProperty)

                if (!buffer.assignParameter(creatorProperty, value)) {
                    token = parser.nextToken()
                    continue
                }

                parser.nextToken()

                var bean = try {
                    creator.build(context, buffer)
                } catch (e: Exception) {
                    wrapInstantiationProblem(e, context)
                }

                bean ?: return context.handleInstantiationProblem(handledType(), null, creatorReturnedNullException())

                parser.assignCurrentValue(bean)

                if (bean::class != myBeanType.rawClass) {
                    return handlePolymorphic(parser, context, bean, unknown!!)
                }

                if (unknown != null) {
                    bean = handleUnknownProperties(context, bean, unknown)
                }

                return deserialize(parser, context, bean)
            }

            val index = propertyNameMatcher.matchName(propertyName)

            if (index >= 0) {
                val property = propertiesByIndex[index]

                if (!myBeanType.isRecordType || property is MethodProperty) {
                    try {
                        buffer.bufferProperty(property, deserializeWithErrorWrapping(parser, context, property))
                    } catch (reference: UnresolvedForwardReferenceException) {
                        val referring = handleUnresolvedReference(context, property, reference)
                        (referrings ?: ArrayList<BeanReferring>().also { referrings = it }).add(referring)
                    }

                    token = parser.nextToken()
                    continue
                }
            }

            if (IgnorePropertiesUtil.shouldIgnore(propertyName, myIgnorableProperties, myIncludableProperties)) {
                handleIgnoredProperty(parser, context, handledType(), propertyName)
                token = parser.nextToken()
                continue
            }

            myAnySetter?.also { anySetter ->
                try {
                    buffer.bufferAnyParameterProperty(anySetter, propertyName, anySetter.deserialize(parser, context))
                } catch (e: Exception) {
                    wrapAndThrow(e, myBeanType.rawClass, propertyName, context)
                }

                token = parser.nextToken()
                continue
            }

            if (myIgnoreAllUnknown) {
                parser.skipChildren()
                token = parser.nextToken()
                continue
            }

            if (unknown == null) {
                unknown = context.bufferForInputBuffering(parser)
            }

            unknown.writeName(propertyName)
            unknown.copyCurrentStructure(parser)
            token = parser.nextToken()
        }

        val bean = try {
            creator.build(context, buffer)!!
        } catch (e: Exception) {
            return wrapInstantiationProblem(e, context)
        }

        myInjectables?.also { injectValues(context, bean) }
        referrings?.forEach { referring -> referring.bean = bean }

        return if (unknown == null) {
            bean
        } else if (bean::class != myBeanType.rawClass) {
            handlePolymorphic(null, context, bean, unknown)
        } else {
            handleUnknownProperties(context, bean, unknown)
        }
    }

    @Throws(DatabindException::class)
    private fun handleUnresolvedReference(context: DeserializationContext, property: SettableBeanProperty,
            reference: UnresolvedForwardReferenceException): BeanReferring {
        val referring = BeanReferring(context, reference, property.type, property)
        reference.readableObjectId!!.appendReferring(referring)
        return referring
    }

    @Throws(DatabindException::class)
    protected fun deserializeWithErrorWrapping(parser: CirJsonParser, context: DeserializationContext,
            property: SettableBeanProperty): Any? {
        try {
            return property.deserialize(parser, context)
        } catch (e: Exception) {
            wrapAndThrow(e, myBeanType.rawClass, property.name, context)
        }
    }

    /**
     * Helper method called for rare case of pointing to [CirJsonToken.VALUE_NULL] token. While this is most often an
     * erroneous condition, there is one specific case with XML handling where polymorphic type with no properties is
     * exposed as such, and should be handled same as empty Object.
     */
    @Throws(CirJacksonException::class)
    protected open fun deserializeFromNull(parser: CirJsonParser, context: DeserializationContext): Any? {
        return context.handleUnexpectedToken(getValueType(context), parser)
    }

    @Throws(CirJacksonException::class)
    override fun deserializeFromArray(parser: CirJsonParser, context: DeserializationContext): Any? {
        val delegateDeserializer = myArrayDelegateDeserializer ?: myDelegateDeserializer

        if (delegateDeserializer != null) {
            val bean = myValueInstantiator.createUsingArrayDelegate(context,
                    delegateDeserializer.deserialize(parser, context))!!
            myInjectables?.also { injectValues(context, bean) }
            return bean
        }

        val action = findCoercionFromEmptyArray(context)
        val unwrap = context.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)

        if (!unwrap && action == CoercionAction.FAIL) {
            return context.handleUnexpectedToken(getValueType(context), parser)
        }

        val unwrappedToken = parser.nextToken()

        return if (unwrappedToken == CirJsonToken.END_ARRAY) {
            when (action) {
                CoercionAction.AS_EMPTY -> getEmptyValue(context)
                CoercionAction.AS_NULL, CoercionAction.TRY_CONVERT -> getNullValue(context)
                else -> context.handleUnexpectedToken(getValueType(context), CirJsonToken.START_ARRAY, parser, null)
            }
        } else if (!unwrap) {
            context.handleUnexpectedToken(getValueType(context), parser)
        } else if (unwrappedToken == CirJsonToken.START_ARRAY) {
            val targetType = getValueType(context)
            context.handleUnexpectedToken(targetType, CirJsonToken.START_ARRAY, parser,
                    "Cannot deserialize value of type ${targetType.typeDescription} from deeply-nested Array: only single wrapper allowed with `DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS`")
        } else {
            val value = deserialize(parser, context)

            if (parser.nextToken() != CirJsonToken.END_ARRAY) {
                handleMissingEndArrayForSingle(parser, context)
            }

            value
        }
    }

    /*
     *******************************************************************************************************************
     * Deserializing when we have to consider an active View
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun deserializeWithView(parser: CirJsonParser, context: DeserializationContext, bean: Any,
            activeView: KClass<*>): Any? {
        val propertyNameMatcher = myPropertyNameMatcher!!
        val propertiesByIndex = myPropertiesByIndex!!
        var index = parser.currentNameMatch(propertyNameMatcher)

        while (true) {
            if (index >= 0) {
                parser.nextToken()
                val property = propertiesByIndex[index]

                if (!property.visibleInView(activeView)) {
                    if (context.isEnabled(DeserializationFeature.FAIL_ON_UNEXPECTED_VIEW_PROPERTIES)) {
                        return context.reportInputMismatch(handledType(),
                                "Input mismatch while deserializing ${handledType().name}. Property '${property.name}' is not part of current active view '${activeView.qualifiedName}' (disable 'DeserializationFeature.FAIL_ON_UNEXPECTED_VIEW_PROPERTIES' to allow)")
                    }

                    parser.skipChildren()
                    index = parser.nextNameMatch(propertyNameMatcher)
                    continue
                }

                try {
                    property.deserializeAndSet(parser, context, bean)
                } catch (e: Exception) {
                    wrapAndThrow(e, bean, parser.currentName(), context)
                }

                index = parser.nextNameMatch(propertyNameMatcher)
                continue
            }

            if (index == PropertyNameMatcher.MATCH_END_OBJECT) {
                return bean
            } else if (index != PropertyNameMatcher.MATCH_UNKNOWN_NAME) {
                return handleUnexpectedWithin(parser, context, bean)
            }

            parser.nextToken()
            handleUnknownVanilla(parser, context, bean, parser.currentName()!!)
            index = parser.nextNameMatch(propertyNameMatcher)
        }
    }

    /*
     *******************************************************************************************************************
     * Handling for cases where we have "unwrapped" values
     *******************************************************************************************************************
     */

    /**
     * Method called when there are declared "unwrapped" properties which need special handling
     */
    @Throws(CirJacksonException::class)
    protected open fun deserializeWithUnwrapped(parser: CirJsonParser, context: DeserializationContext): Any? {
        if (myDelegateDeserializer != null) {
            return myValueInstantiator.createUsingDelegate(context,
                    myDelegateDeserializer!!.deserialize(parser, context))
        } else if (myPropertyBasedCreator != null) {
            return deserializeUsingPropertyBasedWithUnwrapped(parser, context)
        }

        val tokens = context.bufferForInputBuffering(parser)
        tokens.writeStartObject()
        val bean = myValueInstantiator.createUsingDefault(context)!!

        parser.assignCurrentValue(bean)

        myInjectables?.also { injectValues(context, bean) }
        val activeView = context.takeIf { myNeedViewProcessing }?.activeView
        val propertyNameMatcher = myPropertyNameMatcher!!
        val propertiesByIndex = myPropertiesByIndex!!
        var index = parser.currentNameMatch(propertyNameMatcher)

        while (true) {
            if (index >= 0) {
                parser.nextToken()
                val property = propertiesByIndex[index]

                if (activeView != null && !property.visibleInView(activeView)) {
                    parser.skipChildren()
                    index = parser.nextNameMatch(propertyNameMatcher)
                    continue
                }

                try {
                    property.deserializeAndSet(parser, context, bean)
                } catch (e: Exception) {
                    wrapAndThrow(e, bean, property.name, context)
                }

                index = parser.nextNameMatch(propertyNameMatcher)
                continue
            }

            if (index == PropertyNameMatcher.MATCH_END_OBJECT) {
                break
            } else if (index == PropertyNameMatcher.MATCH_ODD_TOKEN) {
                return handleUnexpectedWithin(parser, context, bean)
            }

            val propertyName = parser.currentName()!!
            parser.nextToken()

            if (IgnorePropertiesUtil.shouldIgnore(propertyName, myIgnorableProperties, myIncludableProperties)) {
                handleIgnoredProperty(parser, context, bean, propertyName)
                index = parser.nextNameMatch(propertyNameMatcher)
                continue
            }

            val anySetter = myAnySetter ?: let {
                tokens.writeName(propertyName)
                tokens.copyCurrentStructure(parser)
                index = parser.nextNameMatch(propertyNameMatcher)
                continue
            }

            val buffer = context.bufferAsCopyOfValue(parser)
            tokens.writeName(propertyName)
            tokens.append(buffer)

            try {
                anySetter.deserializeAndSet(buffer.asParserOnFirstToken(context), context, bean, propertyName)
            } catch (e: Exception) {
                wrapAndThrow(e, bean, propertyName, context)
            }

            index = parser.nextNameMatch(propertyNameMatcher)
        }

        tokens.writeEndObject()
        myUnwrappedPropertyHandler!!.processUnwrapped(parser, context, bean, tokens)
        return bean
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeWithUnwrapped(parser: CirJsonParser, context: DeserializationContext,
            bean: Any): Any? {
        val token = parser.currentToken()

        if (token == CirJsonToken.START_OBJECT) {
            parser.nextToken()
        }

        val tokens = context.bufferForInputBuffering(parser)
        tokens.writeStartObject()
        val activeView = context.takeIf { myNeedViewProcessing }?.activeView
        val propertyNameMatcher = myPropertyNameMatcher!!
        val propertiesByIndex = myPropertiesByIndex!!
        var index = parser.currentNameMatch(propertyNameMatcher)

        while (true) {
            if (index >= 0) {
                parser.nextToken()
                val property = propertiesByIndex[index]

                if (activeView != null && !property.visibleInView(activeView)) {
                    parser.skipChildren()
                    index = parser.nextNameMatch(propertyNameMatcher)
                    continue
                }

                try {
                    property.deserializeAndSet(parser, context, bean)
                } catch (e: Exception) {
                    wrapAndThrow(e, bean, property.name, context)
                }

                index = parser.nextNameMatch(propertyNameMatcher)
                continue
            }

            if (index == PropertyNameMatcher.MATCH_END_OBJECT) {
                break
            } else if (index == PropertyNameMatcher.MATCH_ODD_TOKEN) {
                return handleUnexpectedWithin(parser, context, bean)
            }

            val propertyName = parser.currentName()!!
            parser.nextToken()

            if (IgnorePropertiesUtil.shouldIgnore(propertyName, myIgnorableProperties, myIncludableProperties)) {
                handleIgnoredProperty(parser, context, bean, propertyName)
                index = parser.nextNameMatch(propertyNameMatcher)
                continue
            }

            val anySetter = myAnySetter ?: let {
                tokens.writeName(propertyName)
                tokens.copyCurrentStructure(parser)
                index = parser.nextNameMatch(propertyNameMatcher)
                continue
            }

            val buffer = context.bufferAsCopyOfValue(parser)
            tokens.writeName(propertyName)
            tokens.append(buffer)

            try {
                anySetter.deserializeAndSet(buffer.asParserOnFirstToken(context), context, bean, propertyName)
            } catch (e: Exception) {
                wrapAndThrow(e, bean, propertyName, context)
            }

            index = parser.nextNameMatch(propertyNameMatcher)
        }

        tokens.writeEndObject()
        myUnwrappedPropertyHandler!!.processUnwrapped(parser, context, bean, tokens)
        return bean
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeUsingPropertyBasedWithUnwrapped(parser: CirJsonParser,
            context: DeserializationContext): Any? {
        val creator = myPropertyBasedCreator!!
        val buffer = creator.startBuilding(parser, context, myObjectIdReader)

        val tokens = context.bufferForInputBuffering(parser)
        tokens.writeStartObject()

        var token = parser.currentToken()
        val propertyNameMatcher = myPropertyNameMatcher!!
        val propertiesByIndex = myPropertiesByIndex!!
        val unwrappedPropertyHandler = myUnwrappedPropertyHandler!!

        while (token == CirJsonToken.PROPERTY_NAME) {
            val propertyName = parser.currentName()!!
            parser.nextToken()
            val creatorProperty = creator.findCreatorProperty(propertyName)

            if (buffer.readIdProperty(propertyName) && creatorProperty == null) {
                token = parser.nextToken()
                continue
            }

            if (creatorProperty != null) {
                if (!buffer.assignParameter(creatorProperty,
                                deserializeWithErrorWrapping(parser, context, creatorProperty))) {
                    token = parser.nextToken()
                    continue
                }

                token = parser.nextToken()

                val bean = try {
                    creator.build(context, buffer)!!
                } catch (e: Exception) {
                    wrapInstantiationProblem(e, context)!!
                }

                parser.assignCurrentValue(bean)

                while (token == CirJsonToken.PROPERTY_NAME) {
                    tokens.copyCurrentStructure(parser)
                    token = parser.nextToken()
                }

                if (token != CirJsonToken.END_OBJECT) {
                    return context.reportWrongTokenException(this, CirJsonToken.END_OBJECT,
                            "Attempted to unwrap '${handledType().qualifiedName}' value")
                }

                tokens.writeEndObject()

                return if (bean::class != myBeanType.rawClass) {
                    context.reportInputMismatch(creatorProperty,
                            "Cannot create polymorphic instances with unwrapped values")
                } else {
                    unwrappedPropertyHandler.processUnwrapped(parser, context, bean, tokens)
                }
            }

            val index = propertyNameMatcher.matchName(propertyName)

            if (index >= 0) {
                val property = propertiesByIndex[index]
                buffer.bufferProperty(property, deserializeWithErrorWrapping(parser, context, property))
                token = parser.nextToken()
                continue
            }

            val anySetter = myAnySetter ?: let {
                tokens.writeName(propertyName)
                tokens.copyCurrentStructure(parser)
                token = parser.nextToken()
                continue
            }

            val otherBuffer = context.bufferAsCopyOfValue(parser)
            tokens.writeName(propertyName)
            tokens.append(otherBuffer)

            try {
                buffer.bufferAnyProperty(anySetter, propertyName,
                        anySetter.deserialize(otherBuffer.asParserOnFirstToken(context), context))
            } catch (e: Exception) {
                wrapAndThrow(e, myBeanType.rawClass, propertyName, context)
            }

            token = parser.nextToken()
        }

        val bean = try {
            creator.build(context, buffer)!!
        } catch (e: Exception) {
            return wrapInstantiationProblem(e, context)
        }

        return unwrappedPropertyHandler.processUnwrapped(parser, context, bean, tokens)
    }

    /*
     *******************************************************************************************************************
     * Handling for cases where we have property/-ies with external type id
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun deserializeWithExternalTypeId(parser: CirJsonParser, context: DeserializationContext): Any? {
        return if (myPropertyBasedCreator != null) {
            deserializeUsingPropertyBasedWithExternalTypeId(parser, context)
        } else if (myDelegateDeserializer != null) {
            myValueInstantiator.createUsingDelegate(context, myDelegateDeserializer!!.deserialize(parser, context))
        } else {
            deserializeWithExternalTypeId(parser, context, myValueInstantiator.createUsingDefault(context)!!)
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeWithExternalTypeId(parser: CirJsonParser, context: DeserializationContext,
            bean: Any): Any? {
        return deserializeWithExternalTypeId(parser, context, bean, myExternalTypeIdHandler!!.start())
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeWithExternalTypeId(parser: CirJsonParser, context: DeserializationContext, bean: Any,
            externalTypeHandler: ExternalTypeHandler): Any? {
        val activeView = context.takeIf { myNeedViewProcessing }?.activeView
        val propertyNameMatcher = myPropertyNameMatcher!!
        val propertiesByIndex = myPropertiesByIndex!!

        var index = parser.currentNameMatch(propertyNameMatcher)

        while (true) {
            if (index >= 0) {
                val property = propertiesByIndex[index]
                val token = parser.nextToken()!!

                if (token.isScalarValue) {
                    externalTypeHandler.handleTypePropertyValue(parser, context, parser.currentName()!!, bean)
                }

                if (activeView != null && !property.visibleInView(activeView)) {
                    parser.skipChildren()
                    index = parser.nextNameMatch(propertyNameMatcher)
                    continue
                }

                try {
                    property.deserializeAndSet(parser, context, bean)
                } catch (e: Exception) {
                    wrapAndThrow(e, bean, property.name, context)
                }

                index = parser.nextNameMatch(propertyNameMatcher)
                continue
            }

            if (index == PropertyNameMatcher.MATCH_END_OBJECT) {
                break
            } else if (index != PropertyNameMatcher.MATCH_UNKNOWN_NAME) {
                return handleUnexpectedWithin(parser, context, bean)
            }

            val propertyName = parser.currentName()!!
            parser.nextToken()

            if (IgnorePropertiesUtil.shouldIgnore(propertyName, myIgnorableProperties, myIncludableProperties)) {
                handleIgnoredProperty(parser, context, bean, propertyName)
                index = parser.nextNameMatch(propertyNameMatcher)
                continue
            } else if (externalTypeHandler.handlePropertyValue(parser, context, propertyName, bean)) {
                index = parser.nextNameMatch(propertyNameMatcher)
                continue
            }

            val anySetter = myAnySetter ?: let {
                handleUnknownProperty(parser, context, bean, parser.currentName()!!)
                index = parser.nextNameMatch(propertyNameMatcher)
                continue
            }

            try {
                anySetter.deserializeAndSet(parser, context, bean, propertyName)
            } catch (e: Exception) {
                wrapAndThrow(e, bean, propertyName, context)
            }

            index = parser.nextNameMatch(propertyNameMatcher)
        }

        return externalTypeHandler.complete(parser, context, bean)
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeUsingPropertyBasedWithExternalTypeId(parser: CirJsonParser,
            context: DeserializationContext): Any? {
        val externalTypeHandler = myExternalTypeIdHandler!!.start()
        val creator = myPropertyBasedCreator!!
        val buffer = creator.startBuilding(parser, context, myObjectIdReader)
        val propertyNameMatcher = myPropertyNameMatcher!!
        val propertiesByIndex = myPropertiesByIndex!!
        var token = parser.currentToken()

        while (token == CirJsonToken.PROPERTY_NAME) {
            val propertyName = parser.currentName()!!
            token = parser.nextToken()!!
            val creatorProperty = creator.findCreatorProperty(propertyName)

            if (buffer.readIdProperty(propertyName) && creatorProperty == null) {
                token = parser.nextToken()
                continue
            }

            if (creatorProperty != null) {
                if (externalTypeHandler.handlePropertyValue(parser, context, propertyName, null) ||
                        !buffer.assignParameter(creatorProperty,
                                deserializeWithErrorWrapping(parser, context, creatorProperty))) {
                    token = parser.nextToken()
                    continue
                }

                parser.nextToken()

                val bean = try {
                    creator.build(context, buffer)!!
                } catch (e: Exception) {
                    wrapAndThrow(e, myBeanType.rawClass, propertyName, context)
                }

                return if (bean::class != myBeanType.rawClass) {
                    context.reportBadDefinition(myBeanType,
                            "Cannot create polymorphic instances with external type ids ($myBeanType -> ${bean::class})")
                } else {
                    deserializeWithExternalTypeId(parser, context, bean, externalTypeHandler)
                }
            }

            val index = propertyNameMatcher.matchName(propertyName)

            if (index >= 0) {
                val property = propertiesByIndex[index]

                if (token.isScalarValue) {
                    externalTypeHandler.handleTypePropertyValue(parser, context, propertyName, null)
                }

                buffer.bufferProperty(property, property.deserialize(parser, context))
                token = parser.nextToken()
                continue
            }

            if (externalTypeHandler.handlePropertyValue(parser, context, propertyName, null)) {
                token = parser.nextToken()
                continue
            } else if (IgnorePropertiesUtil.shouldIgnore(propertyName, myIgnorableProperties, myIncludableProperties)) {
                handleIgnoredProperty(parser, context, handledType(), propertyName)
                token = parser.nextToken()
                continue
            }

            val anySetter = myAnySetter ?: let {
                handleUnknownProperty(parser, context, myValueClass, propertyName)
                token = parser.nextToken()
                continue
            }

            buffer.bufferAnyProperty(anySetter, propertyName, anySetter.deserialize(parser, context))
        }

        return try {
            externalTypeHandler.complete(parser, context, buffer, creator)
        } catch (e: Exception) {
            wrapInstantiationProblem(e, context)
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    /**
     * Helper method for getting a lazily constructed exception to be reported to
     * [DeserializationContext.handleInstantiationProblem].
     */
    protected open fun creatorReturnedNullException(): Exception {
        return myNullFromCreator ?: NullPointerException("CirJSON Creator returned null").also {
            myNullFromCreator = it
        }
    }

    /**
     * Method called if an unexpected token (other than [CirJsonToken.PROPERTY_NAME]) is found after POJO has been
     * instantiated and partially bound.
     */
    @Throws(CirJacksonException::class)
    protected open fun handleUnexpectedWithin(parser: CirJsonParser, context: DeserializationContext, bean: Any): Any? {
        return context.handleUnexpectedToken(getValueType(context), parser)
    }

    private class BeanReferring(private val myContext: DeserializationContext,
            reference: UnresolvedForwardReferenceException, beanType: KotlinType,
            private val myProperty: SettableBeanProperty) : ReadableObjectId.Referring(reference, beanType) {

        var bean: Any? = null

        override fun handleResolvedForwardReference(id: Any, value: Any?) {
            val bean = bean ?: return myContext.reportInputMismatch(myProperty,
                    "Cannot resolve ObjectId forward reference using property '${myProperty.name}' (of type ${myProperty.declaringClass.qualifiedName}): Bean not yet resolved")
            myProperty.set(bean, value)
        }

    }

}