package org.cirjson.cirjackson.databind.util

import java.util.concurrent.atomic.AtomicInteger

/**
 * Simple identity value class that may be used as a Serializable key for entries that need to retain identity of some
 * kind, but where the actual appearance of id itself does not matter. Instances NEVER equal each other, only
 * themselves, even if generated ids might be the same (although they should not be).
 */
open class UniqueId protected constructor(prefix: String?) : Comparable<UniqueId> {

    protected val myId: String

    init {
        val id = ID_SEQ.getAndIncrement().toHexString(HexFormat.Default)
        myId = prefix?.let { it + id } ?: id
    }

    override fun toString(): String {
        return myId
    }

    override fun equals(other: Any?): Boolean {
        return this === other
    }

    override fun hashCode(): Int {
        return myId.hashCode()
    }

    override fun compareTo(other: UniqueId): Int {
        return myId.compareTo(other.myId)
    }

    companion object {

        private val ID_SEQ = AtomicInteger(4096)

        fun create(): UniqueId {
            return UniqueId(null)
        }

        fun create(prefix: String?): UniqueId {
            return UniqueId(prefix)
        }

    }

}