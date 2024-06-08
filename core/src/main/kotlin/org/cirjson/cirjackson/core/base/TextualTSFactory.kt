package org.cirjson.cirjackson.core.base

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.io.ContentReference
import org.cirjson.cirjackson.core.io.IOContext
import java.io.*
import java.net.URL
import java.nio.file.Path

/**
 * Intermediate [TokenStreamFactory] subclass used as the base for textual data formats.
 */
abstract class TextualTSFactory : DecorableTSFactory {

    constructor(streamReadConstraints: StreamReadConstraints, streamWriteConstraints: StreamWriteConstraints,
            errorReportConfiguration: ErrorReportConfiguration, formatReadFeatures: Int,
            formatWriteFeatures: Int) : super(streamReadConstraints, streamWriteConstraints, errorReportConfiguration,
            formatReadFeatures, formatWriteFeatures)

    constructor(src: TextualTSFactory) : super(src)

    /**
     * Constructors used by builders for instantiation.
     *
     * @param baseBuilder Builder with configurations to use
     */
    constructor(baseBuilder: DecorableTSFBuilder<*, *>) : super(baseBuilder)

    /*
     *******************************************************************************************************************
     * Default introspection
     *******************************************************************************************************************
     */

    override val isHandlingBinaryNatively: Boolean
        get() = false

    /**
     * Introspection method that can be used by base factory to check whether access using `CharArray` is something that
     * actual parser implementations can take advantage of, over having to use [Reader]. Subtypes are expected to
     * override definition; default implementation (suitable for CirJSON) alleges that optimization are possible; and
     * thereby is likely to try to access [String] content by first copying it into recyclable intermediate buffer.
     *
     * Default implementation simply returns `true`
     */
    open val isCharArrayUsePossible: Boolean = true

    /*
     *******************************************************************************************************************
     * Factory methods: parsers, with context
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, data: ByteArray, offset: Int, len: Int): CirJsonParser {
        val context = createContext(createContentReference(data, offset, len), true)

        if (inputDecorator != null) {
            val input = inputDecorator.decorate(context, data, offset, len)

            if (input != null) {
                return createParser(readContext, context, input)
            }
        }

        return createParser(readContext, context, data, offset, len)
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, content: CharArray, offset: Int,
            len: Int): CirJsonParser {
        if (inputDecorator != null) {
            return createParser(readContext, CharArrayReader(content, offset, len))
        }

        return createParser(readContext, createContext(createContentReference(content, offset, len), true), content,
                offset, len, false) // important: buffer is NOT recyclable, as it's from caller
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, input: DataInput): CirJsonParser {
        val context = createContext(createContentReference(input), false)
        return createParser(readContext, context, decorate(context, input))
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, file: File): CirJsonParser {
        val context = createContext(createContentReference(file), false)
        return createParser(readContext, context, decorate(context, fileInputStream(file)))
    }

    override fun createParser(readContext: ObjectReadContext, path: Path): CirJsonParser {
        val context = createContext(createContentReference(path), false)
        return createParser(readContext, context, decorate(context, pathInputStream(path)))
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, input: InputStream): CirJsonParser {
        val context = createContext(createContentReference(input), false)
        return createParser(readContext, context, decorate(context, input))
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, reader: Reader): CirJsonParser {
        val context = createContext(createContentReference(reader), false)
        return createParser(readContext, context, decorate(context, reader))
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, content: String): CirJsonParser {
        val stringLength = content.length

        if (inputDecorator != null || stringLength > 0x8000 || !isCharArrayUsePossible) {
            return createParser(readContext, StringReader(content))
        }

        val context = createContext(createContentReference(content), true)
        val buffer = context.allocateTokenBuffer(stringLength)
        content.toCharArray(buffer, 0, 0, stringLength)
        return createParser(readContext, context, buffer, 0, stringLength, true)
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, url: URL): CirJsonParser {
        val context = createContext(createContentReference(url), true)
        return createParser(readContext, context, decorate(context, optimizedStreamFromURL(url)))
    }

    /*
     *******************************************************************************************************************
     * Factory methods: parsers, abstract, for subclasses to implement
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected abstract fun createParser(readContext: ObjectReadContext, context: IOContext, data: ByteArray,
            offset: Int, len: Int): CirJsonParser

    @Throws(CirJacksonException::class)
    protected abstract fun createParser(readContext: ObjectReadContext, context: IOContext, content: CharArray,
            offset: Int, len: Int, recyclable: Boolean): CirJsonParser

    @Throws(CirJacksonException::class)
    protected abstract fun createParser(readContext: ObjectReadContext, context: IOContext,
            input: DataInput): CirJsonParser

    @Throws(CirJacksonException::class)
    protected abstract fun createParser(readContext: ObjectReadContext, context: IOContext,
            input: InputStream): CirJsonParser

    @Throws(CirJacksonException::class)
    protected abstract fun createParser(readContext: ObjectReadContext, context: IOContext,
            reader: Reader): CirJsonParser

    /*
     *******************************************************************************************************************
     * Factory methods: generators
     *******************************************************************************************************************
     */

    override fun createGenerator(writeContext: ObjectWriteContext, output: DataOutput,
            encoding: CirJsonEncoding): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    override fun createGenerator(writeContext: ObjectWriteContext, file: File,
            encoding: CirJsonEncoding): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    override fun createGenerator(writeContext: ObjectWriteContext, output: OutputStream,
            encoding: CirJsonEncoding): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    override fun createGenerator(writeContext: ObjectWriteContext, writer: Writer): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Factory methods: generators, abstract, for subclasses to implement
     *******************************************************************************************************************
     */

    /*
     *******************************************************************************************************************
     * Factory methods: context objects
     *******************************************************************************************************************
     */

    override fun createContentReference(contentReference: Any): ContentReference {
        TODO("Not yet implemented")
    }

    override fun createContentReference(contentReference: Any, offset: Int, length: Int): ContentReference {
        TODO("Not yet implemented")
    }

}