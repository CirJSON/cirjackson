package org.cirjson.cirjackson.databind.serialization.standard

import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.implementation.PropertySerializerMap
import kotlin.reflect.KClass

/**
 * Base class for standard serializers that are not (necessarily) container types but that similarly handle content that
 * may vary in ways to require dynamic lookups. Typically, these are referential or delegating types.
 */
abstract class StandardDynamicSerializer<T : Any> : StandardSerializer<T> {

    /**
     * Property for which this serializer is being used, if known at this point (`null` for root value serializers as
     * well as those cached as blueprints).
     */
    protected val myProperty: BeanProperty?

    /**
     * Type serializer used for values, if any: used for serializing values of polymorphic types.
     */
    protected val myValueTypeSerializer: TypeSerializer?

    /**
     * Eagerly fetched serializer for actual value contained or referenced, if fetched.
     */
    protected val myValueSerializer: ValueSerializer<Any>?

    /**
     * If value type cannot be statically determined, mapping from runtime value types to serializers are stored in this
     * object.
     */
    protected var myDynamicValueSerializers = PropertySerializerMap.emptyForProperties()

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    protected constructor(type: KotlinType, property: BeanProperty?, valueTypeSerializer: TypeSerializer?,
            valueSerializer: ValueSerializer<*>?) : super(type) {
        myProperty = property
        myValueTypeSerializer = valueTypeSerializer
        myValueSerializer = valueSerializer as? ValueSerializer<Any>?
    }

    @Suppress("UNCHECKED_CAST")
    protected constructor(source: StandardDynamicSerializer<*>, property: BeanProperty?) : super(
            source as StandardDynamicSerializer<T>) {
        myProperty = property
        myValueTypeSerializer = source.myValueTypeSerializer
        myValueSerializer = source.myValueSerializer
    }

    @Suppress("UNCHECKED_CAST")
    protected constructor(source: StandardDynamicSerializer<*>, property: BeanProperty?,
            valueTypeSerializer: TypeSerializer?, valueSerializer: ValueSerializer<*>?) : super(
            source as StandardDynamicSerializer<T>) {
        myProperty = property
        myValueTypeSerializer = valueTypeSerializer
        myValueSerializer = valueSerializer as? ValueSerializer<Any>?
    }

    /*
     *******************************************************************************************************************
     * Helper methods for locating, caching element/value serializers
     *******************************************************************************************************************
     */

    protected fun findAndAddDynamic(context: SerializerProvider, type: KClass<*>): ValueSerializer<Any> {
        val map = myDynamicValueSerializers
        val result = map.findAndAddSecondarySerializer(type, context, myProperty)

        if (map !== result.map) {
            myDynamicValueSerializers = result.map
        }

        return result.serializer
    }

    protected fun findAndAddDynamic(context: SerializerProvider, type: KClass<*>,
            serializerTransformer: (ValueSerializer<Any>) -> ValueSerializer<Any>): ValueSerializer<Any> {
        val map = myDynamicValueSerializers
        val result = map.findAndAddSecondarySerializer(type, context, myProperty, serializerTransformer)

        if (map !== result.map) {
            myDynamicValueSerializers = result.map
        }

        return result.serializer
    }

    protected fun findAndAddDynamic(context: SerializerProvider, type: KotlinType): ValueSerializer<Any> {
        val map = myDynamicValueSerializers
        val result = map.findAndAddSecondarySerializer(type, context, myProperty)

        if (map !== result.map) {
            myDynamicValueSerializers = result.map
        }

        return result.serializer
    }

    protected fun findAndAddDynamic(context: SerializerProvider, type: KotlinType,
            serializerTransformer: (ValueSerializer<Any>) -> ValueSerializer<Any>): ValueSerializer<Any> {
        val map = myDynamicValueSerializers
        val result = map.findAndAddSecondarySerializer(type, context, myProperty, serializerTransformer)

        if (map !== result.map) {
            myDynamicValueSerializers = result.map
        }

        return result.serializer
    }

}