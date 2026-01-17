package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.annotations.ObjectIdResolver
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver
import org.cirjson.cirjackson.databind.cirjsontype.TypeResolverBuilder
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.introspection.Annotated
import org.cirjson.cirjackson.databind.introspection.BeanPropertyDefinition
import org.cirjson.cirjackson.databind.serialization.VirtualBeanPropertyWriter
import org.cirjson.cirjackson.databind.util.Converter
import kotlin.reflect.KClass

/**
 * Helper class used for handling details of creating handler instances (things like
 * [ValueSerializers][ValueSerializer], [ValueDeserializers][ValueDeserializer], various type handlers) of specific
 * types. Actual handler type has been resolved at this point, so instantiator is strictly responsible for providing a
 * configured instance by constructing and configuring a new instance, or possibly by recycling a shared instance. One
 * use case is that of allowing dependency injection, which would otherwise be challenging to do.
 *
 * Custom instances are allowed to return null to indicate that caller should use the default instantiation handling
 * (which just means calling no-argument constructor via reflection).
 *
 * Care has to be taken to ensure that if instance returned is shared, it will be thread-safe; caller will not
 * synchronize access to returned instances.
 */
abstract class HandlerInstantiator {

    /**
     * Method called to get an instance of the specified type's deserializer.
     *
     * @param config Deserialization configuration in effect
     *
     * @param annotated Element (Class, Method, Field, constructor parameter) that had annotation defining class of
     * deserializer to construct (to allow implementation use information from other annotations)
     *
     * @param deserializerClass Class of deserializer instance to return
     *
     * @return Deserializer instance to use
     */
    abstract fun deserializerInstance(config: DeserializationConfig, annotated: Annotated,
            deserializerClass: KClass<*>): ValueDeserializer<*>?

    /**
     * Method called to get an instance of the specified type's key deserializer.
     *
     * @param config Deserialization configuration in effect
     *
     * @param annotated Element (Class, Method, Field, constructor parameter) that had annotation defining class of key
     * deserializer to construct (to allow implementation use information from other annotations)
     *
     * @param keyDeserializerClass Class of key deserializer instance to return
     *
     * @return Key deserializer instance to use
     */
    abstract fun keyDeserializerInstance(config: DeserializationConfig, annotated: Annotated,
            keyDeserializerClass: KClass<*>): KeyDeserializer?

    /**
     * Method called to get an instance of the specified type's serializer of.
     *
     * @param config Serialization configuration in effect
     *
     * @param annotated Element (Class, Method, Field) that had annotation defining class of serializer to construct
     * (to allow implementation use information from other annotations)
     *
     * @param serializerClass Class of serializer instance to return
     *
     * @return Serializer instance to use
     */
    abstract fun serializerInstance(config: SerializationConfig, annotated: Annotated,
            serializerClass: KClass<*>): ValueSerializer<*>?

    /**
     * Method called to get an instance of the specified type's TypeResolverBuilder.
     *
     * @param config Mapper configuration in effect (either SerializationConfig or DeserializationConfig, depending on
     * when instance is being constructed)
     *
     * @param annotated annotated Element (Class, Method, Field) that had annotation defining class of builder to
     * construct (to allow implementation use information from other annotations)
     *
     * @param builderClass Class of builder instance to return
     *
     * @return TypeResolverBuilder instance to use
     */
    abstract fun typeResolverBuilderInstance(config: MapperConfig<*>, annotated: Annotated,
            builderClass: KClass<*>): TypeResolverBuilder<*>?

    /**
     * Method called to get an instance of the specified type's TypeIdResolver.
     *
     * @param config Mapper configuration in effect (either SerializationConfig or DeserializationConfig, depending on
     * when instance is being constructed)
     *
     * @param annotated annotated Element (Class, Method, Field) that had annotation defining class of resolver to
     * construct (to allow implementation use information from other annotations)
     *
     * @param resolverClass Class of resolver instance to return
     *
     * @return TypeResolverBuilder instance to use
     */
    abstract fun typeIdResolverInstance(config: MapperConfig<*>, annotated: Annotated,
            resolverClass: KClass<*>): TypeIdResolver?

    /**
     * Method called to construct an instance of the specified type's ValueInstantiator.
     */
    open fun valueInstantiatorInstance(config: MapperConfig<*>, annotated: Annotated,
            resolverClass: KClass<*>): ValueInstantiator? {
        return null
    }

    /**
     * Method called to construct an ObjectIdHandler instance of the specified type.
     */
    open fun objectIdGeneratorInstance(config: MapperConfig<*>, annotated: Annotated,
            implementationClass: KClass<*>): ObjectIdGenerator<*>? {
        return null
    }

    /**
     * Method called to construct an ObjectIdResolver instance of the specified type.
     */
    open fun resolverIdGeneratorInstance(config: MapperConfig<*>, annotated: Annotated,
            implementationClass: KClass<*>): ObjectIdResolver? {
        return null
    }

    /**
     * Method called to construct a NamingStrategy instance used for the specified class.
     */
    open fun namingStrategyInstance(config: MapperConfig<*>, annotated: Annotated,
            implementationClass: KClass<*>): PropertyNamingStrategy? {
        return null
    }

    /**
     * Method called to construct a Converter instance used for the specified class.
     */
    open fun converterInstance(config: MapperConfig<*>, annotated: Annotated,
            implementationClass: KClass<*>): Converter<*, *>? {
        return null
    }

    /**
     * Method called to construct a [VirtualBeanPropertyWriter] instance of the specified type.
     */
    open fun virtualPropertyWriterInstance(config: MapperConfig<*>,
            implementationClass: KClass<*>): VirtualBeanPropertyWriter? {
        return null
    }

    /**
     * Method called to construct a Filter (any Object with implementation of `equals(Object)` that determines if given
     * value is to be excluded (`true`) or included (`false`)) to be used based on
     * [org.cirjson.cirjackson.annotations.CirJsonInclude] annotation (or equivalent).
     *
     * Default implementation returns `null` to indicate that default instantiation (use zero-arg constructor of the
     * `filterClass`) should be used.
     */
    open fun includeFilterInstance(config: MapperConfig<*>, forProperty: BeanPropertyDefinition?,
            filterClass: KClass<*>): Any? {
        return null
    }

}