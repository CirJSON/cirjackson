package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.configuration.MapperConfig

open class BasicClassIntrospector : ClassIntrospector {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor() : super() {
    }

    override fun forOperation(config: MapperConfig<*>): BasicClassIntrospector {
        TODO("Not yet implemented")
    }

}