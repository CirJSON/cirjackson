package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader
import org.cirjson.cirjackson.databind.introspection.AnnotatedMethod
import org.cirjson.cirjackson.databind.util.NameTransformer
import org.cirjson.cirjackson.databind.util.typeDescription

/**
 * @constructor Main constructor used both for creating new instances (by [BeanDeserializer.asArrayDeserializer]) and
 * for creating copies with different delegate.
 *
 * @property myDelegate Deserializer we delegate operations that we cannot handle.
 *
 * @property myTargetType Type that the builder will produce, target type; as opposed to `handledType()` which refers
 * to Builder class.
 *
 * @property myOrderedProperties Properties in order expected to be found in CirJSON array.
 */
open class BeanAsArrayBuilderDeserializer(protected val myDelegate: BeanDeserializerBase,
        protected val myTargetType: KotlinType, protected val myOrderedProperties: Array<SettableBeanProperty?>,
        protected val myBuildMethod: AnnotatedMethod) : BeanDeserializerBase(myDelegate) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun unwrappingDeserializer(context: DeserializationContext,
            unwrapper: NameTransformer): ValueDeserializer<Any> {
        return myDelegate.unwrappingDeserializer(context, unwrapper)
    }

    override fun withObjectIdReader(objectIdReader: ObjectIdReader?): BeanDeserializerBase {
        return BeanAsArrayBuilderDeserializer(myDelegate.withObjectIdReader(objectIdReader), myTargetType,
                myOrderedProperties, myBuildMethod)
    }

    override fun withByNameInclusion(ignorableProperties: Set<String>?,
            includableProperties: Set<String>?): BeanDeserializerBase {
        return BeanAsArrayBuilderDeserializer(myDelegate.withByNameInclusion(ignorableProperties, includableProperties),
                myTargetType, myOrderedProperties, myBuildMethod)
    }

    override fun withIgnoreAllUnknown(ignoreAllUnknown: Boolean): BeanDeserializerBase {
        return BeanAsArrayBuilderDeserializer(myDelegate.withIgnoreAllUnknown(ignoreAllUnknown), myTargetType,
                myOrderedProperties, myBuildMethod)
    }

    override fun withBeanProperties(beanProperties: BeanPropertyMap?): BeanDeserializerBase {
        return BeanAsArrayBuilderDeserializer(myDelegate.withBeanProperties(beanProperties), myTargetType,
                myOrderedProperties, myBuildMethod)
    }

    override fun asArrayDeserializer(): BeanDeserializerBase {
        return this
    }

    override fun initializeNameMatcher(context: DeserializationContext) {
        // No op
    }

    /*
     *******************************************************************************************************************
     * BeanDeserializerBase implementation
     *******************************************************************************************************************
     */

    override fun supportsUpdate(config: DeserializationConfig): Boolean? {
        return false
    }

    /*
     *******************************************************************************************************************
     * ValueDeserializer implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun finishBuild(context: DeserializationContext, builder: Any?): Any? {
        return try {
            myBuildMethod.member.invoke(builder)
        } catch (e: Exception) {
            wrapInstantiationProblem(e, context)
        }
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        if (!parser.isExpectedStartArrayToken) {
            return finishBuild(context, deserializeFromNonArray(parser, context))
        } else if (!myVanillaProcessing) {
            return finishBuild(context, deserializeFromNonArray(parser, context))
        }

        var builder = myValueInstantiator.createUsingDefault(context)
        val properties = myOrderedProperties
        var i = 0
        val propertyCount = properties.size

        while (true) {
            if (parser.nextToken() == CirJsonToken.END_ARRAY) {
                return finishBuild(context, builder)
            } else if (i == propertyCount) {
                break
            }

            val property = properties[i]

            if (property != null) {
                try {
                    builder = property.deserializeSetAndReturn(parser, context, builder!!)
                } catch (e: Exception) {
                    wrapAndThrow(e, builder!!, property.name, context)
                }
            } else {
                parser.skipChildren()
            }

            ++i
        }

        if (!myIgnoreAllUnknown && context.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)) {
            return context.reportInputMismatch(handledType(),
                    "Unexpected CirJSON values; expected at most $propertyCount properties (in CirJSON Array)")
        }

        while (parser.nextToken() != CirJsonToken.END_ARRAY) {
            parser.skipChildren()
        }

        return finishBuild(context, builder)
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext, intoValue: Any): Any? {
        return myDelegate.deserialize(parser, context, intoValue)
    }

    @Throws(CirJacksonException::class)
    override fun deserializeFromObject(parser: CirJsonParser, context: DeserializationContext): Any? {
        return deserializeFromNonArray(parser, context)
    }

    /*
     *******************************************************************************************************************
     * Helper methods, non-standard creation
     *******************************************************************************************************************
     */

    /**
     * Alternate deserialization method that has to check many more configuration aspects than the "vanilla" processing.
     * Note: should NOT resolve builder; caller will do that
     *
     * @return Builder object in use.
     */
    @Throws(CirJacksonException::class)
    protected open fun deserializeNonVanilla(parser: CirJsonParser, context: DeserializationContext): Any? {
        if (myNonStandardCreation) {
            return deserializeFromObjectUsingNonDefault(parser, context)
        }

        val builder = myValueInstantiator.createUsingDefault(context)!!

        if (myInjectables != null) {
            injectValues(context, builder)
        }

        val activeView = context.takeIf { myNeedViewProcessing }?.activeView
        val properties = myOrderedProperties
        var i = 0
        val propertyCount = properties.size

        while (true) {
            if (parser.nextToken() == CirJsonToken.END_ARRAY) {
                return builder
            } else if (i == propertyCount) {
                break
            }

            val property = properties[i]
            ++i

            if (property?.let { activeView == null || it.visibleInView(activeView) } ?: false) {
                try {
                    property.deserializeSetAndReturn(parser, context, builder)
                } catch (e: Exception) {
                    wrapAndThrow(e, builder, property.name, context)
                }

                continue
            }

            parser.skipChildren()
        }

        if (!myIgnoreAllUnknown && context.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)) {
            return context.reportWrongTokenException(this, CirJsonToken.END_ARRAY,
                    "Unexpected CirJSON value(s); expected at most $propertyCount properties (in CirJSON Array)")
        }

        while (parser.nextToken() != CirJsonToken.END_ARRAY) {
            parser.skipChildren()
        }

        return builder
    }

    /**
     * Method called to deserialize bean using "property-based creator": this means that a non-default constructor or
     * factory method is called, and then possibly other setters. The trick is that values for creator method need to be
     * buffered, first; and due to non-guaranteed ordering possibly some other properties as well.
     */
    @Throws(CirJacksonException::class)
    final override fun deserializeUsingPropertyBased(parser: CirJsonParser, context: DeserializationContext): Any? {
        val creator = myPropertyBasedCreator!!
        val buffer = creator.startBuilding(parser, context, myObjectIdReader)

        val activeView = context.takeIf { myNeedViewProcessing }?.activeView
        val properties = myOrderedProperties
        var i = -1
        var builder: Any? = null

        while (parser.nextToken() != CirJsonToken.END_ARRAY) {
            ++i

            val property = properties.getOrNull(i) ?: let {
                parser.skipChildren()
                continue
            }

            if (activeView != null && !property.visibleInView(activeView)) {
                parser.skipChildren()
                continue
            } else if (builder != null) {
                try {
                    builder = property.deserializeSetAndReturn(parser, context, builder)
                } catch (e: Exception) {
                    wrapAndThrow(e, builder!!, property.name, context)
                }

                continue
            }

            val propertyName = property.name
            val creatorProperty = creator.findCreatorProperty(propertyName)

            if (creatorProperty == null) {
                if (!buffer.readIdProperty(propertyName)) {
                    buffer.bufferProperty(property, property.deserialize(parser, context))
                }

                continue
            } else if (!buffer.assignParameter(creatorProperty, creatorProperty.deserialize(parser, context))) {
                continue
            }

            try {
                builder = creator.build(context, buffer)!!
            } catch (e: Exception) {
                wrapAndThrow(e, myBeanType.rawClass, propertyName, context)
            }

            if (builder::class != myBeanType.rawClass) {
                return context.reportBadDefinition(myBeanType,
                        "Cannot support implicit polymorphic deserialization for POJOs-as-Arrays style: nominal type ${myBeanType.typeDescription}, actual type ${builder::class.qualifiedName}")
            }
        }

        return builder ?: try {
            creator.build(context, buffer)
        } catch (e: Exception) {
            wrapInstantiationProblem(e, context)
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods, error reporting
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun deserializeFromNonArray(parser: CirJsonParser, context: DeserializationContext): Any? {
        return context.handleUnexpectedToken(getValueType(context), parser.currentToken(), parser,
                "Cannot deserialize a POJO (of type ${myBeanType.rawClass.qualifiedName}) from non-Array representation (token: ${parser.currentToken()}): type/property designed to be serialized as CirJSON Array")
    }

}