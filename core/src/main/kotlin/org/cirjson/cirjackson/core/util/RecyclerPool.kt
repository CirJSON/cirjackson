package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.util.RecyclerPool.*
import org.cirjson.cirjackson.core.util.RecyclerPool.BoundedPoolBase.Companion.DEFAULT_CAPACITY
import java.io.Serializable
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
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
interface RecyclerPool<P : WithPool<P>> : Serializable {

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
     * Method that should be called when previously acquired (see [acquireAndLinkPooled]) pooled value that is no longer
     * needed; this lets pool to take ownership for possible reuse.
     *
     * @param pooled Pooled instance to release back to pool
     */
    fun releasePooled(pooled: P)

    /**
     * Optional method that may allow dropping of all pooled Objects; mostly useful for unbounded pool implementations
     * that may retain significant memory and that may then be cleared regularly.
     *
     * @return `true` if pool supports operation and dropped all pooled Objects; `false` otherwise.
     */
    fun clear(): Boolean {
        return false
    }

    /**
     * Diagnostic method for obtaining an estimate of number of pooled items this pool contains, available for
     * recycling. Note that, in addition to this information possibly not being available (denoted by return value of
     * `-1`), even when available, this may be just an approximation.
     *
     * Default method implementation simply returns `-1` and is meant to be overridden by concrete subclasses.
     *
     * @return Number of pooled entries available from this pool, if available; `-1` if not.
     */
    fun pooledCount(): Int {
        return -1
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

        /**
         * Method called when this item is to be released back to the pool that owns it (if any)
         */
        fun releaseToPool()

    }

    /*
     *******************************************************************************************************************
     * Partial/base RecyclerPool implementations
     *******************************************************************************************************************
     */

    /**
     * Default [RecyclerPool] implementation that uses [ThreadLocal] for recycling instances. Instances are stored using
     * [java.lang.ref.SoftReference]s so that they may be Garbage Collected as needed by JVM.
     *
     * Note that this implementation may not work well on platforms where [java.lang.ref.SoftReference]s are not
     * well-supported (like Android), or on platforms where [java.lang.Thread]s are not long-living or reused (like
     * Project Loom).
     */
    abstract class ThreadLocalPoolBase<P : WithPool<P>> protected constructor() : RecyclerPool<P> {

        abstract override fun acquirePooled(): P

        override fun acquireAndLinkPooled(): P {
            return acquirePooled()
        }

        override fun releasePooled(pooled: P) {
            // no-op
        }

        override fun clear(): Boolean {
            return false
        }

        override fun pooledCount(): Int {
            return -1
        }

    }

    /**
     * [RecyclerPool] implementation that does not use any pool but simply creates new instances when necessary.
     */
    abstract class NonRecyclingPoolBase<P : WithPool<P>> : RecyclerPool<P> {

        abstract override fun acquirePooled(): P

        override fun acquireAndLinkPooled(): P {
            return acquirePooled()
        }

        override fun releasePooled(pooled: P) {
            // no-op
        }

        /**
         * Although no pooling occurs, we consider clearing to succeed, so always returns `true`.
         *
         * @return Always returns `true`
         */
        override fun clear(): Boolean {
            return true
        }

        override fun pooledCount(): Int {
            return 0
        }

    }

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

        protected open fun resolveToShared(shared: StatefulImplBase<P>): StatefulImplBase<P>? {
            return shared.takeIf { serialization == SERIALIZATION_SHARED }
        }

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

        override fun releasePooled(pooled: P) {
            pool.offerLast(pooled)
        }

        override fun pooledCount(): Int {
            return pool.size
        }

        override fun clear(): Boolean {
            pool.clear()
            return true
        }

    }

    /**
     * [RecyclerPool] implementation that uses a bounded queue ([ArrayBlockingQueue] for recycling instances. This is
     * "bounded" pool since it will never hold on to more pooled instances than its size configuration: the default size
     * is [DEFAULT_CAPACITY].
     */
    abstract class BoundedPoolBase<P : WithPool<P>> protected constructor(capacityAsId: Int) :
            StatefulImplBase<P>(capacityAsId) {

        val capacity = capacityAsId.takeIf { it > 0 } ?: DEFAULT_CAPACITY

        private val pool = ArrayBlockingQueue<P>(capacity)

        override fun acquirePooled(): P {
            return pool.poll() ?: createPooled()
        }

        override fun releasePooled(pooled: P) {
            pool.offer(pooled)
        }

        override fun pooledCount(): Int {
            return pool.size
        }

        override fun clear(): Boolean {
            pool.clear()
            return true
        }

        companion object {

            /**
             * Default capacity which limits number of items that are ever retained for reuse.
             */
            const val DEFAULT_CAPACITY = 100

        }

    }

}