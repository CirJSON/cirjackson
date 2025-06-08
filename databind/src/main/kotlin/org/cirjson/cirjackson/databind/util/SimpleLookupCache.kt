package org.cirjson.cirjackson.databind.util

import org.cirjson.cirjackson.databind.util.internal.PrivateMaxEntriesMap

/**
 * Synchronized cache with bounded size: used for reusing lookup values and lazily instantiated reusable items.
 *
 * Note that serialization behavior is such that contents are NOT serialized, on an assumption that all use cases are
 * for caching where persistence does not make sense. The only thing serialized is the initial and maximum size of the
 * contents.
 *
 * The implementation evicts the least recently used entry when max size is reached; this is implemented by the backing
 * `PrivateMaxEntriesMap` implementation. Implementation is thread-safe and does NOT require external synchronization.
 */
open class SimpleLookupCache<K, V>(protected val myInitialEntries: Int, protected val myMaxEntries: Int) :
        LookupCache<K, V> {

    protected val myMap = PrivateMaxEntriesMap.Builder<K, V>().initialCapacity(myInitialEntries)
            .maximumCapacity(myMaxEntries.toLong()).concurrencyLevel(4).build()

    /*
     *******************************************************************************************************************
     * Life-cycle
     *******************************************************************************************************************
     */

    override fun emptyCopy(): LookupCache<K, V> {
        return SimpleLookupCache(myInitialEntries, myMaxEntries)
    }

    override fun snapshot(): SimpleLookupCache<K, V> {
        return SimpleLookupCache(myInitialEntries, myMaxEntries)
    }

    /*
     *******************************************************************************************************************
     * Public API, basic lookup/additions
     *******************************************************************************************************************
     */

    override operator fun set(key: K, value: V): V? {
        return myMap.put(key, value)
    }

    override fun setIfAbsent(key: K, value: V): V? {
        return myMap.putIfAbsent(key, value)
    }

    override operator fun get(key: K): V? {
        return myMap[key]
    }

    override fun clear() {
        myMap.clear()
    }

    override val size: Int
        get() = myMap.size

    override fun contents(consumer: (K, V) -> Unit) {
        for ((key, value) in myMap) {
            consumer(key, value)
        }
    }

}