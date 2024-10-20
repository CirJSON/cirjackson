package org.cirjson.cirjackson.core.filter

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.core.ObjectWriteContext
import org.cirjson.cirjackson.core.TestBase
import java.io.StringWriter
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class BasicParserFilteringTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testNonFiltering() {
        val parser = createParser(SIMPLE)
        val result = readAndWrite(parser)
        assertEquals(SIMPLE, result)
    }

    @Test
    fun testSingleMatchFilteringWithoutPath() {
        val parser = FilteringParserDelegate(createParser(SIMPLE), NameMatchFilter("value"),
                TokenFilter.Inclusion.ONLY_INCLUDE_ALL, false)
        val result = readAndWrite(parser)
        assertEquals(apostropheToQuote("3"), result)
        assertEquals(1, parser.matchCount)
    }

    @Test
    fun testSingleMatchFilteringWithPath() {
        val doc = apostropheToQuote(
                "{'__cirJsonId__':'0','a':123,'array':['1',1,2],'ob':{'__cirJsonId__':'2','value0':2,'value':3,'value2':4},'b':true}")
        val parser = FilteringParserDelegate(createParser(doc), NameMatchFilter("a"),
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, false)
        val result = readAndWrite(parser)
        assertEquals(apostropheToQuote("{'a':123}"), result)
        assertEquals(1, parser.matchCount)
    }

    private fun createParser(doc: String): CirJsonParser {
        return factory.createParser(ObjectReadContext.empty(), doc)
    }

    private fun readAndWrite(parser: CirJsonParser): String {
        val stringWriter = StringWriter()
        val generator = factory.createGenerator(ObjectWriteContext.empty(), stringWriter)

        try {
            while (parser.nextToken() != null) {
                generator.copyCurrentEvent(parser)
            }
        } catch (e: Exception) {
            generator.flush()
            fail("Unexpected problem during `readAndWrite`. Output so far: '$stringWriter'; problem: $e")
        }

        parser.close()
        generator.close()
        return stringWriter.toString()
    }

    private class NameMatchFilter(vararg names: String) : TokenFilter() {

        private val myNames = setOf(*names)

        override fun includeProperty(name: String): TokenFilter {
            return if (name in myNames) INCLUDE_ALL else this
        }

        override fun includeScalar(): Boolean {
            return false
        }

    }

    private class StrictNameMatchFilter(vararg names: String) : TokenFilter() {

        private val myNames = setOf(*names, "__cirJsonId__")

        override fun includeProperty(name: String): TokenFilter? {
            return if (name in myNames) INCLUDE_ALL else null
        }

    }

    private class IndexMatchFilter(vararg indices: Int) : TokenFilter() {

        private val myIndices = BitSet().apply {
            for (index in indices) {
                set(index)
            }
        }

        override fun includeElement(index: Int): TokenFilter? {
            return if (myIndices[index]) INCLUDE_ALL else null
        }

        override fun includeScalar(): Boolean {
            return false
        }

    }

    private class NoArraysFilter : TokenFilter() {

        override fun filterStartArray(): TokenFilter? {
            return null
        }

    }

    private class NoObjectsFilter : TokenFilter() {

        override fun filterStartObject(): TokenFilter? {
            return null
        }

    }

    private class LoggingFilter(private val myParent: TokenFilter, private val myLog: ArrayList<String> = ArrayList()) :
            TokenFilter() {

        private fun reWrap(filter: TokenFilter?): TokenFilter? {
            return if (filter == null) {
                null
            } else if (filter === INCLUDE_ALL) {
                INCLUDE_ALL
            } else {
                LoggingFilter(filter, myLog)
            }
        }

        override fun includeElement(index: Int): TokenFilter? {
            myLog.add("includeElement: $index")
            return reWrap(myParent.includeElement(index))
        }

        override fun includeProperty(name: String): TokenFilter? {
            myLog.add("includeProperty: $name")
            return reWrap(myParent.includeProperty(name))
        }

        override fun filterStartObject(): TokenFilter? {
            myLog.add("filterStartObject")
            return reWrap(myParent.filterStartObject())
        }

        override fun filterStartArray(): TokenFilter? {
            myLog.add("filterStartArray")
            return reWrap(myParent.filterStartArray())
        }

        override fun filterFinishObject() {
            myLog.add("filterFinishObject")
            myParent.filterFinishObject()
        }

        override fun filterFinishArray() {
            myLog.add("filterFinishArray")
            myParent.filterFinishArray()
        }

        override fun includeScalar(): Boolean {
            myLog.add("includeScalar")
            return myParent.includeScalar()
        }

    }

    private class IncludeEmptyIfNotFilteredFilter : TokenFilter() {

        override fun includeEmptyArray(contentsFiltered: Boolean): Boolean {
            return !contentsFiltered
        }

        override fun includeEmptyObject(contentsFiltered: Boolean): Boolean {
            return !contentsFiltered
        }

        override fun includeScalar(): Boolean {
            return false
        }

    }

    private class IncludeEmptyFilter : TokenFilter() {

        override fun includeEmptyArray(contentsFiltered: Boolean): Boolean {
            return true
        }

        override fun includeEmptyObject(contentsFiltered: Boolean): Boolean {
            return true
        }

        override fun includeScalar(): Boolean {
            return false
        }

    }

    companion object {

        private val INCLUDE_EMPTY_IF_NOT_FILTERED = IncludeEmptyIfNotFilteredFilter()

        private val INCLUDE_EMPTY = IncludeEmptyFilter()

        private val SIMPLE = apostropheToQuote(
                "{'__cirJsonId__':'0','a':123,'array':['1',1,2],'ob':{'__cirJsonId__':'2','value0':2,'value':3,'value2':0.25},'b':true}")

    }

}