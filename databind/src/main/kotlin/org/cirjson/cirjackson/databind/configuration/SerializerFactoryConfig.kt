package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.serialization.Serializers
import org.cirjson.cirjackson.databind.serialization.ValueSerializerModifier
import org.cirjson.cirjackson.databind.serialization.implementation.FailingSerializer
import org.cirjson.cirjackson.databind.util.ArrayBuilders

/**
 * Configuration settings container class for [org.cirjson.cirjackson.databind.serialization.SerializerFactory]
 * implementations.
 *
 * @property nullKeySerializer Serializer used to (try to) output a `null` key, due to an entry of [Map] having `null`
 * key.
 *
 * @property nullValueSerializer Serializer used to output a `null` value, unless explicitly redefined for property.
 */
class SerializerFactoryConfig private constructor(allAdditionalSerializers: Array<Serializers>?,
        allAdditionalKeySerializers: Array<Serializers>?, modifiers: Array<ValueSerializerModifier>?,
        val nullKeySerializer: ValueSerializer<Any>, val nullValueSerializer: ValueSerializer<Any>?) {

    /**
     * List of providers for additional serializers, checked before considering default basic or bean serializers.
     */
    private val myAdditionalSerializers = allAdditionalSerializers ?: NO_SERIALIZERS

    /**
     * List of providers for additional key serializers, checked before considering default key serializers.
     */
    private val myAdditionalKeySerializers = allAdditionalKeySerializers ?: NO_SERIALIZERS

    /**
     * List of modifiers that can change the way [org.cirjson.cirjackson.databind.serialization.BeanSerializer]
     * instances are configured and constructed.
     */
    private val myModifiers = modifiers ?: NO_MODIFIERS

    constructor() : this(null, null, null, DEFAULT_NULL_KEY_SERIALIZER, null)

    fun withAdditionalSerializers(additional: Serializers): SerializerFactoryConfig {
        val all = ArrayBuilders.insertInListNoDup(myAdditionalSerializers, additional)
        return SerializerFactoryConfig(all, myAdditionalKeySerializers, myModifiers, nullKeySerializer,
                nullValueSerializer)
    }

    fun withAdditionalKeySerializers(additional: Serializers): SerializerFactoryConfig {
        val all = ArrayBuilders.insertInListNoDup(myAdditionalKeySerializers, additional)
        return SerializerFactoryConfig(myAdditionalSerializers, all, myModifiers, nullKeySerializer,
                nullValueSerializer)
    }

    fun withSerializerModifier(modifier: ValueSerializerModifier): SerializerFactoryConfig {
        val all = ArrayBuilders.insertInListNoDup(myModifiers, modifier)
        return SerializerFactoryConfig(myAdditionalSerializers, myAdditionalKeySerializers, all, nullKeySerializer,
                nullValueSerializer)
    }

    fun withNullKeySerializer(serializer: ValueSerializer<Any>): SerializerFactoryConfig {
        return SerializerFactoryConfig(myAdditionalSerializers, myAdditionalKeySerializers, myModifiers, serializer,
                nullValueSerializer)
    }

    fun withNullValueSerializer(serializer: ValueSerializer<Any>): SerializerFactoryConfig {
        return SerializerFactoryConfig(myAdditionalSerializers, myAdditionalKeySerializers, myModifiers,
                nullKeySerializer, serializer)
    }

    companion object {

        val DEFAULT_NULL_KEY_SERIALIZER: ValueSerializer<Any> =
                FailingSerializer("Null key for a Map not allowed in CirJSON (use a converting NullKeySerializer?)")

        /**
         * Constant for empty `Serializers` array (which by definition is stateless and reusable)
         */
        private val NO_SERIALIZERS = emptyArray<Serializers>()

        private val NO_MODIFIERS = emptyArray<ValueSerializerModifier>()

    }

}