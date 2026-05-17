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
 * This concrete subclass implements Collection or Map property that is set indirectly by getting the property value and
 * directly modifying it.
 */
class SetterlessProperty : SettableBeanProperty {

    private val myAnnotated: AnnotatedMethod

    /**
     * Get method for accessing property value used to access property (of Collection or Map type) to modify.
     */
    private val myGetter: KFunction<*>

    constructor(propertyDefinition: BeanPropertyDefinition, type: KotlinType, typeDeserializer: TypeDeserializer?,
            contextAnnotations: Annotations?, method: AnnotatedMethod) : super(propertyDefinition, type,
            typeDeserializer, contextAnnotations) {
        myAnnotated = method
        myGetter = method.annotated
    }

    private constructor(source: SetterlessProperty, deserializer: ValueDeserializer<*>?,
            nullProvider: NullValueProvider) : super(source, deserializer, nullProvider) {
        myAnnotated = source.myAnnotated
        myGetter = source.myGetter
    }

    private constructor(source: SetterlessProperty, propertyName: PropertyName) : super(source, propertyName) {
        myAnnotated = source.myAnnotated
        myGetter = source.myGetter
    }

    override fun withName(propertyName: PropertyName): SettableBeanProperty {
        return SetterlessProperty(this, propertyName)
    }

    override fun withValueDeserializer(deserializer: ValueDeserializer<*>): SettableBeanProperty {
        if (myValueDeserializer === deserializer) {
            return this
        }

        val nullProvider = deserializer.takeIf { myValueDeserializer === myNullProvider } ?: myNullProvider
        return SetterlessProperty(this, deserializer, nullProvider)
    }

    override fun withNullProvider(nullProvider: NullValueProvider): SettableBeanProperty {
        return SetterlessProperty(this, myValueDeserializer, nullProvider)
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
        val token = parser.currentToken()

        if (token == CirJsonToken.VALUE_NULL) {
            return
        }

        if (myValueTypeDeserializer != null) {
            return context.reportBadDefinition(type,
                    "Problem deserializing 'setterless' property (\"$name\"): no way to handle typed deserializer with setterless yet")
        }

        val toModify = try {
            myGetter.javaMethod!!.invoke(instance)
        } catch (e: Exception) {
            throwAsCirJacksonException(parser, e)
        } ?: return context.reportBadDefinition(type,
                "Problem deserializing 'setterless' property (\"$name\"): get method returned null")

        myValueDeserializer.deserialize(parser, context, toModify)
    }

    @Throws(CirJacksonException::class)
    override fun deserializeSetAndReturn(parser: CirJsonParser, context: DeserializationContext, instance: Any): Any {
        deserializeAndSet(parser, context, instance)
        return instance
    }

    override fun set(instance: Any, value: Any?) {
        throw UnsupportedOperationException("Should never call `set()` on setterless property (\"$name\")")
    }

    override fun setAndReturn(instance: Any, value: Any?): Any {
        set(instance, value)
        return instance
    }

}