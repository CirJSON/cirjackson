package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.configuration.SerializerFactoryConfig

/**
 * Factory class that can provide serializers for standard classes, as well as custom classes that extend standard
 * classes or implement one of "well-known" interfaces (such as [Collection]).
 * 
 * Since all the serializers are eagerly instantiated, and there is no additional introspection or customizability of
 * these types, this factory is essentially stateless.
 * 
 * @constructor We will provide default constructor to allow subclassing, but make it protected so that no non-singleton
 * instances of the class will be instantiated.
 */
abstract class BasicSerializerFactory protected constructor(config: SerializerFactoryConfig?) : SerializerFactory() {

    /*
     *******************************************************************************************************************
     * Configuration
     *******************************************************************************************************************
     */

    /**
     * Configuration settings for this factory; immutable instance (just like this factory), new version created via
     * copy-constructor (fluent-style)
     */
    protected val myFactoryConfig = config ?: SerializerFactoryConfig()

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * Method used for creating a new instance of this factory, but with different configuration. Reason for specifying
     * factory method (instead of plain constructor) is to allow proper sub-classing of factories.
     * 
     * Note that custom subclasses generally **must override** implementation of this method, as it usually requires
     * instantiating a new instance of factory type. Check out docs for [BeanSerializerFactory] for more details.
     */
    protected abstract fun withConfig(config: SerializerFactoryConfig?): SerializerFactory

    /**
     * Convenience method for creating a new factory instance with an additional serializer provider.
     */
    final override fun withAdditionalSerializers(additional: Serializers): SerializerFactory {
        return withConfig(myFactoryConfig.withAdditionalSerializers(additional))
    }

    /**
     * Convenience method for creating a new factory instance with an additional key serializer provider.
     */
    final override fun withAdditionalKeySerializers(additional: Serializers): SerializerFactory {
        return withConfig(myFactoryConfig.withAdditionalKeySerializers(additional))
    }

    /**
     * Convenience method for creating a new factory instance with additional bean serializer modifier.
     */
    final override fun withSerializerModifier(modifier: ValueSerializerModifier): SerializerFactory {
        return withConfig(myFactoryConfig.withSerializerModifier(modifier))
    }

    final override fun withNullKeySerializers(serializer: ValueSerializer<*>): SerializerFactory {
        return withConfig(myFactoryConfig.withNullKeySerializer(serializer))
    }

    final override fun withNullValueSerializers(serializer: ValueSerializer<*>): SerializerFactory {
        return withConfig(myFactoryConfig.withNullValueSerializer(serializer))
    }

    /*
     *******************************************************************************************************************
     * SerializerFactory implementation
     *******************************************************************************************************************
     */

    override fun createKeySerializer(context: SerializerProvider, type: KotlinType): ValueSerializer<Any>? {
        TODO("Not yet implemented")
    }

    override val defaultNullKeySerializer: ValueSerializer<Any>
        get() = myFactoryConfig.nullKeySerializer

    override val defaultNullValueSerializer: ValueSerializer<Any>?
        get() = myFactoryConfig.nullValueSerializer

}