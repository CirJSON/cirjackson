package org.cirjson.cirjackson.core.base

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.io.ContentReference
import org.cirjson.cirjackson.core.io.IOContext
import java.io.*
import java.net.URL
import java.nio.file.Path

/**
 * Intermediate [TokenStreamFactory] subclass used as the base for binary (non-textual) data formats.
 */
abstract class BinaryTSFactory : DecorableTSFactory {

    constructor(streamReadConstraints: StreamReadConstraints, streamWriteConstraints: StreamWriteConstraints,
            errorReportConfiguration: ErrorReportConfiguration, formatReadFeatures: Int,
            formatWriteFeatures: Int) : super(streamReadConstraints, streamWriteConstraints, errorReportConfiguration,
            formatReadFeatures, formatWriteFeatures)

    constructor(src: BinaryTSFactory) : super(src)

    /**
     * Constructor used by builders for instantiation.
     *
     * @param baseBuilder Builder with configurations to use
     */
    constructor(baseBuilder: DecorableTSFBuilder<*, *>) : super(baseBuilder)

    override val isHandlingBinaryNatively = true

    /*
     *******************************************************************************************************************
     * Factory methods: parsers
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, data: ByteArray, offset: Int,
            length: Int): CirJsonParser {
        val ioContext = createContext(createContentReference(data, offset, length), true, null)

        if (inputDecorator != null) {
            val input = inputDecorator.decorate(ioContext, data, offset, length)

            if (input != null) {
                return createParser(readContext, ioContext, input)
            }
        }

        return createParser(readContext, ioContext, data, offset, length)
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, content: CharArray, offset: Int,
            length: Int): CirJsonParser {
        return nonByteSource()
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, input: DataInput): CirJsonParser {
        val ioContext = createContext(createContentReference(input), false)
        return createParser(readContext, ioContext, decorate(ioContext, input))
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, file: File): CirJsonParser {
        val input = fileInputStream(file)
        val ioContext = createContext(createContentReference(file), true)
        return createParser(readContext, ioContext, decorate(ioContext, input))
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, path: Path): CirJsonParser {
        val input = pathInputStream(path)
        val ioContext = createContext(createContentReference(path), true)
        return createParser(readContext, ioContext, decorate(ioContext, input))
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, input: InputStream): CirJsonParser {
        val ioContext = createContext(createContentReference(input), false)
        return createParser(readContext, ioContext, decorate(ioContext, input))
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, reader: Reader): CirJsonParser {
        return nonByteSource()
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, content: String): CirJsonParser {
        return nonByteSource()
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, url: URL): CirJsonParser {
        val input = optimizedStreamFromURL(url)
        val ioContext = createContext(createContentReference(url), true)
        return createParser(readContext, ioContext, decorate(ioContext, input))
    }

    @Throws(CirJacksonException::class)
    abstract fun createParser(readContext: ObjectReadContext, ioContext: IOContext, data: ByteArray, offset: Int,
            length: Int): CirJsonParser

    @Throws(CirJacksonException::class)
    abstract fun createParser(readContext: ObjectReadContext, ioContext: IOContext, input: DataInput): CirJsonParser

    @Throws(CirJacksonException::class)
    abstract fun createParser(readContext: ObjectReadContext, ioContext: IOContext, input: InputStream): CirJsonParser

    /*
     *******************************************************************************************************************
     * Factory methods: generators
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun createGenerator(writeContext: ObjectWriteContext, file: File,
            encoding: CirJsonEncoding): CirJsonGenerator {
        val output = fileOutputStream(file)
        val ioContext = createContext(createContentReference(output), true, encoding)
        return decorate(createGenerator(writeContext, ioContext, decorate(ioContext, output)))
    }

    @Throws(CirJacksonException::class)
    override fun createGenerator(writeContext: ObjectWriteContext, path: Path,
            encoding: CirJsonEncoding): CirJsonGenerator {
        val output = pathOutputStream(path)
        val ioContext = createContext(createContentReference(path), true, encoding)
        return decorate(createGenerator(writeContext, ioContext, decorate(ioContext, output)))
    }

    @Throws(CirJacksonException::class)
    override fun createGenerator(writeContext: ObjectWriteContext, output: OutputStream,
            encoding: CirJsonEncoding): CirJsonGenerator {
        val ioContext = createContext(createContentReference(output), false, encoding)
        return decorate(createGenerator(writeContext, ioContext, decorate(ioContext, output)))
    }

    @Throws(CirJacksonException::class)
    override fun createGenerator(writeContext: ObjectWriteContext, writer: Writer): CirJsonGenerator {
        return nonByteTarget()
    }

    /*
     *******************************************************************************************************************
     * Factory methods: context objects
     *******************************************************************************************************************
     */
    override fun createContentReference(contentReference: Any): ContentReference {
        return ContentReference.construct(false, contentReference, errorReportConfiguration)
    }

    override fun createContentReference(contentReference: Any, offset: Int, length: Int): ContentReference {
        return ContentReference.construct(false, contentReference, offset, length, errorReportConfiguration)
    }

    /*
     *******************************************************************************************************************
     * Factory methods: abstract, for subclasses to implement
     *******************************************************************************************************************
     */

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
     * @param ioContext IOContext for generator to use
     *
     * @param output Writer for generator to use
     *
     * @return Generator constructed
     *
     * @throws CirJacksonException If there is a problem constructing generator
     */
    @Throws(CirJacksonException::class)
    protected abstract fun createGenerator(writeContext: ObjectWriteContext, ioContext: IOContext,
            output: OutputStream): CirJsonGenerator

    @Throws(CirJacksonException::class)
    protected fun <T> nonByteSource(): T {
        throw UnsupportedOperationException("Cannot create parser for character-based (not byte-based) source")
    }

    @Throws(CirJacksonException::class)
    protected fun <T> nonByteTarget(): T {
        throw UnsupportedOperationException("Cannot create generator for character-based (not byte-based) target")
    }

}