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

    open fun withOverrides(overrides: MixInResolver?): MixInHandler {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Mutators
     *******************************************************************************************************************
     */

    open fun addLocalDefinitions(sourceMixins: Map<KClass<*>, KClass<*>?>): MixInHandler {
        TODO("Not yet implemented")
    }

    open fun addLocalDefinition(target: KClass<*>, mixinSource: KClass<*>?): MixInHandler {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * MixInResolver implementation
     *******************************************************************************************************************
     */

    override fun snapshot(): MixInResolver {
        TODO("Not yet implemented")
    }

    override fun findMixInClassFor(kClass: KClass<*>): KClass<*>? {
        TODO("Not yet implemented")
    }

    override fun hasMixIns(): Boolean {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Other
     *******************************************************************************************************************
     */

    open fun localSize(): Int {
        TODO("Not yet implemented")
    }

}