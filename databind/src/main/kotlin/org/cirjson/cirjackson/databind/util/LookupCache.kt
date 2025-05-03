package org.cirjson.cirjackson.databind.util

import org.cirjson.cirjackson.core.util.Snapshottable

/**
 * An interface describing the required API for the CirJackson-Databind Type cache.
 *
 * Note that while the interface itself does not specify synchronization requirements for implementations, specific use
 * cases do. Typically, implementations are expected to be thread-safe, that is, to handle synchronization.
 *
 * @see org.cirjson.cirjackson.databind.type.TypeFactory.withCache
 *
 * @see SimpleLookupCache
 */
interface LookupCache<K, V> : Snapshottable<LookupCache<K, V>> {

    /**
     * Method to apply operation on cache contents without exposing them.
     *
     * Default implementation throws [UnsupportedOperationException]. Implementations are required to override this
     * method.
     *
     * @param consumer Operation to apply on cache contents.
     *
     * @throws UnsupportedOperationException if implementation does not override this method.
     */
    @Throws(UnsupportedOperationException::class)
    fun contents(consumer: (K, V) -> Unit) {
        throw UnsupportedOperationException()
    }

    fun emptyCopy(): LookupCache<K, V>

    val size: Int

    /**
     * Returns the value associated with [key] (can return `null`)
     */
    operator fun get(key: K): V?

    operator fun set(key: K, value: V): V?

    fun setIfAbsent(key: K, value: V): V?

    /**
     * Method for removing all contents this cache has.
     */
    fun clear()

}