package org.cirjson.cirjackson.core.base

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.io.ContentReference
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.io.UTF8Writer
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
    override fun createParser(readContext: ObjectReadContext, data: ByteArray, offset: Int,
            length: Int): CirJsonParser {
        val context = createContext(createContentReference(data, offset, length), true)

        if (inputDecorator != null) {
            val input = inputDecorator.decorate(context, data, offset, length)

            if (input != null) {
                return createParser(readContext, context, input)
            }
        }

        return createParser(readContext, context, data, offset, length)
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, content: CharArray, offset: Int,
            length: Int): CirJsonParser {
        if (inputDecorator != null) {
            return createParser(readContext, CharArrayReader(content, offset, length))
        }

        return createParser(readContext, createContext(createContentReference(content, offset, length), true), content,
                offset, length, false) // important: buffer is NOT recyclable, as it's from caller
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

    @Throws(CirJacksonException::class)
    override fun createGenerator(writeContext: ObjectWriteContext, file: File,
            encoding: CirJsonEncoding): CirJsonGenerator {
        val output = fileOutputStream(file)
        val ioContext = createContext(createContentReference(file), true, encoding)

        return if (encoding == CirJsonEncoding.UTF8) {
            decorate(createUTF8Generator(writeContext, ioContext, decorate(ioContext, output)))
        } else {
            decorate(createGenerator(writeContext, ioContext,
                    decorate(ioContext, createWriter(ioContext, output, encoding))))
        }
    }

    @Throws(CirJacksonException::class)
    override fun createGenerator(writeContext: ObjectWriteContext, path: Path,
            encoding: CirJsonEncoding): CirJsonGenerator {
        val output = pathOutputStream(path)
        val ioContext = createContext(createContentReference(path), true, encoding)

        return if (encoding == CirJsonEncoding.UTF8) {
            decorate(createUTF8Generator(writeContext, ioContext, decorate(ioContext, output)))
        } else {
            decorate(createGenerator(writeContext, ioContext,
                    decorate(ioContext, createWriter(ioContext, output, encoding))))
        }
    }

    @Throws(CirJacksonException::class)
    override fun createGenerator(writeContext: ObjectWriteContext, output: OutputStream,
            encoding: CirJsonEncoding): CirJsonGenerator {
        val ioContext = createContext(createContentReference(output), true, encoding)

        return if (encoding == CirJsonEncoding.UTF8) {
            decorate(createUTF8Generator(writeContext, ioContext, decorate(ioContext, output)))
        } else {
            decorate(createGenerator(writeContext, ioContext,
                    decorate(ioContext, createWriter(ioContext, output, encoding))))
        }
    }

    @Throws(CirJacksonException::class)
    override fun createGenerator(writeContext: ObjectWriteContext, writer: Writer): CirJsonGenerator {
        val ioContext = createContext(createContentReference(writer), false)
        return decorate(createGenerator(writeContext, ioContext, decorate(ioContext, writer)))
    }

    /*
     *******************************************************************************************************************
     * Factory methods: generators, abstract, for subclasses to implement
     *******************************************************************************************************************
     */

    /**
     * Overridable factory method that actually instantiates generator for given [Writer] and context object.
     *
     * This method is specifically designed to remain compatible between minor versions so that subclasses can count on
     * it being called as expected. That is, it is part of official interface from subclass perspective, although not a
     * public method available to users of factory implementations.
     *
     * @param writeContext Object write context for generator to use
     *
     * @param context IOContext for generator to use
     *
     * @param writer Writer for generator to use
     *
     * @return Generator constructed
     *
     * @throws CirJacksonException If there is a problem constructing generator
     */
    @Throws(CirJacksonException::class)
    protected abstract fun createGenerator(writeContext: ObjectWriteContext, context: IOContext,
            writer: Writer): CirJsonGenerator

    /**
     * Overridable factory method that actually instantiates generator for given [OutputStream] and context object,
     * using UTF-8 encoding.
     *
     * This method is specifically designed to remain compatible between minor versions so that subclasses can count on
     * it being called as expected. That is, it is part of official interface from subclass perspective, although not a
     * public method available to users of factory implementations.
     *
     * @param writeContext Object write context for generator to use
     *
     * @param context IOContext for generator to use
     *
     * @param output OutputStream for generator to use
     *
     * @return Generator constructed
     *
     * @throws CirJacksonException If there is a problem constructing generator
     */
    @Throws(CirJacksonException::class)
    protected abstract fun createUTF8Generator(writeContext: ObjectWriteContext, context: IOContext,
            output: OutputStream): CirJsonGenerator

    /*
     *******************************************************************************************************************
     * Factory methods: context objects
     *******************************************************************************************************************
     */

    override fun createContentReference(contentReference: Any): ContentReference {
        return ContentReference.construct(true, contentReference, errorReportConfiguration)
    }

    override fun createContentReference(contentReference: Any, offset: Int, length: Int): ContentReference {
        return ContentReference.construct(true, contentReference, offset, length, errorReportConfiguration)
    }

    /*
     *******************************************************************************************************************
     * Factory methods: helpers
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun createWriter(context: IOContext, output: OutputStream, encoding: CirJsonEncoding): Writer {
        if (encoding == CirJsonEncoding.UTF8) {
            return UTF8Writer(context, output)
        }

        try {
            return OutputStreamWriter(output, encoding.javaName)
        } catch (e: IOException) {
            throw wrapIOFailure(e)
        }
    }

}