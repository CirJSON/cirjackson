package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.TestBase
import java.util.*

open class StringGenerationTestBase : TestBase() {

    protected fun generateMediumText(size: Int): String {
        val stringBuilder = StringBuilder(size + 1000)
        val random = Random(size.toLong())

        do {
            when (random.nextInt(4)) {
                0 -> stringBuilder.append(" foo")
                1 -> stringBuilder.append(" bar")
                2 -> stringBuilder.append(stringBuilder.length)
                3 -> stringBuilder.append(" \"stuff\"")
            }
        } while (stringBuilder.length < size)

        return stringBuilder.toString()
    }

    protected fun generateRandom(size: Int): String {
        val stringBuilder = StringBuilder(size + 1000)
        val random = Random(size.toLong())

        for (i in 0..<size) {
            if (random.nextBoolean()) {
                var value = random.nextInt() and 0xFFFF

                if (value in 0xD800..0xDFFF) {
                    val fullValue = random.nextInt() and 0xFFFFF
                    stringBuilder.append((0xD800 + (fullValue shr 10)).toChar())
                    value = 0xDC00 + (fullValue and 0x3FF)
                }

                stringBuilder.append(value.toChar())
            } else {
                stringBuilder.append((random.nextInt() and 0x7F).toChar())
            }
        }

        return stringBuilder.toString()
    }

    companion object {

        val SIZES = intArrayOf(1100, 2300, 3800, 7500, 19000, 33333)

        val SAMPLES = arrayOf("\"test\"", "\n", "\\n", "\r\n", "a\\b", "tab:\nok?",
                "a\tb\tc\n\u000cdef\t \tg\"\"\"h\"\\ijklmn\b", "\"\"\"", "\\r)'\"",
                "Longer text & other stuff:\twith some\r\n\r\n random linefeeds etc added in to cause some \"special\" handling \\\\ to occur...\n")

    }

}