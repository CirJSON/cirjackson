package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.io.schubfach.DoubleToDecimal
import org.cirjson.cirjackson.core.io.schubfach.FloatToDecimal

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

        if (v < 0) {
            if (v == Int.MIN_VALUE) {
                return outputSmallestInt(buffer, offset)
            }

            buffer[offset++] = '-'
            v = -v
        }

        if (v < MILLION) {
            return when {
                v < 10 -> {
                    buffer[offset] = ('0'.code + v).toChar()
                    offset + 1
                }

                v < 1000 -> leading(v, buffer, offset)

                else -> {
                    val thousands = divideBy1000(v)
                    v -= thousands * 1000
                    offset = leading(thousands, buffer, offset)
                    offset = full(v, buffer, offset)
                    offset
                }
            }
        }

        if (v >= BILLION) {
            v -= BILLION

            buffer[offset++] = if (v >= BILLION) {
                v -= BILLION
                '2'
            } else {
                '1'
            }

            return outputFullBillion(v, buffer, offset)
        }

        var newValue = divideBy1000(v)
        val ones = v - newValue * 1000
        v = newValue
        newValue = divideBy1000(newValue)
        val thousands = v - newValue * 1000
        offset = leading(newValue, buffer, offset)
        offset = full(thousands, buffer, offset)
        return full(ones, buffer, offset)
    }

    /**
     * Method for appending value of given `Int` value into specified `ByteArray`.
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
    fun outputInt(value: Int, buffer: ByteArray, offset: Int): Int {
        var v = value
        var offset = offset

        if (v < 0) {
            if (v == Int.MIN_VALUE) {
                return outputSmallestInt(buffer, offset)
            }

            buffer[offset++] = '-'.code.toByte()
            v = -v
        }

        if (v < MILLION) {
            return when {
                v < 10 -> {
                    buffer[offset] = ('0'.code + v).toByte()
                    offset + 1
                }

                v < 1000 -> leading(v, buffer, offset)

                else -> {
                    val thousands = divideBy1000(v)
                    v -= thousands * 1000
                    offset = leading(thousands, buffer, offset)
                    offset = full(v, buffer, offset)
                    offset
                }
            }
        }

        if (v >= BILLION) {
            v -= BILLION

            buffer[offset++] = if (v >= BILLION) {
                v -= BILLION
                '2'.code.toByte()
            } else {
                '1'.code.toByte()
            }

            return outputFullBillion(v, buffer, offset)
        }

        var newValue = divideBy1000(v)
        val ones = v - newValue * 1000
        v = newValue
        newValue = divideBy1000(newValue)
        val thousands = v - newValue * 1000
        offset = leading(newValue, buffer, offset)
        offset = full(thousands, buffer, offset)
        return full(ones, buffer, offset)
    }

    /**
     * Method for appending value of given `Long` value into specified `CharArray`.
     *
     * NOTE: caller must guarantee that the output buffer has enough room for String representation of the value.
     *
     * @param value Value to append to buffer
     *
     * @param buffer Buffer to append value to: caller must guarantee there is enough room
     *
     * @param offset Offset within output buffer (`buffer`) to append number at
     *
     * @return Offset within buffer after outputting `Long`
     */
    @Suppress("NAME_SHADOWING")
    fun outputLong(value: Long, buffer: CharArray, offset: Int): Int {
        var v = value
        var offset = offset

        if (v < 0L) {
            if (v > LONG_MIN_INT) {
                return outputInt(v.toInt(), buffer, offset)
            }

            if (v == Long.MIN_VALUE) {
                return outputSmallestLong(buffer, offset)
            }

            buffer[offset++] = '-'
            v = -v
        } else if (v <= LONG_MAX_INT) {
            return outputInt(v.toInt(), buffer, offset)
        }

        var upper = v / BILLION_L
        v -= upper * BILLION_L

        offset = if (upper < BILLION_L) {
            outputUptoBillion(upper.toInt(), buffer, offset)
        } else {
            val high = upper / BILLION_L
            upper -= high * BILLION_L
            offset = leading(high.toInt(), buffer, offset)
            outputFullBillion(upper.toInt(), buffer, offset)
        }

        return outputFullBillion(v.toInt(), buffer, offset)
    }

    /**
     * Method for appending value of given `Long` value into specified `ByteArray`.
     *
     * NOTE: caller must guarantee that the output buffer has enough room for String representation of the value.
     *
     * @param value Value to append to buffer
     *
     * @param buffer Buffer to append value to: caller must guarantee there is enough room
     *
     * @param offset Offset within output buffer (`buffer`) to append number at
     *
     * @return Offset within buffer after outputting `Long`
     */
    @Suppress("NAME_SHADOWING")
    fun outputLong(value: Long, buffer: ByteArray, offset: Int): Int {
        var v = value
        var offset = offset

        if (v < 0L) {
            if (v > LONG_MIN_INT) {
                return outputInt(v.toInt(), buffer, offset)
            }

            if (v == Long.MIN_VALUE) {
                return outputSmallestLong(buffer, offset)
            }

            buffer[offset++] = '-'.code.toByte()
            v = -v
        } else if (v <= LONG_MAX_INT) {
            return outputInt(v.toInt(), buffer, offset)
        }

        var upper = v / BILLION_L
        v -= upper * BILLION_L

        offset = if (upper < BILLION_L) {
            outputUptoBillion(upper.toInt(), buffer, offset)
        } else {
            val high = upper / BILLION_L
            upper -= high * BILLION_L
            offset = leading(high.toInt(), buffer, offset)
            outputFullBillion(upper.toInt(), buffer, offset)
        }

        return outputFullBillion(v.toInt(), buffer, offset)
    }

    /**
     * Optimized code for integer division by `1000`; typically 50% higher throughput for calculation
     */
    internal fun divideBy1000(number: Int): Int {
        return (number * 274_877_907L ushr 38).toInt()
    }

    /*
     *******************************************************************************************************************
     * Convenience serialization methods
     *******************************************************************************************************************
     */

    /**
     * @param v Double
     *
     * @param useFastWriter whether to use Schubfach algorithm to write output (default `false`)
     *
     * @return Double as a string
     */
    fun toString(v: Double, useFastWriter: Boolean = false): String {
        return if (useFastWriter) DoubleToDecimal.toString(v) else v.toString()
    }

    /**
     * @param v Float
     *
     * @param useFastWriter whether to use Schubfach algorithm to write output (default `false`)
     *
     * @return Float as a string
     */
    fun toString(v: Float, useFastWriter: Boolean = false): String {
        return if (useFastWriter) FloatToDecimal.toString(v) else v.toString()
    }

    /*
     *******************************************************************************************************************
     * Internal helper methods
     *******************************************************************************************************************
     */

    @Suppress("NAME_SHADOWING")
    private fun outputUptoBillion(v: Int, buffer: CharArray, offset: Int): Int {
        var offset = offset

        if (v < MILLION) {
            return if (v < 1000) {
                leading(v, buffer, offset)
            } else {
                val thousands = divideBy1000(v)
                val ones = v - thousands * 1000
                outputUptoMillion(buffer, offset, thousands, ones)
            }
        }

        var thousands = divideBy1000(v)
        val ones = v - thousands * 1000
        val millions = divideBy1000(thousands)
        thousands -= millions * 1000

        offset = leading(millions, buffer, offset)

        var encoding = TRIPLET_TO_CHARS[thousands]
        buffer[offset++] = (encoding shr 16).toChar()
        buffer[offset++] = (encoding shr 8 and 0x7F).toChar()
        buffer[offset++] = (encoding and 0x7F).toChar()

        encoding = TRIPLET_TO_CHARS[ones]
        buffer[offset++] = (encoding shr 16).toChar()
        buffer[offset++] = (encoding shr 8 and 0x7F).toChar()
        buffer[offset++] = (encoding and 0x7F).toChar()

        return offset
    }

    @Suppress("NAME_SHADOWING")
    private fun outputFullBillion(v: Int, buffer: CharArray, offset: Int): Int {
        var offset = offset
        var thousands = divideBy1000(v)
        val ones = v - thousands * 1000
        val millions = divideBy1000(thousands)

        var encoding = TRIPLET_TO_CHARS[millions]
        buffer[offset++] = (encoding shr 16).toChar()
        buffer[offset++] = (encoding shr 8 and 0x7F).toChar()
        buffer[offset++] = (encoding and 0x7F).toChar()

        thousands -= millions * 1000
        encoding = TRIPLET_TO_CHARS[thousands]
        buffer[offset++] = (encoding shr 16).toChar()
        buffer[offset++] = (encoding shr 8 and 0x7F).toChar()
        buffer[offset++] = (encoding and 0x7F).toChar()

        encoding = TRIPLET_TO_CHARS[ones]
        buffer[offset++] = (encoding shr 16).toChar()
        buffer[offset++] = (encoding shr 8 and 0x7F).toChar()
        buffer[offset++] = (encoding and 0x7F).toChar()

        return offset
    }

    @Suppress("NAME_SHADOWING")
    private fun outputUptoBillion(v: Int, buffer: ByteArray, offset: Int): Int {
        var offset = offset

        if (v < MILLION) {
            return if (v < 1000) {
                leading(v, buffer, offset)
            } else {
                val thousands = divideBy1000(v)
                val ones = v - thousands * 1000
                outputUptoMillion(buffer, offset, thousands, ones)
            }
        }

        var thousands = divideBy1000(v)
        val ones = v - thousands * 1000
        val millions = divideBy1000(thousands)
        thousands -= millions * 1000

        offset = leading(millions, buffer, offset)

        var encoding = TRIPLET_TO_CHARS[thousands]
        buffer[offset++] = (encoding shr 16).toByte()
        buffer[offset++] = (encoding shr 8 and 0x7F).toByte()
        buffer[offset++] = (encoding and 0x7F).toByte()

        encoding = TRIPLET_TO_CHARS[ones]
        buffer[offset++] = (encoding shr 16).toByte()
        buffer[offset++] = (encoding shr 8 and 0x7F).toByte()
        buffer[offset++] = (encoding and 0x7F).toByte()

        return offset
    }

    @Suppress("NAME_SHADOWING")
    private fun outputFullBillion(v: Int, buffer: ByteArray, offset: Int): Int {
        var offset = offset
        var thousands = divideBy1000(v)
        val ones = v - thousands * 1000
        val millions = divideBy1000(thousands)

        var encoding = TRIPLET_TO_CHARS[millions]
        buffer[offset++] = (encoding shr 16).toByte()
        buffer[offset++] = (encoding shr 8 and 0x7F).toByte()
        buffer[offset++] = (encoding and 0x7F).toByte()

        thousands -= millions * 1000
        encoding = TRIPLET_TO_CHARS[thousands]
        buffer[offset++] = (encoding shr 16).toByte()
        buffer[offset++] = (encoding shr 8 and 0x7F).toByte()
        buffer[offset++] = (encoding and 0x7F).toByte()

        encoding = TRIPLET_TO_CHARS[ones]
        buffer[offset++] = (encoding shr 16).toByte()
        buffer[offset++] = (encoding shr 8 and 0x7F).toByte()
        buffer[offset++] = (encoding and 0x7F).toByte()

        return offset
    }

    @Suppress("NAME_SHADOWING")
    private fun outputUptoMillion(buffer: CharArray, offset: Int, thousands: Int, ones: Int): Int {
        var offset = offset
        var encoding = TRIPLET_TO_CHARS[thousands]

        if (thousands > 9) {
            if (thousands > 99) {
                buffer[offset++] = (encoding shr 16).toChar()
            }

            buffer[offset++] = (encoding shr 8 and 0x7F).toChar()
        }

        buffer[offset++] = (encoding and 0x7F).toChar()

        encoding = TRIPLET_TO_CHARS[ones]
        buffer[offset++] = (encoding shr 16).toChar()
        buffer[offset++] = (encoding shr 8 and 0x7F).toChar()
        buffer[offset++] = (encoding and 0x7F).toChar()

        return offset
    }

    @Suppress("NAME_SHADOWING")
    private fun outputUptoMillion(buffer: ByteArray, offset: Int, thousands: Int, ones: Int): Int {
        var offset = offset
        var encoding = TRIPLET_TO_CHARS[thousands]

        if (thousands > 9) {
            if (thousands > 99) {
                buffer[offset++] = (encoding shr 16).toByte()
            }

            buffer[offset++] = (encoding shr 8 and 0x7F).toByte()
        }

        buffer[offset++] = (encoding and 0x7F).toByte()

        encoding = TRIPLET_TO_CHARS[ones]
        buffer[offset++] = (encoding shr 16).toByte()
        buffer[offset++] = (encoding shr 8 and 0x7F).toByte()
        buffer[offset++] = (encoding and 0x7F).toByte()

        return offset
    }

    @Suppress("NAME_SHADOWING")
    private fun leading(t: Int, buffer: CharArray, offset: Int): Int {
        var offset = offset
        val encoding = TRIPLET_TO_CHARS[t]

        if (t > 9) {
            if (t > 99) {
                buffer[offset++] = (encoding shr 16).toChar()
            }

            buffer[offset++] = (encoding shr 8 and 0x7F).toChar()
        }

        buffer[offset++] = (encoding and 0x7F).toChar()

        return offset
    }

    @Suppress("NAME_SHADOWING")
    private fun leading(t: Int, buffer: ByteArray, offset: Int): Int {
        var offset = offset
        val encoding = TRIPLET_TO_CHARS[t]

        if (t > 9) {
            if (t > 99) {
                buffer[offset++] = (encoding shr 16).toByte()
            }

            buffer[offset++] = (encoding shr 8 and 0x7F).toByte()
        }

        buffer[offset++] = (encoding and 0x7F).toByte()

        return offset
    }

    @Suppress("NAME_SHADOWING")
    private fun full(t: Int, buffer: CharArray, offset: Int): Int {
        var offset = offset
        val encoding = TRIPLET_TO_CHARS[t]
        buffer[offset++] = (encoding shr 16).toChar()
        buffer[offset++] = (encoding shr 8 and 0x7F).toChar()
        buffer[offset++] = (encoding and 0x7F).toChar()

        return offset
    }

    @Suppress("NAME_SHADOWING")
    private fun full(t: Int, buffer: ByteArray, offset: Int): Int {
        var offset = offset
        val encoding = TRIPLET_TO_CHARS[t]
        buffer[offset++] = (encoding shr 16).toByte()
        buffer[offset++] = (encoding shr 8 and 0x7F).toByte()
        buffer[offset++] = (encoding and 0x7F).toByte()

        return offset
    }

    private fun outputSmallestLong(buffer: CharArray, offset: Int): Int {
        val length = SMALLEST_LONG.length
        SMALLEST_LONG.toCharArray(buffer, offset, 0, length)
        return offset + length
    }

    @Suppress("NAME_SHADOWING")
    private fun outputSmallestLong(buffer: ByteArray, offset: Int): Int {
        var offset = offset
        val length = SMALLEST_LONG.length

        for (i in 0..<length) {
            buffer[offset++] = SMALLEST_LONG[i].code.toByte()
        }

        return offset
    }

    private fun outputSmallestInt(buffer: CharArray, offset: Int): Int {
        val length = SMALLEST_INT.length
        SMALLEST_INT.toCharArray(buffer, offset, 0, length)
        return offset + length
    }

    @Suppress("NAME_SHADOWING")
    private fun outputSmallestInt(buffer: ByteArray, offset: Int): Int {
        var offset = offset
        val length = SMALLEST_INT.length

        for (i in 0..<length) {
            buffer[offset++] = SMALLEST_INT[i].code.toByte()
        }

        return offset
    }

}