package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.cirjsontype.NamedType
import org.cirjson.cirjackson.databind.cirjsontype.SubtypeResolver
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.AnnotatedClass
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import kotlin.reflect.KClass

/**
 * Standard [SubtypeResolver] implementation.
 */
open class StandardSubtypeResolver : SubtypeResolver {

    protected var myRegisteredSubtypes: MutableSet<NamedType>?

    constructor() : super() {
        myRegisteredSubtypes = null
    }

    constructor(registeredSubtypes: MutableSet<NamedType>?) {
        myRegisteredSubtypes = registeredSubtypes
    }

    override fun snapshot(): SubtypeResolver {
        return myRegisteredSubtypes?.let { StandardSubtypeResolver(LinkedHashSet(it)) } ?: StandardSubtypeResolver()
    }

    /*
     *******************************************************************************************************************
     * Subtype registration
     *******************************************************************************************************************
     */

    override fun registerSubtypes(vararg subtypes: NamedType): StandardSubtypeResolver {
        if (myRegisteredSubtypes == null) {
            myRegisteredSubtypes = LinkedHashSet()
        }

        myRegisteredSubtypes!!.addAll(subtypes)
        return this
    }

    override fun registerSubtypes(vararg subtypes: KClass<*>): StandardSubtypeResolver {
        val types = Array(subtypes.size) { NamedType(subtypes[it]) }
        return registerSubtypes(*types)
    }

    override fun registerSubtypes(subtypes: Collection<KClass<*>>): StandardSubtypeResolver {
        return registerSubtypes(*subtypes.toTypedArray())
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