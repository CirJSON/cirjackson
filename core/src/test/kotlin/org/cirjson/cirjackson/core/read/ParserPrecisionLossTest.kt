package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class ParserPrecisionLossTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testCopyCurrentEventBigDecimal() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                copyCurrentEventBigDecimal(generatorMode, parserMode)
            }
        }
    }

    private fun copyCurrentEventBigDecimal(generatorMode: Int, parserMode: Int) {
        val value = "1E+999"
        val doc = "$value "
        val parser = createParser(factory, parserMode, doc)
        parser.nextToken()
        val generator = createGenerator(factory, generatorMode)
        generator.copyCurrentEventExact(parser)
        generator.close()
        parser.close()
        assertEquals(value, generator.streamWriteOutputTarget!!.toString())
    }

}