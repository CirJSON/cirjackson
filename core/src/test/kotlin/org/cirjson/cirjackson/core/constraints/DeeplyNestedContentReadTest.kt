package org.cirjson.cirjackson.core.constraints

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.StreamReadConstraints
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.StreamConstraintsException
import kotlin.test.Test
import kotlin.test.fail

class DeeplyNestedContentReadTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testDeepNestingStreaming() {
        val doc = createDeepNestedDoc()

        for (mode in ALL_STREAMING_PARSER_MODES) {
            createParser(factory, mode, doc).use { parser ->
                deepNestingStreaming(parser)
            }
        }
    }

    @Suppress("ControlFlowWithEmptyBody")
    private fun deepNestingStreaming(parser: CirJsonParser) {
        try {
            while (parser.nextToken() != null) {
            }
            fail("expected StreamConstraintsException")
        } catch (e: StreamConstraintsException) {
            verifyException(e,
                    "Document nesting depth ($OVER_MAX_NESTING) exceeds the maximum allowed ($MAX_NESTING, from `StreamReadConstraints.maxNestingDepth`)")
        }
    }

    private fun createDeepNestedDoc(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append("[\"root\", ")

        for (i in 1..OVER_MAX_NESTING) {
            stringBuilder.append("{ \"__cirJsonId__\": \"obj/$i\", \"a\": [ \"array/$i\", ")
        }

        stringBuilder.append(" \"val\" ")

        for (i in 1..OVER_MAX_NESTING) {
            stringBuilder.append("]}")
        }

        stringBuilder.append("]")
        return stringBuilder.toString()
    }

    companion object {

        private const val MAX_NESTING = StreamReadConstraints.DEFAULT_MAX_DEPTH

        private const val OVER_MAX_NESTING = MAX_NESTING + 1

    }

}