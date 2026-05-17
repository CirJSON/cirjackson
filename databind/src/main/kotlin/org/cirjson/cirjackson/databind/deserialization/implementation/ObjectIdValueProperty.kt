package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.PropertyMetadata
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.deserialization.NullValueProvider
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import kotlin.reflect.KClass

/**
 * Specialized [SettableBeanProperty] implementation used for virtual property that represents Object ID that is used
 * for some POJO types (or properties).
 */
class ObjectIdValueProperty : SettableBeanProperty {

    private val myObjectIdReader: ObjectIdReader

    constructor(objectIdReader: ObjectIdReader, metadata: PropertyMetadata?) : super(objectIdReader.propertyName,
            objectIdReader.idType, metadata, objectIdReader.deserializer) {
        myObjectIdReader = objectIdReader
    }

    private constructor(source: ObjectIdValueProperty, deserializer: ValueDeserializer<*>?,
            nullProvider: NullValueProvider) : super(source, deserializer, nullProvider) {
        myObjectIdReader = source.myObjectIdReader
    }

    private constructor(source: ObjectIdValueProperty, propertyName: PropertyName) : super(source, propertyName) {
        myObjectIdReader = source.myObjectIdReader
    }

    override fun withName(propertyName: PropertyName): SettableBeanProperty {
        return ObjectIdValueProperty(this, propertyName)
    }

    override fun withValueDeserializer(deserializer: ValueDeserializer<*>): SettableBeanProperty {
        if (myValueDeserializer === deserializer) {
            return this
        }

        val nullProvider = deserializer.takeIf { myValueDeserializer === myNullProvider } ?: myNullProvider
        return ObjectIdValueProperty(this, deserializer, nullProvider)
    }

    override fun withNullProvider(nullProvider: NullValueProvider): SettableBeanProperty {
        return ObjectIdValueProperty(this, myValueDeserializer, nullProvider)
    }

    /*
     *******************************************************************************************************************
     * BeanProperty implementation
     *******************************************************************************************************************
     */

    override fun <A : Annotation> getAnnotation(clazz: KClass<A>): A? {
        return null
    }

    override val member: AnnotatedMember?
        get() = null

    /*
     *******************************************************************************************************************
     * SettableBeanProperty implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserializeAndSet(parser: CirJsonParser, context: DeserializationContext, instance: Any) {
        deserializeSetAndReturn(parser, context, instance)
    }

    @Throws(CirJacksonException::class)
    override fun deserializeSetAndReturn(parser: CirJsonParser, context: DeserializationContext, instance: Any): Any? {
        if (parser.hasToken(CirJsonToken.VALUE_NULL)) {
            return null
        }

        val id = myValueDeserializer.deserialize(parser, context)!!
        val readableObjectId = context.findObjectId(id, myObjectIdReader.generator, myObjectIdReader.resolver)
        readableObjectId.bindItem(instance)
        val idProperty = myObjectIdReader.idProperty ?: return instance
        return idProperty.setAndReturn(instance, id)
    }

    override fun set(instance: Any, value: Any?) {
        setAndReturn(instance, value)
    }

    override fun setAndReturn(instance: Any, value: Any?): Any {
        val idProperty = myObjectIdReader.idProperty ?: throw UnsupportedOperationException(
                "Should not call set() on ObjectIdProperty that has no SettableBeanProperty")
        return idProperty.setAndReturn(instance, value)
    }

}