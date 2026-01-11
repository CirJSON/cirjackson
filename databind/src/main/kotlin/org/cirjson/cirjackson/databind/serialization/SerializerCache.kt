package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.core.util.Snapshottable
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.serialization.implementation.ReadOnlyClassToSerializerMap
import org.cirjson.cirjackson.databind.util.LookupCache
import org.cirjson.cirjackson.databind.util.TypeKey

class SerializerCache : Snapshottable<SerializerCache> {

    constructor(cache: LookupCache<TypeKey, ValueSerializer<Any>>) {
    }

    override fun snapshot(): SerializerCache {
        TODO("Not yet implemented")
    }

    val readOnlyLookupMap: ReadOnlyClassToSerializerMap
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Lookup methods for accessing shared (slow) cache
     *******************************************************************************************************************
     */

    val size: Int
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Methods for adding shared serializer instances
     *******************************************************************************************************************
     */

    @Synchronized
    fun flush() {
        TODO("Not yet implemented")
    }

    companion object {

        const val DEFAULT_MAX_CACHE_SIZE = 4000

    }

}