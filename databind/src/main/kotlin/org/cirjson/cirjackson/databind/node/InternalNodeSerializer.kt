package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.databind.cirjson.CirJsonMapper

/**
 * Helper object used to support non-recursive serialization for use by [BaseCirJsonNode.toString] (and related) method.
 */
object InternalNodeSerializer {

    private val MAPPER = CirJsonMapper.shared()

    private val COMPACT_WRITER = MAPPER.writer()

    private val PRETTY_WRITER = MAPPER.writerWithDefaultPrettyPrinter()

    fun toString(root: BaseCirJsonNode): String {
        TODO("Not yet implemented")
    }

    fun toPrettyString(root: BaseCirJsonNode): String {
        TODO("Not yet implemented")
    }

}