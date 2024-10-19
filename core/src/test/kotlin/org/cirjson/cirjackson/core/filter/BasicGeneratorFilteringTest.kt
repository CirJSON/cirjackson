package org.cirjson.cirjackson.core.filter

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.ObjectWriteContext
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.io.SerializedString
import java.io.StringWriter
import java.math.BigDecimal
import java.math.BigInteger
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

    @Test
    fun testSingleMatchFilteringWithPathRawBinary() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), NameMatchFilter("array"),
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, false)

        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeName("header")
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeNumber(2)
        generator.writeBinary(byteArrayOf(1))
        generator.writeRawValue(SerializedString("1"))
        generator.writeRawValue("2")
        generator.writeEndArray()

        generator.writeName("array")
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeBinary(byteArrayOf(1))
        generator.writeNumber(1.toShort())
        generator.writeNumber(2)
        generator.writeNumber(3L)
        generator.writeNumber(BigInteger.valueOf(4))
        generator.writeRaw(" ")
        generator.writeNumber(BigDecimal("5.0"))
        generator.writeRaw(SerializedString(" /*x*/"))
        generator.writeNumber(6.25f)
        generator.writeNumber(7.5)
        generator.writeEndArray()

        generator.writeArrayPropertyStart("extra")
        generator.writeArrayId(Any())
        generator.writeBinary(byteArrayOf(1))
        generator.writeNumber(1.toShort())
        generator.writeNumber(2)
        generator.writeNumber(3L)
        generator.writeNumber(BigInteger.valueOf(4))
        generator.writeNumber(BigDecimal("5.0"))
        generator.writeNumber(6.25f)
        generator.writeNumber(7.5)
        generator.writeEndArray()

        generator.writeEndObject()
        generator.close()

        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','array':['1','AQ==',1,2,3,4 ,5.0 /*x*/,6.25,7.5]}"),
                writer.toString())
        assertEquals(1, generator.matchCount)
    }

    @Test
    fun testMultipleMatchFilteringWithPathAlternate() {
        multipleMatchFilteringWithPathAlternate(true)
        multipleMatchFilteringWithPathAlternate(false)
    }

    private fun multipleMatchFilteringWithPathAlternate(exclude: Boolean) {
        val writer = StringWriter()
        val filter = if (exclude) NameExcludeFilter(true, "value", "a") else NameMatchFilter("value")
        val generator = FilteringGeneratorDelegate(createGenerator(writer), filter,
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)

        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeName(SerializedString("a"))
        generator.writeNumber(123)

        generator.writeName("array")
        generator.writeStartArray(2)
        generator.writeArrayId(2)
        generator.writeNumber("1")
        generator.writeNumber(2.toShort())
        generator.writeEndArray()

        generator.writeName(SerializedString("ob"))
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeNumberProperty("value0", 2)
        generator.writeName(SerializedString("value"))
        generator.writeStartArray(1)
        generator.writeArrayId(1)
        generator.writeString(SerializedString("x"))
        generator.writeEndArray()
        generator.writeStringProperty("value2", "foo")

        generator.writeEndObject()

        generator.writeBooleanProperty("b", true)

        generator.writeEndObject()
        generator.close()

        if (exclude) {
            assertEquals(apostropheToQuote(
                    "{'__cirJsonId__':'0','array':['1',1,2],'ob':{'__cirJsonId__':'2','value0':2,'value2':'foo'},'b':true}"),
                    writer.toString())
            assertEquals(5, generator.matchCount)
        } else {
            assertEquals(apostropheToQuote("{'__cirJsonId__':'0','ob':{'__cirJsonId__':'1','value':['2','x']}}"),
                    writer.toString())
            assertEquals(1, generator.matchCount)
        }
    }

    @Test
    fun testMultipleMatchFilteringWithPath1() {
        var writer = StringWriter()
        var generator = FilteringGeneratorDelegate(createGenerator(writer), NameMatchFilter("value0", "value2"),
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)
        val doc =
                "{'__cirJsonId__':'0','a':123,'array':['1',1,2],'ob':{'__cirJsonId__':'2','value0':2,'value':3,'value2':4},'b':true}"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','ob':{'__cirJsonId__':'1','value0':2,'value2':4}}"),
                writer.toString())

        writer = StringWriter()
        generator = FilteringGeneratorDelegate(createGenerator(writer), NameExcludeFilter(true, "ob"),
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','a':123,'array':['1',1,2],'b':true}"), writer.toString())

        writer = StringWriter()
        generator = FilteringGeneratorDelegate(createGenerator(writer), NameExcludeFilter(false, "ob"),
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','a':123,'b':true}"), writer.toString())
    }

    @Test
    fun testMultipleMatchFilteringWithPath2() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), NameMatchFilter("array", "b", "value"),
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)
        val doc =
                "{'__cirJsonId__':'0','a':123,'array':['1',1,2],'ob':{'__cirJsonId__':'2','value0':2,'value':3,'value2':4},'b':true}"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote(
                "{'__cirJsonId__':'0','array':['1',1,2],'ob':{'__cirJsonId__':'2','value':3},'b':true}"),
                writer.toString())
        assertEquals(3, generator.matchCount)
    }

    @Test
    fun testMultipleMatchFilteringWithPath3() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), NameMatchFilter("value"),
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)
        val doc =
                "{'__cirJsonId__':'0','root':{'__cirJsonId__':'1','a0':true,'a':{'__cirJsonId__':'2','value':3},'b':{'__cirJsonId__':'3','value':'abc'}},'b0':false}"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote(
                "{'__cirJsonId__':'0','root':{'__cirJsonId__':'1','a':{'__cirJsonId__':'2','value':3},'b':{'__cirJsonId__':'3','value':'abc'}}}"),
                writer.toString())
        assertEquals(2, generator.matchCount)
    }

    @Test
    fun testMultipleMatchFilteringWithPath4() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), NameMatchFilter("b0"),
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)
        val doc =
                "{'__cirJsonId__':'0','root':{'__cirJsonId__':'1','a0':true,'a':{'__cirJsonId__':'2','value':3},'b':{'__cirJsonId__':'3','value':'abc'}},'b0':false}"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','b0':false}"), writer.toString())
        assertEquals(1, generator.matchCount)
    }

    @Test
    fun testNoMatchFiltering1() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), NameMatchFilter("invalid"),
                TokenFilter.Inclusion.INCLUDE_NON_NULL, true)
        val doc =
                "{'__cirJsonId__':'0','root':{'__cirJsonId__':'1','a0':true,'b':{'__cirJsonId__':'2','value':4}},'b0':false}"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote(
                "{'__cirJsonId__':'0','root':{'__cirJsonId__':'1','b':{'__cirJsonId__':'2'}}}"),
                writer.toString())
        assertEquals(0, generator.matchCount)
    }

    @Test
    fun testNoMatchFiltering2() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), NameMatchFilter("invalid"),
                TokenFilter.Inclusion.INCLUDE_NON_NULL, true)
        val doc = "['0',${nmfSmallDoc(1)},${nmfSmallDoc(4)},${nmfSmallDoc(7)}]"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote(
                "['0',${nmfSmallExpectedDoc(1)},${nmfSmallExpectedDoc(4)},${nmfSmallExpectedDoc(7)}]"),
                writer.toString())
        assertEquals(0, generator.matchCount)
    }

    @Test
    fun testNoMatchFiltering3() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), NameMatchFilter("invalid"),
                TokenFilter.Inclusion.INCLUDE_NON_NULL, true)
        val doc = "['0',['1',${nmfSmallDoc(2)}],['4',${nmfSmallDoc(5)}],['8',${nmfSmallDoc(9)}]]"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote("['0',['1',${nmfSmallExpectedDoc(2)}],['5',${nmfSmallExpectedDoc(6)}],['9',${
            nmfSmallExpectedDoc(10)
        }]]"), writer.toString())
        assertEquals(0, generator.matchCount)
    }

    @Test
    fun testNoMatchFiltering4() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), StrictNameMatchFilter("invalid"),
                TokenFilter.Inclusion.INCLUDE_NON_NULL, true)
        val doc =
                "{'__cirJsonId__':'0','root':{'__cirJsonId__':'1','a0':true,'a':{'__cirJsonId__':'2','value':3},'b':{'__cirJsonId__':'3','value':4}},'b0':false}"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote("{'__cirJsonId__':'0'}"), writer.toString())
        assertEquals(0, generator.matchCount)
    }

    @Test
    fun testNoMatchFiltering5() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), StrictNameMatchFilter("invalid"),
                TokenFilter.Inclusion.INCLUDE_NON_NULL, true)
        val doc = "['0',${nmfSmallDoc(1)},${nmfSmallDoc(4)},${nmfSmallDoc(7)}]"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote("['0',{'__cirJsonId__':'1'},{'__cirJsonId__':'2'},{'__cirJsonId__':'3'}]"),
                writer.toString())
        assertEquals(0, generator.matchCount)
    }

    @Test
    fun testNoMatchFiltering6() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), StrictNameMatchFilter("invalid"),
                TokenFilter.Inclusion.INCLUDE_NON_NULL, true)
        val doc = "['0',['1',${nmfSmallDoc(2)}],['4',${nmfSmallDoc(5)}],['8',${nmfSmallDoc(9)}]]"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote(
                "['0',['1',{'__cirJsonId__':'2'}],['3',{'__cirJsonId__':'4'}],['5',{'__cirJsonId__':'6'}]]"),
                writer.toString())
        assertEquals(0, generator.matchCount)
    }

    private fun nmfSmallDoc(baseId: Int): String {
        return "{'__cirJsonId__':'$baseId','root':{'__cirJsonId__':'${baseId + 1}','a0':true,'b':{'__cirJsonId__':'${baseId + 2}','value':4}},'b0':false}"
    }

    private fun nmfSmallExpectedDoc(baseId: Int): String {
        return "{'__cirJsonId__':'$baseId','root':{'__cirJsonId__':'${baseId + 1}','b':{'__cirJsonId__':'${baseId + 2}'}}}"
    }

    @Test
    fun testValueOmitsFieldName1() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), NoArraysFilter(),
                TokenFilter.Inclusion.INCLUDE_NON_NULL, true)
        val doc = "{'__cirJsonId__':'0','root':['1','a'],'b0':false}"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','b0':false}"), writer.toString())
        assertEquals(1, generator.matchCount)
    }

    @Test
    fun testValueOmitsFieldName2() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), NoObjectsFilter(),
                TokenFilter.Inclusion.INCLUDE_NON_NULL, true)
        val doc =
                "['0','a',{'__cirJsonId__':'1','root':{'__cirJsonId__':'2','b':{'__cirJsonId__':'3','value':4}},'b0':false}]"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote("['0','a']"), writer.toString())
        assertEquals(1, generator.matchCount)
    }

    @Test
    fun testIndexMatchWithPath1() {
        var writer = StringWriter()
        var generator = FilteringGeneratorDelegate(createGenerator(writer), IndexMatchFilter(1),
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)
        val doc =
                "{'__cirJsonId__':'0','a':123,'array':['1',1,2],'ob':{'__cirJsonId__':'2','value0':2,'value':3,'value2':'abc'},'b':true}"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','array':['1',2]}"), writer.toString())
        assertEquals(1, generator.matchCount)

        writer = StringWriter()
        generator = FilteringGeneratorDelegate(createGenerator(writer), IndexMatchFilter(0),
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','array':['1',1]}"), writer.toString())
        assertEquals(1, generator.matchCount)
    }

    @Test
    fun testIndexMatchWithPath2() {
        var writer = StringWriter()
        var generator = FilteringGeneratorDelegate(createGenerator(writer), IndexMatchFilter(0, 1),
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)
        var doc =
                "{'__cirJsonId__':'0','a':123,'array':['1',1,2],'ob':{'__cirJsonId__':'2','value0':2,'value':3,'value2':4},'b':true}"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','array':['1',1,2]}"), writer.toString())
        assertEquals(2, generator.matchCount)

        writer = StringWriter()
        generator = FilteringGeneratorDelegate(createGenerator(writer), IndexMatchFilter(1, 3, 5),
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)
        doc = "{'__cirJsonId__':'0','a':123,'misc':['1',1,2, null, true, false, 'abc', 123],'ob':null,'b':true}"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','misc':['1',2,true,'abc']}"), writer.toString())
        assertEquals(3, generator.matchCount)

        writer = StringWriter()
        generator = FilteringGeneratorDelegate(createGenerator(writer), IndexMatchFilter(2, 6),
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)
        doc = "{'__cirJsonId__':'0','misc':['1',1,2, null, 0.25, false, 'abc', 11234567890]}"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','misc':['1',null,11234567890]}"), writer.toString())
        assertEquals(2, generator.matchCount)

        writer = StringWriter()
        generator = FilteringGeneratorDelegate(createGenerator(writer), IndexMatchFilter(1),
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)
        doc = "{'__cirJsonId__':'0','misc':['1',1,0.25,11234567890]}"
        writeCirJsonDoc(factory, doc, generator)
        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','misc':['1',0.25]}"), writer.toString())
        assertEquals(1, generator.matchCount)
    }

    @Test
    fun testWriteStartObjectWithObject() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), TokenFilter.INCLUDE_ALL,
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)

        val value = "val"

        val obj = Any()
        generator.writeStartObject(obj, 2)
        generator.writeObjectId(obj)
        generator.writeName("field1")
        generator.writeStartObject(value)
        generator.writeObjectId(value)
        generator.writeEndObject()

        generator.writeName("field2")
        generator.writeNumber(BigDecimal("1.0"))

        generator.writeEndObject()
        generator.close()
        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','field1':{'__cirJsonId__':'1'},'field2':1.0}"),
                writer.toString())
    }

    @Test
    fun testRawValueDelegationWithArray() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), TokenFilter.INCLUDE_ALL,
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)

        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeRawValue(charArrayOf('1'), 0, 1)
        generator.writeRawValue("123", 2, 1)
        generator.writeRaw(',')
        generator.writeRaw("/* comment")
        generator.writeRaw("... */".toCharArray(), 3, 3)
        generator.writeRaw(" ,42".toCharArray(), 1, 3)
        generator.writeEndArray()
        generator.close()

        assertEquals(apostropheToQuote("['0',1,3,/* comment */,42]"), writer.toString())
    }

    @Test
    fun testRawValueDelegationWithObject() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), TokenFilter.INCLUDE_ALL,
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)

        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeNumberProperty("f1", 1)
        generator.writeName("f2")
        generator.writeRawValue(charArrayOf('1', '2', '.', '3', '-'), 0, 4)
        generator.writeNumberProperty("f3", 3)
        generator.writeEndObject()
        generator.close()

        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','f1':1,'f2':12.3,'f3':3}"), writer.toString())
    }

    @Test
    fun testIncludeEmptyArrayIfNotFiltered() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), INCLUDE_EMPTY_IF_NOT_FILTERED,
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)

        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeArrayPropertyStart("empty_array")
        generator.writeArrayId(Any())
        generator.writeEndArray()
        generator.writeArrayPropertyStart("filtered_array")
        generator.writeArrayId(Any())
        generator.writeNumber(6)
        generator.writeEndArray()
        generator.writeEndObject()
        generator.close()

        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','empty_array':['1']}"), writer.toString())
    }

    @Test
    fun testIncludeEmptyArray() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), INCLUDE_EMPTY,
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)

        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeArrayPropertyStart("empty_array")
        generator.writeArrayId(Any())
        generator.writeEndArray()
        generator.writeArrayPropertyStart("filtered_array")
        generator.writeArrayId(Any())
        generator.writeNumber(6)
        generator.writeEndArray()
        generator.writeEndObject()
        generator.close()

        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','empty_array':['1'],'filtered_array':['2']}"),
                writer.toString())
    }

    @Test
    fun testIncludeEmptyObjectIfNotFiltered() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), INCLUDE_EMPTY_IF_NOT_FILTERED,
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)

        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeObjectPropertyStart("empty_object")
        generator.writeObjectId(Any())
        generator.writeEndObject()
        generator.writeObjectPropertyStart("filtered_object")
        generator.writeObjectId(Any())
        generator.writeNumberProperty("foo", 6)
        generator.writeEndObject()
        generator.writeEndObject()
        generator.close()

        assertEquals(apostropheToQuote("{'__cirJsonId__':'0','empty_object':{'__cirJsonId__':'1'}}"), writer.toString())
    }

    @Test
    fun testIncludeEmptyObject() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), INCLUDE_EMPTY,
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)

        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeObjectPropertyStart("empty_object")
        generator.writeObjectId(Any())
        generator.writeEndObject()
        generator.writeObjectPropertyStart("filtered_object")
        generator.writeObjectId(Any())
        generator.writeNumberProperty("foo", 6)
        generator.writeEndObject()
        generator.writeEndObject()
        generator.close()

        assertEquals(apostropheToQuote(
                "{'__cirJsonId__':'0','empty_object':{'__cirJsonId__':'1'},'filtered_object':{'__cirJsonId__':'2'}}"),
                writer.toString())
    }

    @Test
    fun testIncludeEmptyArrayInObjectIfNotFiltered() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), INCLUDE_EMPTY_IF_NOT_FILTERED,
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)

        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeObjectPropertyStart("object_with_empty_array")
        generator.writeObjectId(Any())
        generator.writeArrayPropertyStart("foo")
        generator.writeArrayId(Any())
        generator.writeEndArray()
        generator.writeEndObject()
        generator.writeObjectPropertyStart("object_with_filtered_array")
        generator.writeObjectId(Any())
        generator.writeArrayPropertyStart("foo")
        generator.writeArrayId(Any())
        generator.writeNumber(6)
        generator.writeEndArray()
        generator.writeEndObject()
        generator.writeEndObject()
        generator.close()

        assertEquals(
                apostropheToQuote("{'__cirJsonId__':'0','object_with_empty_array':{'__cirJsonId__':'1','foo':['2']}}"),
                writer.toString())
    }

    @Test
    fun testIncludeEmptyArrayInObject() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), INCLUDE_EMPTY,
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)

        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeObjectPropertyStart("object_with_empty_array")
        generator.writeObjectId(Any())
        generator.writeArrayPropertyStart("foo")
        generator.writeArrayId(Any())
        generator.writeEndArray()
        generator.writeEndObject()
        generator.writeObjectPropertyStart("object_with_filtered_array")
        generator.writeObjectId(Any())
        generator.writeArrayPropertyStart("foo")
        generator.writeArrayId(Any())
        generator.writeNumber(6)
        generator.writeEndArray()
        generator.writeEndObject()
        generator.writeEndObject()
        generator.close()

        assertEquals(
                apostropheToQuote(
                        "{'__cirJsonId__':'0','object_with_empty_array':{'__cirJsonId__':'1','foo':['2']},'object_with_filtered_array':{'__cirJsonId__':'3','foo':['4']}}"),
                writer.toString())
    }

    @Test
    fun testIncludeEmptyObjectInArrayIfNotFiltered() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), INCLUDE_EMPTY_IF_NOT_FILTERED,
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)

        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeArrayPropertyStart("array_with_empty_object")
        generator.writeArrayId(Any())
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeEndObject()
        generator.writeEndArray()
        generator.writeArrayPropertyStart("array_with_filtered_object")
        generator.writeArrayId(Any())
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeNumberProperty("foo", 5)
        generator.writeEndObject()
        generator.writeEndArray()
        generator.writeEndObject()
        generator.close()

        assertEquals(
                apostropheToQuote("{'__cirJsonId__':'0','array_with_empty_object':['1',{'__cirJsonId__':'2'}]}"),
                writer.toString())
    }

    @Test
    fun testIncludeEmptyObjectInArray() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), INCLUDE_EMPTY,
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)

        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeArrayPropertyStart("array_with_empty_object")
        generator.writeArrayId(Any())
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeEndObject()
        generator.writeEndArray()
        generator.writeArrayPropertyStart("array_with_filtered_object")
        generator.writeArrayId(Any())
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeNumberProperty("foo", 5)
        generator.writeEndObject()
        generator.writeEndArray()
        generator.writeEndObject()
        generator.close()

        assertEquals(apostropheToQuote(
                "{'__cirJsonId__':'0','array_with_empty_object':['1',{'__cirJsonId__':'2'}],'array_with_filtered_object':['3',{'__cirJsonId__':'4'}]}"),
                writer.toString())
    }

    @Test
    fun testIncludeEmptyTopLevelObject() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), INCLUDE_EMPTY_IF_NOT_FILTERED,
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)

        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeEndObject()
        generator.close()

        assertEquals(apostropheToQuote("{'__cirJsonId__':'0'}"), writer.toString())
    }

    @Test
    fun testIncludeEmptyTopLevelArray() {
        val writer = StringWriter()
        val generator = FilteringGeneratorDelegate(createGenerator(writer), INCLUDE_EMPTY_IF_NOT_FILTERED,
                TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH, true)

        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeEndArray()
        generator.close()

        assertEquals(apostropheToQuote("['0']"), writer.toString())
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