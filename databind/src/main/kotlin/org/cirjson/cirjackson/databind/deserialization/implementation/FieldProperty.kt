package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.NullValueProvider
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.introspection.AnnotatedField
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.introspection.BeanPropertyDefinition
import org.cirjson.cirjackson.databind.util.Annotations
import org.cirjson.cirjackson.databind.util.checkAndFixAccess
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaField

/**
 * This concrete subclass implements property that is set directly assigning to a Field.
 */
class FieldProperty : SettableBeanProperty {

    private val myAnnotated: AnnotatedField

    /**
     * Actual field to set when deserializing this property. Transient since there is no need to persist; only needed
     * during construction of objects.
     */
    @Transient
    private val myField: KProperty<*>

    private val mySkipNulls: Boolean

    constructor(propertyDefinition: BeanPropertyDefinition, type: KotlinType, typeDeserializer: TypeDeserializer?,
            contextAnnotations: Annotations?, field: AnnotatedField) : super(propertyDefinition, type, typeDeserializer,
            contextAnnotations) {
        myAnnotated = field
        myField = field.annotated
        mySkipNulls = NullsConstantProvider.isSkipper(myNullProvider)
    }

    private constructor(source: FieldProperty, deserializer: ValueDeserializer<*>?,
            nullProvider: NullValueProvider) : super(source, deserializer, nullProvider) {
        myAnnotated = source.myAnnotated
        myField = source.myField
        mySkipNulls = source.mySkipNulls
    }

    private constructor(source: FieldProperty, newName: PropertyName) : super(source, newName) {
        myAnnotated = source.myAnnotated
        myField = source.myField
        mySkipNulls = source.mySkipNulls
    }

    /**
     * Constructor used for Serialization when reading persisted object
     */
    private constructor(source: FieldProperty) : super(source) {
        myAnnotated = source.myAnnotated
        myField = myAnnotated.annotated
        mySkipNulls = source.mySkipNulls
    }

    override fun withName(propertyName: PropertyName): SettableBeanProperty {
        return FieldProperty(this, propertyName)
    }

    override fun withValueDeserializer(deserializer: ValueDeserializer<*>): SettableBeanProperty {
        if (myValueDeserializer === deserializer) {
            return this
        }

        val nullValueProvider = deserializer.takeIf { myValueDeserializer === myNullProvider } ?: myNullProvider
        return FieldProperty(this, deserializer, nullValueProvider)
    }

    override fun withNullProvider(nullProvider: NullValueProvider): SettableBeanProperty {
        return FieldProperty(this, myValueDeserializer, nullProvider)
    }

    override fun fixAccess(config: DeserializationConfig) {
        myField.javaField!!.checkAndFixAccess(config.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))
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
            myField.javaField!!.set(instance, value)
        } catch (e: Exception) {
            throwAsCirJacksonException(parser, e, value)
        }
    }

    @Throws(CirJacksonException::class)
    override fun deserializeSetAndReturn(parser: CirJsonParser, context: DeserializationContext, instance: Any): Any {
        deserializeAndSet(parser, context, instance)
        return instance
    }

    override fun set(instance: Any, value: Any?) {
        if (value == null && mySkipNulls) {
            return
        }

        try {
            myField.javaField!!.set(instance, value)
        } catch (e: Exception) {
            throwAsCirJacksonException(e, value)
        }
    }

    override fun setAndReturn(instance: Any, value: Any?): Any {
        set(instance, value)
        return instance
    }

}