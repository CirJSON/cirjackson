package org.cirjson.cirjackson.core.io

object NumberOutput {

    private const val MILLION = 1000000

    private const val BILLION = 1000000000

    private const val BILLION_L = 1000000000L

    private const val LONG_MIN_INT = Int.MIN_VALUE.toLong()

    private const val LONG_MAX_INT = Int.MAX_VALUE.toLong()

    internal const val SMALLEST_INT = Int.MIN_VALUE.toString()

    internal const val SMALLEST_LONG = Long.MIN_VALUE.toString()

    private val TRIPLET_TO_CHARS = IntArray(1000).apply {
        var fullIndex = 0

        for (i in 0..<10) {
            for (j in 0..<10) {
                for (k in 0..<10) {
                    val encoded = (i + '0'.code shl 16) + (j + '0'.code shl 8) + (k + '0'.code)
                    this[fullIndex++] = encoded
                }
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Efficient serialization methods using raw buffers
     *******************************************************************************************************************
     */

    /**
     * Method for appending value of given `Int` value into specified `CharArray`.
     *
     * NOTE: caller must guarantee that the output buffer has enough room for String representation of the value.
     *
     * @param value Value to append to buffer
     *
     * @param buffer Buffer to append value to: caller must guarantee there is enough room
     *
     * @param offset Offset within output buffer (`buffer`) to append number at
     *
     * @return Offset within buffer after outputting `Int`
     */
    @Suppress("NAME_SHADOWING")
    fun outputInt(value: Int, buffer: CharArray, offset: Int): Int {
        var v = value
        var offset = offset
        TODO("Not yet implemented")
    }

}