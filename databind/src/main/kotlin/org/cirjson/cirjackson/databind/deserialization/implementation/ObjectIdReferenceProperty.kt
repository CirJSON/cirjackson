package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.deserialization.NullValueProvider
import org.cirjson.cirjackson.databind.deserialization.ReadableObjectId
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.UnresolvedForwardReferenceException
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.introspection.ObjectIdInfo
import kotlin.reflect.KClass

open class ObjectIdReferenceProperty : SettableBeanProperty {

    private val myForward: SettableBeanProperty

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor(forward: SettableBeanProperty, objectIdInfo: ObjectIdInfo) : super(forward) {
        myForward = forward
        myObjectIdInfo = objectIdInfo
    }

    constructor(source: ObjectIdReferenceProperty, deserializer: ValueDeserializer<*>?,
            nullProvider: NullValueProvider) : super(source, deserializer, nullProvider) {
        myForward = source.myForward
        myObjectIdInfo = source.myObjectIdInfo
    }

    constructor(source: ObjectIdReferenceProperty, propertyName: PropertyName) : super(source, propertyName) {
        myForward = source.myForward
        myObjectIdInfo = source.myObjectIdInfo
    }

    override fun withName(propertyName: PropertyName): SettableBeanProperty {
        return ObjectIdReferenceProperty(this, propertyName)
    }

    override fun withValueDeserializer(deserializer: ValueDeserializer<*>): SettableBeanProperty {
        if (myValueDeserializer === deserializer) {
            return this
        }

        val nullProvider = deserializer.takeIf { myValueDeserializer === myNullProvider } ?: myNullProvider
        return ObjectIdReferenceProperty(this, deserializer, nullProvider)
    }

    override fun withNullProvider(nullProvider: NullValueProvider): SettableBeanProperty {
        return ObjectIdReferenceProperty(this, myValueDeserializer, nullProvider)
    }

    override fun fixAccess(config: DeserializationConfig) {
        myForward.fixAccess(config)
    }

    /*
     *******************************************************************************************************************
     * BeanProperty implementation
     *******************************************************************************************************************
     */

    override fun <A : Annotation> getAnnotation(clazz: KClass<A>): A? {
        return myForward.getAnnotation(clazz)
    }

    override val member: AnnotatedMember?
        get() = myForward.member

    /*
     *******************************************************************************************************************
     * SettableBeanProperty implementation
     *******************************************************************************************************************
     */

    override val creatorIndex: Int
        get() = myForward.creatorIndex

    @Throws(CirJacksonException::class)
    override fun deserializeAndSet(parser: CirJsonParser, context: DeserializationContext, instance: Any) {
        deserializeSetAndReturn(parser, context, instance)
    }

    @Throws(CirJacksonException::class)
    override fun deserializeSetAndReturn(parser: CirJsonParser, context: DeserializationContext, instance: Any): Any? {
        return try {
            setAndReturn(instance, deserialize(parser, context))
        } catch (reference: UnresolvedForwardReferenceException) {
            val usingIdentityInfo = myObjectIdInfo != null || myValueDeserializer.getObjectIdReader(context) != null

            if (!usingIdentityInfo) {
                throw DatabindException.from(parser, "Unresolved forward reference but no identity info", reference)
            }

            reference.readableObjectId!!.appendReferring(PropertyReferring(this, reference, myType.rawClass, instance))
            null
        }
    }

    override fun set(instance: Any, value: Any?) {
        myForward.set(instance, value)
    }

    override fun setAndReturn(instance: Any, value: Any?): Any {
        return myForward.setAndReturn(instance, value)
    }

    class PropertyReferring(private val myParent: ObjectIdReferenceProperty,
            reference: UnresolvedForwardReferenceException, type: KClass<*>, private val myPojo: Any) :
            ReadableObjectId.Referring(reference, type) {

        @Throws(CirJacksonException::class)
        override fun handleResolvedForwardReference(id: Any, value: Any?) {
            if (!hasId(id)) {
                throw IllegalArgumentException(
                        "Trying to resolve a forward reference with id [$id] that wasn't previously seen as unresolved.")
            }

            myParent.set(myPojo, value)
        }

    }

}