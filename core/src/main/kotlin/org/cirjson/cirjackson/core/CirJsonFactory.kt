package org.cirjson.cirjackson.core

import java.io.*
import java.net.URL

/**
 * The main factory class of CirJackson package, used to configure and construct reader (aka parser, [CirJsonParser])
 * and writer (aka generator, [CirJsonGenerator]) instances.
 *
 * Factory instances are thread-safe and reusable after configuration (if any). Typically, applications and services use
 * only a single globally shared factory instance, unless they need differently configured factories. Factory reuse is
 * important if efficiency matters; most recycling of expensive construct is done on per-factory basis.
 *
 * Creation of a factory instance is a light-weight operation, and since there is no need for pluggable alternative
 * implementations (as there is no "standard" CirJSON processor API to implement), the default constructor is used for
 * constructing factory instances.
 */
open class CirJsonFactory : TokenStreamFactory() {

    override val isRequiringPropertyOrdering: Boolean
        get() = TODO("Not yet implemented")

    override val isHandlingBinaryNatively: Boolean
        get() = TODO("Not yet implemented")

    override val isParsingAsyncPossible: Boolean
        get() = TODO("Not yet implemented")

    override val formatReadFeatureType: Class<out FormatFeature>
        get() = TODO("Not yet implemented")

    override val formatWriteFeatureType: Class<out FormatFeature>
        get() = TODO("Not yet implemented")

    override fun canUseSchema(schema: FormatSchema): Boolean {
        TODO("Not yet implemented")
    }

    override val formatName: String
        get() = TODO("Not yet implemented")

    override fun isEnabled(feature: Feature): Boolean {
        TODO("Not yet implemented")
    }

    override fun isEnabled(feature: StreamReadFeature): Boolean {
        TODO("Not yet implemented")
    }

    override fun isEnabled(feature: StreamWriteFeature): Boolean {
        TODO("Not yet implemented")
    }

    override fun isEnabled(feature: CirJsonParser.Feature): Boolean {
        TODO("Not yet implemented")
    }

    override fun isEnabled(feature: CirJsonGenerator.Feature): Boolean {
        TODO("Not yet implemented")
    }

    override val factoryFeatures: Int
        get() = TODO("Not yet implemented")
    override val parserFeatures: Int
        get() = TODO("Not yet implemented")
    override val generatorFeatures: Int
        get() = TODO("Not yet implemented")
    override val formatParserFeatures: Int
        get() = TODO("Not yet implemented")
    override val formatGeneratorFeatures: Int
        get() = TODO("Not yet implemented")
    override val streamReadConstraints: StreamReadConstraints
        get() = TODO("Not yet implemented")
    override val streamWriteConstraints: StreamWriteConstraints
        get() = TODO("Not yet implemented")

    override fun createParser(data: ByteArray): CirJsonParser {
        TODO("Not yet implemented")
    }

    override fun createParser(data: ByteArray, offset: Int, len: Int): CirJsonParser {
        TODO("Not yet implemented")
    }

    override fun createParser(content: CharArray): CirJsonParser {
        TODO("Not yet implemented")
    }

    override fun createParser(content: CharArray, offset: Int, len: Int): CirJsonParser {
        TODO("Not yet implemented")
    }

    override fun createParser(input: DataInput): CirJsonParser {
        TODO("Not yet implemented")
    }

    override fun createParser(file: File): CirJsonParser {
        TODO("Not yet implemented")
    }

    override fun createParser(input: InputStream): CirJsonParser {
        TODO("Not yet implemented")
    }

    override fun createParser(reader: Reader): CirJsonParser {
        TODO("Not yet implemented")
    }

    override fun createParser(content: String): CirJsonParser {
        TODO("Not yet implemented")
    }

    override fun createParser(url: URL): CirJsonParser {
        TODO("Not yet implemented")
    }

    override fun createNonBlockingByteArrayParser(): CirJsonParser {
        TODO("Not yet implemented")
    }

    override fun createNonBlockingByteBufferParser(): CirJsonParser {
        TODO("Not yet implemented")
    }

    override fun createGenerator(output: DataOutput): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    override fun createGenerator(output: DataOutput, encoding: CirJsonEncoding): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    override fun createGenerator(file: File, encoding: CirJsonEncoding): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    override fun createGenerator(output: OutputStream): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    override fun createGenerator(output: OutputStream, encoding: CirJsonEncoding): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    override fun createGenerator(writer: Writer): CirJsonGenerator {
        TODO("Not yet implemented")
    }

    override fun version(): Version {
        TODO("Not yet implemented")
    }

}