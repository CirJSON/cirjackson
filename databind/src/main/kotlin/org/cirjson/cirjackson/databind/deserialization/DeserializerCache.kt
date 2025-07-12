package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.core.util.Snapshottable
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.util.LookupCache

class DeserializerCache(private val myCachedDeserializers: LookupCache<KotlinType, ValueDeserializer<Any>>) :
        Snapshottable<DeserializerCache> {

    override fun snapshot(): DeserializerCache {
        TODO("Not yet implemented")
    }

    companion object {

        const val DEFAULT_MAX_CACHE_SIZE = 1000

    }

}