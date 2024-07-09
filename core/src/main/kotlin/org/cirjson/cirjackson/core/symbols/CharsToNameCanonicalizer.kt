package org.cirjson.cirjackson.core.symbols

import org.cirjson.cirjackson.core.StreamReadConstraints
import org.cirjson.cirjackson.core.TokenStreamFactory
import org.cirjson.cirjackson.core.exception.StreamConstraintsException
import org.cirjson.cirjackson.core.extensions.countNotNull
import org.cirjson.cirjackson.core.util.InternCache
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/**
 * This class is a kind of specialized type-safe Map, from char array to String value. Specialization means that in
 * addition to type-safety and specific access patterns (key char array, Value optionally interned String; values added
 * on access if necessary), and that instances are meant to be used concurrently, but by using well-defined mechanisms
 * to obtain such concurrently usable instances. Main use for the class is to store symbol table information for things
 * like compilers and parsers; especially when number of symbols (keywords) is limited.
 *
 * For optimal performance, usage pattern should be one where matches should be very common (especially after
 * "warm-up"), and as with most hash-based maps/sets, that hash codes are uniformly distributed. Also, collisions are
 * slightly more expensive than with HashMap or HashSet, since hash codes are not used in resolving collisions; that is,
 * equals() comparison is done with all symbols in same bucket index.
 *
 * Finally, rehashing is also more expensive, as hash codes are not stored; rehashing requires all entries' hash codes
 * to be recalculated. Reason for not storing hash codes is the reduced memory usage, hoping for better memory locality.
 *
 * Usual usage pattern is to create a single "master" instance, and either use that instance in sequential fashion, or
 * to create derived "child" instances, which after use, are asked to return possible symbol additions to master
 * instance. In either case benefit is that symbol table gets initialized so that further uses are more efficient, as
 * eventually all symbols needed will already be in symbol table. At that point no more Symbol String allocations are
 * needed, nor changes to symbol table itself.
 *
 * Note that while individual SymbolTable instances are NOT thread-safe (much like generic collection classes),
 * concurrently used "child" instances can be freely used without synchronization. However, using master table
 * concurrently with child instances can only be done if access to master instance is read-only (i.e. no modifications
 * done).
 */
class CharsToNameCanonicalizer {

    /**
     * Sharing of learnt symbols is done by optional linking of symbol table instances with their parents. When parent
     * linkage is defined, and child instance is released (call to `release`), parent's shared tables may be updated
     * from the child instance.
     */
    private val myParent: CharsToNameCanonicalizer?

    /**
     * Member that is only used by the root table instance: root passes immutable state info child instances, and
     * children may return new state if they add entries to the table. Child tables do NOT use the reference.
     */
    private val myTableInfo: AtomicReference<TableInfo>?

    /**
     * Constraints used by [TokenStreamFactory] that uses this canonicalizer.
     */
    private val myStreamReadConstraints: StreamReadConstraints

    /**
     * Seed value we use as the base to make hash codes non-static between different runs, but still stable for lifetime
     * of a single symbol table instance.
     *
     * This is done for security reasons, to avoid potential DoS attack via hash collisions.
     */
    val hashSeed: Int

    /**
     * Feature flags of [TokenStreamFactory] that uses this canonicalizer.
     */
    private val myFactoryFeatures: Int

    /**
     * Whether any canonicalization should be attempted (whether using intern or not.
     *
     * NOTE: non-final since we may need to disable this with overflow.
     */
    private var myCanonicalize: Boolean

    /**
     * Primary matching symbols; it's expected most match occur from
     * here.
     */
    private lateinit var mySymbols: Array<String?>

    /**
     * Overflow buckets; if primary doesn't match, lookup is done from here.
     *
     * Note: Number of buckets is half of number of symbol entries, on assumption there's less need for buckets.
     */
    private lateinit var myBuckets: Array<Bucket?>

    /**
     * Current size (number of entries); needed to know if and when there's a rehash.
     */
    private var mySize: Int = 0

    /**
     * Limit that indicates maximum size this instance can hold before it needs to be expanded and rehashed. Calculated
     * using fill factor passed in to constructor.
     */
    private var mySizeThreshold = 0

    /**
     * Mask used to get index from hash values; equal to `_buckets.length - 1`, when _buckets.length is a power of two.
     */
    private var myIndexMask = 0

    /**
     * Length of the collision chain.
     *
     * We need to keep track of the longest collision list; this is needed both to indicate problems with attacks and to
     * allow flushing for other cases.
     *
     * Mostly needed by unit tests; calculates length of the longest collision chain. This should typically be a low
     * number, but may be up to [size] - 1 in the pathological case
     */
    var maxCollisionLength = 0
        private set

    /**
     * Flag that indicates whether underlying data structures for the main hash area are shared or not. If they are,
     * then they need to be handled in copy-on-write way, i.e. if they need to be modified, a copy needs to be made
     * first; at this point it will not be shared anymore, and can be modified.
     *
     * This flag needs to be checked both when adding new main entries, and when adding new collision list queues (i.e.
     * creating a new collision list head entry)
     */
    private var myIsHashShared = false

    /**
     * Lazily constructed structure that is used to keep track of collision buckets that have overflowed once: this is
     * used to detect likely attempts at denial-of-service attacks that uses hash collisions.
     */
    private var myOverflows: BitSet? = null

    /**
     * Main method for constructing a root symbol table instance.
     */
    private constructor(streamReadConstraints: StreamReadConstraints, factoryFeatures: Int, seed: Int) {
        myParent = null
        hashSeed = seed
        myStreamReadConstraints = streamReadConstraints

        myCanonicalize = true
        myFactoryFeatures = factoryFeatures
        myTableInfo = AtomicReference<TableInfo>(TableInfo.createInitial(DEFAULT_T_SIZE))
    }

    /**
     * Internal constructor used when creating child instances.
     */
    private constructor(parent: CharsToNameCanonicalizer, streamReadConstraints: StreamReadConstraints,
            factoryFeatures: Int, seed: Int, parentState: TableInfo) {
        myParent = parent
        myStreamReadConstraints = streamReadConstraints
        hashSeed = seed
        myTableInfo = null
        myFactoryFeatures = factoryFeatures
        myCanonicalize = TokenStreamFactory.Feature.CANONICALIZE_PROPERTY_NAMES.isEnabledIn(factoryFeatures)

        mySymbols = parentState.mySymbols
        myBuckets = parentState.myBuckets

        mySize = parentState.mySize
        maxCollisionLength = parentState.myLongestCollisionList

        val arrayLength = mySymbols.size
        mySizeThreshold = thresholdSize(arrayLength)
        myIndexMask = arrayLength - 1

        myIsHashShared = true
    }

    /*
     *******************************************************************************************************************
     * Life-cycle: factory methods, merging
     *******************************************************************************************************************
     */

    /**
     * "Factory" method; will create a new child instance of this symbol table. It will be a copy-on-write instance,
     * i.e. it will only use read-only copy of parent's data, but when changes are needed, a copy will be created.
     *
     * Note: It is generally not safe to both use makeChild/mergeChild, AND to use instance actively. Instead, a
     * separate 'root' instance should be used on which only makeChild/mergeChild are called, but instance itself is not
     * used as a symbol table.
     *
     * @return Actual canonicalizer instance that can be used by a parser
     */
    fun makeChild(): CharsToNameCanonicalizer {
        return CharsToNameCanonicalizer(this, myStreamReadConstraints, myFactoryFeatures, hashSeed, myTableInfo!!.get())
    }

    /**
     * Method called by the using code to indicate it is done with this instance. This lets instance merge accumulated
     * changes into parent (if need be), safely and efficiently, and without calling code having to know about parent
     * information.
     */
    fun release() {
        if (!isMaybeDirty) {
            return
        }

        if (myParent != null && myCanonicalize) {
            myParent.mergeChild(TableInfo(this))
            myIsHashShared = true
        }
    }

    /**
     * Method that allows contents of child table to potentially be "merged in" with contents of this symbol table.
     *
     * Note that caller has to make sure symbol table passed in is really a child or sibling of this symbol table.
     *
     * @param childState The state to merge
     */
    private fun mergeChild(childState: TableInfo) {
        val childCount = childState.mySize
        val currState = myTableInfo!!.get()

        if (childCount == currState.mySize) {
            return
        }

        val newValue = if (childCount > MAX_ENTRIES_FOR_REUSE) TableInfo.createInitial(DEFAULT_T_SIZE) else childState
        myTableInfo.compareAndSet(currState, newValue)
    }

    /*
     *******************************************************************************************************************
     * Public API, generic accessors
     *******************************************************************************************************************
     */

    /**
     * Number of symbol entries contained by this canonicalizer instance
     */
    val size
        get() = myTableInfo?.get()?.mySize ?: mySize

    /**
     * Accessor for checking number of primary hash buckets this symbol table uses.
     */
    val bucketCount
        get() = mySymbols.size

    val isMaybeDirty
        get() = !myIsHashShared

    /**
     * Number of collisions in the primary hash area. Accessor mostly needed by unit tests; calculates number of entries
     * that are in collision list. Value can be at most ([size] - 1), but should usually be much lower, ideally 0.
     */
    val collisionCount
        get() = myBuckets.fold(0) { a, b -> a + (b?.length ?: 0) }

    /*
     *******************************************************************************************************************
     * Public API, accessing symbols
     *******************************************************************************************************************
     */

    fun findSymbol(buffer: CharArray, start: Int, length: Int, hash: Int): String {
        if (length < 1) {
            return ""
        }

        if (!myCanonicalize) {
            myStreamReadConstraints.validateNameLength(length)
            return String(buffer, start, length)
        }

        val index = hashToIndex(hash)
        var symbol = mySymbols[index]

        if (symbol != null) {
            if (symbol.length == length) {
                var i = 0

                while (symbol[i] == buffer[start + i]) {
                    if (++i == length) {
                        return symbol
                    }
                }
            }

            val bucket = myBuckets[index shr 1]

            if (bucket != null) {
                symbol = bucket.has(buffer, start, length)

                if (symbol != null) {
                    return symbol
                }

                symbol = findOtherSymbol(buffer, start, length, bucket.next)

                if (symbol != null) {
                    return symbol
                }
            }
        }

        myStreamReadConstraints.validateNameLength(length)
        return addSymbol(buffer, start, length, index)
    }

    /**
     * Helper method that takes in a "raw" hash value, shuffles it as necessary, and truncates to be used as the index.
     *
     * @param hash Raw hash value to use for calculating index
     *
     * @return Index value calculated
     */
    private fun hashToIndex(hash: Int): Int {
        var rawHash = hash
        rawHash += rawHash ushr 15
        rawHash = rawHash xor (rawHash shl 7)
        rawHash += rawHash ushr 3
        return rawHash and myIndexMask
    }

    private fun findOtherSymbol(buffer: CharArray, start: Int, length: Int, bucket: Bucket?): String? {
        var realBucket = bucket

        while (realBucket != null) {
            val symbol = realBucket.has(buffer, start, length)

            if (symbol != null) {
                return symbol
            }

            realBucket = realBucket.next
        }

        return null
    }

    private fun addSymbol(buffer: CharArray, start: Int, length: Int, i: Int): String {
        var index = i

        if (myIsHashShared) {
            copyArrays()
            myIsHashShared = false
        } else {
            rehash()
            index = hashToIndex(calculateHash(buffer, start, length))
        }

        var newSymbol = String(buffer, start, length)

        if (TokenStreamFactory.Feature.INTERN_PROPERTY_NAMES.isEnabledIn(myFactoryFeatures)) {
            newSymbol = InternCache.INSTANCE.intern(newSymbol)
        }

        ++mySize

        if (mySymbols[index] == null) {
            mySymbols[index] = newSymbol
            return newSymbol
        }

        val bix = index shr 1
        val newBucket = Bucket(newSymbol, myBuckets[bix])
        val collLength = newBucket.length

        if (collLength > MAX_COLL_CHAIN_LENGTH) {
            handleSpillOverflow(bix, newBucket, index)
        } else {
            myBuckets[bix] = newBucket
            maxCollisionLength = max(collLength, maxCollisionLength)
        }

        return newSymbol
    }

    /**
     * Method called when copy-on-write is needed; generally when first change is made to a derived symbol table.
     */
    private fun copyArrays() {
        val oldSymbols = mySymbols
        mySymbols = oldSymbols.copyOf()
        val oldBuckets = myBuckets
        myBuckets = oldBuckets.copyOf()
    }

    /**
     * Method called when size (number of entries) of symbol table grows so big that load factor is exceeded. Since size
     * has to remain a power of two, arrays will then always be doubled. Main work is really redistributing old entries
     * into new String/Bucket entries.
     */
    private fun rehash() {
        val size = mySymbols.size
        val newSize = size + size

        if (newSize > MAX_T_SIZE) {
            mySize = 0
            myCanonicalize = false
            mySymbols = arrayOfNulls(DEFAULT_T_SIZE)
            myBuckets = arrayOfNulls(DEFAULT_T_SIZE shr 1)
            myIndexMask = DEFAULT_T_SIZE - 1
            myIsHashShared = false
            return
        }

        val oldSymbols = mySymbols
        val oldBuckets = myBuckets
        mySymbols = arrayOfNulls(newSize)
        myBuckets = arrayOfNulls(newSize shr 1)
        myIndexMask = newSize - 1
        mySizeThreshold = thresholdSize(newSize)

        var count = 0
        var maxColl = 0

        for (i in 0..<size) {
            val symbol = oldSymbols[i] ?: continue
            ++count
            val index = hashToIndex(calculateHash(symbol))

            if (mySymbols[index] == null) {
                mySymbols[index] = symbol
            } else {
                val bix = index shr 1
                val newBucket = Bucket(symbol, myBuckets[bix])
                myBuckets[bix] = newBucket
                maxColl = max(maxColl, newBucket.length)
            }
        }

        val bucketSize = size shr 1

        for (i in 0..<bucketSize) {
            var bucket = oldBuckets[i]

            while (bucket != null) {
                ++count
                val symbol = bucket.symbol
                val index = hashToIndex(calculateHash(symbol))

                if (mySymbols[index] == null) {
                    mySymbols[index] = symbol
                } else {
                    val bix = index shr 1
                    val newBucket = Bucket(symbol, myBuckets[bix])
                    myBuckets[bix] = newBucket
                    maxColl = max(maxColl, newBucket.length)
                }

                bucket = bucket.next
            }
        }

        maxCollisionLength = maxColl
        myOverflows = null

        if (count != mySize) {
            throw IllegalStateException("Internal error on rehash(): had $mySize entries; now have $count")
        }
    }

    /**
     * Method called when an overflow bucket has hit the maximum expected length: this may be a case of DoS attack. Deal
     * with it based on settings by either clearing up bucket (to avoid indefinite expansion) or throwing exception.
     * Currently, the first overflow for any single bucket DOES NOT throw an exception, only second time (per symbol
     * table instance)
     */
    private fun handleSpillOverflow(bucketIndex: Int, newBucket: Bucket, mainIndex: Int) {
        if (myOverflows == null) {
            myOverflows = BitSet()
            myOverflows!![bucketIndex] = true
        } else {
            if (myOverflows!![bucketIndex]) {
                if (TokenStreamFactory.Feature.FAIL_ON_SYMBOL_HASH_OVERFLOW.isEnabledIn(myFactoryFeatures)) {
                    reportTooManyCollisions(MAX_COLL_CHAIN_LENGTH)
                }

                myCanonicalize = false
            } else {
                myOverflows!![bucketIndex] = true
            }
        }

        mySymbols[mainIndex] = newBucket.symbol
        myBuckets[bucketIndex] = null
        mySize -= newBucket.length
        maxCollisionLength = -1
    }

    private fun reportTooManyCollisions(maxLength: Int) {
        throw StreamConstraintsException("Longest collision chain in symbol table (of size $mySize) now exceeds " +
                "maximum, $maxLength -- suspect a DoS attack based on hash collisions. You can disable the check via " +
                "`TokenStreamFactory.Feature.FAIL_ON_SYMBOL_HASH_OVERFLOW`")
    }

    /**
     * Implementation of a hashing method for variable length Strings. Most of the time intention is that this
     * calculation is done by caller during parsing, not here; however, sometimes it needs to be done for parsed
     * "String" too.
     *
     * @param buffer Input buffer that contains name to decode
     *
     * @param start Pointer to the first character of the name
     *
     * @param length Length of String; has to be at least 1 (caller guarantees)
     *
     * @return Hash code calculated
     */
    fun calculateHash(buffer: CharArray, start: Int, length: Int): Int {
        var hash = hashSeed

        for (i in start..<start + length) {
            hash = hash * HASH_MULT + buffer[i].code
        }

        return if (hash != 0) hash else 1
    }

    /**
     * Implementation of a hashing method for variable length Strings. Most of the time intention is that this
     * calculation is done by caller during parsing, not here; however, sometimes it needs to be done for parsed
     * "String" too.
     *
     * @param key The key to hash
     *
     * @return Hash code calculated
     */
    fun calculateHash(key: String): Int {
        val length = key.length
        var hash = hashSeed

        for (i in 0..<length) {
            hash = hash * HASH_MULT + key[i].code
        }

        return if (hash != 0) hash else 1
    }

    /**
     * Diagnostics method that will verify that internal data structures are consistent; not meant as user-facing method
     * but only for test suites and possible troubleshooting.
     */
    internal fun verifyInternalConsistency() {
        var count = 0
        val size = mySymbols.size

        for (symbol in mySymbols) {
            if (symbol != null) {
                ++count
            }
        }

        val bucketSize = size shr 1

        for (i in 0..<bucketSize) {
            var bucket = myBuckets[i]

            while (bucket != null) {
                ++count
                bucket = bucket.next
            }
        }

        if (count != mySize) {
            throw IllegalStateException("Internal error: expected internal size $mySize vs calculated count $count")
        }
    }

    override fun toString(): String {
        return StringBuilder().apply {
            val primaryCount = mySymbols.countNotNull()

            append("[CharsToNameCanonicalizer, size: ")
            append(mySize)
            append('/')
            append(mySymbols.size)
            append(", ")
            append(primaryCount)
            append('/')
            append(mySize - primaryCount)
            append(" coll; avg length: ")

            val averageLength = calculateAverageLength()
            append(averageLength)
            append(']')
        }.toString()
    }

    private fun calculateAverageLength(): Double {
        var pathCount = mySize

        for (bucket in myBuckets) {
            val spillLength = bucket?.length ?: continue
            for (i in 1..spillLength) {
                pathCount += i
            }
        }

        return if (mySize != 0) {
            pathCount.toDouble() / mySize.toDouble()
        } else {
            0.0
        }
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    /**
     * This class is a symbol table entry. Each entry acts as a node in a linked list.
     */
    private class Bucket(val symbol: String, val next: Bucket?) {

        val length: Int = (next?.length ?: 0) + 1

        fun has(buffer: CharArray, start: Int, length: Int): String? {
            if (symbol.length != length) {
                return null
            }

            var i = 0

            do {
                if (symbol[i] != buffer[start + i]) {
                    return null
                }
            } while (++i < length)

            return symbol
        }

    }

    /**
     * Immutable value class used for sharing information as efficiently as possible, by only require synchronization of
     * reference manipulation but not access to contents.
     */
    private class TableInfo(val mySize: Int, val myLongestCollisionList: Int, val mySymbols: Array<String?>,
            val myBuckets: Array<Bucket?>) {

        constructor(source: CharsToNameCanonicalizer) : this(source.mySize, source.maxCollisionLength,
                source.mySymbols, source.myBuckets)

        companion object {

            fun createInitial(size: Int): TableInfo {
                return TableInfo(0, 0, arrayOfNulls(size), arrayOfNulls(size))
            }

        }

    }

    companion object {

        /**
         * If we use "multiply-add" based hash algorithm, this is the multiplier we use.
         *
         * Note that JDK uses 31; but it seems that 33 produces fewer collisions, at least with tests we have.
         */
        const val HASH_MULT = 33

        /**
         * Default initial table size. Shouldn't be miniscule (as there's cost to both array reallocation and
         * rehashing), but let's keep it reasonably small. For systems that properly reuse factories it doesn't matter
         * either way; but when recreating factories often, initial overhead may dominate.
         */
        private const val DEFAULT_T_SIZE = 64

        /**
         * Let's not expand symbol tables past some maximum size; this should be protected against OOMEs caused by large
         * documents with unique (~= random) names.
         *
         * 64k entries == 256k mem
         */
        private const val MAX_T_SIZE = 0x10000

        /**
         * Let's only share reasonably sized symbol tables. Max size set to 3/4 of 16k; this corresponds to 64k main
         * hash index. This should allow for enough distinct names for almost any case.
         */
        internal const val MAX_ENTRIES_FOR_REUSE = 12000

        /**
         * Also: to thwart attacks based on hash collisions (which may or may not be cheap to calculate), we will need
         * to detect "too long" collision chains.
         *
         * Note: longest chain we have been able to produce without malicious intent has been 38 (with
         * "org.cirjson.cirjackson.core.main.TestWithTonsaSymbols"); our setting should be reasonable here.
         */
        internal const val MAX_COLL_CHAIN_LENGTH = 150

        private fun thresholdSize(hashAreaSize: Int): Int {
            return hashAreaSize - (hashAreaSize shr 2)
        }

        fun createRoot(owner: TokenStreamFactory?): CharsToNameCanonicalizer {
            return createRoot(owner, 0)
        }

        fun createRoot(owner: TokenStreamFactory?, seed: Int): CharsToNameCanonicalizer {
            val realSeed = if (seed != 0) seed else System.identityHashCode(owner)
            val streamReadConstraints = owner?.streamReadConstraints ?: StreamReadConstraints.defaults()
            val factoryFeatures = owner?.factoryFeatures ?: 0
            return CharsToNameCanonicalizer(streamReadConstraints, factoryFeatures, realSeed)
        }

    }

}