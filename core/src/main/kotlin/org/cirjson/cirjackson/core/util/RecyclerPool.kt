package org.cirjson.cirjackson.core.util

import java.io.Serializable
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * API for object pools that control creation and possible reuse of objects that are costly to create (often things like
 * encoding/decoding buffers).
 *
 * Also contains partial (base) implementations for pools that use different strategies on retaining objects for reuse.
 *
 * Following implementations are included:
 *
 * * [NonRecyclingPoolBase] which does not retain or recycle anything and will always simply construct and return new
 * instance when `acquireBufferRecycler` is called
 * * [ThreadLocalPoolBase] which uses [ThreadLocal] to retain at most 1 object per [Thread].
 * * [BoundedPoolBase] is "bounded pool" and retains at most N objects (default value being
 * [BoundedPoolBase.DEFAULT_CAPACITY]) at any given time.
 *
 * Default implementations are also included as nested classes.
 *
 * @param P Type of Objects pool recycles
 */
interface RecyclerPool<P : RecyclerPool.WithPool<P>> : Serializable {

    /**
     * Method for subclasses to implement for actual acquire logic; called by [acquireAndLinkPooled].
     *
     * @return Instance acquired (pooled or just constructed)
     */
    fun acquirePooled(): P

    /**
     * Method called to acquire a Pooled value from this pool AND make sure it is linked back to this [RecyclerPool] as
     * necessary for it to be released (see [#releasePooled]) later after usage ends. Actual acquisition is done by a
     * call to [acquirePooled].
     *
     * Default implementation calls [acquirePooled] followed by a call to [WithPool.withPool].
     *
     * @return Pooled instance for caller to use; caller expected to call [releasePooled] after it is done using
     * instance.
     */
    fun acquireAndLinkPooled(): P {
        return acquirePooled().withPool(this)
    }

    /**
     * Simple add-on interface that poolable entities must implement.
     *
     * @param P Self type
     */
    interface WithPool<P : WithPool<P>> {

        /**
         * Method to call to add link from pooled item back to pool that handles it
         *
         * @param pool Pool that "owns" pooled item
         *
         * @return This item (for call chaining)
         */
        fun withPool(pool: RecyclerPool<P>): P

    }

    /*
     *******************************************************************************************************************
     * Partial/base RecyclerPool implementations
     *******************************************************************************************************************
     */

    /**
     * Intermediate base class for instances that are stateful and require special handling with respect to JDK
     * serialization, to retain "global" reference distinct from non-shared ones.
     *
     * @property serialization Value that indicates basic aspects of pool for JDK serialization; either marker for
     * shared/non-shared, or possibly bounded size; depends on subclass.
     */
    abstract class StatefulImplBase<P : WithPool<P>> protected constructor(private val serialization: Int) :
            RecyclerPool<P> {

        abstract fun createPooled(): P

        companion object {

            const val SERIALIZATION_SHARED = -1

            const val SERIALIZATION_NON_SHARED = 1

        }

    }

    /**
     * [RecyclerPool] implementation that uses [ConcurrentLinkedDeque] for recycling instances.
     *
     * Pool is unbounded: see [RecyclerPool] what this means.
     */
    abstract class ConcurrentDequePoolBase<P : WithPool<P>> protected constructor(serialization: Int) :
            StatefulImplBase<P>(serialization) {

        @Transient
        private val pool: Deque<P> = ConcurrentLinkedDeque()

        override fun acquirePooled(): P {
            return pool.pollFirst() ?: createPooled()
        }

    }

}