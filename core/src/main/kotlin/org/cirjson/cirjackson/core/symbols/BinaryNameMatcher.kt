package org.cirjson.cirjackson.core.symbols

import org.cirjson.cirjackson.core.util.Named
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Simplified static symbol table used instead of global quad-based canonicalizer when we have smaller set of symbols
 * (like properties of a POJO class).
 *
 * @constructor Constructor used for creating per-`TokenStreamFactory` "root" symbol tables (for formats that use such
 * approach): ones used for merging and sharing common symbols
 *
 * @param matcher Backup matcher used if efficient primary matching cannot be used
 *
 * @param nameLookup Set of names to match
 *
 * @param myHashSize Estimated basic hash area size to use (slightly bigger than number of entries in `nameLookup` array
 *
 * @property myHashSize Number of slots for primary entries within [myHashArea]; which is at most `1/8` of actual size
 * of the underlying array (4-int slots, primary covers only half of the area; plus, additional area for longer symbols
 * after hash area).
 */
class BinaryNameMatcher private constructor(matcher: SimpleNameMatcher, nameLookup: Array<String?>?,
        private val myHashSize: Int) : HashedMatcherBase(matcher, nameLookup) {

    /**
     * Primary hash information area: consists of `2 * myHashSize` entries of 16 bytes (4 integers), arranged in a
     * cascading lookup structure (details of which may be tweaked depending on expected rates of collisions).
     */
    private var myHashArea: IntArray = IntArray(myHashSize shl 3)

    /**
     * Offset within [myHashArea] where secondary entries start
     */
    private val mySecondaryStart = myHashSize shl 2

    /**
     * Offset within [myHashArea] where tertiary entries start
     */
    private val myTertiaryStart = mySecondaryStart + (mySecondaryStart shr 1)

    /**
     * Constant that determines size of buckets for tertiary entries: `1 << myTertiaryShift` is the size, and shift
     * value is also used for translating from primary offset into tertiary bucket (shift right by
     * `4 + myTertiaryShift`).
     *
     * Default value is 2, for buckets of 4 slots; grows bigger with bigger table sizes.
     */
    private val myTertiaryShift = calculateTertiaryShift(myHashSize)

    /**
     * Total number of Strings in the symbol table
     */
    var size = 0
        private set

    /**
     * Pointer to the offset within spill-over area where there is room for more spilled over entries (if any). Spill
     * over area is within fixed-size portion of [myHashArea].
     */
    private var mySpilloverEnd = myHashArea.size - myHashSize

    /**
     * Offset within [myHashArea] that follows main slots and contains quads for longer names (13 bytes or longer), and
     * points to the first available int that may be used for appending quads of the next long name. Note that long name
     * area follows immediately after the fixed-size main hash area (`myHashArea`).
     */
    private var myLongNameOffset = myHashArea.size

    /*
     *******************************************************************************************************************
     * Public API, mutators
     *******************************************************************************************************************
     */

    fun addName(name: String): Int {
        val ch = name.toByteArray(Charsets.UTF_8)
        val length = ch.size

        if (length > 12) {
            val quads = quads(name)
            return addName(name, quads, quads.size)
        }

        if (length <= 4) {
            return addName(name, decodeLast(ch, 0, length))
        }

        val q1 = decodeFull(ch, 0)

        return if (length <= 8) {
            addName(name, q1, decodeLast(ch, 4, length - 4))
        } else {
            addName(name, q1, decodeFull(ch, 4), decodeLast(ch, 8, length - 8))
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun addName(name: String, q1: Int): Int {
        val index = size
        val offset = findOffsetForAdd(calculateHash(q1))
        myHashArea[offset] = q1
        myHashArea[offset + 3] = lengthAndIndex(1)
        return index
    }

    @Suppress("UNUSED_PARAMETER")
    private fun addName(name: String, q1: Int, q2: Int): Int {
        val index = size
        val offset = findOffsetForAdd(calculateHash(q1, q2))
        myHashArea[offset] = q1
        myHashArea[offset + 1] = q2
        myHashArea[offset + 3] = lengthAndIndex(2)
        return index
    }

    @Suppress("UNUSED_PARAMETER")
    private fun addName(name: String, q1: Int, q2: Int, q3: Int): Int {
        val index = size
        val offset = findOffsetForAdd(calculateHash(q1, q2, q3))
        myHashArea[offset] = q1
        myHashArea[offset + 1] = q2
        myHashArea[offset + 3] = lengthAndIndex(3)
        return index
    }

    private fun addName(name: String, quads: IntArray, quadLength: Int): Int {
        return when (quadLength) {
            1 -> addName(name, quads[0])

            2 -> addName(name, quads[0], quads[1])

            3 -> addName(name, quads[0], quads[1], quads[2])

            else -> {
                val index = size
                val hash = calculateHash(quads, quadLength)
                val offset = findOffsetForAdd(hash)
                myHashArea[offset] = hash
                val longStart = appendLongName(quads, quadLength)
                myHashArea[offset + 1] = longStart
                myHashArea[offset + 3] = lengthAndIndex(quadLength)
                index
            }
        }
    }

    /**
     * Method called to find the location within hash table to add a new symbol in.
     */
    private fun findOffsetForAdd(hash: Int): Int {
        var offset = calculateOffset(hash)
        val hashArea = myHashArea

        if (hashArea[offset + 3] == 0) {
            return offset
        }

        var offset2 = mySecondaryStart + (offset shr 3 shl 2)

        if (hashArea[offset2 + 3] == 0) {
            return offset2
        }

        offset2 = myTertiaryStart + (offset shr myTertiaryStart + 2 shl myTertiaryShift)
        val bucketSize = 1 shl myTertiaryShift
        var end = offset2 + bucketSize

        while (offset2 < end) {
            if (hashArea[offset2 + 2] == 0) {
                return offset2
            }

            offset2 += 4
        }

        offset = mySpilloverEnd
        end = myHashSize shl 3

        if (mySpilloverEnd >= end) {
            throw IllegalStateException("Internal error: Overflow with $size entries (hash size of $myHashSize)")
        }

        mySpilloverEnd += 4
        return offset
    }

    private fun appendLongName(quads: IntArray, quadLength: Int): Int {
        val start = myLongNameOffset

        if (start + quadLength > myHashArea.size) {
            val toAdd = start + quadLength - myHashArea.size
            val minAdd = min(4096, myHashSize)
            val newSize = myHashArea.size + max(toAdd, minAdd)
            myHashArea = myHashArea.copyOf(newSize)
        }

        quads.copyInto(myHashArea, start, 0, quadLength)
        myLongNameOffset = quadLength
        return start
    }

    /*
     *******************************************************************************************************************
     * Public API, accessors, mostly for Unit Tests
     *******************************************************************************************************************
     */

    fun bucketCount(): Int {
        return myHashSize
    }

    fun primaryQuadCount(): Int {
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

    fun secondaryQuadCount(): Int {
        val indexRange = IntProgression.fromClosedRange(mySecondaryStart + 3, myTertiaryStart - 1, 4)
        return myHashArea.filterIndexed { index, value -> index in indexRange && value != 0 }.size
    }

    fun tertiaryQuadCount(): Int {
        var count = 0
        var offset = myTertiaryStart + 3
        val end = offset + myHashSize

        while (offset < end) {
            if (myHashArea[offset] != 0) {
                ++count
            }

            offset += 4
        }

        return count
    }

    fun spilloverQuadCount(): Int {
        return mySpilloverEnd - spilloverStart() shr 2
    }

    fun totalCount(): Int {
        var count = 0
        var offset = 3
        val end = myHashSize shl 3

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

    override fun matchByQuad(q1: Int): Int {
        TODO("Not yet implemented")
    }

    override fun matchByQuad(q1: Int, q2: Int): Int {
        TODO("Not yet implemented")
    }

    override fun matchByQuad(q1: Int, q2: Int, q3: Int): Int {
        TODO("Not yet implemented")
    }

    override fun matchByQuad(quads: IntArray, quadLength: Int): Int {
        TODO("Not yet implemented")
    }

    private fun calculateOffset(hash: Int): Int {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Access from spillover areas
     *******************************************************************************************************************
     */

    private fun findTertiary(originalOffset: Int, q1: Int): Int {
        TODO("Not yet implemented")
    }

    private fun findTertiary(originalOffset: Int, q1: Int, q2: Int): Int {
        TODO("Not yet implemented")
    }

    private fun findTertiary(originalOffset: Int, q1: Int, q2: Int, q3: Int): Int {
        TODO("Not yet implemented")
    }

    private fun findTertiary(originalOffset: Int, quads: IntArray, quadLength: Int): Int {
        TODO("Not yet implemented")
    }

    @Suppress("NAME_SHADOWING")
    private fun verifyLongName(quads: IntArray, quadLength: Int, spillOffset: Int): Boolean {
        var spillOffset = spillOffset
        TODO("Not yet implemented")
    }

    @Suppress("NAME_SHADOWING")
    private fun verifyLongName2(quads: IntArray, quadLength: Int, spillOffset: Int): Boolean {
        var spillOffset = spillOffset
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    private fun lengthAndIndex(quadLength: Int): Int {
        TODO("Not yet implemented")
    }

    private fun spilloverStart(): Int {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Other methods
     *******************************************************************************************************************
     */

    override fun toString(): String {
        val primary = primaryQuadCount()
        val secondary = secondaryQuadCount()
        val tertiary = tertiaryQuadCount()
        val spillover = spilloverQuadCount()
        val total = totalCount()

        return "[ByteQuadsCanonicalizer: size=$size, bucketCount=$myHashSize, $primary/$secondary/$tertiary/$spillover primary/secondary/tertiary/spillover (=${primary + secondary + tertiary + spillover}), total:$total]"
    }

    companion object {

        /**
         * Limit to 32k entries as well as 32k-int (i.e. 128kb) strings so that both size and offset (in string table)
         * can be encoded in a single int.
         */
        const val MAX_ENTRIES = 0x7FFF

        private const val MAX_LENGTH_IN_QUADS = 0x7FFF

        private const val MULT = 33

        private const val MULT2 = 65599

        private const val MULT3 = 31

        internal fun calculateTertiaryShift(primarySlots: Int): Int {
            val tertiarySlots = primarySlots shr 2
            return when {
                tertiarySlots < 64 -> 4
                tertiarySlots <= 256 -> 5
                tertiarySlots <= 1024 -> 6
                else -> 7
            }
        }

        /*
         ***************************************************************************************************************
         * Life-cycle: factory methods
         ***************************************************************************************************************
         */

        fun constructFrom(propertyNames: List<Named?>, alreadyInterned: Boolean): BinaryNameMatcher {
            return construct(stringsFromNames(propertyNames, alreadyInterned))
        }

        fun construct(symbols: List<String?>): BinaryNameMatcher {
            return construct(symbols, SimpleNameMatcher.construct(null, symbols))
        }

        fun constructFrom(locale: Locale, propertyNames: List<Named?>, alreadyInterned: Boolean): BinaryNameMatcher {
            val names = stringsFromNames(propertyNames, alreadyInterned)
            return construct(names, SimpleNameMatcher.constructCaseInsensitive(locale, names))
        }

        private fun construct(symbols: List<String?>, base: SimpleNameMatcher): BinaryNameMatcher {
            val size = findSize(symbols.size)
            val lookup = symbols.toTypedArray()
            val matcher = BinaryNameMatcher(base, lookup, size)

            for (name in symbols) {
                matcher.addName(name!!)
            }

            return matcher
        }

        /*
         ***************************************************************************************************************
         * Hash calculation
         ***************************************************************************************************************
         */

        /**
         * Calculates the hash based on the quad
         *
         * @param q1 Quad of name representation
         *
         * @return The calculated hash
         */
        private fun calculateHash(q1: Int): Int {
            val hash = q1 + (q1 ushr 16) xor (q1 shl 3)
            return hash + hash ushr 11
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
        private fun calculateHash(q1: Int, q2: Int): Int {
            var hash = q1 + (q1 ushr 15) xor (q1 ushr 9)
            hash += q2 * MULT xor (q2 ushr 15)
            hash += (hash ushr 7) + (hash ushr 3)
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
        private fun calculateHash(q1: Int, q2: Int, q3: Int): Int {
            var hash = q1 + (q1 ushr 15) xor (q1 ushr 9)
            hash += ((hash * MULT) + q2) xor ((q2 ushr 15) + (q2 ushr 7))
            hash += ((hash * MULT3) + q3) xor ((q3 ushr 13) + (q3 ushr 9))
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
        private fun calculateHash(quads: IntArray, qLength: Int): Int {
            if (qLength < 4) {
                throw IllegalArgumentException("Quads length must be at least 4 (got $qLength)")
            }

            var hash = quads[0]
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
         ***************************************************************************************************************
         * Helper methods
         ***************************************************************************************************************
         */

        fun quads(name: String): IntArray {
            val bytes = name.toByteArray(Charsets.UTF_8)
            val length = bytes.size
            val buffer = IntArray(length + 3 shr 2)

            var input = 0
            var output = 0
            var left = length

            while (left > 4) {
                buffer[output++] = decodeFull(bytes, input)
                input += 4
                left -= 4
            }

            buffer[output] = decodeLast(bytes, input, left)
            return buffer
        }

        private fun decodeFull(bytes: ByteArray, offset: Int): Int {
            return (bytes[offset].toInt() shl 24) + (bytes[offset + 1].toInt() and 0xFF shl 16) +
                    (bytes[offset + 2].toInt() and 0xFF shl 8) + (bytes[offset + 3].toInt() and 0xFF)
        }

        private fun decodeLast(bytes: ByteArray, offset: Int, bytesAmount: Int): Int {
            var realOffset = offset
            var value = bytes[realOffset++].toInt() and 0xFF

            if (bytesAmount == 4) {
                value = value shl 8 or (bytes[realOffset++].toInt() and 0xFF)
            }

            if (bytesAmount >= 3) {
                value = value shl 8 or (bytes[realOffset++].toInt() and 0xFF)
            }

            if (bytesAmount >= 2) {
                value = value shl 8 or (bytes[realOffset].toInt() and 0xFF)
            }

            return value
        }

    }

}