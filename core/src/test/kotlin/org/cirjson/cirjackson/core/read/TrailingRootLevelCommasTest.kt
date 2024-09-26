package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.Test
import kotlin.test.fail

class TrailingRootLevelCommasTest : TestBase() {

    val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_MISSING_VALUES).build()

    @Test
    fun testRootLevelCommas() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            rootLevelCommas(mode)
        }
    }

    private fun rootLevelCommas(mode: Int) {
        val doc = ","
        val parser = createParser(factory, mode, doc)

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character (','")
        }

        parser.close()
    }

}