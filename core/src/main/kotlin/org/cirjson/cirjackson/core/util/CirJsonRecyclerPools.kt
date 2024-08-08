package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import java.lang.ref.SoftReference
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Set of [RecyclerPool] implementations to be used by the default CirJSON-backed [CirJsonFactory] for recycling
 * [BufferRecycler] containers.
 */
object CirJsonRecyclerPools {

    /**
     * Method to call to get the default recycler pool instance. This is same as calling [newConcurrentDequePool].
     *
     * @return the default [RecyclerPool] implementation to use if no specific implementation desired.
     */
    fun defaultPool(): RecyclerPool<BufferRecycler> {
        return newConcurrentDequePool()
    }

    /**
     * Accessor for getting the shared/global [ThreadLocalPool] instance (due to design, only one instance ever needed)
     *
     * @return Globally shared instance of [ThreadLocalPool]
     */
    fun threadLocalPool(): RecyclerPool<BufferRecycler> {
        return ThreadLocalPool.GLOBAL
    }

    /**
     * Accessor for getting the shared/global [NonRecyclingPool] instance (due to design, only one instance ever needed)
     *
     * @return Globally shared instance of [NonRecyclingPool].
     */
    fun nonRecyclingPool(): RecyclerPool<BufferRecycler> {
        return NonRecyclingPool.GLOBAL
    }

    /**
     * Accessor for getting the shared/global [ConcurrentDequePool] instance.
     *
     * @return Globally shared instance of [ConcurrentDequePool].
     */
    fun sharedConcurrentDequePool(): RecyclerPool<BufferRecycler> {
        return ConcurrentDequePool.GLOBAL
    }

    /**
     * Accessor for constructing a new, non-shared [ConcurrentDequePool] instance.
     *
     * @return New instance of [ConcurrentDequePool].
     */
    fun newConcurrentDequePool(): RecyclerPool<BufferRecycler> {
        return ConcurrentDequePool.construct()
    }

    /**
     * Accessor for getting the shared/global [BoundedPool] instance.
     *
     * @return Globally shared instance of [BoundedPool].
     */
    fun sharedBoundedPool(): RecyclerPool<BufferRecycler> {
        return BoundedPool.GLOBAL
    }

    /**
     * Accessor for constructing a new, non-shared [BoundedPool] instance.
     *
     * @param size Maximum number of values to pool
     *
     * @return New instance of [BoundedPool].
     */
    fun newBoundedPool(size: Int): RecyclerPool<BufferRecycler> {
        return BoundedPool.construct(size)
    }

    /*
     *******************************************************************************************************************
     * Concrete RecyclerPool implementations for recycling BufferRecyclers
     *******************************************************************************************************************
     */

    /**
     * [ThreadLocal]-based [RecyclerPool] implementation used for recycling [BufferRecycler] instances: see
     * [RecyclerPool.ThreadLocalPoolBase] for full explanation of functioning.
     */
    class ThreadLocalPool private constructor() : RecyclerPool.ThreadLocalPoolBase<BufferRecycler>() {

        override fun acquirePooled(): BufferRecycler {
            return ourRecyclerRef.get()?.get() ?: BufferRecycler().also { ourRecyclerRef.set(SoftReference(it)) }
        }

        companion object {

            val GLOBAL = ThreadLocalPool()

            /**
             * This `ThreadLocal` contains a [SoftReference] to a [BufferRecycler] used to provide a low-cost buffer
             * recycling between reader and writer instances.
             */
            val ourRecyclerRef = ThreadLocal<SoftReference<BufferRecycler>>()

        }

    }

    /**
     * Dummy [RecyclerPool] implementation that does not recycle anything but simply creates new instances when asked to
     * acquire items.
     */
    open class NonRecyclingPool protected constructor() : RecyclerPool.NonRecyclingPoolBase<BufferRecycler>() {

        override fun acquirePooled(): BufferRecycler {
            return BufferRecycler()
        }

        companion object {

            val GLOBAL = NonRecyclingPool()

        }

    }

    /**
     * [RecyclerPool] implementation that uses [ConcurrentLinkedDeque] for recycling instances.
     *
     * Pool is unbounded: see [RecyclerPool] what this means.
     */
    open class ConcurrentDequePool protected constructor(serialization: Int) :
            RecyclerPool.ConcurrentDequePoolBase<BufferRecycler>(serialization) {

        override fun createPooled(): BufferRecycler {
            return BufferRecycler()
        }

        companion object {

            val GLOBAL = ConcurrentDequePool(SERIALIZATION_SHARED)

            fun construct(): ConcurrentDequePool {
                return ConcurrentDequePool(SERIALIZATION_NON_SHARED)
            }

        }

    }

    /**
     * [RecyclerPool] implementation that uses a bounded queue ([ArrayBlockingQueue]) for recycling instances. This is a
     * "bounded" pool since it will never hold on to more [BufferRecycler] instances than its size configuration: the
     * default size is [RecyclerPool.BoundedPoolBase.DEFAULT_CAPACITY].
     */
    open class BoundedPool protected constructor(capacityAsId: Int) :
            RecyclerPool.BoundedPoolBase<BufferRecycler>(capacityAsId) {

        override fun createPooled(): BufferRecycler {
            return BufferRecycler()
        }

        companion object {

            val GLOBAL = BoundedPool(SERIALIZATION_SHARED)

            fun construct(capacityAsId: Int): BoundedPool {
                if (capacityAsId <= 0) {
                    throw IllegalArgumentException("capacityAsId must be greater than 0, was $capacityAsId")
                }

                return BoundedPool(capacityAsId)
            }

        }

    }

}