package org.cirjson.cirjackson.core.base

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.io.ContentReference
import java.io.*
import java.net.URL

/**
 * Intermediate [TokenStreamFactory] subclass used as the base for textual data formats.
 */
abstract class TextualTSFactory : DecorableTSFactory {

    constructor(streamReadConstraints: StreamReadConstraints, streamWriteConstraints: StreamWriteConstraints,
            errorReportConfiguration: ErrorReportConfiguration, formatReadFeatures: Int,
            formatWriteFeatures: Int) : super(streamReadConstraints, streamWriteConstraints, errorReportConfiguration,
            formatReadFeatures, formatWriteFeatures)

    constructor(src: TextualTSFactory) : super(src)

    /*
     *******************************************************************************************************************
     * Default introspection
     *******************************************************************************************************************
     */

    override val isHandlingBinaryNatively: Boolean
        get() = false

    /*
     *******************************************************************************************************************
     * Factory methods: parsers, with context
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, data: ByteArray, offset: Int, len: Int): CirJsonParser {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, content: CharArray, offset: Int,
            len: Int): CirJsonParser {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, input: DataInput): CirJsonParser {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, file: File): CirJsonParser {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, input: InputStream): CirJsonParser {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, reader: Reader): CirJsonParser {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, content: String): CirJsonParser {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun createParser(readContext: ObjectReadContext, url: URL): CirJsonParser {
        TODO("Not yet implemented")
    }

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

    override fun copy(): TokenStreamFactory {
        TODO("Not yet implemented")
    }

    override fun snapshot(): TokenStreamFactory {
        TODO("Not yet implemented")
    }

    override fun rebuild(): TSFBuilder<*, *> {
        TODO("Not yet implemented")
    }

    override val isRequiringPropertyOrdering: Boolean
        get() = TODO("Not yet implemented")

    override val isParsingAsyncPossible: Boolean
        get() = TODO("Not yet implemented")

    override fun canUseSchema(schema: FormatSchema): Boolean {
        TODO("Not yet implemented")
    }

    override val formatName: String
        get() = TODO("Not yet implemented")

    override fun <P : CirJsonParser, ByteArrayFeeder> createNonBlockingByteArrayParser(
            readContext: ObjectReadContext): P {
        TODO("Not yet implemented")
    }

    override fun <P : CirJsonParser, ByteArrayFeeder> createNonBlockingByteBufferParser(
            readContext: ObjectReadContext): P {
        TODO("Not yet implemented")
    }

    override fun createContentReference(contentReference: Any): ContentReference {
        TODO("Not yet implemented")
    }

    override fun createContentReference(contentReference: Any, offset: Int, length: Int): ContentReference {
        TODO("Not yet implemented")
    }

    override fun version(): Version {
        TODO("Not yet implemented")
    }

}