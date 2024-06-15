package org.cirjson.cirjackson.core.symbols

import org.cirjson.cirjackson.core.util.InternCache
import java.util.concurrent.atomic.AtomicReference

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

        internal fun calculateTertiaryShift(primarySlots: Int): Int {
            val tertiarySlots = primarySlots shr 2

            return when {
                tertiarySlots < 64 -> 4
                tertiarySlots <= 256 -> 5
                tertiarySlots <= 1024 -> 6
                else -> 7
            }
        }

    }

}