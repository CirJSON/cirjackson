package org.cirjson.cirjackson.core.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Singleton class that adds a simple first-level cache in front of regular String.intern() functionality. This is done
 * as a minor performance optimization, to avoid calling native intern() method in cases where same String is being
 * interned multiple times.
 *
 * Note: that this class extends [ConcurrentHashMap] is an implementation detail -- no code should ever directly call ConcurrentHashMap methods.
 */
class InternCache private constructor() : ConcurrentHashMap<String, String>(DEFAULT_MAX_ENTRIES, 0.8f, 4) {

    /**
     * As minor optimization let's try to avoid "flush storms",
     * cases where multiple threads might try to concurrently
     * flush the map.
     */
    private val lock = ReentrantLock()

    fun intern(input: String): String {
        var result = this[input]

        if (result != null) {
            return result
        }

        if (size >= DEFAULT_MAX_ENTRIES) {
            lock.withLock {
                if (size >= DEFAULT_MAX_ENTRIES) {
                    clear()
                }
            }
        }

        result = input.intern()
        this[result] = result
        return result
    }

    companion object {

        /**
         * Size to use is somewhat arbitrary, so let's choose something that's
         * neither too small (low hit ratio) nor too large (waste of memory).
         *
         * One consideration is possible attack via colliding [String.hashCode];
         * because of this, limit to reasonably low setting.
         */
        private const val DEFAULT_MAX_ENTRIES = 200

        val INSTANCE = InternCache()

    }

}