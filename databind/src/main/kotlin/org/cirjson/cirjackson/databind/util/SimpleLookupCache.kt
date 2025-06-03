package org.cirjson.cirjackson.databind.util

open class SimpleLookupCache<K, V>(protected val myInitialEntries: Int, protected val myMaxEntries: Int) :
        LookupCache<K, V> {

    /*
     *******************************************************************************************************************
     * Life-cycle
     *******************************************************************************************************************
     */

    override fun emptyCopy(): LookupCache<K, V> {
        TODO("Not yet implemented")
    }

    override fun snapshot(): SimpleLookupCache<K, V> {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, basic lookup/additions
     *******************************************************************************************************************
     */

    override operator fun set(key: K, value: V): V? {
        TODO("Not yet implemented")
    }

    override fun setIfAbsent(key: K, value: V): V? {
        TODO("Not yet implemented")
    }

    override operator fun get(key: K): V? {
        TODO("Not yet implemented")
    }

    override fun clear() {
        TODO("Not yet implemented")
    }

    override val size: Int
        get() = TODO("Not yet implemented")

}