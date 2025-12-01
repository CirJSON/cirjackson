package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.type.ClassKey
import kotlin.reflect.KClass

/**
 * Basic [MixInResolver] implementation that both allows simple "local" override definitions (with simple Mix-in class
 * over Target class mapping) and allows optional custom overrides for lookup.
 *
 * Implementation is only thread-safe after initialization (that is, when underlying Map is not modified but only read).
 */
open class MixInHandler : MixInResolver {

    /**
     * External resolver that gets called before looking at any locally defined mix-in target classes.
     */
    protected val myOverrides: MixInResolver?

    /**
     * Simple mix-in targets defined locally.
     */
    protected var myLocalMixIns: Map<ClassKey, KClass<*>?>?

    /*
     *******************************************************************************************************************
     * Construction, mutant factories
     *******************************************************************************************************************
     */

    constructor(overrides: MixInResolver?) {
        myOverrides = overrides
        myLocalMixIns = null
    }

    constructor(overrides: MixInResolver?, localMixIns: Map<ClassKey, KClass<*>?>?) {
        myOverrides = overrides
        myLocalMixIns = localMixIns
    }

    /**
     * Mutant factory for constructor a new resolver instance with given mix-in resolver override.
     */
    open fun withOverrides(overrides: MixInResolver?): MixInHandler {
        return MixInHandler(overrides, myLocalMixIns)
    }

    /**
     * Mutant factory method that constructs a new instance that has no locally defined mix-in/target mappings.
     */
    open fun withoutLocalDefinitions(): MixInHandler {
        return MixInHandler(myOverrides, null)
    }

    /*
     *******************************************************************************************************************
     * Mutators
     *******************************************************************************************************************
     */

    open fun addLocalDefinitions(sourceMixins: Map<KClass<*>, KClass<*>?>): MixInHandler {
        if (sourceMixins.isNotEmpty()) {
            var localMixIns = myLocalMixIns

            if (localMixIns == null) {
                localMixIns = HashMap()
                myLocalMixIns = localMixIns
            } else if (localMixIns !is MutableMap<ClassKey, KClass<*>?>) {
                localMixIns = HashMap()
                myLocalMixIns = localMixIns
            }

            for ((key, value) in sourceMixins) {
                localMixIns[ClassKey(key)] = value
            }
        }

        return this
    }

    open fun addLocalDefinition(target: KClass<*>, mixinSource: KClass<*>?): MixInHandler {
        var localMixIns = myLocalMixIns

        if (localMixIns == null) {
            localMixIns = HashMap()
            myLocalMixIns = localMixIns
        } else if (localMixIns !is MutableMap<ClassKey, KClass<*>?>) {
            localMixIns = HashMap()
            myLocalMixIns = localMixIns
        }

        localMixIns[ClassKey(target)] = mixinSource
        return this
    }

    /*
     *******************************************************************************************************************
     * MixInResolver implementation
     *******************************************************************************************************************
     */

    override fun snapshot(): MixInResolver {
        val overrides = myOverrides?.snapshot()
        val localMixIns = myLocalMixIns?.let { HashMap(it) }
        return MixInHandler(overrides, localMixIns)
    }

    override fun findMixInClassFor(kClass: KClass<*>): KClass<*>? {
        return myOverrides?.findMixInClassFor(kClass) ?: myLocalMixIns?.get(ClassKey(kClass))
    }

    override fun hasMixIns(): Boolean {
        return myLocalMixIns != null || myOverrides != null && myOverrides.hasMixIns()
    }

    /*
     *******************************************************************************************************************
     * Other
     *******************************************************************************************************************
     */

    open fun localSize(): Int {
        return myLocalMixIns?.size ?: 0
    }

}