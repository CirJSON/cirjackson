package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonObjectFormatVisitor
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.serialization.PropertyWriter
import kotlin.reflect.KClass

/**
 * Helper class needed to support flexible filtering of Map properties with generic CirJSON Filter functionality. Since
 * [Maps][Map] are not handled as a collection of properties by CirJackson (unlike POJOs), a bit more wrapping is
 * required.
 */
open class MapProperty(protected val myTypeSerializer: TypeSerializer?, property: BeanProperty?) :
        PropertyWriter(property?.metadata ?: PropertyMetadata.STANDARD_REQUIRED_OR_OPTIONAL) {

    protected val myProperty = property ?: BOGUS_PROP

    protected var myKey: Any? = null

    protected var myValue: Any? = null

    protected var myKeySerializer: ValueSerializer<Any>? = null

    protected var myValueSerializer: ValueSerializer<Any>? = null

    /**
     * Initialization method that needs to be called before passing property to filter.
     */
    open fun reset(key: Any?, value: Any?, keySerializer: ValueSerializer<Any>?,
            valueSerializer: ValueSerializer<Any>?) {
        myKey = key
        myValue = value
        myKeySerializer = keySerializer
        myValueSerializer = valueSerializer
    }

    override val name: String
        get() = myKey as? String ?: myKey.toString()

    open var value: Any?
        get() = myValue
        set(value) {
            myValue = value
        }

    override val fullName: PropertyName
        get() = PropertyName(name)

    override fun <A : Annotation> getAnnotation(clazz: KClass<A>): A? {
        return myProperty.getAnnotation(clazz)
    }

    override fun <A : Annotation> getContextAnnotation(clazz: KClass<A>): A? {
        return myProperty.getContextAnnotation(clazz)
    }

    override fun serializeAsProperty(value: Any, generator: CirJsonGenerator, provider: SerializerProvider) {
        myKeySerializer!!.serialize(myKey!!, generator, provider)

        if (myTypeSerializer == null) {
            myValueSerializer!!.serialize(myValue!!, generator, provider)
        } else {
            myValueSerializer!!.serializeWithType(myValue!!, generator, provider, myTypeSerializer)
        }
    }

    override fun serializeAsOmittedProperty(value: Any, generator: CirJsonGenerator, provider: SerializerProvider) {
        if (!generator.isAbleOmitProperties) {
            generator.writeOmittedProperty(name)
        }
    }

    override fun serializeAsElement(value: Any, generator: CirJsonGenerator, provider: SerializerProvider) {
        if (myTypeSerializer == null) {
            myValueSerializer!!.serialize(myValue!!, generator, provider)
        } else {
            myValueSerializer!!.serializeWithType(myValue!!, generator, provider, myTypeSerializer)
        }
    }

    override fun serializeAsOmittedElement(value: Any, generator: CirJsonGenerator, provider: SerializerProvider) {
        generator.writeNull()
    }

    override fun depositSchemaProperty(objectVisitor: CirJsonObjectFormatVisitor, provider: SerializerProvider) {
        myProperty.depositSchemaProperty(objectVisitor, provider)
    }

    override val type: KotlinType
        get() = myProperty.type

    override val wrapperName: PropertyName?
        get() = myProperty.wrapperName

    override val member: AnnotatedMember?
        get() = myProperty.member

    companion object {

        private val BOGUS_PROP = BeanProperty.Bogus()

    }

}