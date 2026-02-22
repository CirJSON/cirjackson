package org.cirjson.cirjackson.databind.serialization.standard

import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.implementation.PropertySerializerMap
import kotlin.reflect.KClass

/**
 * Intermediate base class for serializers used for serializing types that contain element(s) of other types, such as
 * arrays, [Collections][Collection] ([Lists][List], [Sets][Set], etc.) and [Maps][Map] and iterable things
 * ([Iterators][Iterator]).
 */
abstract class StandardContainerSerializer<T : Any> : StandardSerializer<T> {

    /**
     * Property that contains values handled by this serializer, if known; `null` for root value serializers (ones
     * directly called by [org.cirjson.cirjackson.databind.ObjectMapper] and
     * [org.cirjson.cirjackson.databind.ObjectWriter]).
     */
    protected val myProperty: BeanProperty?

    /**
     * If value type cannot be statically determined, mapping from runtime value types to serializers are stored in this
     * object.
     */
    protected var myDynamicValueSerializers = PropertySerializerMap.emptyForProperties()

    /*
     *******************************************************************************************************************
     * Construction, initialization
     *******************************************************************************************************************
     */

    protected constructor(type: KClass<*>) : this(type, null)

    protected constructor(type: KClass<*>, property: BeanProperty?) : super(type) {
        myProperty = property
    }

    protected constructor(type: KotlinType, property: BeanProperty?) : super(type) {
        myProperty = property
    }

    protected constructor(source: StandardContainerSerializer<*>) : this(source, null)

    protected constructor(source: StandardContainerSerializer<*>, property: BeanProperty?) : super(
            source.myHandledType) {
        myProperty = property
    }

    /**
     * Factory(-like) method that can be used to construct a new container serializer that uses specified
     * [TypeSerializer] for decorating contained values with additional type information.
     *
     * @param valueTypeSerializer Type serializer to use for contained values; can be `null`, in which case `this`
     * serializer is returned as is
     * 
     * @return Serializer instance that uses given type serializer for values if that is possible (or if not, just
     * `this` serializer)
     */
    open fun withValueTypeSerializer(valueTypeSerializer: TypeSerializer?): StandardContainerSerializer<*> {
        valueTypeSerializer ?: return this
        return withValueTypeSerializerImplementation(valueTypeSerializer)
    }

    /*
     *******************************************************************************************************************
     * Extended API
     *******************************************************************************************************************
     */

    /**
     * Accessor for finding declared (static) element type for type this serializer is used for.
     */
    abstract val contentType: KotlinType

    /**
     * Accessor for serializer used for serializing contents (List and array elements, Map values, etc.) of the
     * container for which this serializer is used, if it is known statically. Note that for dynamic types this may
     * return `null`; if so, caller has to instead use [contentType] and
     * [SerializerProvider.findContentValueSerializer].
     */
    abstract val contentSerializer: ValueSerializer<*>?

    /*
     *******************************************************************************************************************
     * Abstract methods for subclasses to implement
     *******************************************************************************************************************
     */

    abstract override fun isEmpty(provider: SerializerProvider, value: T?): Boolean

    /**
     * Method called to determine if the given value (of type handled by this serializer) contains exactly one element.
     * 
     * Note: although it might seem sensible to instead define something like `elementCount()` accessor, this would not
     * work well for containers that do not keep track of size (like linked lists may not).
     * 
     * Note, too, that this method is only called by serializer itself; and specifically is not used for
     * non-array/collection types like [Map] or [Map.Entry] instances.
     */
    abstract fun hasSingleElement(value: T): Boolean

    /*
     *******************************************************************************************************************
     * Helper methods for locating, caching element/value serializers
     *******************************************************************************************************************
     */

    /**
     * Method that needs to be implemented to allow construction of a new serializer object with given [TypeSerializer],
     * used when addition type information is to be embedded.
     */
    protected abstract fun withValueTypeSerializerImplementation(
            valueTypeSerializer: TypeSerializer): StandardContainerSerializer<*>

    protected open fun findAndAddDynamic(context: SerializerProvider, type: KClass<*>): ValueSerializer<Any> {
        val map = myDynamicValueSerializers
        val result = map.findAndAddSecondarySerializer(type, context, myProperty)

        if (map !== result.map) {
            myDynamicValueSerializers = result.map
        }

        return result.serializer
    }

    protected open fun findAndAddDynamic(context: SerializerProvider, type: KotlinType): ValueSerializer<Any> {
        val map = myDynamicValueSerializers
        val result = map.findAndAddSecondarySerializer(type, context, myProperty)

        if (map !== result.map) {
            myDynamicValueSerializers = result.map
        }

        return result.serializer
    }

}