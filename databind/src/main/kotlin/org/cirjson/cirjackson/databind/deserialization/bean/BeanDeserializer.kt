package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.symbols.PropertyNameMatcher
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.deserialization.BeanDeserializerBuilder
import org.cirjson.cirjackson.databind.deserialization.ReadableObjectId
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.UnresolvedForwardReferenceException
import org.cirjson.cirjackson.databind.deserialization.implementation.ExternalTypeHandler
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader
import org.cirjson.cirjackson.databind.deserialization.implementation.UnwrappedPropertyHandler
import org.cirjson.cirjackson.databind.util.NameTransformer
import org.cirjson.cirjackson.databind.util.nullable
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
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected fun deserializeOther(parser: CirJsonParser, context: DeserializationContext, token: CirJsonToken): Any? {
        TODO("Not yet implemented")
    }

    /**
     * Secondary deserialization method, called in cases where POJO instance is created as part of deserialization,
     * potentially after collecting some or all of the properties to set.
     */
    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext, intoValue: Any): Any? {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    /**
     * Streamlined version that is only used when no "special" features are enabled.
     */
    @Throws(CirJacksonException::class)
    private fun vanillaDeserialize(parser: CirJsonParser, context: DeserializationContext, token: CirJsonToken): Any? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    private fun vanillaDeserialize(parser: CirJsonParser, context: DeserializationContext, bean: Any,
            propertyName: String): Any? {
        TODO("Not yet implemented")
    }

    /**
     * General version used when handling needs more advanced features.
     */
    @Throws(CirJacksonException::class)
    override fun deserializeFromObject(parser: CirJsonParser, context: DeserializationContext): Any? {
        TODO("Not yet implemented")
    }

    /**
     * Method called to deserialize bean using "property-based creator": this means that a non-default constructor or
     * factory method is called, and then possibly other setters. The trick is that values for creator method need to be
     * buffered, first; and due to non-guaranteed ordering possibly some other properties as well.
     */
    @Throws(CirJacksonException::class)
    override fun deserializeUsingPropertyBased(parser: CirJsonParser, context: DeserializationContext): Any? {
        TODO("Not yet implemented")
    }

    @Throws(DatabindException::class)
    private fun handleUnresolvedReference(context: DeserializationContext, property: SettableBeanProperty,
            reference: UnresolvedForwardReferenceException): BeanReferring {
        TODO("Not yet implemented")
    }

    @Throws(DatabindException::class)
    protected fun deserializeWithErrorWrapping(parser: CirJsonParser, context: DeserializationContext,
            property: SettableBeanProperty): Any? {
        TODO("Not yet implemented")
    }

    /**
     * Helper method called for rare case of pointing to [CirJsonToken.VALUE_NULL] token. While this is most often an
     * erroneous condition, there is one specific case with XML handling where polymorphic type with no properties is
     * exposed as such, and should be handled same as empty Object.
     */
    @Throws(CirJacksonException::class)
    protected open fun deserializeFromNull(parser: CirJsonParser, context: DeserializationContext): Any? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Deserializing when we have to consider an active View
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun deserializeWithView(parser: CirJsonParser, context: DeserializationContext, bean: Any,
            activeView: KClass<*>): Any? {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeWithUnwrapped(parser: CirJsonParser, context: DeserializationContext,
            bean: Any): Any? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeUsingPropertyBasedWithUnwrapped(parser: CirJsonParser,
            context: DeserializationContext): Any? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Handling for cases where we have property/-ies with external type id
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun deserializeWithExternalTypeId(parser: CirJsonParser, context: DeserializationContext): Any? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeWithExternalTypeId(parser: CirJsonParser, context: DeserializationContext,
            bean: Any): Any? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeWithExternalTypeId(parser: CirJsonParser, context: DeserializationContext, bean: Any,
            externalTypeHandler: ExternalTypeHandler): Any? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeUsingPropertyBasedWithExternalTypeId(parser: CirJsonParser,
            context: DeserializationContext): Any? {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    /**
     * Method called if an unexpected token (other than [CirJsonToken.PROPERTY_NAME]) is found after POJO has been
     * instantiated and partially bound.
     */
    @Throws(CirJacksonException::class)
    protected open fun handleUnexpectedWithin(parser: CirJsonParser, context: DeserializationContext, bean: Any): Any? {
        TODO("Not yet implemented")
    }

    private class BeanReferring(private val myContext: DeserializationContext,
            reference: UnresolvedForwardReferenceException, beanType: KotlinType,
            private val myProperty: SettableBeanProperty) : ReadableObjectId.Referring(reference, beanType) {

        var bean: Any? = null

        override fun handleResolvedForwardReference(id: Any, value: Any?) {
            TODO("Not yet implemented")
        }

    }

}