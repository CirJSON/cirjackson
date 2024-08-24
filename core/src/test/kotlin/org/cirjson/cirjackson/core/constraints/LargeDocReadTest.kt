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
class LargeDocReadTest : AsyncTestBase() {

    private val factoryDefault = newStreamFactory()

    private val factory10k = CirJsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxDocumentLength(10_000L).build()).build()

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
        val doc = generateCirJSON(12_000)

        for (mode in ALL_STREAMING_PARSER_MODES) {
            try {
                createParser(factory10k, mode, doc).use { parser ->
                    consumeToken(parser)
                    fail("expected StreamConstraintsException")
                }
            } catch (e: StreamConstraintsException) {
                verifyMaxDocLen(factory10k, e)
            }
        }
    }

    @Test
    fun testLargeNameWithSmallLimitAsync() {
        val doc = cirJsonDoc(generateCirJSON(12_000))

        try {
            asyncForBytes(factory10k, 1000, doc, 1).use { parser ->
                consumeToken(parser)
                fail("expected StreamConstraintsException")
            }
        } catch (e: StreamConstraintsException) {
            verifyMaxDocLen(factory10k, e)
        }

        try {
            asyncForByteBuffer(factory10k, 1000, doc, 1).use { parser ->
                consumeToken(parser)
                fail("expected StreamConstraintsException")
            }
        } catch (e: StreamConstraintsException) {
            verifyMaxDocLen(factory10k, e)
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
        val stringBuilder = StringBuilder(length)
        stringBuilder.append("[\n\"root\",\n")

        var i = 0

        while (length > stringBuilder.length) {
            stringBuilder.append(++i).append(",\n")
        }

        stringBuilder.append("true \n] ")
        return stringBuilder.toString()
    }

    private fun verifyMaxDocLen(factory: CirJsonFactory, e: StreamConstraintsException) {
        verifyException(e, "Document length")
        verifyException(e, "exceeds the maximum allowed (${factory.streamReadConstraints.maxDocumentLength}")
    }

    companion object {

        private const val MAX_NAME_LENGTH = StreamReadConstraints.DEFAULT_MAX_NAME_LEN

    }

}