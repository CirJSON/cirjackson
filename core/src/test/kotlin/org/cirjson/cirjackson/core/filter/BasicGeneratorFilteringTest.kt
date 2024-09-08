package org.cirjson.cirjackson.core.filter

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.ObjectWriteContext
import org.cirjson.cirjackson.core.TestBase
import java.io.StringWriter
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class BasicGeneratorFilteringTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testNonFiltering() {
        val writer = StringWriter()
        val generator = createGenerator(writer)
        val doc =
                "{'__cirJsonId__':'root','a':123,'array':['array',1,2],'ob':{'__cirJsonId__':'ob','value0':2,'value':3,'value2':4},'b':true}"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote(doc), writer.toString())
    }

    @Test
    fun testSingleMatchFilteringWithoutPath() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), NameMatchFilter("value"),
                TokenFilter.Inclusion.ONLY_INCLUDE_ALL, false)
        val doc =
                "{'__cirJsonId__':'root','a':123,'array':['array',1,2],'ob':{'__cirJsonId__':'ob','value0':2,'value':3,'value2':4},'b':true}"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals("3", writer.toString())
    }

    @Test
    fun testSingleMatchFilteringWithPath() {
        val writer = StringWriter()
        val filter = NameMatchFilter("value")
        val generator = FilteringGeneratorDelegate(createGenerator(writer), filter,
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, false)
        assertSame(writer, generator.streamWriteOutputTarget)
        assertNotNull(generator.filterContext)
        assertSame(filter, generator.filter)
        val doc =
                "{'__cirJsonId__':'root','a':123,'array':['array',1,2],'ob':{'__cirJsonId__':'ob','value0':2,'value':3,'value2':4},'b':true}"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','ob':{'__cirJsonId__':'1','value':3}}"), writer.toString())
    }

    @Test
    fun testSingleMatchFilteringWithPathSkippedArray() {
        val writer = StringWriter()
        val filter = NameMatchFilter("value")
        val generator = FilteringGeneratorDelegate(createGenerator(writer), filter,
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, false)
        assertSame(writer, generator.streamWriteOutputTarget)
        assertNotNull(generator.filterContext)
        assertSame(filter, generator.filter)
        val doc =
                "{'__cirJsonId__':'root','array':['array',1,['array/1',2,3]],'ob':['ob',{'__cirJsonId__':'ob/0','value':'bar'}],'b':{'__cirJsonId__':'b','foo':['b/foo',1,'foo']}}"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','ob':['1',{'__cirJsonId__':'2','value':'bar'}]}"),
                writer.toString())
    }

    private fun createGenerator(writer: StringWriter): CirJsonGenerator {
        return factory.createGenerator(ObjectWriteContext.empty(), writer)
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

    private class NameExcludeFilter(private val myIncludeArrays: Boolean, vararg names: String) : TokenFilter() {

        private val myNames = HashSet(names.filterNot { it == "__cirJsonId__" })

        override fun includeElement(index: Int): TokenFilter? {
            return if (myIncludeArrays) this else null
        }

        override fun includeProperty(name: String): TokenFilter? {
            return if (name in myNames) null else this
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

    }

}