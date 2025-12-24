package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.type.*
import kotlin.reflect.KClass

abstract class DeserializerFactory {

    /*
     *******************************************************************************************************************
     * Basic DeserializerFactory API
     *******************************************************************************************************************
     */

    abstract fun createBeanDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<Any>?

    abstract fun createBuilderBasedDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription, builderClass: KClass<*>): ValueDeserializer<Any>

    abstract fun createEnumDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<*>

    abstract fun createReferenceDeserializer(context: DeserializationContext, type: ReferenceType,
            beanDescription: BeanDescription): ValueDeserializer<*>

    abstract fun createTreeDeserializer(config: DeserializationConfig, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<*>

    abstract fun createArrayDeserializer(context: DeserializationContext, type: ArrayType,
            beanDescription: BeanDescription): ValueDeserializer<*>

    abstract fun createCollectionDeserializer(context: DeserializationContext, type: CollectionType,
            beanDescription: BeanDescription): ValueDeserializer<*>

    abstract fun createCollectionLikeDeserializer(context: DeserializationContext, type: CollectionLikeType,
            beanDescription: BeanDescription): ValueDeserializer<*>

    abstract fun createMapDeserializer(context: DeserializationContext, type: MapType,
            beanDescription: BeanDescription): ValueDeserializer<*>

    abstract fun createMapLikeDeserializer(context: DeserializationContext, type: MapLikeType,
            beanDescription: BeanDescription): ValueDeserializer<*>

    abstract fun createKeyDeserializer(context: DeserializationContext, type: KotlinType): KeyDeserializer?

    abstract fun hasExplicitDeserializerFor(context: DatabindContext, valueType: KClass<*>): Boolean

    /*
     *******************************************************************************************************************
     * Mutant factories for registering additional configuration
     *******************************************************************************************************************
     */

    abstract fun withAdditionalDeserializers(additional: Deserializers): DeserializerFactory

    abstract fun withAdditionalKeyDeserializers(additional: KeyDeserializers): DeserializerFactory

    abstract fun withDeserializerModifier(modifier: ValueDeserializerModifier): DeserializerFactory

    abstract fun withValueInstantiators(instantiators: ValueInstantiators): DeserializerFactory

}