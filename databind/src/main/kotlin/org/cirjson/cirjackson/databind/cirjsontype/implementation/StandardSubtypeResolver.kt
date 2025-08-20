package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.databind.cirjsontype.NamedType
import org.cirjson.cirjackson.databind.cirjsontype.SubtypeResolver
import kotlin.reflect.KClass

open class StandardSubtypeResolver : SubtypeResolver {

    constructor() : super() {
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

}