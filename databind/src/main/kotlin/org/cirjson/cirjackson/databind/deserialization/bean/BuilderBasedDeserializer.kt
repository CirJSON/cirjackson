package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.symbols.PropertyNameMatcher
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.deserialization.BeanDeserializerBuilder
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader
import org.cirjson.cirjackson.databind.deserialization.implementation.UnwrappedPropertyHandler
import org.cirjson.cirjackson.databind.introspection.AnnotatedMethod
import org.cirjson.cirjackson.databind.util.NameTransformer

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
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Concrete deserialization methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserializeFromObject(parser: CirJsonParser, context: DeserializationContext): Any? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun deserializeUsingPropertyBased(parser: CirJsonParser, context: DeserializationContext): Any? {
        TODO("Not yet implemented")
    }

}