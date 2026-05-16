package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.NullValueProvider
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.introspection.AnnotatedMethod
import org.cirjson.cirjackson.databind.introspection.BeanPropertyDefinition
import org.cirjson.cirjackson.databind.util.Annotations
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

/**
 * This concrete subclass implements property that is set using regular "setter" method.
 */
class MethodProperty : SettableBeanProperty {

    private val myAnnotated: AnnotatedMethod

    /**
     * Setter method for modifying property value; used for "regular" method-accessible properties.
     */
    @Transient
    private val mySetter: KFunction<*>

    private val mySkipNulls: Boolean

    constructor(propertyDefinition: BeanPropertyDefinition, type: KotlinType, typeDeserializer: TypeDeserializer?,
            contextAnnotations: Annotations?, method: AnnotatedMethod) : super(propertyDefinition, type,
            typeDeserializer, contextAnnotations) {
        myAnnotated = method
        mySetter = method.annotated
        mySkipNulls = NullsConstantProvider.isSkipper(myNullProvider)
    }

    private constructor(source: MethodProperty, deserializer: ValueDeserializer<*>?,
            nullProvider: NullValueProvider) : super(source, deserializer, nullProvider) {
        myAnnotated = source.myAnnotated
        mySetter = source.mySetter
        mySkipNulls = NullsConstantProvider.isSkipper(nullProvider)
    }

    private constructor(source: MethodProperty, propertyName: PropertyName) : super(source, propertyName) {
        myAnnotated = source.myAnnotated
        mySetter = source.mySetter
        mySkipNulls = source.mySkipNulls
    }

    /**
     * Constructor used for serialization when reading persisted object
     */
    private constructor(source: MethodProperty, setter: KFunction<*>) : super(source) {
        myAnnotated = source.myAnnotated
        mySetter = setter
        mySkipNulls = source.mySkipNulls
    }

    override fun withName(propertyName: PropertyName): SettableBeanProperty {
        return MethodProperty(this, propertyName)
    }

    override fun withValueDeserializer(deserializer: ValueDeserializer<*>): SettableBeanProperty {
        if (myValueDeserializer === deserializer) {
            return this
        }

        val nullProvider = deserializer.takeIf { myValueDeserializer === myNullProvider } ?: myNullProvider
        return MethodProperty(this, deserializer, nullProvider)
    }

    override fun withNullProvider(nullProvider: NullValueProvider): SettableBeanProperty {
        return MethodProperty(this, myValueDeserializer, nullProvider)
    }

    override fun fixAccess(config: DeserializationConfig) {
        myAnnotated.fixAccess(config.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))
    }

    /*
     *******************************************************************************************************************
     * BeanProperty implementation
     *******************************************************************************************************************
     */

    override fun <A : Annotation> getAnnotation(clazz: KClass<A>): A? {
        return myAnnotated.getAnnotation(clazz)
    }

    override val member: AnnotatedMember
        get() = myAnnotated

    /*
     *******************************************************************************************************************
     * SettableBeanProperty implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserializeAndSet(parser: CirJsonParser, context: DeserializationContext, instance: Any) {
        val value = if (parser.hasToken(CirJsonToken.VALUE_NULL)) {
            if (mySkipNulls) {
                return
            }

            myNullProvider.getNullValue(context)
        } else if (myValueTypeDeserializer == null) {
            myValueDeserializer.deserialize(parser, context) ?: let {
                if (mySkipNulls) {
                    return
                }

                myNullProvider.getNullValue(context)
            }
        } else {
            myValueDeserializer.deserializeWithType(parser, context, myValueTypeDeserializer)
        }

        try {
            mySetter.javaMethod!!.invoke(instance, value)
        } catch (e: Exception) {
            throwAsCirJacksonException(parser, e, value)
        }
    }

    @Throws(CirJacksonException::class)
    override fun deserializeSetAndReturn(parser: CirJsonParser, context: DeserializationContext, instance: Any): Any {
        val value = if (parser.hasToken(CirJsonToken.VALUE_NULL)) {
            if (mySkipNulls) {
                return instance
            }

            myNullProvider.getNullValue(context)
        } else if (myValueTypeDeserializer == null) {
            myValueDeserializer.deserialize(parser, context) ?: let {
                if (mySkipNulls) {
                    return instance
                }

                myNullProvider.getNullValue(context)
            }
        } else {
            myValueDeserializer.deserializeWithType(parser, context, myValueTypeDeserializer)
        }

        try {
            val result = mySetter.javaMethod!!.invoke(instance, value)
            return result ?: instance
        } catch (e: Exception) {
            throwAsCirJacksonException(parser, e, value)
        }
    }

    override fun set(instance: Any, value: Any?) {
        if (value == null && mySkipNulls) {
            return
        }

        try {
            mySetter.javaMethod!!.invoke(instance, value)
        } catch (e: Exception) {
            throwAsCirJacksonException(e, value)
        }
    }

    override fun setAndReturn(instance: Any, value: Any?): Any {
        if (value == null && mySkipNulls) {
            return instance
        }

        try {
            val result = mySetter.javaMethod!!.invoke(instance, value)
            return result ?: instance
        } catch (e: Exception) {
            throwAsCirJacksonException(e, value)
        }
    }

}