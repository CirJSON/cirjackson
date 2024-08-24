package org.cirjson.cirjackson.core.constraints

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.StreamReadConstraints
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.exception.StreamConstraintsException
import org.cirjson.cirjackson.core.support.AsyncReaderWrapperBase
import kotlin.test.Test
import kotlin.test.fail

@Suppress("ControlFlowWithEmptyBody")
class LargeNameReadTest : AsyncTestBase() {

    private val factoryDefault = newStreamFactory()

    private val factoryName100 =
            CirJsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder().maxNameLength(100).build())
                    .build()

    @Test
    fun testLargeName() {
        val doc = generateCirJSON(MAX_NAME_LENGTH - 100)

        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            createParser(factoryDefault, mode, doc).use { parser ->
                consumeToken(parser)
            }
        }
    }

    @Test
    fun testLargeNameWithSmallLimit() {
        val doc = generateCirJSON(1000)

        for (mode in ALL_STREAMING_PARSER_MODES) {
            try {
                createParser(factoryName100, mode, doc).use { parser ->
                    consumeToken(parser)
                    fail("expected StreamConstraintsException")
                }
            } catch (e: StreamConstraintsException) {
                verifyException(e, "Name length")
            }
        }
    }

    @Test
    fun testLargeNameWithSmallLimitAsync() {
        val doc = cirJsonDoc(generateCirJSON(1000))

        try {
            asyncForBytes(factoryName100, 1000, doc, 1).use { parser ->
                consumeToken(parser)
                fail("expected StreamConstraintsException")
            }
        } catch (e: StreamConstraintsException) {
            verifyException(e, "Name length")
        }

        try {
            asyncForByteBuffer(factoryName100, 1000, doc, 1).use { parser ->
                consumeToken(parser)
                fail("expected StreamConstraintsException")
            }
        } catch (e: StreamConstraintsException) {
            verifyException(e, "Name length")
        }
    }

    private fun consumeToken(parser: CirJsonParser) {
        while (parser.nextToken() != null) {
        }
    }

    private fun consumeToken(wrapperBase: AsyncReaderWrapperBase) {
        while (wrapperBase.nextToken() != null) {
        }
    }

    private fun generateCirJSON(length: Int): String {
        val stringBuilder = StringBuilder(length + 22)
        stringBuilder.append("{\"__cirJsonId__\":\"root\",\"")

        for (i in 1..length) {
            stringBuilder.append('a')
        }

        stringBuilder.append("\":\"value\"}")
        return stringBuilder.toString()
    }

    companion object {

        private const val MAX_NAME_LENGTH = StreamReadConstraints.DEFAULT_MAX_NAME_LEN

    }

}