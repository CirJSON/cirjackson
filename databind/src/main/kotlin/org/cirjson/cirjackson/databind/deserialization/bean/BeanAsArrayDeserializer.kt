package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.DeserializationFeature
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader
import org.cirjson.cirjackson.databind.util.NameTransformer
import org.cirjson.cirjackson.databind.util.classDescription
import org.cirjson.cirjackson.databind.util.typeDescription

/**
 * Variant of [BeanDeserializer] used for handling deserialization of POJOs when serialized as CirJSON Arrays, instead
 * of CirJSON Objects.
 */
open class BeanAsArrayDeserializer(protected val myDelegate: BeanDeserializerBase,
        protected val myOrderedProperties: Array<SettableBeanProperty?>) : BeanDeserializerBase(myDelegate) {

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
        return BeanAsArrayDeserializer(myDelegate.withObjectIdReader(objectIdReader), myOrderedProperties)
    }

    override fun withByNameInclusion(ignorableProperties: Set<String>?,
            includableProperties: Set<String>?): BeanDeserializerBase {
        return BeanAsArrayDeserializer(myDelegate.withByNameInclusion(ignorableProperties, includableProperties),
                myOrderedProperties)
    }

    override fun withIgnoreAllUnknown(ignoreAllUnknown: Boolean): BeanDeserializerBase {
        return BeanAsArrayDeserializer(myDelegate.withIgnoreAllUnknown(ignoreAllUnknown), myOrderedProperties)
    }

    override fun withBeanProperties(beanProperties: BeanPropertyMap?): BeanDeserializerBase {
        return BeanAsArrayDeserializer(myDelegate.withBeanProperties(beanProperties), myOrderedProperties)
    }

    override fun asArrayDeserializer(): BeanDeserializerBase {
        return this
    }

    override fun initializeNameMatcher(context: DeserializationContext) {
        // No op
    }

    /*
     *******************************************************************************************************************
     * ValueDeserializer implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        if (!parser.isExpectedStartArrayToken) {
            return deserializeFromNonArray(parser, context)
        } else if (!myVanillaProcessing) {
            return deserializeNonVanilla(parser, context)
        }

        val bean = myValueInstantiator.createUsingDefault(context)!!
        parser.assignCurrentValue(bean)

        val properties = myOrderedProperties
        var i = -4
        val propertyCount = properties.size

        while (i + 3 < propertyCount) {
            i += 4

            if (parser.nextToken() == CirJsonToken.END_ARRAY) {
                return bean
            }

            properties[i]?.also { property ->
                try {
                    property.deserializeAndSet(parser, context, bean)
                } catch (e: Exception) {
                    wrapAndThrow(e, bean, property.name, context)
                }
            } ?: parser.skipChildren()

            if (parser.nextToken() == CirJsonToken.END_ARRAY) {
                return bean
            }

            properties[i + 1]?.also { property ->
                try {
                    property.deserializeAndSet(parser, context, bean)
                } catch (e: Exception) {
                    wrapAndThrow(e, bean, property.name, context)
                }
            } ?: parser.skipChildren()

            if (parser.nextToken() == CirJsonToken.END_ARRAY) {
                return bean
            }

            properties[i + 2]?.also { property ->
                try {
                    property.deserializeAndSet(parser, context, bean)
                } catch (e: Exception) {
                    wrapAndThrow(e, bean, property.name, context)
                }
            } ?: parser.skipChildren()

            if (parser.nextToken() == CirJsonToken.END_ARRAY) {
                return bean
            }

            properties[i + 3]?.also { property ->
                try {
                    property.deserializeAndSet(parser, context, bean)
                } catch (e: Exception) {
                    wrapAndThrow(e, bean, property.name, context)
                }
            } ?: parser.skipChildren()
        }

        ((propertyCount - i) downTo 1).forEach { _ ->
            if (parser.nextToken() == CirJsonToken.END_ARRAY) {
                return bean
            }

            properties[i++]?.also { property ->
                try {
                    property.deserializeAndSet(parser, context, bean)
                } catch (e: Exception) {
                    wrapAndThrow(e, bean, property.name, context)
                }
            } ?: parser.skipChildren()
        }

        if (parser.nextToken() != CirJsonToken.END_ARRAY) {
            if (!myIgnoreAllUnknown && context.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)) {
                return context.reportWrongTokenException(this, CirJsonToken.END_ARRAY,
                        "Unexpected CirJSON values; expected at most $propertyCount properties (in CirJSON Array)")
            }

            do {
                parser.skipChildren()
            } while (parser.nextToken() != CirJsonToken.END_ARRAY)
        }

        return bean
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext, intoValue: Any): Any? {
        parser.assignCurrentValue(intoValue)

        if (!parser.isExpectedStartArrayToken) {
            return deserializeFromNonArray(parser, context)
        }

        if (myInjectables != null) {
            injectValues(context, intoValue)
        }

        val properties = myOrderedProperties
        var i = 0
        val propertyCount = properties.size

        while (true) {
            if (parser.nextToken() == CirJsonToken.END_ARRAY) {
                return intoValue
            }

            if (i == propertyCount) {
                break
            }

            properties[i]?.also { property ->
                try {
                    property.deserializeAndSet(parser, context, intoValue)
                } catch (e: Exception) {
                    wrapAndThrow(e, intoValue, property.name, context)
                }
            } ?: parser.skipChildren()

            ++i
        }

        if (!myIgnoreAllUnknown && context.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)) {
            return context.reportWrongTokenException(this, CirJsonToken.END_ARRAY,
                    "Unexpected CirJSON values; expected at most $propertyCount properties (in CirJSON Array)")
        }

        do {
            parser.skipChildren()
        } while (parser.nextToken() != CirJsonToken.END_ARRAY)

        return intoValue
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
     */
    @Throws(CirJacksonException::class)
    protected open fun deserializeNonVanilla(parser: CirJsonParser, context: DeserializationContext): Any? {
        if (myNonStandardCreation) {
            return deserializeFromObjectUsingNonDefault(parser, context)
        }

        val bean = myValueInstantiator.createUsingDefault(context)!!
        parser.assignCurrentValue(bean)

        if (myInjectables != null) {
            injectValues(context, bean)
        }

        val activeView = context.takeIf { myNeedViewProcessing }?.activeView
        val properties = myOrderedProperties
        var i = 0
        val propertyCount = properties.size

        while (true) {
            if (parser.nextToken() == CirJsonToken.END_ARRAY) {
                return bean
            } else if (i == propertyCount) {
                break
            }

            val property = properties[i++]

            if (property?.let { activeView == null || it.visibleInView(activeView) } ?: false) {
                try {
                    property.deserializeAndSet(parser, context, bean)
                } catch (e: Exception) {
                    wrapAndThrow(e, bean, property.name, context)
                }

                continue
            }

            parser.skipChildren()
        }

        if (!myIgnoreAllUnknown) {
            return context.reportWrongTokenException(this, CirJsonToken.END_ARRAY,
                    "Unexpected CirJSON values; expected at most $propertyCount properties (in CirJSON Array)")
        }

        do {
            parser.skipChildren()
        } while (parser.nextToken() != CirJsonToken.END_ARRAY)

        return bean
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
        var bean: Any? = null

        while (parser.nextToken() != CirJsonToken.END_ARRAY) {
            ++i
            val property = properties.getOrNull(i)

            if (property == null || activeView != null && !property.visibleInView(activeView)) {
                parser.skipChildren()
                continue
            }

            if (bean != null) {
                try {
                    property.deserializeAndSet(parser, context, bean)
                } catch (e: Exception) {
                    wrapAndThrow(e, bean, property.name, context)
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
            }

            if (!buffer.assignParameter(creatorProperty, creatorProperty.deserialize(parser, context))) {
                continue
            }

            try {
                bean = creator.build(context, buffer)!!
            } catch (e: Exception) {
                wrapAndThrow(e, myBeanType.rawClass, propertyName, context)
            }

            parser.assignCurrentValue(bean)

            if (bean::class != myBeanType.rawClass) {
                return context.reportBadDefinition(myBeanType,
                        "Cannot support implicit polymorphic deserialization for POJOs-as-Arrays style: nominal type ${myBeanType.typeDescription}, actual type ${bean.classDescription}")
            }
        }

        return bean ?: try {
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
                "Cannot deserialize a POJO (of type ${myBeanType.typeDescription}) from non-Array representation (token: ${parser.currentToken()}): type/property designed to be serialized as CirJSON Array")
    }

}