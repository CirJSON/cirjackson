package org.cirjson.cirjackson.databind.serialization.implementation

import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import kotlin.reflect.KClass

/**
 * Helper container used for resolving serializers for dynamic (possibly but not necessarily polymorphic) properties:
 * properties whose type is not forced to use dynamic (declared) type and that are not final. If so, serializer to use
 * can only be established once actual value type is known. Since this happens a lot unless static typing is forced (or
 * types are final) this implementation is optimized for efficiency. Instances are immutable; new instances are created
 * with factory methods: this is important to ensure correct multithreaded access.
 */
abstract class PropertySerializerMap {

    /**
     * Configuration setting that determines what happens when maximum size (currently 8) is reached: if `true`, will
     * "start from beginning"; if `false`, will simply stop adding new entries.
     */
    protected val myResetWhenFull: Boolean

    protected constructor(resetWhenFull: Boolean) {
        myResetWhenFull = resetWhenFull
    }

    protected constructor(base: PropertySerializerMap) {
        myResetWhenFull = base.myResetWhenFull
    }

    /**
     * Main lookup method. Takes a "raw" type since usage is always from place where parameterization is fixed such that
     * there cannot be type-parametric variations.
     */
    abstract fun serializerFor(type: KClass<*>): ValueSerializer<Any>?

    /**
     * Method called if initial lookup fails, when looking for a primary serializer (one that is directly attached to a
     * property). Will both find serializer and construct new map instance if warranted, and return both.
     */
    fun findAndAddPrimarySerializer(type: KotlinType, provider: SerializerProvider,
            property: BeanProperty): SerializerAndMapResult {
        val serializer = provider.findPrimaryPropertySerializer(type, property)
        return SerializerAndMapResult(serializer, newWith(type.rawClass, serializer))
    }

    /**
     * Method called if initial lookup fails, when looking for a non-primary serializer (one that is not directly
     * attached to a property). Will both find serializer and construct new map instance if warranted, and return both.
     */
    fun findAndAddSecondarySerializer(type: KClass<*>, provider: SerializerProvider,
            property: BeanProperty?): SerializerAndMapResult {
        val serializer = provider.findContentValueSerializer(type, property)
        return SerializerAndMapResult(serializer, newWith(type, serializer))
    }

    /**
     * Method called if initial lookup fails, when looking for a non-primary serializer (one that is not directly
     * attached to a property). Will both find serializer and construct new map instance if warranted, and return both.
     */
    fun findAndAddSecondarySerializer(type: KotlinType, provider: SerializerProvider,
            property: BeanProperty?): SerializerAndMapResult {
        val serializer = provider.findContentValueSerializer(type, property)
        return SerializerAndMapResult(serializer, newWith(type.rawClass, serializer))
    }

    /**
     * Method called if initial lookup fails, when looking for a non-primary serializer (one that is not directly
     * attached to a property). Will both find serializer and construct new map instance if warranted, and return both.
     */
    fun findAndAddSecondarySerializer(type: KClass<*>, provider: SerializerProvider, property: BeanProperty?,
            serializerTransformer: (ValueSerializer<Any>) -> ValueSerializer<Any>): SerializerAndMapResult {
        var serializer = provider.findContentValueSerializer(type, property)
        serializer = serializerTransformer(serializer)
        return SerializerAndMapResult(serializer, newWith(type, serializer))
    }

    /**
     * Method called if initial lookup fails, when looking for a non-primary serializer (one that is not directly
     * attached to a property). Will both find serializer and construct new map instance if warranted, and return both.
     */
    fun findAndAddSecondarySerializer(type: KotlinType, provider: SerializerProvider, property: BeanProperty?,
            serializerTransformer: (ValueSerializer<Any>) -> ValueSerializer<Any>): SerializerAndMapResult {
        var serializer = provider.findContentValueSerializer(type, property)
        serializer = serializerTransformer(serializer)
        return SerializerAndMapResult(serializer, newWith(type.rawClass, serializer))
    }

    /**
     * Method called if initial lookup fails, when looking for a root value serializer: one that is not directly
     * attached to a property, but needs to have [org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer] wrapped
     * around it. Will both find the serializer and construct new map instance if warranted, and return both.
     */
    fun findAndAddRootValueSerializer(type: KClass<*>, provider: SerializerProvider): SerializerAndMapResult {
        val serializer = provider.findTypedValueSerializer(type, false)
        return SerializerAndMapResult(serializer, newWith(type, serializer))
    }

    /**
     * Method called if initial lookup fails, when looking for a root value serializer: one that is not directly
     * attached to a property, but needs to have [org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer] wrapped
     * around it. Will both find the serializer and construct new map instance if warranted, and return both.
     */
    fun findAndAddRootValueSerializer(type: KotlinType, provider: SerializerProvider): SerializerAndMapResult {
        val serializer = provider.findTypedValueSerializer(type, false)
        return SerializerAndMapResult(serializer, newWith(type.rawClass, serializer))
    }

    /**
     * Method called if initial lookup fails, when looking for a key serializer (possible attached indirectly to a
     * property) Will both find serializer and construct new map instance if warranted, and return both.
     */
    fun findAndAddKeySerializer(type: KClass<*>, provider: SerializerProvider,
            property: BeanProperty?): SerializerAndMapResult {
        val serializer = provider.findKeySerializer(type, property)
        return SerializerAndMapResult(serializer, newWith(type, serializer))
    }

    /**
     * Method that can be used to 'register' a serializer that caller has resolved without help of this map.
     */
    fun addSerializer(type: KClass<*>, serializer: ValueSerializer<Any>): SerializerAndMapResult {
        return SerializerAndMapResult(serializer, newWith(type, serializer))
    }

    /**
     * Method that can be used to 'register' a serializer that caller has resolved without help of this map.
     */
    fun addSerializer(type: KotlinType, serializer: ValueSerializer<Any>): SerializerAndMapResult {
        return SerializerAndMapResult(serializer, newWith(type.rawClass, serializer))
    }

    abstract fun newWith(type: KClass<*>, serializer: ValueSerializer<Any>): PropertySerializerMap

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    /**
     * Value class used for returning tuple that has both serializer that was retrieved and new map instance
     */
    data class SerializerAndMapResult(val serializer: ValueSerializer<Any>, val map: PropertySerializerMap)

    /**
     * Trivial container for bundling type + serializer entries.
     */
    private data class TypeAndSerializer(val type: KClass<*>, val serializer: ValueSerializer<Any>)

    /*
     *******************************************************************************************************************
     * Implementations
     *******************************************************************************************************************
     */

    /**
     * Bogus instance that contains no serializers; used as the default map with new serializers.
     */
    private class Empty(resetWhenFull: Boolean) : PropertySerializerMap(resetWhenFull) {

        override fun serializerFor(type: KClass<*>): ValueSerializer<Any>? {
            return null
        }

        override fun newWith(type: KClass<*>, serializer: ValueSerializer<Any>): PropertySerializerMap {
            return Single(this, type, serializer)
        }

        companion object {

            val FOR_PROPERTIES = Empty(false)

            val FOR_ROOT_VALUES = Empty(true)

            fun emptyFor(source: PropertySerializerMap): Empty {
                return if (source.myResetWhenFull) FOR_ROOT_VALUES else FOR_PROPERTIES
            }

        }

    }

    /**
     * Map that contains a single serializer; although seemingly silly this is probably the most commonly used variant
     * because many theoretically dynamic or polymorphic types just have single actual type.
     */
    private class Single(base: PropertySerializerMap, private val myType: KClass<*>,
            private val mySerializer: ValueSerializer<Any>) : PropertySerializerMap(base) {

        override fun serializerFor(type: KClass<*>): ValueSerializer<Any>? {
            if (myType == type) {
                return mySerializer
            }

            return null
        }

        override fun newWith(type: KClass<*>, serializer: ValueSerializer<Any>): PropertySerializerMap {
            return Double(this, myType, mySerializer, type, serializer)
        }

    }

    private class Double(base: PropertySerializerMap, private val myType1: KClass<*>,
            private val mySerializer1: ValueSerializer<Any>, private val myType2: KClass<*>,
            private val mySerializer2: ValueSerializer<Any>) : PropertySerializerMap(base) {

        override fun serializerFor(type: KClass<*>): ValueSerializer<Any>? {
            if (myType1 == type) {
                return mySerializer1
            }

            if (myType2 == type) {
                return mySerializer2
            }

            return null
        }

        override fun newWith(type: KClass<*>, serializer: ValueSerializer<Any>): PropertySerializerMap {
            val typeAndSerializers =
                    arrayOf(TypeAndSerializer(myType1, mySerializer1), TypeAndSerializer(myType2, mySerializer2),
                            TypeAndSerializer(type, serializer))
            return Multi(this, typeAndSerializers)
        }

    }

    private class Multi(base: PropertySerializerMap, private val myEntries: Array<TypeAndSerializer>) :
            PropertySerializerMap(base) {

        override fun serializerFor(type: KClass<*>): ValueSerializer<Any>? {
            return myEntries.find { it.type == type }?.serializer
        }

        override fun newWith(type: KClass<*>, serializer: ValueSerializer<Any>): PropertySerializerMap {
            val length = myEntries.size

            if (length == MAX_ENTRIES) {
                return if (myResetWhenFull) {
                    Single(this, type, serializer)
                } else {
                    this
                }
            }

            val entries = Array(length + 1) {
                if (it != length) {
                    myEntries[it]
                } else {
                    TypeAndSerializer(type, serializer)
                }
            }

            return Multi(this, entries)
        }

        companion object {

            /**
             * Let's limit number of serializers we actually cache; linear lookup won't scale too well beyond smallish
             * number, and if we really want to support larger collections should use a hash map. But it seems unlikely
             * this is a common use case so for now let's just stop building after hard-coded limit. 8 sounds like a
             * reasonable stab for now.
             */
            private const val MAX_ENTRIES = 8

        }

    }

    companion object {

        fun emptyForProperties(): PropertySerializerMap {
            return Empty.FOR_PROPERTIES
        }

        fun emptyForRootValues(): PropertySerializerMap {
            return Empty.FOR_ROOT_VALUES
        }

    }

}