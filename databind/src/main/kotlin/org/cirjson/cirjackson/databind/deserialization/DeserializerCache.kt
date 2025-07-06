package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.core.util.Snapshottable

class DeserializerCache : Snapshottable<DeserializerCache> {

    override fun snapshot(): DeserializerCache {
        TODO("Not yet implemented")
    }

    companion object {

        const val DEFAULT_MAX_CACHE_SIZE = 1000

    }

}