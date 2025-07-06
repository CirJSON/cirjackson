package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.core.util.Snapshottable

class SerializerCache : Snapshottable<SerializerCache> {

    override fun snapshot(): SerializerCache {
        TODO("Not yet implemented")
    }

    companion object {

        const val DEFAULT_MAX_CACHE_SIZE = 4000

    }

}