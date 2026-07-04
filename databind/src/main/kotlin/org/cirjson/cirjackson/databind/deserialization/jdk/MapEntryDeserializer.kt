package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.ContextualKeyDeserializer
import org.cirjson.cirjackson.databind.deserialization.standard.ContainerDeserializerBase
import org.cirjson.cirjackson.databind.type.LogicalType

/**
 * Basic serializer that can take JSON "Object" structure and construct a [Map.Entry] instance, with typed contents.
 * 
 * Note: for untyped content (one indicated by passing `Any::class` as the type), [UntypedObjectDeserializer] is used
 * instead. It can also construct [Map.Entries][Map.Entry], but not with specific POJO types, only other containers and
 * primitives/wrappers.
 */
@CirJacksonStandardImplementation
open class MapEntryDeserializer : ContainerDeserializerBase<Map.Entry<Any?, Any?>> {

    /**
     * Key deserializer to use; either passed via constructor (when indicated by annotations), or resolved when
     * [createContextual] is called;
     */
    protected val myKeyDeserializer: KeyDeserializer?

    /**
     * Value deserializer.
     */
    protected val myValueDeserializer: ValueDeserializer<Any>?

    /**
     * If value instances have polymorphic type information, this is the type deserializer that can handle it
     */
    protected val myValueTypeDeserializer: TypeDeserializer?

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor(type: KotlinType, keyDeserializer: KeyDeserializer?, valueDeserializer: ValueDeserializer<Any>?,
            valueTypeDeserializer: TypeDeserializer?) : super(type) {
        if (type.containedTypeCount() != 2) {
            throw IllegalArgumentException("Missing generic type information for $type")
        }

        myKeyDeserializer = keyDeserializer
        myValueDeserializer = valueDeserializer
        myValueTypeDeserializer = valueTypeDeserializer
    }

    /**
     * Copy-constructor that can be used by subclasses to allow copy-on-write styling copying of settings of an existing
     * instance.
     */
    protected constructor(source: MapEntryDeserializer) : super(source) {
        myKeyDeserializer = source.myKeyDeserializer
        myValueDeserializer = source.myValueDeserializer
        myValueTypeDeserializer = source.myValueTypeDeserializer
    }

    protected constructor(source: MapEntryDeserializer, keyDeserializer: KeyDeserializer?,
            valueDeserializer: ValueDeserializer<Any>?, valueTypeDeserializer: TypeDeserializer?) : super(source) {
        myKeyDeserializer = keyDeserializer
        myValueDeserializer = valueDeserializer
        myValueTypeDeserializer = valueTypeDeserializer
    }

    /**
     * Fluent factory method used to create a copy with slightly different settings. When subclassing, MUST be
     * overridden.
     */
    @Suppress("UNCHECKED_CAST")
    protected open fun withResolved(keyDeserializer: KeyDeserializer?, valueDeserializer: ValueDeserializer<*>?,
            valueTypeDeserializer: TypeDeserializer?): MapEntryDeserializer {
        if (myKeyDeserializer === keyDeserializer && myValueDeserializer === valueDeserializer &&
                myValueTypeDeserializer === valueTypeDeserializer) {
            return this
        }

        return MapEntryDeserializer(this, keyDeserializer, valueDeserializer as ValueDeserializer<Any>?,
                valueTypeDeserializer)
    }

    override fun logicalType(): LogicalType {
        return LogicalType.MAP
    }

    /*
     *******************************************************************************************************************
     * Validation, post-processing (ResolvableDeserializer)
     *******************************************************************************************************************
     */

    /**
     * Method called to finalize setup of this deserializer, when it is known for which property deserializer is needed
     * for.
     */
    override fun createContextual(context: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        val keyDeserializer =
                myKeyDeserializer?.let { (it as? ContextualKeyDeserializer)?.createContextual(context, property) ?: it }
                        ?: context.findKeyDeserializer(myContainerType.containedType(0)!!, property)
        val valueType = myContainerType.containedType(1)!!
        val valueDeserializer = findConvertingContentDeserializer(context, property,
                myValueDeserializer)?.let { context.handleSecondaryContextualization(it, property, valueType) }
                ?: context.findContextualValueDeserializer(valueType, property)
        val valueTypeDeserializer = myValueTypeDeserializer?.forProperty(property)
        return withResolved(keyDeserializer, valueDeserializer, valueTypeDeserializer)
    }

    /*
     *******************************************************************************************************************
     * ContainerDeserializerBase implementation
     *******************************************************************************************************************
     */

    override val contentType: KotlinType
        get() = myContainerType.containedType(1)!!

    override val contentDeserializer: ValueDeserializer<Any>?
        get() = myValueDeserializer

    /*
     *******************************************************************************************************************
     * ValueDeserializer implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Map.Entry<Any?, Any?>? {
        var token = parser.currentToken()

        if (token == CirJsonToken.START_OBJECT) {
            token = parser.nextToken()
        } else if (token != CirJsonToken.PROPERTY_NAME && token != CirJsonToken.END_OBJECT) {
            if (token != CirJsonToken.START_ARRAY) {
                return context.handleUnexpectedToken(getValueType(context), parser) as Map.Entry<Any?, Any?>?
            }

            return deserializeFromArray(parser, context)
        }

        if (token != CirJsonToken.PROPERTY_NAME) {
            return if (token == CirJsonToken.END_OBJECT) {
                context.reportInputMismatch(this, "Cannot deserialize a Map.Entry out of empty CirJSON Object")
            } else {
                context.handleUnexpectedToken(getValueType(context), parser) as Map.Entry<Any?, Any?>?
            }
        }

        val keyDeserializer = myKeyDeserializer!!
        val valueDeserializer = myValueDeserializer!!
        val typeDeserializer = myValueTypeDeserializer

        val keyString = parser.currentName()!!
        val key = keyDeserializer.deserializeKey(keyString, context)
        token = parser.nextToken()

        val value = try {
            if (token == CirJsonToken.VALUE_NULL) {
                valueDeserializer.getNullValue(context)
            } else if (typeDeserializer == null) {
                valueDeserializer.deserialize(parser, context)
            } else {
                valueDeserializer.deserializeWithType(parser, context, typeDeserializer)
            }
        } catch (e: Exception) {
            wrapAndThrow(context, e, Map.Entry::class, keyString)
        }

        token = parser.nextToken()

        return if (token != CirJsonToken.END_OBJECT) {
            if (token == CirJsonToken.PROPERTY_NAME) {
                context.reportInputMismatch(this,
                        "Problem binding CirJSON into Map.Entry: more than one entry in CirJSON (second field: '${parser.currentName()}')")
            } else {
                context.reportInputMismatch(this,
                        "Problem binding CirJSON into Map.Entry: unexpected content after CirJSON Object entry: $token")
            }
        } else {
            mapOf(key to value).entries.first()
        }
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext,
            intoValue: Map.Entry<Any?, Any?>): Map.Entry<Any?, Any?>? {
        throw IllegalStateException("Cannot update Map.Entry values")
    }

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        return typeDeserializer.deserializeTypedFromObject(parser, context)
    }

}