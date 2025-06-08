package org.cirjson.cirjackson.databind.util.internal

import org.cirjson.cirjackson.databind.util.internal.PrivateMaxEntriesMap.Builder
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.abs
import kotlin.math.min

/**
 * A hash table supporting full concurrency of retrievals, adjustable expected concurrency for updates, and a maximum
 * capacity to bound the map by. This implementation differs from [ConcurrentHashMap] in that it maintains a page
 * replacement algorithm that is used to evict an entry when the map has exceeded its capacity. Unlike the `Java
 * Collections Framework`, this map does not have a publicly visible constructor and instances are created through a
 * [Builder].
 *
 * An entry is evicted from the map when the entry size exceeds its `maximum capacity` threshold.
 *
 * An `EvictionListener` may be supplied for notification when an entry is evicted from the map. This listener is
 * invoked on a caller's thread and will not block other threads from operating on the map. An implementation should be
 * aware that the caller's thread will not expect long execution times or failures as a side effect of the listener
 * being notified. Execution safety and a fast turn around time can be achieved by performing the operation
 * asynchronously, such as by submitting a task to an [java.util.concurrent.ExecutorService].
 *
 * The `concurrency level` determines the number of threads that can concurrently modify the table. Using a
 * significantly higher or lower value than needed can waste space or lead to thread contention, but an estimate within
 * an order of magnitude of the ideal value does not usually have a noticeable impact. Because placement in hash tables
 * is essentially random, the actual concurrency will vary.
 *
 * This class and its views and iterators implement all the *optional* methods of the [Map] and [Iterator] interfaces.
 *
 * Like [Hashtable] but unlike [HashMap], this class does *not* allow `null` to be used as a key or value. Unlike
 * [LinkedHashMap], this class does *not* provide predictable iteration order. A snapshot of the keys and entries may be
 * obtained in ascending and descending order of retention.
 *
 * @param K the type of keys maintained by this map
 *
 * @param V the type of mapped values
 */
class PrivateMaxEntriesMap<K, V> private constructor(builder: Builder<K, V>) : AbstractMutableMap<K, V>(),
        ConcurrentMap<K, V> {

    private val myConcurrencyLevel = builder.concurrencyLevel

    private val myData: ConcurrentMap<K, Node<K, V>> =
            ConcurrentHashMap(builder.initialCapacity, 0.75f, myConcurrencyLevel)

    private val myReadBufferReadCount = LongArray(NUMBER_OF_READ_BUFFERS)

    private val myEvictionDeque = LinkedDeque<Node<K, V>>()

    private val myWeightedSize = AtomicLong()

    private val myCapacity = AtomicLong(min(builder.maximumCapacity, MAXIMUM_CAPACITY))

    private val myEvictionLock: Lock = ReentrantLock()

    private val myWriteBuffer: Queue<Runnable> = ConcurrentLinkedQueue()

    private val myReadBufferWriteCount = AtomicLongArray(NUMBER_OF_READ_BUFFERS)

    private val myReadBufferDrainAtWriteCount = AtomicLongArray(NUMBER_OF_READ_BUFFERS)

    private val myReadBuffers = AtomicReferenceArray<Node<K, V>>(NUMBER_OF_READ_BUFFERS * READ_BUFFER_SIZE)

    private val myDrainStatus = AtomicReference(DrainStatus.IDLE)

    private var myKeys: MutableSet<K>? = null

    private var myValues: MutableCollection<V>? = null

    private var myEntries: MutableSet<MutableMap.MutableEntry<K, V>>? = null

    var capacity: Long
        get() = myCapacity.get()
        set(value) {
            checkArgument(capacity >= 0)

            myEvictionLock.withLock {
                myCapacity.lazySet(min(value, MAXIMUM_CAPACITY))
                drainBuffers()
                evict()
            }
        }

    /**
     * Determines whether the map has exceeded its capacity.
     */
    private fun hasOverflowed(): Boolean {
        return myWeightedSize.get() > myCapacity.get()
    }

    /**
     * Evicts entries from the map while it exceeds the capacity and appends evicted entries to the notification queue
     * for processing.
     */
    private fun evict() {
        while (hasOverflowed()) {
            val node = myEvictionDeque.poll() ?: return
            myData.remove(node.key, node)
            makeDead(node)
        }
    }

    /**
     * Performs the post-processing work required after a read.
     *
     * @param node the entry in the page replacement policy
     */
    private fun afterRead(node: Node<K, V>) {
        val bufferIndex = readBufferIndex()
        val writeCount = recordRead(bufferIndex, node)
        drainOnReadIfNeeded(bufferIndex, writeCount)
    }

    /**
     * Records a read in the buffer and return its write count.
     *
     * @param bufferIndex the index to the chosen read buffer
     *
     * @param node the entry in the page replacement policy
     *
     * @return the number of writes on the chosen read buffer
     */
    private fun recordRead(bufferIndex: Int, node: Node<K, V>): Long {
        val writeCount = myReadBufferWriteCount[bufferIndex]
        myReadBufferWriteCount.lazySet(bufferIndex, writeCount + 1)
        val index = (writeCount and READ_BUFFER_INDEX_MASK.toLong()).toInt()
        myReadBuffers.lazySet(readBufferIndex(bufferIndex, index), node)
        return writeCount
    }

    /**
     * Attempts to drain the buffers if it is determined to be needed when post-processing a read.
     *
     * @param bufferIndex the index to the chosen read buffer
     *
     * @param writeCount the number of writes on the chosen read buffer
     */
    private fun drainOnReadIfNeeded(bufferIndex: Int, writeCount: Long) {
        val pending = writeCount - myReadBufferDrainAtWriteCount[bufferIndex]
        val delayable = pending < READ_BUFFER_THRESHOLD
        val status = myDrainStatus.get()

        if (status.shouldDrainBuffers(delayable)) {
            tryToDrainBuffers()
        }
    }

    /**
     * Performs the post-processing work required after write.
     *
     * @param task the pending operation to be applied
     */
    private fun afterWrite(task: Runnable) {
        myWriteBuffer.add(task)
        myDrainStatus.lazySet(DrainStatus.REQUIRED)
        tryToDrainBuffers()
    }

    /**
     * Attempts to acquire the eviction lock and apply the pending operations, up to the amortized threshold, to the
     * page replacement policy.
     */
    private fun tryToDrainBuffers() {
        if (myEvictionLock.tryLock()) {
            try {
                myDrainStatus.lazySet(DrainStatus.PROCESSING)
                drainBuffers()
            } finally {
                myDrainStatus.compareAndSet(DrainStatus.PROCESSING, DrainStatus.IDLE)
                myEvictionLock.unlock()
            }
        }
    }

    /**
     * Drains the read and write buffers up to an amortized threshold.
     */
    internal fun drainBuffers() {
        drainReadBuffers()
        drainWriteBuffers()
    }

    /**
     * Drains the read buffers, each up to an amortized threshold.
     */
    private fun drainReadBuffers() {
        val start = Thread.currentThread().id.toInt()
        val end = start + NUMBER_OF_READ_BUFFERS

        for (i in start..<end) {
            drainReadBuffer(i and READ_BUFFERS_MASK)
        }
    }

    /**
     * Drains the read buffer up to an amortized threshold.
     */
    private fun drainReadBuffer(bufferIndex: Int) {
        val writeCount = myReadBufferWriteCount[bufferIndex]

        for (i in 0..<READ_BUFFER_DRAIN_THRESHOLD) {
            val index = (myReadBufferReadCount[bufferIndex] and READ_BUFFER_INDEX_MASK.toLong()).toInt()
            val arrayIndex = readBufferIndex(bufferIndex, index)
            val node = myReadBuffers[arrayIndex] ?: break
            myReadBuffers.lazySet(arrayIndex, null)
            applyRead(node)
            myReadBufferReadCount[bufferIndex]++
        }

        myReadBufferDrainAtWriteCount.lazySet(bufferIndex, writeCount)
    }

    /**
     * Updates the node's location in the page replacement policy.
     */
    private fun applyRead(node: Node<K, V>) {
        if (myEvictionDeque.contains(node)) {
            myEvictionDeque.moveToBack(node)
        }
    }

    /**
     * Drains the write buffers, each up to an amortized threshold.
     */
    private fun drainWriteBuffers() {
        for (i in 0..<WRITE_BUFFER_DRAIN_THRESHOLD) {
            val task = myWriteBuffer.poll() ?: break
            task.run()
        }
    }

    /**
     * Attempts to transition the node from the `alive` state to the `retired` state.
     *
     * @param node the entry in the page replacement policy
     *
     * @param expect the expected weighted value
     *
     * @return if successful
     */
    private fun tryToRetire(node: Node<K, V>, expect: WeightedValue<V>): Boolean {
        if (!expect.isAlive) {
            return false
        }

        val retired = WeightedValue(expect.value, -expect.weight)
        return node.compareAndSet(expect, retired)
    }

    /**
     * Atomically transitions the node from the `alive` state to the `retired` state, if a valid transition.
     *
     * @param node the entry in the page replacement policy
     */
    private fun makeRetired(node: Node<K, V>) {
        while (true) {
            val current = node.get()

            if (!current.isAlive) {
                return
            }

            val retired = WeightedValue(current.value, -current.weight)

            if (node.compareAndSet(current, retired)) {
                return
            }
        }
    }

    /**
     * Atomically transitions the node to the `dead` state and decrements the `weightedSize`.
     *
     * @param node the entry in the page replacement policy
     */
    private fun makeDead(node: Node<K, V>) {
        while (true) {
            val current = node.get()
            val dead = WeightedValue(current.value, 0)

            if (node.compareAndSet(current, dead)) {
                myWeightedSize.lazySet(myWeightedSize.get() - abs(current.weight))
                return
            }
        }
    }

    /**
     * Adds the node to the page replacement policy.
     */
    private inner class AddTask(private val myNode: Node<K, V>, private val myWeight: Int) : Runnable {

        override fun run() {
            myWeightedSize.lazySet(myWeightedSize.get() + myWeight)

            if (myNode.get().isAlive) {
                myEvictionDeque.add(myNode)
                evict()
            }
        }

    }

    /**
     * Removes a node from the page replacement policy.
     */
    private inner class RemovalTask(private val myNode: Node<K, V>) : Runnable {

        override fun run() {
            myEvictionDeque.remove(myNode)
            makeDead(myNode)
        }

    }

    /**
     * Updates the weighted size and evicts an entry on overflow.
     */
    private inner class UpdateTask(private val myNode: Node<K, V>, private val myWeightDifference: Int) : Runnable {

        override fun run() {
            myWeightedSize.lazySet(myWeightedSize.get() + myWeightDifference)
            applyRead(myNode)
            evict()
        }

    }

    override fun isEmpty(): Boolean {
        return myData.isEmpty()
    }

    override val size: Int
        get() = myData.size

    override fun clear() {
        myEvictionLock.withLock {
            var node = myEvictionDeque.poll()

            while (node != null) {
                myData.remove(node.key, node)
                makeDead(node)
                node = myEvictionDeque.poll()
            }

            for (i in 0..<myReadBuffers.length()) {
                myReadBuffers.lazySet(i, null)
            }

            var task = myWriteBuffer.poll()

            while (task != null) {
                task.run()
                task = myWriteBuffer.poll()
            }
        }
    }

    override fun containsKey(key: K): Boolean {
        return myData.containsKey(key)
    }

    override fun containsValue(value: V): Boolean {
        for (node in myData.values) {
            if (node.value == value) {
                return true
            }
        }

        return false
    }

    override fun get(key: K): V? {
        val node = myData[key] ?: return null
        afterRead(node)
        return node.value
    }

    override fun put(key: K, value: V): V? {
        return put(key, value, false)
    }

    override fun putIfAbsent(key: K, value: V): V? {
        return put(key, value, true)
    }

    /**
     * Adds a node to the list and the data store. If an existing node is found, then its value is updated if allowed.
     *
     * @param key key with which the specified value is to be associated
     *
     * @param value value to be associated with the specified key
     *
     * @param onlyIfAbsent a write is performed only if the key is not already associated with a value
     *
     * @return the prior value in the data store or `null` if no mapping was found
     */
    private fun put(key: K, value: V, onlyIfAbsent: Boolean): V? {
        val weight = 1
        val weightedValue = WeightedValue(value, weight)
        val node = Node(key, weightedValue)

        while (true) {
            val prior = myData.putIfAbsent(node.key, node)

            if (prior == null) {
                afterWrite(AddTask(node, weight))
                return null
            } else if (onlyIfAbsent) {
                applyRead(prior)
                return prior.value
            }

            while (true) {
                val oldWeightedValue = prior.get()

                if (!oldWeightedValue.isAlive) {
                    break
                }

                if (handlePrior(prior, weightedValue, oldWeightedValue)) {
                    return oldWeightedValue.value
                }
            }
        }
    }

    private fun handlePrior(prior: Node<K, V>, weightedValue: WeightedValue<V>,
            oldWeightedValue: WeightedValue<V>): Boolean {
        if (!prior.compareAndSet(oldWeightedValue, weightedValue)) {
            return false
        }

        val weightedDifference = 1 - oldWeightedValue.weight

        if (weightedDifference == 0) {
            afterRead(prior)
        } else {
            afterWrite(UpdateTask(prior, weightedDifference))
        }

        return true
    }

    override fun remove(key: K): V? {
        val node = myData.remove(key) ?: return null
        makeRetired(node)
        afterWrite(RemovalTask(node))
        return node.value
    }

    override fun remove(key: K, value: V): Boolean {
        val node = myData.remove(key) ?: return false

        var weightedValue = node.get()

        while (true) {
            if (!weightedValue.contains(value)) {
                return false
            }

            if (tryToRetire(node, weightedValue)) {
                if (myData.remove(key, node)) {
                    afterWrite(RemovalTask(node))
                    return true
                }
            } else {
                weightedValue = node.get()

                if (weightedValue.isAlive) {
                    continue
                }
            }
        }
    }

    override fun replace(key: K, value: V): V? {
        val weight = 1
        val weightedValue = WeightedValue(value, weight)
        val node = myData[key] ?: return null

        while (true) {
            val oldWeightedValue = node.get()

            if (!oldWeightedValue.isAlive) {
                return null
            }

            if (handlePrior(node, weightedValue, oldWeightedValue)) {
                return oldWeightedValue.value
            }
        }
    }

    override fun replace(key: K, oldValue: V, newValue: V): Boolean {
        val weight = 1
        val newWeightedValue = WeightedValue(newValue, weight)
        val node = myData[key] ?: return false

        while (true) {
            val weightedValue = node.get()

            if (!weightedValue.isAlive || !weightedValue.contains(oldValue)) {
                return false
            }

            if (node.compareAndSet(weightedValue, newWeightedValue)) {
                val weightedDifference = weight - weightedValue.weight

                if (weightedDifference == 0) {
                    afterRead(node)
                } else {
                    afterWrite(UpdateTask(node, weightedDifference))
                }

                return true
            }
        }
    }

    override val keys: MutableSet<K>
        get() {
            if (myKeys == null) {
                myKeys = Keys()
            }

            return myKeys!!
        }

    override val values: MutableCollection<V>
        get() {
            if (myValues == null) {
                myValues = Values()
            }

            return myValues!!
        }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() {
            if (myEntries == null) {
                myEntries = Entries()
            }

            return myEntries!!
        }

    /**
     * The draining status of the buffers.
     */
    private enum class DrainStatus {

        /**
         * A drain is not taking place.
         */
        IDLE {
            override fun shouldDrainBuffers(delayable: Boolean): Boolean {
                return !delayable;
            }
        },

        /**
         * A drain is required due to a pending write modification.
         */
        REQUIRED {
            override fun shouldDrainBuffers(delayable: Boolean): Boolean {
                return true;
            }
        },

        /**
         * A drain is in progress.
         */
        PROCESSING {
            override fun shouldDrainBuffers(delayable: Boolean): Boolean {
                return false;
            }
        };

        /**
         * Determines whether the buffers should be drained.
         *
         * @param delayable if a drain should be delayed until required
         *
         * @return if a drain should be attempted
         */
        abstract fun shouldDrainBuffers(delayable: Boolean): Boolean

    }

    /**
     * A value, its weight, and the entry's status.
     */
    private data class WeightedValue<V>(val value: V, val weight: Int) {

        fun contains(o: Any?): Boolean {
            return value == o
        }

        /**
         * If the entry is available in the hash-table and page replacement policy.
         */
        val isAlive: Boolean
            get() = weight > 0

    }

    private class Node<K, V>(val key: K, weightedValue: WeightedValue<V>) :
            AtomicReference<WeightedValue<V>>(weightedValue), Linked<Node<K, V>> {

        override var previous: Node<K, V>? = null

        override var next: Node<K, V>? = null

        /**
         * Retrieves the value held by the current `WeightedValue`.
         */
        val value: V
            get() = get().value

    }

    /**
     * An adapter to safely externalize the keys.
     */
    private inner class Keys : AbstractMutableSet<K>() {

        override fun add(element: K): Boolean {
            throw UnsupportedOperationException()
        }

        override val size: Int
            get() = this@PrivateMaxEntriesMap.size

        override fun clear() {
            this@PrivateMaxEntriesMap.clear()
        }

        override fun iterator(): MutableIterator<K> {
            return KeysIterator()
        }

        override fun remove(element: K): Boolean {
            return this@PrivateMaxEntriesMap.remove(element) != null
        }

        override fun toArray(): Array<Any?> {
            return myData.keys.toTypedArray()
        }

    }

    /**
     * An adapter to safely externalize the key iterator.
     */
    private inner class KeysIterator : MutableIterator<K> {

        private val myIterator = myData.keys.iterator()

        private var myCurrent: K? = null

        override fun next(): K {
            myCurrent = myIterator.next()
            return myCurrent!!
        }

        override fun hasNext(): Boolean {
            return myIterator.hasNext()
        }

        override fun remove() {
            checkState(myCurrent != null)
            this@PrivateMaxEntriesMap.remove(myCurrent!!)
            myCurrent = null
        }

    }

    /**
     * An adapter to safely externalize the values.
     */
    private inner class Values : AbstractMutableCollection<V>() {

        override fun add(element: V): Boolean {
            throw UnsupportedOperationException()
        }

        override val size: Int
            get() = this@PrivateMaxEntriesMap.size

        override fun clear() {
            this@PrivateMaxEntriesMap.clear()
        }

        override fun iterator(): MutableIterator<V> {
            return ValuesIterator()
        }

        override fun contains(element: V): Boolean {
            return containsValue(element)
        }

    }

    /**
     * An adapter to safely externalize the value iterator.
     */
    private inner class ValuesIterator : MutableIterator<V> {

        private val myIterator = myData.values.iterator()

        private var myCurrent: Node<K, V>? = null

        override fun next(): V {
            myCurrent = myIterator.next()
            return myCurrent!!.value
        }

        override fun hasNext(): Boolean {
            return myIterator.hasNext()
        }

        override fun remove() {
            checkState(myCurrent != null)
            this@PrivateMaxEntriesMap.remove(myCurrent!!.key)
            myCurrent = null
        }

    }

    /**
     * An adapter to safely externalize the entries.
     */
    private inner class Entries : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {

        override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
            throw UnsupportedOperationException("ConcurrentLinkedHashMap does not allow add to be called on entrySet()")
        }

        override val size: Int
            get() = this@PrivateMaxEntriesMap.size

        override fun clear() {
            this@PrivateMaxEntriesMap.clear()
        }

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
            return EntriesIterator()
        }

        override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean {
            val node = myData[element.key]
            return node != null && node.value == element.value
        }

        override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
            return remove(element.key, element.value)
        }

    }

    /**
     * An adapter to safely externalize the entry iterator.
     */
    private inner class EntriesIterator : MutableIterator<MutableMap.MutableEntry<K, V>> {

        private val myIterator = myData.values.iterator()

        private var myCurrent: Node<K, V>? = null

        override fun next(): MutableMap.MutableEntry<K, V> {
            myCurrent = myIterator.next()
            return WriteThroughEntry(myCurrent!!)
        }

        override fun hasNext(): Boolean {
            return myIterator.hasNext()
        }

        override fun remove() {
            checkState(myCurrent != null)
            this@PrivateMaxEntriesMap.remove(myCurrent!!.key)
            myCurrent = null
        }

    }

    /**
     * An entry that allows updates to write through to the map.
     */
    private inner class WriteThroughEntry(node: Node<K, V>) : SimpleEntry<K, V>(node.key, node.value) {

        override fun setValue(newValue: V): V {
            put(key, newValue)
            return super.setValue(newValue)
        }

    }

    /**
     * A builder that creates {@link PrivateMaxEntriesMap} instances. It
     * provides a flexible approach for constructing customized instances with
     * a named parameter syntax. It can be used in the following manner:
     * ```
     * val graph: ConcurrentMap<Vertex, Set<Edge>> = new Builder<Vertex, Set<Edge>>()
     *     .maximumCapacity(5000L)
     *     .build();
     * ```
     */
    class Builder<K, V> {

        var concurrencyLevel = DEFAULT_CONCURRENCY_LEVEL
            private set

        var initialCapacity = DEFAULT_INITIAL_CAPACITY
            private set

        var maximumCapacity = -1L
            private set

        /**
         * Specifies the estimated number of concurrently updating threads. The implementation performs internal sizing
         * to try to accommodate this many threads (default `16`).
         *
         * @param concurrencyLevel the estimated number of concurrently updating threads
         *
         * @throws IllegalArgumentException if the concurrencyLevel is less than or equal to zero
         */
        fun concurrencyLevel(concurrencyLevel: Int): Builder<K, V> {
            checkArgument(concurrencyLevel > 0)
            this.concurrencyLevel = concurrencyLevel
            return this
        }

        /**
         * Specifies the initial capacity of the hash table (default `16`). This is the number of key-value pairs that
         * the hash table can hold before a resize operation is required.
         *
         * @param initialCapacity the initial capacity used to size the hash table to accommodate this many entries.
         *
         * @throws IllegalArgumentException if the initialCapacity is negative
         */
        fun initialCapacity(initialCapacity: Int): Builder<K, V> {
            checkArgument(initialCapacity >= 0)
            this.initialCapacity = initialCapacity
            return this
        }

        /**
         * Specifies the maximum capacity to coerce the map to and may exceed it temporarily.
         *
         * @param maximumCapacity the threshold to bound the map by
         *
         * @throws IllegalArgumentException if the maximumCapacity is negative
         */
        fun maximumCapacity(maximumCapacity: Long): Builder<K, V> {
            checkArgument(maximumCapacity >= 0)
            this.maximumCapacity = maximumCapacity
            return this
        }

        fun build(): PrivateMaxEntriesMap<K, V> {
            checkState(maximumCapacity >= 0)
            return PrivateMaxEntriesMap(this)
        }

        companion object {

            private const val DEFAULT_CONCURRENCY_LEVEL = 16

            private const val DEFAULT_INITIAL_CAPACITY = 16

        }

    }

    companion object {

        /**
         * The number of CPUs
         */
        private val N_CPU = Runtime.getRuntime().availableProcessors()

        /**
         * The maximum capacity of the map.
         */
        private const val MAXIMUM_CAPACITY = Long.MAX_VALUE - Int.MAX_VALUE

        /**
         * The number of read buffers to use.
         */
        private val NUMBER_OF_READ_BUFFERS = min(4, ceilingNextPowerOfTwo(N_CPU))

        /**
         * Mask value for indexing into the read buffers.
         */
        private val READ_BUFFERS_MASK = NUMBER_OF_READ_BUFFERS - 1

        /**
         * The number of pending read operations before attempting to drain.
         */
        private const val READ_BUFFER_THRESHOLD = 4

        /**
         * The maximum number of read operations to perform per amortized drain.
         */
        private const val READ_BUFFER_DRAIN_THRESHOLD = 2 * READ_BUFFER_THRESHOLD

        /**
         * The maximum number of pending reads per buffer.
         */
        private const val READ_BUFFER_SIZE = 2 * READ_BUFFER_DRAIN_THRESHOLD

        /**
         * Mask value for indexing into the read buffer.
         */
        private const val READ_BUFFER_INDEX_MASK = READ_BUFFER_SIZE - 1

        /**
         * The maximum number of write operations to perform per amortized drain.
         */
        private const val WRITE_BUFFER_DRAIN_THRESHOLD = 16

        @Suppress("SameParameterValue")
        private fun ceilingNextPowerOfTwo(i: Int): Int {
            return 1 shl Int.SIZE_BITS - (i - 1).countLeadingZeroBits()
        }

        private fun readBufferIndex(bufferIndex: Int, entryIndex: Int): Int {
            return READ_BUFFER_SIZE * bufferIndex + entryIndex
        }

        /**
         * Returns the index to the read buffer to record into.
         */
        private fun readBufferIndex(): Int {
            return Thread.currentThread().id.toInt() and READ_BUFFERS_MASK
        }

        /**
         * Ensures that the argument expression is `true`.
         */
        private fun checkArgument(expression: Boolean) {
            if (!expression) {
                throw IllegalArgumentException()
            }
        }

        /**
         * Ensures that the state expression is `true`.
         */
        private fun checkState(expression: Boolean) {
            if (!expression) {
                throw IllegalStateException()
            }
        }

    }

}