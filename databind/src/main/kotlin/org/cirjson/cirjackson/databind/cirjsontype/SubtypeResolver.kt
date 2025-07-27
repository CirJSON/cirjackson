package org.cirjson.cirjackson.databind.cirjsontype

import org.cirjson.cirjackson.core.util.Snapshottable
import kotlin.reflect.KClass

abstract class SubtypeResolver : Snapshottable<SubtypeResolver> {

    /*
     *******************************************************************************************************************
     * Snapshottable
     *******************************************************************************************************************
     */

    override fun snapshot(): SubtypeResolver {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Methods for registering external subtype definitions
     *******************************************************************************************************************
     */

    abstract fun registerSubtypes(vararg subtypes: NamedType): SubtypeResolver

    abstract fun registerSubtypes(vararg subtypes: KClass<*>): SubtypeResolver

    abstract fun registerSubtypes(subtypes: Collection<KClass<*>>): SubtypeResolver

}