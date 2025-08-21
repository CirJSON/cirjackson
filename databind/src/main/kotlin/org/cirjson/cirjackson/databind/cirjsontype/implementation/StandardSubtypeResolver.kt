package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.cirjsontype.NamedType
import org.cirjson.cirjackson.databind.cirjsontype.SubtypeResolver
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.AnnotatedClass
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import kotlin.reflect.KClass

open class StandardSubtypeResolver : SubtypeResolver {

    constructor() : super() {
    }

    override fun snapshot(): SubtypeResolver {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Subtype registration
     *******************************************************************************************************************
     */

    override fun registerSubtypes(vararg subtypes: NamedType): StandardSubtypeResolver {
        TODO("Not yet implemented")
    }

    override fun registerSubtypes(vararg subtypes: KClass<*>): StandardSubtypeResolver {
        TODO("Not yet implemented")
    }

    override fun registerSubtypes(subtypes: Collection<KClass<*>>): StandardSubtypeResolver {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Resolution by class (serialization)
     *******************************************************************************************************************
     */

    override fun collectAndResolveSubtypesByClass(config: MapperConfig<*>, property: AnnotatedMember?,
            baseType: KotlinType?): Collection<NamedType> {
        TODO("Not yet implemented")
    }

    override fun collectAndResolveSubtypesByClass(config: MapperConfig<*>,
            baseType: AnnotatedClass): Collection<NamedType> {
        TODO("Not yet implemented")
    }

    override fun collectAndResolveSubtypesByTypeId(config: MapperConfig<*>, property: AnnotatedMember?,
            baseType: KotlinType): Collection<NamedType> {
        TODO("Not yet implemented")
    }

    override fun collectAndResolveSubtypesByTypeId(config: MapperConfig<*>,
            baseType: AnnotatedClass): Collection<NamedType> {
        TODO("Not yet implemented")
    }

}