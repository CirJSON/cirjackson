package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.CirJsonFactory
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Set of [RecyclerPool] implementations to be used by the default CirJSON-backed [CirJsonFactory] for recycling
 * [BufferRecycler] containers.
 */
object CirJsonRecyclerPools {

    fun newConcurrentDequePool(): RecyclerPool<BufferRecycler> {
        return ConcurrentDequePool.construct()
    }

    /**
     * [RecyclerPool] implementation that uses [ConcurrentLinkedDeque] for recycling instances.
     *
     * Pool is unbounded: see [RecyclerPool] what this means.
     */
    class ConcurrentDequePool private constructor(serialization: Int) :
            RecyclerPool.ConcurrentDequePoolBase<BufferRecycler>(serialization) {

        companion object {

            fun construct(): ConcurrentDequePool {
                return ConcurrentDequePool(SERIALIZATION_NON_SHARED)
            }

        }

    }

}