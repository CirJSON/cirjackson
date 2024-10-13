package org.cirjson.cirjackson.core.write

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.io.SerializedString
import org.cirjson.cirjackson.core.util.DefaultPrettyPrinter
import org.cirjson.cirjackson.core.util.MinimalPrettyPrinter
import org.cirjson.cirjackson.core.util.Separators
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import kotlin.test.*

class PrettyPrinterTest : TestBase() {

    private val factory = sharedStreamFactory()

    @Test
    fun testObjectCount() {
        for (mode in ALL_GENERATOR_MODES) {
            objectCount(mode)
        }
    }

    private fun objectCount(mode: Int) {
        val context = object : ObjectWriteContext.Base() {

            override val prettyPrinter = CountPrinter()

        }

        val generator = if (mode == MODE_OUTPUT_STREAM) {
            factory.createGenerator(context, ByteArrayOutputStream())
        } else {
            factory.createGenerator(context, StringWriter())
        }

        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeName("x")
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeNumberProperty("a", 1)
        generator.writeNumberProperty("b", 2)
        generator.writeEndObject()
        generator.writeEndObject()
        generator.close()

        val actual = generator.streamWriteOutputTarget!!.toString()
        assertEquals("{\"__cirJsonId__\":\"0\",\"x\":{\"__cirJsonId__\":\"1\",\"a\":1,\"b\":2(3)}(2)}", actual)
    }

    @Test
    fun testArrayCount() {
        for (mode in ALL_GENERATOR_MODES) {
            arrayCount(mode)
        }
    }

    private fun arrayCount(mode: Int) {
        val context = object : ObjectWriteContext.Base() {

            override val prettyPrinter = CountPrinter()

        }

        val generator = if (mode == MODE_OUTPUT_STREAM) {
            factory.createGenerator(context, ByteArrayOutputStream())
        } else {
            factory.createGenerator(context, StringWriter())
        }

        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeNumber(6)
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeNumber(1)
        generator.writeNumber(2)
        generator.writeNumber(9)
        generator.writeEndArray()
        generator.writeEndArray()
        generator.close()

        val actual = generator.streamWriteOutputTarget!!.toString()
        assertEquals("[\"0\",6,[\"1\",1,2,9(4)](3)]", actual)
    }

    @Test
    fun testSimpleDocWithMinimal() {
        for (generatorMode in ALL_GENERATOR_MODES) {
            for (parserMode in ALL_NON_ASYNC_PARSER_MODES) {
                simpleDocWithMinimal(generatorMode, parserMode)
            }
        }
    }

    private fun simpleDocWithMinimal(generatorMode: Int, parserMode: Int) {
        var context: ObjectWriteContext = object : ObjectWriteContext.Base() {

            override val prettyPrinter = MinimalPrettyPrinter()

        }

        var generator = if (generatorMode == MODE_OUTPUT_STREAM) {
            factory.createGenerator(context, ByteArrayOutputStream())
        } else {
            factory.createGenerator(context, StringWriter())
        }

        var doc = simpleDocWithMinimal(generator, parserMode)
        assertFalse('\n' in doc)
        assertFalse('\t' in doc)

        context = object : ObjectWriteContext.Base() {

            override val prettyPrinter = object : MinimalPrettyPrinter() {

                override fun beforeArrayValues(generator: CirJsonGenerator) {
                    generator.writeRaw('\t')
                }

            }

        }

        generator = if (generatorMode == MODE_OUTPUT_STREAM) {
            factory.createGenerator(context, ByteArrayOutputStream())
        } else {
            factory.createGenerator(context, StringWriter())
        }

        doc = simpleDocWithMinimal(generator, parserMode)
        assertFalse('\n' in doc)
        assertTrue('\t' in doc)
    }

    private fun simpleDocWithMinimal(generator: CirJsonGenerator, parserMode: Int): String {
        writeTestDocument(generator)

        val doc = generator.streamWriteOutputTarget!!.toString()
        val parser = createParser(factory, parserMode, doc)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(3, parser.intValue)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("abc", parser.text)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("f", parser.text)
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("f2", parser.text)
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()

        return doc
    }

    @Test
    fun testRootSeparatorWithoutPrettyPrinter() {
        for (mode in ALL_GENERATOR_MODES) {
            rootSeparatorWithoutPrettyPrinter(mode)
        }
    }

    private fun rootSeparatorWithoutPrettyPrinter(mode: Int) {
        assertEquals("{\"__cirJsonId__\":\"0\"} {\"__cirJsonId__\":\"1\"} [\"2\"] [\"3\"]", generateRoot(mode, null))
    }

    @Test
    fun testRootSeparatorWithPrettyPrinter() {
        for (mode in ALL_GENERATOR_MODES) {
            rootSeparatorWithPrettyPrinter(mode)
        }
    }

    private fun rootSeparatorWithPrettyPrinter(mode: Int) {
        val prettyPrinter = DefaultPrettyPrinter()
        val lf = System.lineSeparator()
        assertEquals("{$lf  \"__cirJsonId__\" : \"0\"$lf} {$lf  \"__cirJsonId__\" : \"1\"$lf} [ \"2\" ] [ \"3\" ]",
                generateRoot(mode, prettyPrinter))
    }

    @Test
    fun testRootSeparatorWithPrettyPrinterWithCustomSeparators() {
        for (mode in ALL_GENERATOR_MODES) {
            rootSeparatorWithPrettyPrinterWithCustomSeparators(mode)
        }
    }

    private fun rootSeparatorWithPrettyPrinterWithCustomSeparators(mode: Int) {
        val separators = Separators.createDefaultInstance().withRootSeparator("|")
        val prettyPrinter = DefaultPrettyPrinter(separators)
        val lf = System.lineSeparator()
        assertEquals("{$lf  \"__cirJsonId__\" : \"0\"$lf}|{$lf  \"__cirJsonId__\" : \"1\"$lf}|[ \"2\" ]|[ \"3\" ]",
                generateRoot(mode, prettyPrinter))
    }

    @Test
    fun testCustomRootSeparatorWithFactory() {
        for (mode in ALL_GENERATOR_MODES) {
            customRootSeparatorWithFactory(mode)
        }
    }

    private fun customRootSeparatorWithFactory(mode: Int) {
        val factory = CirJsonFactory.builder().rootValueSeparator("##").build()
        val generator = createGenerator(factory, mode)
        generator.writeNumber(13)
        generator.writeBoolean(false)
        generator.writeNull()
        generator.close()
        val actual = generator.streamWriteOutputTarget!!.toString()
        assertEquals("13##false##null", actual)
    }

    @Test
    fun testCustomSeparatorsWithMinimal() {
        for (mode in ALL_GENERATOR_MODES) {
            customSeparatorsWithMinimal(mode)
        }
    }

    private fun customSeparatorsWithMinimal(mode: Int) {
        val context: ObjectWriteContext = object : ObjectWriteContext.Base() {

            override val prettyPrinter = MinimalPrettyPrinter().setSeparator(
                    Separators.createDefaultInstance().withObjectNameValueSeparator('=').withObjectEntrySeparator(';')
                            .withArrayElementSeparator('|'))

        }

        val generator = if (mode == MODE_OUTPUT_STREAM) {
            factory.createGenerator(context, ByteArrayOutputStream())
        } else {
            factory.createGenerator(context, StringWriter())
        }

        writeTestDocument(generator)
        val actual = generator.streamWriteOutputTarget!!.toString()
        assertEquals("[\"0\"|3|\"abc\"|[\"1\"|true]|{\"__cirJsonId__\"=\"2\";\"f\"=false;\"f2\"=null}]", actual)
    }

    @Test
    fun testCustomSeparatorsWithPrettyPrinter() {
        for (mode in ALL_GENERATOR_MODES) {
            customSeparatorsWithPrettyPrinter(mode)
        }
    }

    private fun customSeparatorsWithPrettyPrinter(mode: Int) {
        val context: ObjectWriteContext = object : ObjectWriteContext.Base() {

            override val prettyPrinter = DefaultPrettyPrinter().withSeparators(
                    Separators.createDefaultInstance().withObjectNameValueSeparator('=').withObjectEntrySeparator(';')
                            .withArrayElementSeparator('|'))

        }

        val generator = if (mode == MODE_OUTPUT_STREAM) {
            factory.createGenerator(context, ByteArrayOutputStream())
        } else {
            factory.createGenerator(context, StringWriter())
        }

        writeTestDocument(generator)
        val actual = generator.streamWriteOutputTarget!!.toString()
        val lf = System.lineSeparator()
        assertEquals(
                "[ \"0\"| 3| \"abc\"| [ \"1\"| true ]| {$lf  \"__cirJsonId__\" = \"2\";$lf  \"f\" = false;$lf  \"f2\" = null$lf} ]",
                actual)
    }

    @Test
    fun testCustomSeparatorsWithPrettyPrinterWithoutNameSpacing() {
        for (mode in ALL_GENERATOR_MODES) {
            customSeparatorsWithPrettyPrinterWithoutNameSpacing(mode)
        }
    }

    private fun customSeparatorsWithPrettyPrinterWithoutNameSpacing(mode: Int) {
        val context: ObjectWriteContext = object : ObjectWriteContext.Base() {

            override val prettyPrinter = DefaultPrettyPrinter().withSeparators(
                    Separators.createDefaultInstance().withObjectNameValueSeparator('=')
                            .withObjectNameValueSpacing(Separators.Spacing.NONE).withObjectEntrySeparator(';')
                            .withArrayElementSeparator('|'))

        }

        val generator = if (mode == MODE_OUTPUT_STREAM) {
            factory.createGenerator(context, ByteArrayOutputStream())
        } else {
            factory.createGenerator(context, StringWriter())
        }

        writeTestDocument(generator)
        val actual = generator.streamWriteOutputTarget!!.toString()
        val lf = System.lineSeparator()
        assertEquals(
                "[ \"0\"| 3| \"abc\"| [ \"1\"| true ]| {$lf  \"__cirJsonId__\"=\"2\";$lf  \"f\"=false;$lf  \"f2\"=null$lf} ]",
                actual)
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    private fun writeTestDocument(generator: CirJsonGenerator) {
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeNumber(3)
        generator.writeString("abc")

        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeBoolean(true)
        generator.writeEndArray()

        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeName("f")
        generator.writeBoolean(false)
        generator.writeName(SerializedString("f2"))
        generator.writeNull()
        generator.writeEndObject()

        generator.writeEndArray()
        generator.close()
    }

    private fun generateRoot(mode: Int, prettyPrinter: PrettyPrinter?): String {
        val context = object : ObjectWriteContext.Base() {

            override val prettyPrinter = prettyPrinter

        }

        val generator = if (mode == MODE_OUTPUT_STREAM) {
            factory.createGenerator(context, ByteArrayOutputStream())
        } else {
            factory.createGenerator(context, StringWriter())
        }

        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeEndObject()
        generator.writeStartObject()
        generator.writeObjectId(Any())
        generator.writeEndObject()
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeEndArray()
        generator.writeStartArray()
        generator.writeArrayId(Any())
        generator.writeEndArray()
        generator.close()

        return generator.streamWriteOutputTarget!!.toString()
    }

    private class CountPrinter : MinimalPrettyPrinter() {

        override fun writeEndObject(generator: CirJsonGenerator, numberOfEntries: Int) {
            generator.writeRaw("($numberOfEntries)}")
        }

        override fun writeEndArray(generator: CirJsonGenerator, numberOfEntries: Int) {
            generator.writeRaw("($numberOfEntries)]")
        }

    }

}