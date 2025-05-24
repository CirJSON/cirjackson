package org.cirjson.cirjackson.databind.util

/**
 * Specialized lookup class that implements functionality similar to [Map], but for the special case of key always being
 * [String] and using a more compact (and memory-access friendly) hashing scheme. The assumption is also that keys are
 * typically `intern()`ed.
 *
 * Generics are not used to avoid bridge methods and since these maps are not exposed as part of external API.
 */
class CompactStringObjectMap private constructor(private val myHashMask: Int, private val mySpillCount: Int,
        private val myHashArea: Array<Any?>) {

    fun find(key: String): Any? {
        val slot = key.hashCode() and myHashMask
        val index = slot shl 1
        val match = myHashArea[index]

        if (key == match) {
            return myHashArea[index + 1]
        }

        return find(key, slot, match)
    }

    private fun find(key: String, slot: Int, match: Any?): Any? {
        match ?: return null
        val hashSize = myHashMask + 1
        val index = hashSize + (slot shr 1) shl 1
        var realMatch = myHashArea[index]

        if (key == realMatch) {
            return myHashArea[index + 1]
        }

        realMatch ?: return null

        var i = hashSize + (hashSize shr 1) shl 1
        val end = i + mySpillCount

        while (i < end) {
            realMatch = myHashArea[i]

            if (key == realMatch) {
                return myHashArea[i + 1]
            }

            i += 2
        }

        return null
    }

    fun findCaseInsensitive(key: String): Any? {
        for (i in IntProgression.fromClosedRange(0, myHashArea.size - 1, 2)) {
            val key2 = myHashArea[i] as? String ?: continue

            if (key.equals(key2, ignoreCase = true)) {
                return myHashArea[i + 1]
            }
        }

        return null
    }

    fun keys(): List<String> {
        val end = myHashArea.size
        val keys = ArrayList<String>(end shr 2)

        for (i in IntProgression.fromClosedRange(0, end - 1, 2)) {
            val key = myHashArea[i] as? String ?: continue
            keys.add(key)
        }

        return keys
    }

    companion object {

        private val EMPTY = CompactStringObjectMap(1, 0, arrayOfNulls(4))

        fun construct(all: Map<String?, *>): CompactStringObjectMap {
            if (all.isEmpty()) {
                return EMPTY
            }

            val size = findSize(all.size)
            val mask = size - 1
            val allocated = (size + (size shr 1)) * 2
            var hashArea = arrayOfNulls<Any>(allocated)
            var spillCount = 0

            for (entry in all) {
                val key = entry.key ?: continue
                val slot = key.hashCode() and mask
                var index = slot * 2

                if (hashArea[index] != null) {
                    index = size + (slot shr 1) shl 1

                    if (hashArea[index] != null) {
                        index = (size + (size shr 1) shl 1) + spillCount
                        spillCount += 2

                        if (index >= hashArea.size) {
                            hashArea = hashArea.copyOf(hashArea.size + 4)
                        }
                    }
                }

                hashArea[index] = key
                hashArea[index + 1] = entry.value
            }

            return CompactStringObjectMap(mask, spillCount, hashArea)
        }

        private fun findSize(size: Int): Int {
            if (size <= 5) {
                return 8
            }

            if (size <= 12) {
                return 16
            }

            val needed = size + (size shr 2)

            var result = 32

            while (result < needed) {
                result += result
            }

            return result
        }

    }

}