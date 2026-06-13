package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.CirJsonTokenId
import org.cirjson.cirjackson.core.symbols.PropertyNameMatcher
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.configuration.CoercionAction
import org.cirjson.cirjackson.databind.deserialization.BeanDeserializerBuilder
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader
import org.cirjson.cirjackson.databind.deserialization.implementation.UnwrappedPropertyHandler
import org.cirjson.cirjackson.databind.introspection.AnnotatedMethod
import org.cirjson.cirjackson.databind.util.*
import kotlin.reflect.KClass

/**
 * Class that handles deserialization using a separate Builder class, which is used for data binding and produces actual
 * deserialized value at the end of data binding.
 *
 * Note on implementation: much of code has been copied from [BeanDeserializer].
 */
open class BuilderBasedDeserializer : BeanDeserializerBase {

    protected val myBuildMethod: AnnotatedMethod?

    /**
     * Type that the builder will produce, target type; as opposed to `handledType()` which refers to Builder class.
     */
    protected val myTargetType: KotlinType

    protected var myPropertyNameMatcher: PropertyNameMatcher? = null

    protected var myPropertiesByIndex: Array<SettableBeanProperty>? = null

    /**
     * State marker we need in order to avoid infinite recursion for some cases (not very clean, alas, but has to do).
     */
    @Volatile
    @Transient
    private var myCurrentlyTransforming: NameTransformer? = null

    /*
     *******************************************************************************************************************
     * Lifecycle, construction, initialization
     *******************************************************************************************************************
     */

    constructor(builder: BeanDeserializerBuilder, beanDescription: BeanDescription, targetType: KotlinType,
            properties: BeanPropertyMap, backReferences: Map<String, SettableBeanProperty>?,
            ignorableProperties: Set<String>?, ignoreAllUnknown: Boolean, hasViews: Boolean) : this(builder,
            beanDescription, targetType, properties, backReferences, ignorableProperties, ignoreAllUnknown, null,
            hasViews)

    constructor(builder: BeanDeserializerBuilder, beanDescription: BeanDescription, targetType: KotlinType,
            properties: BeanPropertyMap, backReferences: Map<String, SettableBeanProperty>?,
            ignorableProperties: Set<String>?, ignoreAllUnknown: Boolean, includableProperties: Set<String>?,
            hasViews: Boolean) : super(builder, beanDescription, properties, backReferences, ignorableProperties,
            ignoreAllUnknown, includableProperties, hasViews) {
        myBuildMethod = builder.buildMethod
        myTargetType = targetType

        if (myObjectIdReader != null) {
            throw IllegalArgumentException(
                    "Cannot use Object Id with Builder-based deserialization (type ${beanDescription.type})")
        }
    }

    /**
     * Copy-constructor that can be used by subclasses to allow copy-on-write styling copying of settings of an existing
     * instance.
     */
    protected constructor(source: BuilderBasedDeserializer) : this(source, source.myIgnoreAllUnknown)

    protected constructor(source: BuilderBasedDeserializer, ignoreAllUnknown: Boolean) : super(source,
            ignoreAllUnknown) {
        myBuildMethod = source.myBuildMethod
        myTargetType = source.myTargetType
        myPropertyNameMatcher = source.myPropertyNameMatcher
        myPropertiesByIndex = source.myPropertiesByIndex
    }

    protected constructor(source: BuilderBasedDeserializer, unwrapHandler: UnwrappedPropertyHandler?,
            renamedProperties: BeanPropertyMap?, ignoreAllUnknown: Boolean) : super(source, unwrapHandler,
            renamedProperties, ignoreAllUnknown) {
        myBuildMethod = source.myBuildMethod
        myTargetType = source.myTargetType
        myPropertyNameMatcher = myBeanProperties!!.nameMatcher
        myPropertiesByIndex = myBeanProperties.nameMatcherProperties
    }

    protected constructor(source: BuilderBasedDeserializer, objectIdReader: ObjectIdReader?) : super(source,
            objectIdReader) {
        myBuildMethod = source.myBuildMethod
        myTargetType = source.myTargetType
        myPropertyNameMatcher = source.myPropertyNameMatcher
        myPropertiesByIndex = source.myPropertiesByIndex
    }

    protected constructor(source: BuilderBasedDeserializer, ignorableProperties: Set<String>?) : this(source,
            ignorableProperties, source.myIncludableProperties)

    protected constructor(source: BuilderBasedDeserializer, ignorableProperties: Set<String>?,
            includableProperties: Set<String>?) : super(source, ignorableProperties, includableProperties) {
        myBuildMethod = source.myBuildMethod
        myTargetType = source.myTargetType
        myPropertyNameMatcher = source.myPropertyNameMatcher
        myPropertiesByIndex = source.myPropertiesByIndex
    }

    protected constructor(source: BuilderBasedDeserializer, beanProperties: BeanPropertyMap?) : super(source,
            beanProperties) {
        myBuildMethod = source.myBuildMethod
        myTargetType = source.myTargetType
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
        if (myCurrentlyTransforming === unwrapper) {
            return this
        }

        myCurrentlyTransforming = unwrapper

        try {
            val unwrapHandler = myUnwrappedPropertyHandler?.renameAll(context, unwrapper)
            val properties = myBeanProperties!!.renameAll(context, unwrapper)
            return BuilderBasedDeserializer(this, unwrapHandler, properties, true)
        } finally {
            myCurrentlyTransforming = null
        }
    }

    override fun withObjectIdReader(objectIdReader: ObjectIdReader?): BeanDeserializerBase {
        return BuilderBasedDeserializer(this, objectIdReader)
    }

    override fun withByNameInclusion(ignorableProperties: Set<String>?,
            includableProperties: Set<String>?): BeanDeserializerBase {
        return BuilderBasedDeserializer(this, ignorableProperties, includableProperties)
    }

    override fun withIgnoreAllUnknown(ignoreAllUnknown: Boolean): BeanDeserializerBase {
        return BuilderBasedDeserializer(this, ignoreAllUnknown)
    }

    override fun withBeanProperties(beanProperties: BeanPropertyMap?): BeanDeserializerBase {
        return BuilderBasedDeserializer(this, beanProperties)
    }

    @Suppress("UNCHECKED_CAST")
    override fun asArrayDeserializer(): BeanDeserializerBase {
        return BeanAsArrayBuilderDeserializer(this, myTargetType,
                myBeanProperties!!.primaryProperties as Array<SettableBeanProperty?>, myBuildMethod!!)
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

    override fun supportsUpdate(config: DeserializationConfig): Boolean? {
        return false
    }

    @Throws(CirJacksonException::class)
    protected open fun finishBuild(context: DeserializationContext, builder: Any?): Any? {
        myBuildMethod ?: return builder

        return try {
            myBuildMethod.member.invoke(builder)
        } catch (e: Exception) {
            wrapInstantiationProblem(e, context)
        }
    }

    /**
     * Main deserialization method for bean-based objects (POJOs).
     */
    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        if (parser.isExpectedStartObjectToken) {
            if (myVanillaProcessing) {
                return finishBuild(context, vanillaDeserialize(parser, context))
            }

            parser.nextToken()
            return finishBuild(context, deserializeFromObject(parser, context))
        }

        return when (parser.currentTokenId()) {
            CirJsonTokenId.ID_STRING -> finishBuild(context, deserializeFromString(parser, context))

            CirJsonTokenId.ID_NUMBER_INT -> finishBuild(context, deserializeFromNumber(parser, context))

            CirJsonTokenId.ID_NUMBER_FLOAT -> finishBuild(context, deserializeFromDouble(parser, context))

            CirJsonTokenId.ID_EMBEDDED_OBJECT -> parser.embeddedObject

            CirJsonTokenId.ID_TRUE, CirJsonTokenId.ID_FALSE -> finishBuild(context,
                    deserializeFromBoolean(parser, context))

            CirJsonTokenId.ID_START_ARRAY -> deserializeFromArray(parser, context)

            CirJsonTokenId.ID_PROPERTY_NAME, CirJsonTokenId.ID_END_OBJECT -> finishBuild(context,
                    deserializeFromObject(parser, context))

            else -> context.handleUnexpectedToken(getValueType(context), parser)
        }
    }

    /**
     * Secondary deserialization method, called in cases where POJO instance is created as part of deserialization,
     * potentially after collecting some or all of the properties to set.
     */
    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext, intoValue: Any): Any? {
        val valueType = myTargetType
        val builderRawType = handledType()
        val instanceRawType = intoValue::class

        return if (builderRawType.isAssignableFrom(instanceRawType)) {
            context.reportBadDefinition(valueType,
                    "Deserialization of $valueType by passing existing Builder (${builderRawType.qualifiedName}) instance not supported")
        } else {
            context.reportBadDefinition(valueType,
                    "Deserialization of $valueType by passing existing instance (${instanceRawType.qualifiedName}) not supported")
        }
    }

    /*
     *******************************************************************************************************************
     * Concrete deserialization methods
     *******************************************************************************************************************
     */

    /**
     * Streamlined version that is only used when no "special" features are enabled.
     */
    @Throws(CirJacksonException::class)
    private fun vanillaDeserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        var builder = myValueInstantiator.createUsingDefault(context)!!

        while (true) {
            val index = parser.nextNameMatch(myPropertyNameMatcher!!)

            if (index >= 0) {
                parser.nextToken()
                val property = myPropertiesByIndex!![index]

                try {
                    builder = property.deserializeSetAndReturn(parser, context, builder)!!
                } catch (e: Exception) {
                    wrapAndThrow(e, builder, property.name, context)
                }

                continue
            }

            if (index == PropertyNameMatcher.MATCH_END_OBJECT) {
                return builder
            } else if (index != PropertyNameMatcher.MATCH_UNKNOWN_NAME) {
                parser.nextToken()
                handleUnknownVanilla(parser, context, builder, parser.currentName()!!)
                continue
            }

            return handleUnexpectedWithin(parser, context, builder)
        }
    }

    /**
     * General version used when handling needs more advanced features.
     */
    @Throws(CirJacksonException::class)
    override fun deserializeFromObject(parser: CirJsonParser, context: DeserializationContext): Any? {
        if (myNonStandardCreation) {
            return if (myUnwrappedPropertyHandler != null) {
                deserializeWithUnwrapped(parser, context)
            } else if (myExternalTypeIdHandler != null) {
                deserializeWithExternalTypeId(parser, context)
            } else {
                deserializeFromObjectUsingNonDefault(parser, context)
            }
        }

        var bean = myValueInstantiator.createUsingDefault(context)!!
        myInjectables?.also { injectValues(context, bean) }

        if (myNeedViewProcessing) {
            val activeView = context.activeView

            if (activeView != null) {
                return deserializeWithView(parser, context, bean, activeView)
            }
        }

        var index = parser.currentNameMatch(myPropertyNameMatcher!!)

        while (true) {
            if (index >= 0) {
                parser.nextToken()

                try {
                    bean = myPropertiesByIndex!![index].deserializeSetAndReturn(parser, context, bean)!!
                } catch (e: Exception) {
                    wrapAndThrow(e, bean, parser.currentName(), context)
                }

                index = parser.nextNameMatch(myPropertyNameMatcher!!)
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
     * Method called to deserialize bean using "property-based creator": this means that a non-default constructor or
     * factory method is called, and then possibly other setters. The trick is that values for creator method need to be
     * buffered, first; and due to non-guaranteed ordering possibly some other properties as well.
     *
     * @return Builder instance constructed
     */
    @Throws(CirJacksonException::class)
    override fun deserializeUsingPropertyBased(parser: CirJsonParser, context: DeserializationContext): Any? {
        val creator = myPropertyBasedCreator!!
        val buffer = creator.startBuilding(parser, context, myObjectIdReader)
        val activeView = context.takeIf { myNeedViewProcessing }?.activeView

        var unknown: TokenBuffer? = null

        var token = parser.currentToken()

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

                if (!buffer.assignParameter(creatorProperty, creatorProperty.deserialize(parser, context))) {
                    token = parser.nextToken()
                    continue
                }

                parser.nextToken()

                var builder = try {
                    creator.build(context, buffer)!!
                } catch (e: Exception) {
                    wrapAndThrow(e, myBeanType.rawClass, propertyName, context)
                }

                if (builder::class != myBeanType.rawClass) {
                    return handlePolymorphic(parser, context, builder, unknown!!)
                }

                if (unknown != null) {
                    builder = handleUnknownProperties(context, builder, unknown)
                }

                return deserializeWithBuilder(parser, context, builder)
            }

            val index = myPropertyNameMatcher!!.matchName(propertyName)

            if (index >= 0) {
                val property = myPropertiesByIndex!![index]
                buffer.bufferProperty(property, property.deserialize(parser, context))
                token = parser.nextToken()
                continue
            }

            myAnySetter?.also { anySetter ->
                buffer.bufferAnyProperty(anySetter, propertyName, anySetter.deserialize(parser, context))
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

        val builder = try {
            creator.build(context, buffer)!!
        } catch (e: Exception) {
            wrapInstantiationProblem(e, context)!!
        }

        return if (unknown != null) {
            if (builder::class != myBeanType.rawClass) {
                handlePolymorphic(null, context, builder, unknown)
            } else {
                handleUnknownProperties(context, builder, unknown)
            }
        } else {
            builder
        }
    }

    @Throws(CirJacksonException::class)
    protected fun deserializeWithBuilder(parser: CirJsonParser, context: DeserializationContext, builder: Any): Any? {
        var realBuilder = builder
        myInjectables?.also { injectValues(context, realBuilder) }

        if (myUnwrappedPropertyHandler != null) {
            if (parser.hasToken(CirJsonToken.START_OBJECT)) {
                parser.nextToken()
            }

            val tokens = context.bufferForInputBuffering(parser)
            tokens.writeStartObject()
            return deserializeWithUnwrapped(parser, context, realBuilder, tokens)
        } else if (myExternalTypeIdHandler != null) {
            return deserializeWithExternalTypeId(parser, context, realBuilder)
        }

        if (myNeedViewProcessing) {
            val activeView = context.activeView

            if (activeView != null) {
                return deserializeWithView(parser, context, realBuilder, activeView)
            }
        }

        var index = if (parser.isExpectedStartObjectToken) {
            parser.nextNameMatch(myPropertyNameMatcher!!)
        } else {
            parser.currentNameMatch(myPropertyNameMatcher!!)
        }

        while (true) {
            if (index >= 0) {
                parser.nextToken()
                val property = myPropertiesByIndex!![index]

                try {
                    realBuilder = property.deserializeSetAndReturn(parser, context, realBuilder)!!
                } catch (e: Exception) {
                    wrapAndThrow(e, realBuilder, property.name, context)
                }

                index = parser.nextNameMatch(myPropertyNameMatcher!!)
                continue
            }

            if (index == PropertyNameMatcher.MATCH_END_OBJECT) {
                return realBuilder
            } else if (index != PropertyNameMatcher.MATCH_UNKNOWN_NAME) {
                return handleUnexpectedWithin(parser, context, realBuilder)
            }

            parser.nextToken()
            handleUnknownVanilla(parser, context, realBuilder, parser.currentName()!!)
            index = parser.nextNameMatch(myPropertyNameMatcher!!)
        }
    }

    @Throws(CirJacksonException::class)
    override fun deserializeFromArray(parser: CirJsonParser, context: DeserializationContext): Any? {
        val delegateDeserializer = myArrayDelegateDeserializer ?: myDelegateDeserializer

        if (delegateDeserializer != null) {
            val builder = myValueInstantiator.createUsingDelegate(context,
                    delegateDeserializer.deserialize(parser, context))!!
            myInjectables?.also { injectValues(context, builder) }
            return finishBuild(context, builder)
        }

        val action = findCoercionFromEmptyArray(context)
        val unwrap = context.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)

        if (!unwrap && action == CoercionAction.FAIL) {
            return context.handleUnexpectedToken(getValueType(context), parser)
        }

        val token = parser.nextToken()

        if (token == CirJsonToken.END_ARRAY) {
            return when (action) {
                CoercionAction.AS_EMPTY -> getEmptyValue(context)
                CoercionAction.AS_NULL, CoercionAction.TRY_CONVERT -> getNullValue(context)
                else -> context.handleUnexpectedToken(getValueType(context), CirJsonToken.START_ARRAY, parser, null)
            }
        }

        if (!unwrap) {
            return context.handleUnexpectedToken(getValueType(context), parser)
        }

        val value = deserialize(parser, context)

        if (parser.nextToken() != CirJsonToken.END_ARRAY) {
            handleMissingEndArrayForSingle(parser, context)
        }

        return value
    }

    /*
     *******************************************************************************************************************
     * Deserializing when we have to consider an active View
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun deserializeWithView(parser: CirJsonParser, context: DeserializationContext, bean: Any,
            activeView: KClass<*>): Any? {
        var realBean = bean
        var index = parser.currentNameMatch(myPropertyNameMatcher!!)

        while (true) {
            if (index >= 0) {
                parser.nextToken()
                val property = myPropertiesByIndex!![index]

                if (!property.visibleInView(activeView)) {
                    if (context.isEnabled(DeserializationFeature.FAIL_ON_UNEXPECTED_VIEW_PROPERTIES)) {
                        return context.reportInputMismatch(handledType(),
                                "Input mismatch while deserializing ${handledType().name}. Property '${property.name}' is not part of current active view '${activeView.qualifiedName}' (disable 'DeserializationFeature.FAIL_ON_UNEXPECTED_VIEW_PROPERTIES' to allow)")
                    }

                    parser.skipChildren()
                    index = parser.nextNameMatch(myPropertyNameMatcher!!)
                    continue
                }

                try {
                    realBean = property.deserializeSetAndReturn(parser, context, realBean)!!
                } catch (e: Exception) {
                    wrapAndThrow(e, realBean, property.name, context)
                }

                index = parser.nextNameMatch(myPropertyNameMatcher!!)
                continue
            }

            if (index == PropertyNameMatcher.MATCH_END_OBJECT) {
                return realBean
            } else if (index != PropertyNameMatcher.MATCH_UNKNOWN_NAME) {
                return handleUnexpectedWithin(parser, context, realBean)
            }

            parser.nextToken()
            handleUnknownVanilla(parser, context, realBean, parser.currentName()!!)
            index = parser.nextNameMatch(myPropertyNameMatcher!!)
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
        myDelegateDeserializer?.also {
            return myValueInstantiator.createUsingDelegate(context, it.deserialize(parser, context))
        }

        if (myPropertyBasedCreator != null) {
            return deserializeUsingPropertyBasedWithUnwrapped(parser, context)
        }

        val tokens = context.bufferForInputBuffering(parser)
        tokens.writeStartObject()
        var bean = myValueInstantiator.createUsingDefault(context)!!
        myInjectables?.also { injectValues(context, bean) }

        val activeView = context.takeIf { myNeedViewProcessing }?.activeView
        var index = parser.currentNameMatch(myPropertyNameMatcher!!)

        while (true) {
            if (index >= 0) {
                parser.nextToken()
                val property = myPropertiesByIndex!![index]

                if (activeView != null && !property.visibleInView(activeView)) {
                    parser.skipChildren()
                    index = parser.nextNameMatch(myPropertyNameMatcher!!)
                    continue
                }

                try {
                    bean = property.deserializeSetAndReturn(parser, context, bean)!!
                } catch (e: Exception) {
                    wrapAndThrow(e, bean, property.name, context)
                }

                index = parser.nextNameMatch(myPropertyNameMatcher!!)
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
                index = parser.nextNameMatch(myPropertyNameMatcher!!)
                continue
            }

            tokens.writeName(propertyName)
            tokens.copyCurrentStructure(parser)

            myAnySetter?.also { anySetter ->
                try {
                    anySetter.deserializeAndSet(parser, context, bean, propertyName)
                } catch (e: Exception) {
                    wrapAndThrow(e, bean, propertyName, context)
                }
            }

            index = parser.nextNameMatch(myPropertyNameMatcher!!)
        }

        tokens.writeEndObject()
        return myUnwrappedPropertyHandler!!.processUnwrapped(parser, context, bean, tokens)
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeWithUnwrapped(parser: CirJsonParser, context: DeserializationContext, builder: Any,
            tokens: TokenBuffer): Any? {
        var realBuilder = builder
        val activeView = context.takeIf { myNeedViewProcessing }?.activeView
        var index = parser.currentNameMatch(myPropertyNameMatcher!!)

        while (true) {
            if (index >= 0) {
                parser.nextToken()
                val property = myPropertiesByIndex!![index]

                if (activeView != null && !property.visibleInView(activeView)) {
                    parser.skipChildren()
                    index = parser.nextNameMatch(myPropertyNameMatcher!!)
                    continue
                }

                try {
                    realBuilder = property.deserializeSetAndReturn(parser, context, realBuilder)!!
                } catch (e: Exception) {
                    wrapAndThrow(e, realBuilder, property.name, context)
                }

                index = parser.nextNameMatch(myPropertyNameMatcher!!)
                continue
            }

            if (index == PropertyNameMatcher.MATCH_END_OBJECT) {
                break
            } else if (index == PropertyNameMatcher.MATCH_ODD_TOKEN) {
                return handleUnexpectedWithin(parser, context, realBuilder)
            }

            val propertyName = parser.currentName()!!
            parser.nextToken()

            if (myIgnorableProperties?.contains(propertyName) ?: false) {
                handleIgnoredProperty(parser, context, realBuilder, propertyName)
                index = parser.nextNameMatch(myPropertyNameMatcher!!)
                continue
            }

            tokens.writeName(propertyName)
            tokens.copyCurrentStructure(parser)
            myAnySetter?.deserializeAndSet(parser, context, realBuilder, propertyName)
            index = parser.nextNameMatch(myPropertyNameMatcher!!)
        }

        tokens.writeEndObject()
        return myUnwrappedPropertyHandler!!.processUnwrapped(parser, context, realBuilder, tokens)
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeUsingPropertyBasedWithUnwrapped(parser: CirJsonParser,
            context: DeserializationContext): Any? {
        val creator = myPropertyBasedCreator!!
        val buffer = creator.startBuilding(parser, context, myObjectIdReader)

        val tokens = context.bufferForInputBuffering(parser)
        tokens.writeStartObject()

        var token = parser.currentToken()

        while (token == CirJsonToken.PROPERTY_NAME) {
            val propertyName = parser.currentName()!!
            parser.nextToken()
            val creatorProperty = creator.findCreatorProperty(propertyName)

            if (buffer.readIdProperty(propertyName) && creatorProperty == null) {
                token = parser.nextToken()
                continue
            }

            if (creatorProperty != null) {
                if (!buffer.assignParameter(creatorProperty, creatorProperty.deserialize(parser, context))) {
                    token = parser.nextToken()
                    continue
                }

                parser.nextToken()

                val builder = try {
                    creator.build(context, buffer)!!
                } catch (e: Exception) {
                    wrapAndThrow(e, myBeanType.rawClass, propertyName, context)
                }

                return if (builder::class != myBeanType.rawClass) {
                    handlePolymorphic(parser, context, builder, tokens)
                } else {
                    deserializeWithUnwrapped(parser, context, builder, tokens)
                }
            }

            val index = myPropertyNameMatcher!!.matchName(propertyName)

            if (index >= 0) {
                val property = myPropertiesByIndex!![index]
                buffer.bufferProperty(property, property.deserialize(parser, context))
                token = parser.nextToken()
                continue
            }

            if (IgnorePropertiesUtil.shouldIgnore(propertyName, myIgnorableProperties, myIncludableProperties)) {
                handleIgnoredProperty(parser, context, handledType(), propertyName)
                token = parser.nextToken()
                continue
            }

            tokens.writeName(propertyName)
            tokens.copyCurrentStructure(parser)
            myAnySetter?.also { buffer.bufferAnyProperty(it, propertyName, it.deserialize(parser, context)) }
            token = parser.nextToken()
        }

        tokens.writeEndObject()

        val builder = try {
            creator.build(context, buffer)!!
        } catch (e: Exception) {
            return wrapInstantiationProblem(e, context)
        }

        return myUnwrappedPropertyHandler!!.processUnwrapped(parser, context, builder, tokens)
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
        } else {
            deserializeWithExternalTypeId(parser, context, myValueInstantiator.createUsingDefault(context)!!)
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeWithExternalTypeId(parser: CirJsonParser, context: DeserializationContext,
            bean: Any): Any? {
        var realBean = bean
        val activeView = context.takeIf { myNeedViewProcessing }?.activeView
        val external = myExternalTypeIdHandler!!.start()
        var index = parser.currentNameMatch(myPropertyNameMatcher!!)

        while (true) {
            if (index >= 0) {
                val property = myPropertiesByIndex!![index]
                val token = parser.nextToken()!!

                if (token.isScalarValue) {
                    external.handleTypePropertyValue(parser, context, parser.currentName()!!, realBean)
                }

                if (activeView != null && !property.visibleInView(activeView)) {
                    parser.skipChildren()
                    index = parser.nextNameMatch(myPropertyNameMatcher!!)
                    continue
                }

                try {
                    realBean = property.deserializeSetAndReturn(parser, context, realBean)!!
                } catch (e: Exception) {
                    wrapAndThrow(e, realBean, property.name, context)
                }

                index = parser.nextNameMatch(myPropertyNameMatcher!!)
                continue
            }

            if (index == PropertyNameMatcher.MATCH_END_OBJECT) {
                break
            } else if (index != PropertyNameMatcher.MATCH_UNKNOWN_NAME) {
                return handleUnexpectedWithin(parser, context, realBean)
            }

            val propertyName = parser.currentName()!!

            if (IgnorePropertiesUtil.shouldIgnore(propertyName, myIgnorableProperties, myIncludableProperties)) {
                handleIgnoredProperty(parser, context, handledType(), propertyName)
                index = parser.nextNameMatch(myPropertyNameMatcher!!)
                continue
            }

            if (external.handlePropertyValue(parser, context, propertyName, realBean)) {
                index = parser.nextNameMatch(myPropertyNameMatcher!!)
                continue
            }

            if (myAnySetter != null) {
                try {
                    myAnySetter!!.deserializeAndSet(parser, context, realBean, propertyName)
                } catch (e: Exception) {
                    wrapAndThrow(e, realBean, propertyName, context)
                }
            } else {
                handleUnknownProperty(parser, context, realBean, propertyName)
            }

            index = parser.nextNameMatch(myPropertyNameMatcher!!)
        }

        return external.complete(parser, context, realBean)
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeUsingPropertyBasedWithExternalTypeId(parser: CirJsonParser,
            context: DeserializationContext): Any? {
        val type = myTargetType
        return context.reportBadDefinition(type,
                "Deserialization (of $type) with Builder, External type id, @CirJsonCreator not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Error handling
     *******************************************************************************************************************
     */

    /**
     * Method called if an unexpected token (other than `FIELD_NAME`) is found after POJO has been instantiated and
     * partially bound.
     */
    @Throws(CirJacksonException::class)
    protected open fun handleUnexpectedWithin(parser: CirJsonParser, context: DeserializationContext,
            beanOrBuilder: Any?): Any? {
        return context.handleUnexpectedToken(getValueType(context), parser)
    }

}