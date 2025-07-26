package org.cirjson.cirjackson.databind.introspection

import kotlin.reflect.KClass

open class MixInHandler : MixInResolver {

    /*
     *******************************************************************************************************************
     * Construction, mutant factories
     *******************************************************************************************************************
     */

    constructor(overrides: MixInResolver?) {
    }

    /*
     *******************************************************************************************************************
     * MixInResolver implementation
     *******************************************************************************************************************
     */

    override fun snapshot(): MixInResolver {
        TODO("Not yet implemented")
    }

    override fun findMixInClassFor(kClass: KClass<*>): KClass<*> {
        TODO("Not yet implemented")
    }

    override fun hasMixIns(): Boolean {
        TODO("Not yet implemented")
    }

}