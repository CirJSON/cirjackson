package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.*
import kotlin.reflect.KClass

open class StandardTypeResolverBuilder : TypeResolverBuilder<StandardTypeResolverBuilder> {

    /*
     *******************************************************************************************************************
     * Construction, initialization, actual building
     *******************************************************************************************************************
     */

    constructor() {
    }

    override fun buildTypeSerializer(context: SerializerProvider, type: KotlinType,
            subtypes: Collection<NamedType>?): TypeSerializer? {
        TODO("Not yet implemented")
    }

    override fun buildTypeDeserializer(context: DeserializationContext, type: KotlinType,
            subtypes: Collection<NamedType>?): TypeDeserializer {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Construction, configuration
     *******************************************************************************************************************
     */

    override fun init(settings: CirJsonTypeInfo.Value?, resolver: TypeIdResolver?): StandardTypeResolverBuilder {
        TODO("Not yet implemented")
    }

    override fun withDefaultImplementation(defaultImplementation: KClass<*>?): StandardTypeResolverBuilder {
        TODO("Not yet implemented")
    }

    override fun withSettings(settings: CirJsonTypeInfo.Value?): StandardTypeResolverBuilder {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    override val defaultImplementation: KClass<*>?
        get() = TODO("Not yet implemented")

}