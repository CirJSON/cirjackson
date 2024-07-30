package org.cirjson.cirjackson.core.io.schubfach

import org.cirjson.cirjackson.core.extensions.String

class FloatToDecimal private constructor() {

    private val myBytes = ByteArray(MAX_CHARS)

    private var myIndex = 0

    private fun toDecimalString(value: Float): String {
        return when (toDecimal(value)) {
            NON_SPECIAL -> charsToString()
            PLUS_ZERO -> "0.0"
            MINUS_ZERO -> "-0.0"
            PLUS_INF -> "Infinity"
            MINUS_INF -> "-Infinity"
            else -> "NaN"
        }
    }

    /**
     * Returns
     * | Value      | Condition                  |
     * | :--------- | :------------------------- |
     * | PLUS_ZERO  | iff v is 0.0               |
     * | MINUS_ZERO | iff v is -0.0              |
     * | PLUS_INF   | iff v is POSITIVE_INFINITY |
     * | MINUS_INF  | iff v is NEGATIVE_INFINITY |
     * | NAN        | iff v is NaN               |
     */
    private fun toDecimal(v: Float): Int {
        val bits = v.toRawBits()
        val t = bits and T_MASK
        val bq = bits ushr DoubleToDecimal.P - 1 and BQ_MASK

        if (bq < BQ_MASK) {
            myIndex = -1

            if (bits < 0) {
                append('-'.code)
            }

            return when {
                bq != 0 -> {
                    val mq = -Q_MIN + 1 - bq
                    val c = C_MIN or t

                    if (mq in 1..<P) {
                        val f = c shr mq

                        if (f shl mq == c) {
                            return toChars(f, 0)
                        }
                    }

                    toDecimal(-mq, c, 0)
                }

                t != 0 -> if (t < C_TINY) toDecimal(Q_MIN, 10 * t, -1) else toDecimal(Q_MIN, t, 0)

                bits == 0 -> PLUS_ZERO

                else -> MINUS_ZERO
            }
        }

        return when {
            t != 0 -> NAN
            bits > 0 -> PLUS_INF
            else -> MINUS_INF
        }
    }

    private fun toDecimal(q: Int, c: Int, dk: Int): Int {
        val out = c and 0x1
        val cb = c.toLong() shl 2
        val cbr = cb + 2

        val (cbl, k) = if (c != C_MIN || q == Q_MIN) {
            (cb - 2) to MathUtils.flog10pow2(q)
        } else {
            (cb - 2) to MathUtils.flog10threeQuartersPow2(q)
        }

        val h = q + MathUtils.flog2pow10(-k) + 33
        val g = MathUtils.g1(k) + 1

        val vb = rop(g, cb shl h)
        val vbl = rop(g, cbl shl h)
        val vbr = rop(g, cbr shl h)

        val s = vb shr 2

        if (s >= 100) {
            val sp10 = 10 * (s * 1_717_986_919L ushr 34).toInt()
            val tp10 = sp10 + 10
            val uPin = vbl + out <= sp10 shl 2
            val wPin = (tp10 shl 2) + out <= vbr

            if (uPin != wPin) {
                return toChars(if (uPin) sp10 else tp10, k)
            }
        }

        val t = s + 1
        val uin = vbl + out <= s shl 2
        val win = (t shl 2) + out <= vbr

        if (uin != win) {
            return toChars(if (uin) s else t, k + dk)
        }

        val cmp = vb - (s + t shl 1)
        return toChars(if (cmp < 0L || cmp == 0 && s and 0x1 == 0) s else t, k + dk)
    }

    /**
     * Formats the decimal f 10^e.
     */
    @Suppress("NAME_SHADOWING")
    private fun toChars(f: Int, e: Int): Int {
        var f = f
        var e = e
        TODO("Not yet implemented")
    }

    private fun toChars1(h: Int, l: Int, e: Int): Int {
        TODO("Not yet implemented")
    }

    @Suppress("NAME_SHADOWING")
    private fun toChars2(h: Int, l: Int, e: Int): Int {
        var e = e
        TODO("Not yet implemented")
    }

    private fun toChars3(h: Int, l: Int, e: Int): Int {
        TODO("Not yet implemented")
    }

    private fun append8Digits(m: Int) {
        TODO("Not yet implemented")
    }

    private fun removeTrailingZeroes() {
        TODO("Not yet implemented")
    }

    private fun y(a: Int): Int {
        TODO("Not yet implemented")
    }

    @Suppress("NAME_SHADOWING")
    private fun exponent(e: Int) {
        var e = e
        TODO("Not yet implemented")
    }

    private fun append(c: Int) {
        myBytes[++myIndex] = c.toByte()
    }

    private fun appendDigit(digit: Int) {
        myBytes[++myIndex] = (digit + '0'.code).toByte()
    }

    /**
     * Using the deprecated constructor enhances performance.
     */
    private fun charsToString(): String {
        return String(myBytes, 0, 0, myIndex + 1).toString()
    }

    companion object {

        /**
         * The precision in bits.
         */
        internal const val P = 24

        /**
         * Exponent width in bits.
         */
        private const val W = Float.SIZE_BITS - P

        /**
         * Minimum value of the exponent: -(2^(W-1)) - P + 3.
         */
        internal const val Q_MIN = (-1 shl W - 1) - P + 3

        /**
         * Maximum value of the exponent: 2^(W-1) - P.
         */
        internal const val Q_MAX = (1 shl W - 1) - P

        /**
         * 10^(E_MIN - 1) <= MIN_VALUE < 10^E_MIN
         */
        internal const val E_MIN = -44

        /**
         * 10^(E_MAX - 1) <= MAX_VALUE < 10^E_MAX
         */
        internal const val E_MAX = 39

        /**
         * Threshold to detect tiny values, as in section 8.1.1 of [1]
         */
        internal const val C_TINY = 8

        /**
         * The minimum k, as in section 8 of [1]
         */
        internal const val K_MIN = -45

        /**
         * The maximum k, as in section 8 of [1]
         */
        internal const val K_MAX = 31

        /**
         * H is as in section 8 of [1].
         */
        internal const val H = 9

        /**
         * Minimum value of the significand of a normal value: 2^(P-1).
         */
        private const val C_MIN = 1 shl P - 1

        /**
         * Mask to extract the biased exponent.
         */
        private const val BQ_MASK = (1 shl W) - 1

        /**
         * Mask to extract the fraction bits.
         */
        private const val T_MASK = (1 shl P - 1) - 1

        /**
         * Used in rop().
         */
        private const val MASK_32 = (1L shl 32) - 1

        /**
         * Used for left-to-tight digit extraction.
         */
        private const val MASK_28 = (1 shl 28) - 1

        private const val NON_SPECIAL: Int = 0

        private const val PLUS_ZERO: Int = 1

        private const val MINUS_ZERO: Int = 2

        private const val PLUS_INF: Int = 3

        private const val MINUS_INF: Int = 4

        private const val NAN: Int = 5

        /**
         * Room for the longest of the forms
         *
         * | Form            | Length           |
         * | :-------------- | :--------------- |
         * | -ddddd.dddd     | H + 2 characters |
         * | -0.00ddddddddd  | H + 5 characters |
         * | -d.ddddddddE-ee | H + 6 characters |
         *
         * where there are H digits d
         */
        private const val MAX_CHARS = H + 6

        /**
         * Returns a string rendering of the {@code float} argument.
         *
         * <p>The characters of the result are all drawn from the ASCII set.
         * <ul>
         * <li> Any NaN, whether quiet or signaling, is rendered as
         * {@code "NaN"}, regardless of the sign bit.
         * <li> The infinities +&infin; and -&infin; are rendered as
         * {@code "Infinity"} and {@code "-Infinity"}, respectively.
         * <li> The positive and negative zeroes are rendered as
         * {@code "0.0"} and {@code "-0.0"}, respectively.
         * <li> A finite negative {@code v} is rendered as the sign
         * '{@code -}' followed by the rendering of the magnitude -{@code v}.
         * <li> A finite positive {@code v} is rendered in two stages:
         * <ul>
         * <li> <em>Selection of a decimal</em>: A well-defined
         * decimal <i>d</i><sub><code>v</code></sub> is selected
         * to represent {@code v}.
         * <li> <em>Formatting as a string</em>: The decimal
         * <i>d</i><sub><code>v</code></sub> is formatted as a string,
         * either in plain or in computerized scientific notation,
         * depending on its value.
         * </ul>
         * </ul>
         *
         * <p>A <em>decimal</em> is a number of the form
         * <i>d</i>&times;10<sup><i>i</i></sup>
         * for some (unique) integers <i>d</i> &gt; 0 and <i>i</i> such that
         * <i>d</i> is not a multiple of 10.
         * These integers are the <em>significand</em> and
         * the <em>exponent</em>, respectively, of the decimal.
         * The <em>length</em> of the decimal is the (unique)
         * integer <i>n</i> meeting
         * 10<sup><i>n</i>-1</sup> &le; <i>d</i> &lt; 10<sup><i>n</i></sup>.
         *
         * <p>The decimal <i>d</i><sub><code>v</code></sub>
         * for a finite positive {@code v} is defined as follows:
         * <ul>
         * <li>Let <i>R</i> be the set of all decimals that round to {@code v}
         * according to the usual round-to-closest rule of
         * IEEE 754 floating-point arithmetic.
         * <li>Let <i>m</i> be the minimal length over all decimals in <i>R</i>.
         * <li>When <i>m</i> &ge; 2, let <i>T</i> be the set of all decimals
         * in <i>R</i> with length <i>m</i>.
         * Otherwise, let <i>T</i> be the set of all decimals
         * in <i>R</i> with length 1 or 2.
         * <li>Define <i>d</i><sub><code>v</code></sub> as
         * the decimal in <i>T</i> that is closest to {@code v}.
         * Or if there are two such decimals in <i>T</i>,
         * select the one with the even significand (there is exactly one).
         * </ul>
         *
         * <p>The (uniquely) selected decimal <i>d</i><sub><code>v</code></sub>
         * is then formatted.
         *
         * <p>Let <i>d</i>, <i>i</i> and <i>n</i> be the significand, exponent and
         * length of <i>d</i><sub><code>v</code></sub>, respectively.
         * Further, let <i>e</i> = <i>n</i> + <i>i</i> - 1 and let
         * <i>d</i><sub>1</sub>&hellip;<i>d</i><sub><i>n</i></sub>
         * be the usual decimal expansion of the significand.
         * Note that <i>d</i><sub>1</sub> &ne; 0 &ne; <i>d</i><sub><i>n</i></sub>.
         * <ul>
         * <li>Case -3 &le; <i>e</i> &lt; 0:
         * <i>d</i><sub><code>v</code></sub> is formatted as
         * <code>0.0</code>&hellip;<code>0</code><!--
         * --><i>d</i><sub>1</sub>&hellip;<i>d</i><sub><i>n</i></sub>,
         * where there are exactly -(<i>n</i> + <i>i</i>) zeroes between
         * the decimal point and <i>d</i><sub>1</sub>.
         * For example, 123 &times; 10<sup>-4</sup> is formatted as
         * {@code 0.0123}.
         * <li>Case 0 &le; <i>e</i> &lt; 7:
         * <ul>
         * <li>Subcase <i>i</i> &ge; 0:
         * <i>d</i><sub><code>v</code></sub> is formatted as
         * <i>d</i><sub>1</sub>&hellip;<i>d</i><sub><i>n</i></sub><!--
         * --><code>0</code>&hellip;<code>0.0</code>,
         * where there are exactly <i>i</i> zeroes
         * between <i>d</i><sub><i>n</i></sub> and the decimal point.
         * For example, 123 &times; 10<sup>2</sup> is formatted as
         * {@code 12300.0}.
         * <li>Subcase <i>i</i> &lt; 0:
         * <i>d</i><sub><code>v</code></sub> is formatted as
         * <i>d</i><sub>1</sub>&hellip;<!--
         * --><i>d</i><sub><i>n</i>+<i>i</i></sub>.<!--
         * --><i>d</i><sub><i>n</i>+<i>i</i>+1</sub>&hellip;<!--
         * --><i>d</i><sub><i>n</i></sub>.
         * There are exactly -<i>i</i> digits to the right of
         * the decimal point.
         * For example, 123 &times; 10<sup>-1</sup> is formatted as
         * {@code 12.3}.
         * </ul>
         * <li>Case <i>e</i> &lt; -3 or <i>e</i> &ge; 7:
         * computerized scientific notation is used to format
         * <i>d</i><sub><code>v</code></sub>.
         * Here <i>e</i> is formatted as by {@link Integer#toString(int)}.
         * <ul>
         * <li>Subcase <i>n</i> = 1:
         * <i>d</i><sub><code>v</code></sub> is formatted as
         * <i>d</i><sub>1</sub><code>.0E</code><i>e</i>.
         * For example, 1 &times; 10<sup>23</sup> is formatted as
         * {@code 1.0E23}.
         * <li>Subcase <i>n</i> &gt; 1:
         * <i>d</i><sub><code>v</code></sub> is formatted as
         * <i>d</i><sub>1</sub><code>.</code><i>d</i><sub>2</sub><!--
         * -->&hellip;<i>d</i><sub><i>n</i></sub><code>E</code><i>e</i>.
         * For example, 123 &times; 10<sup>-21</sup> is formatted as
         * {@code 1.23E-19}.
         * </ul>
         * </ul>
         *
         * @param  v the {@code float} to be rendered.
         * @return a string rendering of the argument.
         */
        fun toString(v: Float): String {
            return FloatToDecimal().toDecimalString(v)
        }

        /**
         * Computes rop(cp g 2^(-95)) See section 9.10 and figure 5 of [1].
         */
        private fun rop(g: Long, cp: Long): Int {
            val x = MathUtils.multiplyHigh(g, cp)
            val vpb = x ushr 31
            return ((x and MASK_32) + MASK_32 ushr 32 or vpb).toInt()
        }

    }

}