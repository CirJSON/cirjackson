package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.databind.deserialization.Deserializers
import org.cirjson.cirjackson.databind.deserialization.KeyDeserializers
import org.cirjson.cirjackson.databind.deserialization.ValueDeserializerModifier
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiators
import org.cirjson.cirjackson.databind.deserialization.kotlin.KotlinKeyDeserializers
import org.cirjson.cirjackson.databind.util.ArrayBuilders
import org.cirjson.cirjackson.databind.util.ArrayIterator

/**
 * Configuration settings container class for [DeserializerFactory].
 *
 * @constructor Copy-constructor that will create an instance that contains the defined set of additional deserializer
 * providers.
 */
open class DeserializerFactoryConfig protected constructor(allAdditionalDeserializers: Array<Deserializers>?,
        allAdditionalKeyDeserializers: Array<KeyDeserializers>?, allModifiers: Array<ValueDeserializerModifier>?,
        allValueInstantiators: Array<ValueInstantiators>?) {

    /**
     * List of providers for additional deserializers, checked before considering default basic or bean deserializers.
     */
    protected val myAdditionalDeserializers = allAdditionalDeserializers ?: NO_DESERIALIZERS

    /**
     * List of providers for additional key deserializers, checked before considering standard key deserializers.
     */
    protected val myAdditionalKeyDeserializers = allAdditionalKeyDeserializers ?: DEFAULT_KEY_DESERIALIZERS

    /**
     * List of modifiers that can change the way
     * [BeanDeserializer][org.cirjson.cirjackson.databind.deserialization.bean.BeanDeserializer] instances are
     * configured and constructed.
     */
    protected val myModifiers = allModifiers ?: NO_MODIFIERS

    /**
     * List of objects that know how to create instances of POJO types; possibly using custom construction
     * (non-annotated constructors; factory methods external to value, type, etc.). Used to support objects that are
     * created using non-standard methods; or to support post-constructor functionality.
     */
    protected val myValueInstantiators = allValueInstantiators ?: NO_VALUE_INSTANTIATORS

    /**
     * Fluent/factory method used to construct a configuration object that has the same deserializer providers as this
     * instance, plus one specified as argument. Additional provider will be added before existing ones, meaning it has
     * priority over existing definitions.
     *
     * Note that, while the parameter is nullable, the base definition throws an [IllegalArgumentException] if the
     * parameter is `null`. That's because child classes might want to accept a `null` parameter.
     */
    open fun withAdditionalDeserializers(additional: Deserializers?): DeserializerFactoryConfig {
        additional ?: throw IllegalArgumentException("Cannot pass null Deserializers")
        val all = ArrayBuilders.insertInListNoDup(myAdditionalDeserializers, additional)
        return DeserializerFactoryConfig(all, myAdditionalKeyDeserializers, myModifiers, myValueInstantiators)
    }

    /**
     * Fluent/factory method used to construct a configuration object that has the same key deserializer providers as
     * this instance, plus one specified as argument. Additional provider will be added before existing ones, meaning it
     * has priority over existing definitions.
     *
     * Note that, while the parameter is nullable, the base definition throws an [IllegalArgumentException] if the
     * parameter is `null`. That's because child classes might want to accept a `null` parameter.
     */
    open fun withAdditionalKeyDeserializers(additional: KeyDeserializers?): DeserializerFactoryConfig {
        additional ?: throw IllegalArgumentException("Cannot pass null KeyDeserializers")
        val all = ArrayBuilders.insertInListNoDup(myAdditionalKeyDeserializers, additional)
        return DeserializerFactoryConfig(myAdditionalDeserializers, all, myModifiers, myValueInstantiators)
    }

    /**
     * Fluent/factory method used to construct a configuration object that has the same configuration as this instance
     * plus one additional deserializer modifier. Added modifier has the highest priority (that is, it gets called
     * before any already registered modifier).
     *
     * Note that, while the parameter is nullable, the base definition throws an [IllegalArgumentException] if the
     * parameter is `null`. That's because child classes might want to accept a `null` parameter.
     */
    open fun withDeserializerModifier(modifier: ValueDeserializerModifier?): DeserializerFactoryConfig {
        modifier ?: throw IllegalArgumentException("Cannot pass null ValueDeserializerModifier")
        val all = ArrayBuilders.insertInListNoDup(myModifiers, modifier)
        return DeserializerFactoryConfig(myAdditionalDeserializers, myAdditionalKeyDeserializers, all,
                myValueInstantiators)
    }

    /**
     * Fluent/factory method used to construct a configuration object that has the same configuration as this instance
     * plus the specified additional value instantiator provider object. Added instantiator provider has the highest
     * priority (that is, it gets called before any already registered resolver).
     *
     * Note that, while the parameter is nullable, the base definition throws an [IllegalArgumentException] if the
     * parameter is `null`. That's because child classes might want to accept a `null` parameter.
     *
     * @param instantiators Object that can provide multiple
     * [ValueInstantiator][org.cirjson.cirjackson.databind.deserialization.ValueInstantiator] for constructing POJO
     * values during deserialization
     */
    open fun withValueInstantiators(instantiators: ValueInstantiators?): DeserializerFactoryConfig {
        instantiators ?: throw IllegalArgumentException("Cannot pass null KeyDeserializers")
        val all = ArrayBuilders.insertInListNoDup(myValueInstantiators, instantiators)
        return DeserializerFactoryConfig(myAdditionalDeserializers, myAdditionalKeyDeserializers, myModifiers, all)
    }

    fun hasDeserializers(): Boolean {
        return myAdditionalDeserializers.isNotEmpty()
    }

    fun hasKeyDeserializers(): Boolean {
        return myAdditionalKeyDeserializers.isNotEmpty()
    }

    fun hasDeserializerModifiers(): Boolean {
        return myModifiers.isNotEmpty()
    }

    fun hasValueInstantiators(): Boolean {
        return myValueInstantiators.isNotEmpty()
    }

    fun deserializers(): Iterable<Deserializers> {
        return ArrayIterator(myAdditionalDeserializers)
    }

    fun keyDeserializers(): Iterable<KeyDeserializers> {
        return ArrayIterator(myAdditionalKeyDeserializers)
    }

    fun deserializerModifiers(): Iterable<ValueDeserializerModifier> {
        return ArrayIterator(myModifiers)
    }

    fun valueInstantiators(): Iterable<ValueInstantiators> {
        return ArrayIterator(myValueInstantiators)
    }

    companion object {

        val NO_DESERIALIZERS = emptyArray<Deserializers>()

        val NO_MODIFIERS = emptyArray<ValueDeserializerModifier>()

        val NO_VALUE_INSTANTIATORS = emptyArray<ValueInstantiators>()

        /**
         * By default, we plug default key deserializers using as "just another" set of key deserializers.
         */
        val DEFAULT_KEY_DESERIALIZERS = arrayOf<KeyDeserializers>(KotlinKeyDeserializers())

    }

}