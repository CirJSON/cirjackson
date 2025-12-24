package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.configuration.DeserializerFactoryConfig
import org.cirjson.cirjackson.databind.type.*
import kotlin.reflect.KClass

abstract class BasicDeserializerFactory protected constructor(
        protected val myFactoryConfig: DeserializerFactoryConfig) : DeserializerFactory() {

    /*
     *******************************************************************************************************************
     * Configuration handling: fluent factories
     *******************************************************************************************************************
     */

    override fun withAdditionalDeserializers(additional: Deserializers): DeserializerFactory {
        TODO("Not yet implemented")
    }

    override fun withAdditionalKeyDeserializers(additional: KeyDeserializers): DeserializerFactory {
        TODO("Not yet implemented")
    }

    override fun withDeserializerModifier(modifier: ValueDeserializerModifier): DeserializerFactory {
        TODO("Not yet implemented")
    }

    override fun withValueInstantiators(instantiators: ValueInstantiators): DeserializerFactory {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory implementation: array deserializers
     *******************************************************************************************************************
     */

    override fun createArrayDeserializer(context: DeserializationContext, type: ArrayType,
            beanDescription: BeanDescription): ValueDeserializer<*> {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory implementation: Collection(-like) deserializers
     *******************************************************************************************************************
     */

    override fun createCollectionDeserializer(context: DeserializationContext, type: CollectionType,
            beanDescription: BeanDescription): ValueDeserializer<*> {
        TODO("Not yet implemented")
    }

    override fun createCollectionLikeDeserializer(context: DeserializationContext, type: CollectionLikeType,
            beanDescription: BeanDescription): ValueDeserializer<*> {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory implementation: Map(-like) deserializers
     *******************************************************************************************************************
     */

    override fun createMapDeserializer(context: DeserializationContext, type: MapType,
            beanDescription: BeanDescription): ValueDeserializer<*> {
        TODO("Not yet implemented")
    }

    override fun createMapLikeDeserializer(context: DeserializationContext, type: MapLikeType,
            beanDescription: BeanDescription): ValueDeserializer<*> {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory implementation: other types
     *******************************************************************************************************************
     */

    override fun createEnumDeserializer(context: DeserializationContext, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<*> {
        TODO("Not yet implemented")
    }

    override fun createTreeDeserializer(config: DeserializationConfig, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<*> {
        TODO("Not yet implemented")
    }

    override fun createReferenceDeserializer(context: DeserializationContext, type: ReferenceType,
            beanDescription: BeanDescription): ValueDeserializer<*> {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory implementation (partial): key deserializers
     *******************************************************************************************************************
     */

    override fun createKeyDeserializer(context: DeserializationContext, type: KotlinType): KeyDeserializer? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * DeserializerFactory implementation: find explicitly supported types
     *******************************************************************************************************************
     */

    override fun hasExplicitDeserializerFor(context: DatabindContext, valueType: KClass<*>): Boolean {
        TODO("Not yet implemented")
    }

}