package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.deserialization.bean.PropertyBasedCreator
import org.cirjson.cirjackson.databind.deserialization.standard.StandardDeserializer
import org.cirjson.cirjackson.databind.introspection.AnnotatedMethod
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.*
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass

/**
 * Deserializer that uses a single-String static factory method for locating Enum values by String id.
 */
open class FactoryBasedEnumDeserializer : StandardDeserializer<Any> {

    protected val myInputType: KotlinType?

    protected val myFactory: AnnotatedMethod

    protected val myDeserializer: ValueDeserializer<*>?

    protected val myValueInstantiator: ValueInstantiator?

    protected val myCreatorProperties: Array<SettableBeanProperty>?

    protected val myHasArguments: Boolean

    /**
     * Lazily instantiated property-based creator.
     */
    @Transient
    @Volatile
    protected var myPropertyCreator: PropertyBasedCreator? = null

    constructor(valueClass: KClass<*>, factory: AnnotatedMethod, parameterType: KotlinType,
            valueInstantiator: ValueInstantiator?, creatorProperties: Array<SettableBeanProperty>?) : super(
            valueClass) {
        myFactory = factory
        myHasArguments = true
        myInputType = parameterType.takeUnless { it.hasRawClass(String::class) || it.hasRawClass(CharSequence::class) }
        myDeserializer = null
        myValueInstantiator = valueInstantiator
        myCreatorProperties = creatorProperties
    }

    constructor(valueClass: KClass<*>, factory: AnnotatedMethod) : super(valueClass) {
        myFactory = factory
        myHasArguments = false
        myInputType = null
        myDeserializer = null
        myValueInstantiator = null
        myCreatorProperties = null
    }

    protected constructor(base: FactoryBasedEnumDeserializer, deserializer: ValueDeserializer<*>?) : super(
            base.myValueClass) {
        myFactory = base.myFactory
        myHasArguments = base.myHasArguments
        myInputType = base.myInputType
        myDeserializer = deserializer
        myValueInstantiator = base.myValueInstantiator
        myCreatorProperties = base.myCreatorProperties
    }

    override fun createContextual(context: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        if (myDeserializer != null || myInputType == null || myCreatorProperties != null) {
            return this
        }

        return FactoryBasedEnumDeserializer(this, context.findContextualValueDeserializer(myInputType, property))
    }

    override fun supportsUpdate(config: DeserializationConfig): Boolean? {
        return false
    }

    override fun logicalType(): LogicalType {
        return LogicalType.ENUM
    }

    override val isCacheable: Boolean
        get() = true

    override val valueInstantiator: ValueInstantiator?
        get() = myValueInstantiator

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        val value: Any?

        if (myDeserializer != null) {
            value = myDeserializer.deserialize(parser, context)
        } else if (myHasArguments) {
            if (myCreatorProperties != null) {
                myValueInstantiator!!

                if (parser.isExpectedStartObjectToken) {
                    val creator = myPropertyCreator ?: PropertyBasedCreator.construct(context, myValueInstantiator,
                            myCreatorProperties, context.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES))
                            .also { myPropertyCreator = it }
                    parser.nextToken()
                    return deserializeEnumUsingPropertyBased(parser, context, creator)
                } else if (!myValueInstantiator.canCreateFromString()) {
                    val targetType = getValueType(context)
                    val token = parser.currentToken()!!
                    return context.reportInputMismatch(targetType,
                            "Input mismatch reading Enum ${targetType.typeDescription}: properties-based `@CirJsonCreator` ($myFactory) expects Object Value, got ${
                                CirJsonToken.valueDescFor(token)
                            } (`CirJsonToken.${token.name}`)")
                }
            }

            var token = parser.currentToken()
            val unwrapping = token == CirJsonToken.START_ARRAY &&
                    context.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)

            if (unwrapping) {
                token = parser.nextToken()
            }

            if (!token!!.isScalarValue) {
                val targetType = getValueType(context)
                return context.reportInputMismatch(targetType,
                        "Input mismatch reading Enum ${targetType.typeDescription}: properties-based `@CirJsonCreator` ($myFactory) expects String Value, got ${
                            CirJsonToken.valueDescFor(token)
                        } (`CirJsonToken.${token.name}`)")
            }

            value = parser.valueAsString

            if (unwrapping && parser.nextToken() != CirJsonToken.END_ARRAY) {
                handleMissingEndArrayForSingle(parser, context)
            }
        } else {
            parser.skipChildren()

            return try {
                myFactory.call()
            } catch (e: Exception) {
                val throwable = e.throwRootCauseIfCirJacksonException()
                context.handleInstantiationProblem(myValueClass, null, throwable)
            }
        }

        return try {
            myFactory.callOnWith(myValueClass, value)
        } catch (e: Exception) {
            val throwable = e.throwRootCauseIfCirJacksonException()

            if (throwable is IllegalArgumentException &&
                    context.isEnabled(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)) {
                null
            } else {
                context.handleInstantiationProblem(myValueClass, value, throwable)
            }
        }
    }

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        return typeDeserializer.deserializeTypedFromAny(parser, context)
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeEnumUsingPropertyBased(parser: CirJsonParser, context: DeserializationContext,
            creator: PropertyBasedCreator): Any? {
        val buffer = creator.startBuilding(parser, context, null)

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
                buffer.assignParameter(creatorProperty, deserializeWithErrorWrapping(parser, context, creatorProperty))
                token = parser.nextToken()
                continue
            }

            parser.skipChildren()
        }

        return creator.build(context, buffer)
    }

    @Throws(CirJacksonException::class)
    protected fun deserializeWithErrorWrapping(parser: CirJsonParser, context: DeserializationContext,
            property: SettableBeanProperty): Any? {
        try {
            return property.deserialize(parser, context)
        } catch (e: Exception) {
            wrapAndThrow(e, handledType(), property.name, context)
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun wrapAndThrow(throwable: Throwable, bean: Any, fieldName: String,
            context: DeserializationContext): Nothing {
        throw CirJacksonException.wrapWithPath(throwOrReturnThrowable(throwable, context), bean, fieldName)
    }

    private fun throwOrReturnThrowable(throwable: Throwable, context: DeserializationContext): Throwable {
        var realThrowable = throwable.rootCause.throwIfError()
        val wrap = context.isEnabled(DeserializationFeature.WRAP_EXCEPTIONS)

        if (wrap) {
            realThrowable = realThrowable.throwIfRuntimeException()
        }

        return realThrowable
    }

}