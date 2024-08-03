package org.cirjson.cirjackson.core.symbols

import org.cirjson.cirjackson.core.TokenStreamFactory
import org.cirjson.cirjackson.core.exception.StreamConstraintsException
import org.cirjson.cirjackson.core.util.InternCache
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

/**
 * Replacement for [CharsToNameCanonicalizer] which aims at more localized memory access due to flattening of name quad
 * data. Performance improvement modest for simple CirJSON document data binding (maybe 3%), but should help more for
 * larger symbol tables, or for binary formats like Smile.
 *
 * Hash area is divided into 4 sections:
 * 1. Primary area (1/2 of total size), direct match from hash (LSB)
 * 2. Secondary area (1/4 of total size), match from `hash (LSB) shr 1`
 * 3. Tertiary area (1/8 of total size), match from `hash (LSB) shr 2`
 * 4. Spill-over area (remaining 1/8) with linear scan, insertion order
 *
 * and within every area, entries are 4 `Int`s, where 1 - 3 `Int`s contain 1 - 12 UTF-8 encoded bytes of name
 * (null-padded), and last int is offset in `myNames` that contains actual name Strings.
 */
class ByteQuadsCanonicalizer {

    /**
     * Reference to the root symbol table, for child tables, so that they can merge table information back as necessary.
     */
    private val myParent: ByteQuadsCanonicalizer?

    /**
     * Member that is only used by the root table instance: root passes immutable state info child instances, and
     * children may return new state if they add entries to the table. Child tables do NOT use the reference.
     */
    private val myTableInfo: AtomicReference<TableInfo>?

    /**
     * Seed value we use as the base to make hash codes non-static between different runs, but still stable for lifetime
     * of a single symbol table instance. This is done for security reasons, to avoid potential DoS attack via hash
     * collisions.
     */
    val hashSeed: Int

    /**
     * Entity that knows how to `intern` Strings, if needed, or `null` if no interning is wanted.
     */
    private val myInterner: InternCache?

    /**
     * Flag that indicates whether we should throw an exception if enough hash collisions are detected (true); or just
     * worked around (false).
     */
    private val myIsFailingOnDoS: Boolean

    /**
     * Primary hash information area: consists of `2 * bucketCount` entries of 16 bytes (4 `Int`s), arranged in a
     * cascading lookup structure (details of which may be tweaked depending on expected rates of collisions).
     */
    private lateinit var myHashArea: IntArray

    /**
     * Number of slots for primary entries within [myHashArea]; which is at most `1/8` of actual size of the underlying
     * array (4-int slots, primary covers only half of the area; plus, additional area for longer symbols after hash
     * area).
     */
    var bucketCount = 0
        private set

    /**
     * Offset within [myHashArea] where secondary entries start
     */
    private var mySecondaryStart = 0

    /**
     * Offset within [myHashArea] where tertiary entries start
     */
    private var myTertiaryStart = 0

    /**
     * Constant that determines size of buckets for tertiary entries: `1 shl myTertiaryShift` is the size, and shift
     * value is also used for translating from primary offset into tertiary bucket (shift right by
     * `4 + myTertiaryShift`).
     *
     * Default value is 2, for buckets of 4 slots; grows bigger with bigger table sizes.
     */
    private var myTertiaryShift = 0

    /**
     * Total number of Strings in the symbol table; only used for child tables.
     */
    private var myCount = 0

    /**
     * Array that contains `String` instances matching entries in [myHashArea]. Contains nulls for unused entries. Note
     * that this size is twice that of [myHashArea]
     */
    private lateinit var myNames: Array<String?>

    /**
     * Pointer to the offset within spill-over area where there is room for more spilled over entries (if any). Spill
     * over area is within fixed-size portion of [myHashArea].
     */
    private var mySpilloverEnd = 0

    /**
     * Offset within [myHashArea] that follows main slots and contains quads for longer names (13 bytes or longer), and
     * points to the first available int that may be used for appending quads of the next long name.
     *
     * Note that long name area follows immediately after the fixed-size main hash area ([myHashArea]).
     */
    private var myLongNameOffset = 0

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
     * Constructor used for creating per-`TokenStreamFactory` "root" symbol tables: ones used for merging and sharing
     * common symbols
     *
     * @param size Initial primary hash area size
     *
     * @param seed Random seed valued used to make it more difficult to cause collisions (used for collision-based DoS
     * attacks).
     */
    private constructor(size: Int, seed: Int) {
        myParent = null
        myCount = 0

        myIsHashShared = true
        hashSeed = seed
        myInterner = null
        myIsFailingOnDoS = true

        var sz = size

        if (sz < MIN_HASH_SIZE) {
            sz = MIN_HASH_SIZE
        } else {
            if (sz and (sz - 1) != 0) {
                var curr = MIN_HASH_SIZE

                while (curr < sz) {
                    curr += curr
                }

                sz = curr
            }
        }

        myTableInfo = AtomicReference<TableInfo>(TableInfo.createInitial(sz))
    }

    /**
     * Constructor used when creating a child instance
     */
    private constructor(parent: ByteQuadsCanonicalizer, seed: Int, state: TableInfo, intern: Boolean,
            failOnDoS: Boolean) {
        myParent = parent
        hashSeed = seed
        myInterner = if (intern) InternCache.INSTANCE else null
        myIsFailingOnDoS = failOnDoS
        myTableInfo = null

        myCount = state.myCount
        bucketCount = state.size
        mySecondaryStart = bucketCount shl 2
        myTertiaryStart = mySecondaryStart + (mySecondaryStart shr 1)
        myTertiaryStart = state.myTertiaryShift

        myHashArea = state.mainHash
        myNames = state.myNames
        mySpilloverEnd = state.mySpilloverEnd
        myLongNameOffset = state.myLongNameOffset

        myIsHashShared = true
    }

    /**
     * Alternate constructor used in cases where a "placeholder" child instance is needed when symbol table is not
     * really used, but caller needs a non-null placeholder to keep code functioning with minimal awareness of
     * distinction (all lookups fail to match any name without error; add methods should NOT be called).
     */
    private constructor(state: TableInfo) {
        myParent = null
        hashSeed = 0
        myInterner = null
        myIsFailingOnDoS = true
        myTableInfo = null

        myCount = -1

        myHashArea = state.mainHash
        myNames = state.myNames

        bucketCount = state.size


        val end = myHashArea.size
        mySecondaryStart = end
        myTertiaryStart = end
        myTertiaryShift = 1

        mySpilloverEnd = end
        myLongNameOffset = end

        myIsHashShared = true
    }

    /**
     * Factory method used to create actual symbol table instance to use for parsing.
     *
     * @param flags Bit flags of active [org.cirjson.cirjackson.core.TokenStreamFactory.Feature]s enabled.
     *
     * @return Actual canonicalizer instance that can be used by a parser
     */
    fun makeChild(flags: Int): ByteQuadsCanonicalizer {
        return ByteQuadsCanonicalizer(this, hashSeed, myTableInfo!!.get(),
                TokenStreamFactory.Feature.INTERN_PROPERTY_NAMES.isEnabledIn(flags),
                TokenStreamFactory.Feature.FAIL_ON_SYMBOL_HASH_OVERFLOW.isEnabledIn(flags))
    }

    /**
     * Method similar to [makeChild] but one that only creates real instance of
     * [org.cirjson.cirjackson.core.TokenStreamFactory.Feature.CANONICALIZE_PROPERTY_NAMES] is enabled: otherwise a
     * "bogus" instance is created.
     *
     * @param flags Bit flags of active [org.cirjson.cirjackson.core.TokenStreamFactory.Feature]s enabled.
     *
     * @return Actual canonicalizer instance that can be used by a parser if (and only if) canonicalization is enabled;
     * otherwise a non-null "placeholder" instance.
     */
    fun makeChildOrPlaceholder(flags: Int): ByteQuadsCanonicalizer {
        return if (TokenStreamFactory.Feature.CANONICALIZE_PROPERTY_NAMES.isEnabledIn(flags)) {
            ByteQuadsCanonicalizer(this, hashSeed, myTableInfo!!.get(),
                    TokenStreamFactory.Feature.INTERN_PROPERTY_NAMES.isEnabledIn(flags),
                    TokenStreamFactory.Feature.FAIL_ON_SYMBOL_HASH_OVERFLOW.isEnabledIn(flags))
        } else {
            ByteQuadsCanonicalizer(myTableInfo!!.get())
        }
    }

    /**
     * Method called by the using code to indicate it is done with this instance. This lets instance merge accumulated
     * changes into parent (if need be), safely and efficiently, and without calling code having to know about parent
     * information.
     */
    fun release() {
        if (myParent != null && isMaybeDirty) {
            myParent.mergeChild(TableInfo(this))
            myIsHashShared = true
        }
    }

    private fun mergeChild(state: TableInfo) {
        var childState = state

        val childCount = childState.size
        val currentState = myTableInfo!!.get()

        if (childCount == currentState.myCount) {
            return
        }

        if (childCount > MAX_ENTRIES_FOR_REUSE) {
            childState = TableInfo.createInitial(DEFAULT_T_SIZE)
        }

        myTableInfo.compareAndSet(currentState, childState)
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
        get() = myTableInfo?.get()?.myCount ?: myCount

    /**
     * Accessor called to quickly check if a child symbol table may have gotten additional entries. Used for checking to
     * see if a child table should be merged into shared table.
     */
    val isMaybeDirty
        get() = !myIsHashShared

    /**
     * True for "real", canonicalizing child tables; false for root table as well as placeholder "child" tables.
     */
    val isCanonicalizing
        get() = myParent != null

    /**
     * Accessor mostly needed by unit tests; calculates number of entries that are in the primary slot set. These are
     * "perfect" entries, accessible with a single lookup
     */
    val primaryCount: Int
        get() {
            var count = 0
            var offset = 3
            val end = mySecondaryStart

            while (offset < end) {
                if (myHashArea[offset] != 0) {
                    ++count
                }

                offset += 4
            }

            return count
        }

    /**
     * Accessor mostly needed by unit tests; calculates number of entries in secondary buckets
     */
    val secondaryCount: Int
        get() {
            var count = 0
            var offset = mySecondaryStart + 3
            val end = myTertiaryStart

            while (offset < end) {
                if (myHashArea[offset] != 0) {
                    ++count
                }

                offset += 4
            }

            return count
        }

    /**
     * Accessor mostly needed by unit tests; calculates number of entries in tertiary buckets
     */
    val tertiaryCount: Int
        get() {
            var count = 0
            var offset = myTertiaryStart + 3
            val end = offset + bucketCount

            while (offset < end) {
                if (myHashArea[offset] != 0) {
                    ++count
                }

                offset += 4
            }

            return count
        }

    /**
     * Accessor mostly needed by unit tests; calculates number of entries in shared spill-over area
     */
    val spilloverCount
        get() = (mySpilloverEnd - spilloverStart)

    /**
     * Helper accessor that calculates start of the spillover area
     */
    private val spilloverStart: Int
        get() {
            val offset = bucketCount
            return (offset shl 3) - offset
        }

    val totalCount: Int
        get() {
            var count = 0
            var offset = 3
            val end = bucketCount shl 3

            while (offset < end) {
                if (myHashArea[offset] != 0) {
                    ++count
                }

                offset += 4
            }

            return count
        }

    /*
     *******************************************************************************************************************
     * Public API, accessing symbols
     *******************************************************************************************************************
     */

    /**
     * Finds the name represented by the quad
     *
     * @param q1 Quad of name representation
     *
     * @return The name represented by the quad, or `null` if it doesn't exist
     */
    fun findName(q1: Int): String? {
        val offset = calculateOffset(calculateHash(q1))
        val hashArea = myHashArea
        var length = hashArea[offset + 3]

        if (length == 0) {
            return null
        } else if (length == 1) {
            if (hashArea[offset] == q1) {
                return myNames[offset shr 2]
            }
        }

        val offset2 = mySecondaryStart + ((offset shr 3) shl 2)

        length = hashArea[offset2 + 3]

        if (length == 0) {
            return null
        } else if (length == 1) {
            if (hashArea[offset2] == q1) {
                return myNames[offset2 shr 2]
            }
        }

        return findSecondary(offset, q1)
    }

    private fun calculateOffset(hash: Int): Int {
        val index = hash and (bucketCount - 1)
        return index shl 2
    }

    private fun findSecondary(originalOffset: Int, q1: Int): String? {
        var offset = myTertiaryStart + ((originalOffset shr (myTertiaryShift + 2)) shl myTertiaryShift)
        val hashArea = myHashArea
        val bucketSize = 1 shl myTertiaryShift
        val end = offset + bucketSize

        while (offset < end) {
            val length = hashArea[offset + 3]

            if (length == 0) {
                return null
            } else if (length == 1 && hashArea[offset] == q1) {
                return myNames[offset shr 2]
            }

            offset += 4
        }

        offset = spilloverStart

        while (offset < mySpilloverEnd) {
            if (hashArea[offset] == q1 && hashArea[offset + 3] == 1) {
                return myNames[offset shr 2]
            }

            offset += 4
        }

        return null
    }

    /**
     * Finds the name represented by the quads
     *
     * @param q1 First quad of name representation
     *
     * @param q2 Second quad of name representation
     *
     * @return The name represented by the quads, or `null` if it doesn't exist
     */
    fun findName(q1: Int, q2: Int): String? {
        val offset = calculateOffset(calculateHash(q1, q2))
        val hashArea = myHashArea
        var length = hashArea[offset + 3]

        if (length == 0) {
            return null
        } else if (length == 2) {
            if (hashArea[offset] == q1 && hashArea[offset + 1] == q2) {
                return myNames[offset shr 2]
            }
        }

        val offset2 = mySecondaryStart + ((offset shr 3) shl 2)

        length = hashArea[offset2 + 3]

        if (length == 0) {
            return null
        } else if (length == 2) {
            if (hashArea[offset2] == q1 && hashArea[offset2 + 1] == q2) {
                return myNames[offset2 shr 2]
            }
        }

        return findSecondary(offset, q1, q2)
    }

    private fun findSecondary(originalOffset: Int, q1: Int, q2: Int): String? {
        var offset = myTertiaryStart + ((originalOffset shr (myTertiaryShift + 2)) shl myTertiaryShift)
        val hashArea = myHashArea
        val bucketSize = 1 shl myTertiaryShift
        val end = offset + bucketSize

        while (offset < end) {
            val length = hashArea[offset + 3]

            if (length == 0) {
                return null
            } else if (length == 2 && hashArea[offset] == q1 && hashArea[offset + 1] == q2) {
                return myNames[offset shr 2]
            }

            offset += 4
        }

        offset = spilloverStart

        while (offset < mySpilloverEnd) {
            if (hashArea[offset] == q1 && hashArea[offset + 1] == q2 && hashArea[offset + 3] == 2) {
                return myNames[offset shr 2]
            }

            offset += 4
        }

        return null
    }

    /**
     * Finds the name represented by the quads
     *
     * @param q1 First quad of name representation
     *
     * @param q2 Second quad of name representation
     *
     * @param q3 Third quad of name representation
     *
     * @return The name represented by the quads, or `null` if it doesn't exist
     */
    fun findName(q1: Int, q2: Int, q3: Int): String? {
        val offset = calculateOffset(calculateHash(q1, q2, q3))
        val hashArea = myHashArea
        var length = hashArea[offset + 3]

        if (length == 0) {
            return null
        } else if (length == 3) {
            if (hashArea[offset] == q1 && hashArea[offset + 1] == q2 && hashArea[offset + 2] == q3) {
                return myNames[offset shr 2]
            }
        }

        val offset2 = mySecondaryStart + ((offset shr 3) shl 2)

        length = hashArea[offset2 + 3]

        if (length == 0) {
            return null
        } else if (length == 3) {
            if (hashArea[offset2] == q1 && hashArea[offset2 + 1] == q2 && hashArea[offset2 + 2] == q3) {
                return myNames[offset2 shr 2]
            }
        }

        return findSecondary(offset, q1, q2, q3)
    }

    private fun findSecondary(originalOffset: Int, q1: Int, q2: Int, q3: Int): String? {
        var offset = myTertiaryStart + ((originalOffset shr (myTertiaryShift + 2)) shl myTertiaryShift)
        val hashArea = myHashArea
        val bucketSize = 1 shl myTertiaryShift
        val end = offset + bucketSize

        while (offset < end) {
            val length = hashArea[offset + 3]

            if (length == 0) {
                return null
            } else if (length == 3 && hashArea[offset] == q1 && hashArea[offset + 1] == q2 && hashArea[offset + 2] == q3) {
                return myNames[offset shr 2]
            }

            offset += 4
        }

        offset = spilloverStart

        while (offset < mySpilloverEnd) {
            if (hashArea[offset] == q1 && hashArea[offset + 1] == q2 && hashArea[offset + 2] == q3 && hashArea[offset + 3] == 3) {
                return myNames[offset shr 2]
            }

            offset += 4
        }

        return null
    }

    /**
     * Finds the name represented by the quads
     *
     * @param quads Quads of name representation
     *
     * @param qLength Number of quads in `quads`
     *
     * @return The name represented by the quads, or `null` if it doesn't exist
     */
    fun findName(quads: IntArray, qLength: Int): String? {
        if (qLength < 4) {
            return when (qLength) {
                3 -> findName(quads[0], quads[1], quads[2])
                2 -> findName(quads[0], quads[1])
                1 -> findName(quads[0])
                else -> ""
            }
        }

        val hash = calculateHash(quads, qLength)
        val offset = calculateOffset(hash)
        val hashArea = myHashArea
        val length = hashArea[offset + 3]

        if (length == 0) {
            return null
        }

        if (hash == hashArea[offset] && length == qLength) {
            if (verifyLongName(quads, qLength, hashArea[offset + 1])) {
                return myNames[offset shr 2]
            }
        }

        val offset2 = mySecondaryStart + ((offset shr 3) shl 2)
        val length2 = hashArea[offset2 + 3]

        if (hash == hashArea[offset2] && length2 == qLength) {
            if (verifyLongName(quads, qLength, hashArea[offset2 + 1])) {
                return myNames[offset2 shr 2]
            }
        }

        return findSecondary(offset, hash, quads, qLength)
    }

    private fun findSecondary(originalOffset: Int, hash: Int, quads: IntArray, qLength: Int): String? {
        var offset = myTertiaryStart + ((originalOffset shr (myTertiaryShift + 2)) shl myTertiaryShift)
        val hashArea = myHashArea
        val bucketSize = 1 shl myTertiaryShift
        val end = offset + bucketSize

        while (offset < end) {
            val length = hashArea[offset + 3]

            if (length == 0) {
                return null
            } else if (hash == hashArea[offset] && qLength == length) {
                if (verifyLongName(quads, qLength, hashArea[offset + 1])) {
                    return myNames[offset shr 2]
                }
            }

            offset += 4
        }

        offset = spilloverStart

        while (offset < mySpilloverEnd) {
            if (hash == hashArea[offset] && qLength == hashArea[offset + 3]) {
                if (verifyLongName(quads, qLength, hashArea[offset + 1])) {
                    return myNames[offset shr 2]
                }
            }

            offset += 4
        }

        return null
    }

    private fun verifyLongName(quads: IntArray, qLength: Int, offset: Int): Boolean {
        var spillOffset = offset
        var index = 0
        val hashArea = myHashArea

        do {
            if (quads[index++] != hashArea[spillOffset++]) {
                return false
            }
        } while (index < qLength)

        return true
    }

    /*
     *******************************************************************************************************************
     * Public API, mutators
     *******************************************************************************************************************
     */

    /**
     * Adds the name represented by the quad
     *
     * @param newName Name to add
     *
     * @param q1 Quad representation of the name
     *
     * @return name (possibly interned)
     *
     * @throws StreamConstraintsException if the constraint isn't respected
     */
    @Throws(StreamConstraintsException::class)
    fun addName(newName: String, q1: Int): String {
        verifySharing()
        val name = myInterner?.intern(newName) ?: newName
        val offset = findOffsetForAdd(calculateHash(q1))
        myHashArea[offset] = q1
        myHashArea[offset + 3] = 1
        myNames[offset shr 2] = name
        ++myCount
        return name
    }

    private fun verifySharing() {
        if (!myIsHashShared) {
            return
        }

        if (myParent == null) {
            throw if (myCount == 0) {
                IllegalStateException("Internal error: Cannot add names to Root symbol table")
            } else {
                IllegalStateException("Internal error: Cannot add names to Placeholder symbol table")
            }
        }

        myHashArea = myHashArea.copyOf()
        myNames = myNames.copyOf()
        myIsHashShared = false
    }

    /**
     * Method called to find the location within hash table to add a new symbol in.
     *
     * @param hash Hash of name for which to find location
     *
     * @throws StreamConstraintsException If name length exceeds maximum allowed.
     */
    @Throws(StreamConstraintsException::class)
    private fun findOffsetForAdd(hash: Int): Int {
        var offset = calculateOffset(hash)
        val hashArea = myHashArea

        if (hashArea[offset + 3] == 0) {
            return offset
        }

        if (checkNeedForRehash()) {
            return resizeAndFindOffsetForAdd(hash)
        }

        var offset2 = mySecondaryStart + ((offset shr 3) shl 2)

        if (hashArea[offset2 + 3] == 0) {
            return offset2
        }

        offset2 = myTertiaryStart + ((offset shr (myTertiaryShift + 2)) shl myTertiaryShift)
        val bucketSize = 1 shl myTertiaryShift
        val end = offset2 + bucketSize

        while (offset2 < end) {
            if (hashArea[offset2 + 3] == 0) {
                return offset2
            }

            offset2 += 4
        }

        offset = mySpilloverEnd
        mySpilloverEnd += 4

        val realEnd = bucketCount shl 3

        return if (mySpilloverEnd >= realEnd) {
            if (myIsFailingOnDoS) {
                reportTooManyCollisions()
            }

            resizeAndFindOffsetForAdd(hash)
        } else {
            offset
        }
    }

    private fun checkNeedForRehash(): Boolean {
        if (myCount <= bucketCount shr 1) {
            return false
        }

        val spillCount = (mySpilloverEnd - spilloverStart) shr 2
        return spillCount > (1 + myCount) shr 7 || myCount > multiplyByFourFifths(bucketCount)
    }

    @Throws(StreamConstraintsException::class)
    private fun resizeAndFindOffsetForAdd(hash: Int): Int {
        rehash()

        var offset = calculateOffset(hash)
        val hashArea = myHashArea

        if (hashArea[offset + 3] == 0) {
            return offset
        }

        var offset2 = mySecondaryStart + ((offset shr 3) shl 2)

        if (hashArea[offset2 + 3] == 0) {
            return offset2
        }

        offset2 = myTertiaryStart + ((offset shr (myTertiaryShift + 2)) shl myTertiaryShift)
        val bucketSize = 1 shl myTertiaryShift
        val end = offset2 + bucketSize

        while (offset2 < end) {
            if (hashArea[offset2 + 3] == 0) {
                return offset2
            }

            offset2 += 4
        }

        offset = mySpilloverEnd
        mySpilloverEnd += 4
        return offset
    }

    private fun rehash() {
        myIsHashShared = false

        val oldHashArea = myHashArea
        val oldNames = myNames
        val oldSize = bucketCount
        val oldCount = myCount
        val newSize = oldSize + oldSize
        val oldEnd = mySpilloverEnd

        if (newSize > MAX_T_SIZE) {
            nukeSymbols(true)
            return
        }

        myHashArea = IntArray(oldHashArea.size + (oldSize shl 3))
        bucketCount = newSize
        mySecondaryStart = newSize shl 2
        myTertiaryStart = mySecondaryStart + (mySecondaryStart shr 1)
        myTertiaryShift = calculateTertiaryShift(newSize)

        myNames = arrayOfNulls(oldNames.size shl 1)
        nukeSymbols(false)

        var copyCount = 0
        var quads = IntArray(16)
        var offset = 0

        while (offset < oldEnd) {
            val length = oldHashArea[offset + 3]

            if (length == 0) {
                offset += 4
                continue
            }

            ++copyCount
            val name = oldNames[offset shr 2]!!

            when (length) {
                1 -> {
                    quads[0] = oldHashArea[offset]
                    addName(name, quads, 1)
                }

                2 -> {
                    quads[0] = oldHashArea[offset]
                    quads[1] = oldHashArea[offset + 1]
                    addName(name, quads, 2)
                }

                3 -> {
                    quads[0] = oldHashArea[offset]
                    quads[1] = oldHashArea[offset + 1]
                    quads[2] = oldHashArea[offset + 2]
                    addName(name, quads, 3)
                }

                else -> {
                    if (length > quads.size) {
                        quads = IntArray(length)
                    }

                    val quadsOffset = oldHashArea[offset + 1]
                    oldHashArea.copyInto(quads, 0, quadsOffset, quadsOffset + length)
                    addName(name, quads, length)
                }
            }

            offset += 4
        }

        if (copyCount != oldCount) {
            throw IllegalStateException("Internal error: Failed rehash(), oldCount=$oldCount, copyCount=$copyCount")
        }
    }

    private fun nukeSymbols(fill: Boolean) {
        myCount = 0
        mySpilloverEnd = spilloverStart
        myLongNameOffset = bucketCount shl 3

        if (fill) {
            myHashArea.fill(0)
            myNames.fill(null)
        }
    }

    @Throws(StreamConstraintsException::class)
    private fun reportTooManyCollisions() {
        if (bucketCount <= 1024) {
            return
        }

        throw StreamConstraintsException("Spill-over slots in symbol table with $myCount entries, hash area of " +
                "$bucketCount slots is now full (all ${bucketCount shr 3} slots -- suspect a DoS attack based on " +
                "hash collisions. You can disable the check via " +
                "`TokenStreamFactory.Feature.FAIL_ON_SYMBOL_HASH_OVERFLOW`")
    }

    /**
     * Adds the name represented by the quads
     *
     * @param newName Name to add
     *
     * @param q1 First quad of name representation
     *
     * @param q2 Second quad of name representation
     *
     * @return name (possibly interned)
     *
     * @throws StreamConstraintsException if the constraint isn't respected
     */
    @Throws(StreamConstraintsException::class)
    fun addName(newName: String, q1: Int, q2: Int): String {
        verifySharing()
        val name = myInterner?.intern(newName) ?: newName
        val offset = findOffsetForAdd(calculateHash(q1))
        myHashArea[offset] = q1
        myHashArea[offset + 1] = q2
        myHashArea[offset + 3] = 2
        myNames[offset shr 2] = name
        ++myCount
        return name
    }

    /**
     * Adds the name represented by the quads
     *
     * @param newName Name to add
     *
     * @param q1 First quad of name representation
     *
     * @param q2 Second quad of name representation
     *
     * @param q3 Third quad of name representation
     *
     * @return name (possibly interned)
     *
     * @throws StreamConstraintsException if the constraint isn't respected
     */
    @Throws(StreamConstraintsException::class)
    fun addName(newName: String, q1: Int, q2: Int, q3: Int): String {
        verifySharing()
        val name = myInterner?.intern(newName) ?: newName
        val offset = findOffsetForAdd(calculateHash(q1))
        myHashArea[offset] = q1
        myHashArea[offset + 1] = q2
        myHashArea[offset + 2] = q3
        myHashArea[offset + 3] = 3
        myNames[offset shr 2] = name
        ++myCount
        return name
    }

    /**
     * Adds the name represented by the quads
     *
     * @param newName Name to add
     *
     * @param quads Quads of name representation
     *
     * @param qLength Number of quads in `quads`
     *
     * @return name (possibly interned)
     *
     * @throws StreamConstraintsException if the constraint isn't respected
     */
    @Throws(StreamConstraintsException::class)
    fun addName(newName: String, quads: IntArray, qLength: Int): String {
        verifySharing()

        val name = myInterner?.intern(newName) ?: newName
        val offset: Int

        when (qLength) {
            1 -> {
                offset = findOffsetForAdd(calculateHash(quads[0]))
                myHashArea[offset] = quads[0]
                myHashArea[offset + 3] = 1
            }

            2 -> {
                offset = findOffsetForAdd(calculateHash(quads[0], quads[1]))
                myHashArea[offset] = quads[0]
                myHashArea[offset + 1] = quads[1]
                myHashArea[offset + 3] = 2
            }

            3 -> {
                offset = findOffsetForAdd(calculateHash(quads[0], quads[1], quads[2]))
                myHashArea[offset] = quads[0]
                myHashArea[offset + 1] = quads[1]
                myHashArea[offset + 2] = 2
                myHashArea[offset + 3] = 3
            }

            else -> {
                val hash = calculateHash(quads, qLength)
                offset = findOffsetForAdd(hash)

                myHashArea[offset] = hash
                val longStart = appendLongName(quads, qLength)
                myHashArea[offset + 1] = longStart
                myHashArea[offset + 3] = qLength
            }
        }

        myNames[offset shr 2] = name
        ++myCount
        return name
    }

    private fun appendLongName(quads: IntArray, qLength: Int): Int {
        val start = myLongNameOffset

        if (start + qLength > myHashArea.size) {
            val toAdd = start + qLength - myHashArea.size
            val minAdd = min(bucketCount, 4096)
            val newSize = myHashArea.size + max(toAdd, minAdd)
            myHashArea = myHashArea.copyOf(newSize)
        }

        quads.copyInto(myHashArea, start, 0, qLength)
        myLongNameOffset += qLength
        return start
    }

    /*
     *******************************************************************************************************************
     * Hash calculation
     *******************************************************************************************************************
     */

    /**
     * Calculates the hash based on the quad
     *
     * @param q1 Quad of name representation
     *
     * @return The calculated hash
     */
    fun calculateHash(q1: Int): Int {
        var hash = q1 xor hashSeed
        hash += hash ushr 16
        hash = hash xor (hash shl 3)
        hash += hash ushr 12
        return hash
    }

    /**
     * Calculates the hash based on the quads
     *
     * @param q1 First quad of name representation
     *
     * @param q2 Second quad of name representation
     *
     * @return The calculated hash
     */
    fun calculateHash(q1: Int, q2: Int): Int {
        var hash = q1
        hash += hash ushr 15
        hash = hash xor (hash ushr 9)
        hash += q2 * MULT
        hash = hash xor hashSeed
        hash += hash ushr 16
        hash = hash xor (hash ushr 4)
        hash += hash shl 3
        return hash
    }

    /**
     * Calculates the hash based on the quads
     *
     * @param q1 First quad of name representation
     *
     * @param q2 Second quad of name representation
     *
     * @param q3 Third quad of name representation
     *
     * @return The calculated hash
     */
    fun calculateHash(q1: Int, q2: Int, q3: Int): Int {
        var hash = q1 xor hashSeed
        hash += hash ushr 9
        hash *= MULT3
        hash += q2
        hash *= MULT
        hash += hash ushr 15
        hash = hash xor q3
        hash += hash ushr 4
        hash += hash ushr 15
        hash = hash xor (hash shl 9)
        return hash
    }

    /**
     * Calculates the hash based on the quads
     *
     * @param quads Quads of name representation
     *
     * @param qLength Number of quads in `quads`
     *
     * @return The calculated hash
     *
     * @throws IllegalArgumentException if `qLength` is less than 4
     */
    fun calculateHash(quads: IntArray, qLength: Int): Int {
        if (qLength < 4) {
            throw IllegalArgumentException("Quads length must be at least 4 (got $qLength)")
        }

        var hash = quads[0] xor hashSeed
        hash += hash ushr 9
        hash += quads[1]
        hash += hash ushr 15
        hash *= MULT
        hash = hash xor quads[2]
        hash += hash ushr 4

        for (i in 3..<qLength) {
            var next = quads[i]
            next = next xor (next shr 21)
            hash += next
        }

        hash *= MULT2
        hash += hash ushr 19
        hash = hash xor (hash shl 5)
        return hash
    }

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    override fun toString(): String {
        val primary = primaryCount
        val secondary = secondaryCount
        val tertiary = tertiaryCount
        val spillover = spilloverCount
        val total = totalCount

        return "[ByteQuadsCanonicalizer: size=$myCount, bucketCount=$bucketCount, $primary/$secondary/$tertiary/$spillover primary/secondary/tertiary/spillover (=${primary + secondary + tertiary + spillover}), total:$total]"
    }

    /*
     *******************************************************************************************************************
     * Helper class
     *******************************************************************************************************************
     */

    /**
     * Immutable value class used for sharing information as efficiently as possible, by only require synchronization of
     * reference manipulation but not access to contents.
     */
    private class TableInfo(val size: Int, val myCount: Int, val myTertiaryShift: Int, val mainHash: IntArray,
            val myNames: Array<String?>, val mySpilloverEnd: Int, val myLongNameOffset: Int) {

        constructor(source: ByteQuadsCanonicalizer) : this(source.bucketCount, source.myCount, source.myTertiaryShift,
                source.myHashArea, source.myNames, source.mySpilloverEnd, source.myLongNameOffset)

        companion object {

            fun createInitial(size: Int): TableInfo {
                val hashAreaSize = size shl 3
                val tertiaryShift = calculateTertiaryShift(size)

                return TableInfo(size, 0, tertiaryShift, IntArray(hashAreaSize), arrayOfNulls(size shl 1),
                        hashAreaSize - size, hashAreaSize)
            }

        }

    }

    companion object {

        /**
         * Initial size of the primary hash area. Each entry consumes 4 `Int`s (16 bytes), and secondary area is same as
         * primary; so default size will use 2kB of memory (plus 64x4 or 64x8 (256/512 bytes) for references to Strings,
         * and Strings themselves).
         */
        private const val DEFAULT_T_SIZE = 64

        /**
         * Let's not expand symbol tables past some maximum size; with unique (~= random) names.
         *
         * 64k entries == 2M mem hash area
         */
        private const val MAX_T_SIZE = 0x10000

        /**
         * No point in trying to construct tiny tables, just need to resize soon.
         */
        private const val MIN_HASH_SIZE = 16

        internal const val MAX_ENTRIES_FOR_REUSE = 6000

        private const val MULT = 33

        private const val MULT2 = 65599

        private const val MULT3 = 31

        fun createRoot(): ByteQuadsCanonicalizer {
            val now = System.currentTimeMillis()
            val seed = (now.toInt() + (now ushr 32).toInt()) or 1
            return createRoot(seed)
        }

        /**
         * Factory method that should only be called from unit tests, where seed value should remain the same.
         */
        internal fun createRoot(seed: Int): ByteQuadsCanonicalizer {
            return ByteQuadsCanonicalizer(DEFAULT_T_SIZE, seed)
        }

        internal fun calculateTertiaryShift(primarySlots: Int): Int {
            val tertiarySlots = primarySlots shr 2

            return when {
                tertiarySlots < 64 -> 4
                tertiarySlots <= 256 -> 5
                tertiarySlots <= 1024 -> 6
                else -> 7
            }
        }

        internal fun multiplyByFourFifths(number: Int): Int {
            return ((number * 3_435_973_837L) ushr 32).toInt()
        }

    }

}