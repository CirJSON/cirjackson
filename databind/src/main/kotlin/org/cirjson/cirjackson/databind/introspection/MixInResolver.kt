package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.core.util.Snapshottable
import kotlin.reflect.KClass

/**
 * Interface used for decoupling details of how mix-in annotation definitions are accessed (via this interface), and how
 * they are stored (defined by classes that implement the interface)
 */
interface MixInResolver : Snapshottable<MixInResolver> {

    /**
     * Method that will check if there are "mix-in" classes (with mix-in annotations) for given class
     */
    fun findMixInClassFor(kClass: KClass<*>): KClass<*>?

    /**
     * Method that may be called for optimization purposes, to see if calls to mix-in resolver may be avoided. Return
     * value of `true` means that it is possible that a mix-in class will be found; `false` that no mix-in will ever be
     * found. In the latter case, caller can avoid calls altogether.
     *
     * Note that the reason for "empty" resolvers is to use "null object" for simplifying calling code.
     *
     * @return `true`, if this resolver MAY have mix-ins to apply, `false` if not (it is "empty")
     */
    fun hasMixIns(): Boolean

    /**
     * Method called to create a new, non-shared copy, to be used by different `ObjectMapper` instance, and one that
     * should not be connected to this instance, if resolver has mutable state. If resolver is immutable, may simply
     * return `this`.
     */
    override fun snapshot(): MixInResolver
    
}