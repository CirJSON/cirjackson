package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.ObjectWriteContext
import org.cirjson.cirjackson.core.PrettyPrinter
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import kotlin.test.*

class DefaultPrettyPrinterTest : TestBase() {

    @Test
    fun testWithArrayIndenter() {
        val prettyPrinter = DefaultPrettyPrinter().withArrayIndenter(DefaultPrettyPrinter.FixedSpaceIndenter.instance())
        val prettyPrinter2 = prettyPrinter.withArrayIndenter(null)

        assertNotSame(prettyPrinter, prettyPrinter2)
        assertSame(prettyPrinter2, prettyPrinter2.withArrayIndenter(null))
    }

    @Test
    fun testWithObjectIndenter() {
        val prettyPrinter =
                DefaultPrettyPrinter().withObjectIndenter(DefaultPrettyPrinter.FixedSpaceIndenter.instance())
        val prettyPrinter2 = prettyPrinter.withObjectIndenter(null)

        assertNotSame(prettyPrinter, prettyPrinter2)
        assertSame(prettyPrinter2, prettyPrinter2.withObjectIndenter(null))
    }

    @Test
    fun testIndentArraysWith() {
        val operations = fun(generator: CirJsonGenerator) {
            generator.writeNumber(1)
            generator.writeStartArray()
            generator.writeArrayId(intArrayOf())
            generator.writeNumber(2)
            generator.writeEndArray()
            generator.writeStartObject()
            generator.writeObjectId(intArrayOf())
            generator.writeName("a")
            generator.writeNumber(3)
            generator.writeEndObject()
        }

        val prettyPrinter = DefaultPrettyPrinter().withArrayIndenter(null)
        val expected = printTestData(prettyPrinter, false, operations)
        assertEquals(expected, printTestData(prettyPrinter, true, operations))

        prettyPrinter.indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance())
        assertNotEquals(expected, printTestData(prettyPrinter, false, operations))
        assertNotEquals(expected, printTestData(prettyPrinter, true, operations))

        prettyPrinter.indentArraysWith(null)
        assertEquals(expected, printTestData(prettyPrinter, false, operations))
        assertEquals(expected, printTestData(prettyPrinter, true, operations))
    }

    @Test
    fun testIndentObjectWith() {
        val prettyPrinter = DefaultPrettyPrinter().withObjectIndenter(null)
        val expected = printTestData(prettyPrinter, false)
        assertEquals(expected, printTestData(prettyPrinter, true))

        prettyPrinter.indentObjectsWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance())
        assertNotEquals(expected, printTestData(prettyPrinter, false))
        assertNotEquals(expected, printTestData(prettyPrinter, true))

        prettyPrinter.indentObjectsWith(null)
        assertEquals(expected, printTestData(prettyPrinter, false))
        assertEquals(expected, printTestData(prettyPrinter, true))
    }

    @Test
    fun testSystemLinefeed() {
        val prettyPrinter = DefaultPrettyPrinter()
        val lf = System.lineSeparator()
        val expected = "{$lf" +
                "  \"__cirJsonId__\" : \"0\",$lf" +
                "  \"name\" : \"John Doe\",$lf" +
                "  \"age\" : 3.14$lf" +
                "}"
        assertEquals(expected, printTestData(prettyPrinter, false))
        assertEquals(expected, printTestData(prettyPrinter, true))
    }

    @Test
    fun testWithLinefeed() {
        val prettyPrinter = DefaultPrettyPrinter().withObjectIndenter(DefaultIndenter().withLinefeed("\n"))
        val expected = "{\n" +
                "  \"__cirJsonId__\" : \"0\",\n" +
                "  \"name\" : \"John Doe\",\n" +
                "  \"age\" : 3.14\n" +
                "}"
        assertEquals(expected, printTestData(prettyPrinter, false))
        assertEquals(expected, printTestData(prettyPrinter, true))
    }

    @Test
    fun testWithIndent() {
        val prettyPrinter =
                DefaultPrettyPrinter().withObjectIndenter(DefaultIndenter().withLinefeed("\n").withIndent(" "))
        val expected = "{\n" +
                " \"__cirJsonId__\" : \"0\",\n" +
                " \"name\" : \"John Doe\",\n" +
                " \"age\" : 3.14\n" +
                "}"
        assertEquals(expected, printTestData(prettyPrinter, false))
        assertEquals(expected, printTestData(prettyPrinter, true))
    }

    @Test
    fun testUnixLinefeed() {
        val prettyPrinter = DefaultPrettyPrinter().withObjectIndenter(DefaultIndenter("  ", "\n"))
        val expected = "{\n" +
                "  \"__cirJsonId__\" : \"0\",\n" +
                "  \"name\" : \"John Doe\",\n" +
                "  \"age\" : 3.14\n" +
                "}"
        assertEquals(expected, printTestData(prettyPrinter, false))
        assertEquals(expected, printTestData(prettyPrinter, true))
    }

    @Test
    fun testWindowsLinefeed() {
        val prettyPrinter = DefaultPrettyPrinter().withObjectIndenter(DefaultIndenter("  ", "\r\n"))
        val expected = "{\r\n" +
                "  \"__cirJsonId__\" : \"0\",\r\n" +
                "  \"name\" : \"John Doe\",\r\n" +
                "  \"age\" : 3.14\r\n" +
                "}"
        assertEquals(expected, printTestData(prettyPrinter, false))
        assertEquals(expected, printTestData(prettyPrinter, true))
    }

    @Test
    fun testTabIndent() {
        val prettyPrinter = DefaultPrettyPrinter().withObjectIndenter(DefaultIndenter("\t", "\n"))
        val expected = "{\n" +
                "\t\"__cirJsonId__\" : \"0\",\n" +
                "\t\"name\" : \"John Doe\",\n" +
                "\t\"age\" : 3.14\n" +
                "}"
        assertEquals(expected, printTestData(prettyPrinter, false))
        assertEquals(expected, printTestData(prettyPrinter, true))
    }

    @Test
    fun testObjectNameValueSpacingAfter() {
        val separators = Separators().withObjectNameValueSpacing(Separators.Spacing.AFTER)
        val prettyPrinter =
                DefaultPrettyPrinter().withObjectIndenter(DefaultIndenter("  ", "\n")).withSeparators(separators)
        val expected = "{\n" +
                "  \"__cirJsonId__\": \"0\",\n" +
                "  \"name\": \"John Doe\",\n" +
                "  \"age\": 3.14\n" +
                "}"
        assertEquals(expected, printTestData(prettyPrinter, false))
        assertEquals(expected, printTestData(prettyPrinter, true))
    }

    @Test
    fun testObjectNameValueSpacingBefore() {
        val separators = Separators().withObjectNameValueSpacing(Separators.Spacing.BEFORE)
        val prettyPrinter =
                DefaultPrettyPrinter().withObjectIndenter(DefaultIndenter("  ", "\n")).withSeparators(separators)
        val expected = "{\n" +
                "  \"__cirJsonId__\" :\"0\",\n" +
                "  \"name\" :\"John Doe\",\n" +
                "  \"age\" :3.14\n" +
                "}"
        assertEquals(expected, printTestData(prettyPrinter, false))
        assertEquals(expected, printTestData(prettyPrinter, true))
    }

    @Test
    fun testObjectNameValueSpacingNone() {
        val separators = Separators().withObjectNameValueSpacing(Separators.Spacing.NONE)
        val prettyPrinter =
                DefaultPrettyPrinter().withObjectIndenter(DefaultIndenter("  ", "\n")).withSeparators(separators)
        val expected = "{\n" +
                "  \"__cirJsonId__\":\"0\",\n" +
                "  \"name\":\"John Doe\",\n" +
                "  \"age\":3.14\n" +
                "}"
        assertEquals(expected, printTestData(prettyPrinter, false))
        assertEquals(expected, printTestData(prettyPrinter, true))
    }

    @Test
    fun testCopyConfigNew() {
        val separators = Separators().withObjectNameValueSpacing(Separators.Spacing.AFTER)
                .withObjectEntrySpacing(Separators.Spacing.AFTER).withArrayElementSpacing(Separators.Spacing.AFTER)
        val prettyPrinter = DefaultPrettyPrinter(separators).withObjectIndenter(DefaultIndenter("  ", "\n"))
        val expected = printTestData(prettyPrinter, false)
        assertEquals(expected, printTestData(prettyPrinter, true))

        val copy = DefaultPrettyPrinter(prettyPrinter)
        assertEquals(expected, printTestData(copy, false))
        assertEquals(expected, printTestData(copy, true))
    }

    @Test
    fun testRootSeparator() {
        val separators = Separators().withRootSeparator("|")
        val prettyPrinter = DefaultPrettyPrinter().withSeparators(separators)
        val expected = "1|2|3"

        val operations = fun(generator: CirJsonGenerator) {
            generator.writeNumber(1)
            generator.writeNumber(2)
            generator.writeNumber(3)
        }

        assertEquals(expected, printTestData(prettyPrinter, false, operations))
        assertEquals(expected, printTestData(prettyPrinter, true, operations))
    }

    @Test
    fun testWithoutSeparators() {
        val separators = Separators().withRootSeparator(null).withObjectNameValueSpacing(Separators.Spacing.NONE)
        val prettyPrinter =
                DefaultPrettyPrinter().withSeparators(separators).withArrayIndenter(null).withObjectIndenter(null)
        val expected = "1[\"0\",2]{\"__cirJsonId__\":\"1\",\"a\":3}"

        val operations = fun(generator: CirJsonGenerator) {
            generator.writeNumber(1)
            generator.writeStartArray()
            generator.writeArrayId(intArrayOf())
            generator.writeNumber(2)
            generator.writeEndArray()
            generator.writeStartObject()
            generator.writeObjectId(intArrayOf())
            generator.writeName("a")
            generator.writeNumber(3)
            generator.writeEndObject()
        }

        assertEquals(expected, printTestData(prettyPrinter, false, operations))
        assertEquals(expected, printTestData(prettyPrinter, true, operations))
    }

    @Test
    fun testObjectEmptySeparatorDefault() {
        val separators = Separators().withRootSeparator(null).withObjectNameValueSpacing(Separators.Spacing.NONE)
        val prettyPrinter =
                DefaultPrettyPrinter().withSeparators(separators).withArrayIndenter(null).withObjectIndenter(null)
        val expected = "{\"__cirJsonId__\":\"0\",\"objectEmptySeparatorDefault\":{ }}"

        val operations = fun(generator: CirJsonGenerator) {
            generator.writeStartObject()
            generator.writeObjectId(intArrayOf())
            generator.writeName("objectEmptySeparatorDefault")
            generator.writeStartObject()
            generator.writeEndObject()
            generator.writeEndObject()
        }

        assertEquals(expected, printTestData(prettyPrinter, false, operations))
        assertEquals(expected, printTestData(prettyPrinter, true, operations))
    }

    @Test
    fun testObjectEmptySeparatorCustom() {
        val separators = Separators().withRootSeparator(null).withObjectNameValueSpacing(Separators.Spacing.NONE)
                .withObjectEmptySeparator("   ")
        val prettyPrinter =
                DefaultPrettyPrinter().withSeparators(separators).withArrayIndenter(null).withObjectIndenter(null)
        val expected = "{\"__cirJsonId__\":\"0\",\"objectEmptySeparatorCustom\":{   }}"

        val operations = fun(generator: CirJsonGenerator) {
            generator.writeStartObject()
            generator.writeObjectId(intArrayOf())
            generator.writeName("objectEmptySeparatorCustom")
            generator.writeStartObject()
            generator.writeEndObject()
            generator.writeEndObject()
        }

        assertEquals(expected, printTestData(prettyPrinter, false, operations))
        assertEquals(expected, printTestData(prettyPrinter, true, operations))
    }

    @Test
    fun testArrayEmptySeparatorDefault() {
        val separators = Separators().withRootSeparator(null).withObjectNameValueSpacing(Separators.Spacing.NONE)
        val prettyPrinter =
                DefaultPrettyPrinter().withSeparators(separators).withArrayIndenter(null).withObjectIndenter(null)
        val expected = "{\"__cirJsonId__\":\"0\",\"arrayEmptySeparatorDefault\":{ }}"

        val operations = fun(generator: CirJsonGenerator) {
            generator.writeStartObject()
            generator.writeObjectId(intArrayOf())
            generator.writeName("arrayEmptySeparatorDefault")
            generator.writeStartObject()
            generator.writeEndObject()
            generator.writeEndObject()
        }

        assertEquals(expected, printTestData(prettyPrinter, false, operations))
        assertEquals(expected, printTestData(prettyPrinter, true, operations))
    }

    @Test
    fun testArrayEmptySeparatorCustom() {
        val separators = Separators().withRootSeparator(null).withObjectNameValueSpacing(Separators.Spacing.NONE)
                .withArrayEmptySeparator("   ")
        val prettyPrinter =
                DefaultPrettyPrinter().withSeparators(separators).withArrayIndenter(null).withObjectIndenter(null)
        val expected = "{\"__cirJsonId__\":\"0\",\"arrayEmptySeparatorCustom\":[   ]}"

        val operations = fun(generator: CirJsonGenerator) {
            generator.writeStartObject()
            generator.writeObjectId(intArrayOf())
            generator.writeName("arrayEmptySeparatorCustom")
            generator.writeStartArray()
            generator.writeEndArray()
            generator.writeEndObject()
        }

        assertEquals(expected, printTestData(prettyPrinter, false, operations))
        assertEquals(expected, printTestData(prettyPrinter, true, operations))
    }

    @Test
    fun testInvalidSubclass() {
        val prettyPrinter = DefaultPrettyPrinter()
        assertNotNull(prettyPrinter.createInstance())

        try {
            DefaultPrettyPrinterSubclass().createInstance()
            fail("Should not pass")
        } catch (e: IllegalStateException) {
            verifyException(e, "does not override")
        }
    }

    private fun printTestData(prettyPrinter: PrettyPrinter, useBytes: Boolean): String {
        return printTestData(prettyPrinter, useBytes) {
            it.writeStartObject()
            it.writeObjectId(Any())
            it.writeName("name")
            it.writeString("John Doe")
            it.writeName("age")
            it.writeNumber(3.14)
            it.writeEndObject()
        }
    }

    private fun printTestData(prettyPrinter: PrettyPrinter, useBytes: Boolean,
            operations: (CirJsonGenerator) -> Unit): String {
        val context = objectWriteContext(prettyPrinter)

        return if (useBytes) {
            val bytes = ByteArrayOutputStream()
            val generator = CIRJSON_FACTORY.createGenerator(context, bytes)
            operations.invoke(generator)
            generator.close()
            bytes.toString(Charsets.UTF_8)
        } else {
            val writer = StringWriter()
            val generator = CIRJSON_FACTORY.createGenerator(context, writer)
            operations.invoke(generator)
            generator.close()
            writer.toString()
        }
    }

    private fun objectWriteContext(prettyPrinter: PrettyPrinter): ObjectWriteContext {
        return object : ObjectWriteContext.Base() {

            override val prettyPrinter = prettyPrinter

        }
    }

    private class DefaultPrettyPrinterSubclass : DefaultPrettyPrinter()

    companion object {

        val CIRJSON_FACTORY = CirJsonFactory()

    }

}