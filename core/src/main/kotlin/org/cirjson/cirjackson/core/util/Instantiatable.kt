package org.cirjson.cirjackson.core.util

/**
 * Add-on interface used to indicate things that may be "blueprint" objects which can not be used as is, but are used
 * for creating usable per-process (serialization, deserialization) instances, using [createInstance] method.
 *
 * Note that some implementations may choose to implement [createInstance] by simply returning 'this': this is
 * acceptable if instances are stateless.
 *
 * @param T The type of the object getting created.
 *
 * @see DefaultPrettyPrinter
 */
fun interface Instantiatable<T> {

    /**
     * Method called to ensure that we have a non-blueprint object to use; it is either this object (if stateless), or a
     * newly created object with separate state.
     *
     * @return Actual instance to use
     */
    fun createInstance(): T

}