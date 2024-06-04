package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.io.DataOutputAsStream
import java.io.*
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Intermediate base class for actual format-specific factories for constructing parsers (reading) and generators
 * (writing).
 */
abstract class TokenStreamFactory : Versioned {

    /*
     *******************************************************************************************************************
     * Capability introspection
     *******************************************************************************************************************
     */

    /**
     * Introspection that higher-level functionality may call to see whether underlying data format requires a stable
     * ordering of object properties or not. This is usually used for determining whether to force a stable ordering
     * (like alphabetic ordering by name) if no ordering is explicitly specified.
     *
     * Default implementation returns `false` as CirJSON does NOT require stable ordering. Formats that require ordering
     * include positional textual formats like `CSV`, and schema-based binary formats like `Avro`.
     */
    abstract val isRequiringPropertyOrdering: Boolean

    /**
     * Introspection that higher-level functionality may call to see whether underlying data format can read and write
     * binary data natively; that is, embedded it as-is without using encodings such as Base64.
     *
     * Default implementation returns `false` as CirJSON does not support native access: all binary content must use
     * Base64 encoding. Most binary formats (like Smile and Avro) support native binary content.
     */
    abstract val isHandlingBinaryNatively: Boolean

    /**
     * Introspection that can be used to check whether this factory can create non-blocking parsers: parsers that do not
     * use blocking I/O abstractions but instead use a [org.cirjson.cirjackson.core.async.NonBlockingInputFeeder].
     *
     * Helps know whether this factory supports non-blocking ("async") parsing or not (and consequently whether
     * `createNonBlockingXxx()` method(s) work)
     */
    abstract val isParsingAsyncPossible: Boolean

    /**
     * Value for accessing kind of [FormatFeature] that a parser [CirJsonParser] produced by this factory would accept,
     * if any; `null` returned if none.
     */
    abstract val formatReadFeatureType: Class<out FormatFeature>

    /**
     * Value for accessing kind of [FormatFeature] that a parser [CirJsonGenerator] produced by this factory would
     * accept, if any; `null` returned if none.
     */
    abstract val formatWriteFeatureType: Class<out FormatFeature>

    /*
     *******************************************************************************************************************
     * Format detection functionality
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to quickly check whether given schema is something that parsers and/or generators
     * constructed by this factory could use. Note that this means possible use, at the level of data format (i.e.
     * schema is for same data format as parsers and generators this factory constructs); individual schema instances
     * may have further usage restrictions.
     *
     * @param schema Schema instance to check
     *
     * @return Whether parsers and generators constructed by this factory can use specified format schema instance
     */
    abstract fun canUseSchema(schema: FormatSchema): Boolean

    /**
     * Value that returns short textual id identifying format this factory supports.
     */
    abstract val formatName: String

    /*
     *******************************************************************************************************************
     * Configuration access
     *******************************************************************************************************************
     */

    /**
     * Method that verifies if the feature is enabled fo this factory.
     *
     * @param feature The feature to check.
     *
     * @return Whether it's enabled or not.
     */
    abstract fun isEnabled(feature: CirJsonFactory.Feature): Boolean

    /**
     * Method that verifies if the feature is enabled fo this factory.
     *
     * @param feature The feature to check.
     *
     * @return Whether it's enabled or not.
     */
    abstract fun isEnabled(feature: StreamReadFeature): Boolean

    /**
     * Method that verifies if the feature is enabled fo this factory.
     *
     * @param feature The feature to check.
     *
     * @return Whether it's enabled or not.
     */
    abstract fun isEnabled(feature: StreamWriteFeature): Boolean

    /**
     * Method that verifies if the feature is enabled fo this factory.
     *
     * @param feature The feature to check.
     *
     * @return Whether it's enabled or not.
     */
    abstract fun isEnabled(feature: CirJsonParser.Feature): Boolean

    /**
     * Method that verifies if the feature is enabled fo this factory.
     *
     * @param feature The feature to check.
     *
     * @return Whether it's enabled or not.
     */
    abstract fun isEnabled(feature: CirJsonGenerator.Feature): Boolean

    /**
     * Value for getting bit set of all [CirJsonFactory.Feature]s enabled.
     */
    abstract val factoryFeatures: Int

    /**
     * Value for getting bit set of all [CirJsonParser.Feature]s enabled.
     */
    abstract val parserFeatures: Int

    /**
     * Value for getting bit set of all [CirJsonGenerator.Feature]s enabled.
     */
    abstract val generatorFeatures: Int

    /**
     * Value for getting bit set of all format-specific parser features enabled.
     */
    abstract val formatParserFeatures: Int

    /**
     * Value for getting bit set of all format-specific generator features enabled.
     */
    abstract val formatGeneratorFeatures: Int

    /*
     *******************************************************************************************************************
     * Constraints violation checking
     *******************************************************************************************************************
     */

    /**
     * Get the constraints to apply when performing streaming reads done by [CirJsonParser]s constructed by this
     * factory.
     */
    abstract val streamReadConstraints: StreamReadConstraints

    /**
     * Get the constraints to apply when performing streaming writes done by [CirJsonGenerator]s constructed by this
     * factory.
     */
    abstract val streamWriteConstraints: StreamWriteConstraints

    /*
     *******************************************************************************************************************
     * Factory methods, parsers
     *******************************************************************************************************************
     */

    /**
     * Method for constructing parser for parsing the contents of given byte array.
     *
     * @param data Buffer that contains data to parse
     */
    @Throws(IOException::class)
    abstract fun createParser(data: ByteArray): CirJsonParser

    /**
     * Method for constructing parser for parsing the contents of given byte array.
     *
     * @param data Buffer that contains data to parse
     * @param offset Offset of the first data byte within buffer
     * @param len Length of contents to parse within buffer
     *
     * @return Constructed parser
     */
    @Throws(IOException::class)
    abstract fun createParser(data: ByteArray, offset: Int, len: Int): CirJsonParser

    /**
     * Method for constructing parser for parsing contents of given char array.
     *
     * @param content Array that contains data to parse
     *
     * @return Constructed parser
     */
    @Throws(IOException::class)
    abstract fun createParser(content: CharArray): CirJsonParser

    /**
     * Method for constructing parser for parsing contents of given char array.
     *
     * @param content Array that contains data to parse
     * @param offset Offset of the first data byte within buffer
     * @param len Length of contents to parse within buffer
     *
     * @return Constructed parser
     */
    @Throws(IOException::class)
    abstract fun createParser(content: CharArray, offset: Int, len: Int): CirJsonParser

    /**
     * Optional method for constructing parser for reading contents from specified [DataInput] instance.
     *
     * If this factory does not support [DataInput] as source, will throw [UnsupportedOperationException]
     *
     * @param input The data to parse
     *
     * @return Constructed parser
     */
    @Throws(IOException::class)
    abstract fun createParser(input: DataInput): CirJsonParser

    /**
     * Method for constructing CirJSON parser instance to parse contents of specified file.
     *
     * Encoding is auto-detected from contents according to JSON specification recommended mechanism. CirJSON
     * specification supports only UTF-8, UTF-16 and UTF-32 as valid encodings, so auto-detection implemented only for
     * this charsets. For other charsets use the `createParser` that uses a [Reader].
     *
     * Underlying input stream (needed for reading contents) will be **owned** (and managed, i.e. closed as need be) by
     * the parser, since caller has no access to it.
     *
     * @param file File that contains CirJSON content to parse
     *
     * @return Constructed parser
     */
    @Throws(IOException::class)
    abstract fun createParser(file: File): CirJsonParser

    /**
     * Method for constructing CirJSON parser instance to parse the contents accessed via specified input stream.
     *
     * The input stream will **not be owned** by the parser, it will still be managed (i.e. closed if end-of-stream is
     * reached, or parser close method called) if (and only if) [StreamReadFeature.AUTO_CLOSE_SOURCE] is enabled.
     *
     * Note: no encoding argument is taken since it can always be auto-detected as suggested by CirJSON RFC. CirJSON
     * specification supports only UTF-8, UTF-16 and UTF-32 as valid encodings, so auto-detection implemented only for
     * this charsets. For other charsets use the `createParser` that uses a [Reader].
     *
     * @param input InputStream to use for reading CirJSON content to parse
     *
     * @return Constructed parser
     */
    @Throws(IOException::class)
    abstract fun createParser(input: InputStream): CirJsonParser

    /**
     * Method for constructing parser for parsing the contents accessed via specified Reader.
     *
     * The read stream will **not be owned** by the parser, it will still be managed (i.e. closed if end-of-stream is
     * reached, or parser close method called) if (and only if) [StreamReadFeature.AUTO_CLOSE_SOURCE] is enabled.
     *
     * @param reader Reader to use for reading CirJSON content to parse
     *
     * @return Constructed parser
     */
    @Throws(IOException::class)
    abstract fun createParser(reader: Reader): CirJsonParser

    /**
     * Method for constructing parser for parsing contents of given String.
     *
     * @param content The content to parse
     *
     * @return Constructed parser
     */
    @Throws(IOException::class)
    abstract fun createParser(content: String): CirJsonParser

    /**
     * Method for constructing CirJSON parser instance to parse contents of resource reference by given URL.
     *
     * Encoding is auto-detected from contents according to CirJSON specification recommended mechanism. CirJSON
     * specification supports only UTF-8, UTF-16 and UTF-32 as valid encodings, so auto-detection implemented only for
     * this charsets. For other charsets use the `createParser` that uses a [Reader].
     *
     * Underlying input stream (needed for reading contents) will be **owned** (and managed, i.e. closed as need be) by
     * the parser, since caller has no access to it.
     *
     * @param url URL pointing to resource that contains CirJSON content to parse
     *
     * @return Constructed parser
     */
    @Throws(IOException::class)
    abstract fun createParser(url: URL): CirJsonParser

    /**
     * Optional method for constructing parser for non-blocking parsing via
     * [org.cirjson.cirjackson.core.async.ByteArrayFeeder] interface (accessed using
     * [CirJsonParser.getNonBlockingInputFeeder] from constructed instance).
     *
     * If this factory does not support non-blocking parsing (either at all, or from byte array), will throw
     * [UnsupportedOperationException].
     *
     * Note that CirJSON-backed factory only supports parsing of UTF-8 encoded CirJSON content (and US-ASCII since it is
     * proper subset); other encodings are not supported at this point.
     *
     * @return Constructed parser
     *
     * @throws IOException If there are problems constructing parser
     */
    @Throws(IOException::class)
    abstract fun createNonBlockingByteArrayParser(): CirJsonParser

    /**
     * Optional method for constructing parser for non-blocking parsing via
     * [org.cirjson.cirjackson.core.async.ByteBufferFeeder] interface (accessed using
     * [CirJsonParser.getNonBlockingInputFeeder] from constructed instance).
     *
     * If this factory does not support non-blocking parsing (either at all, or from byte array), will throw
     * [UnsupportedOperationException].
     *
     * Note that CirJSON-backed factory only supports parsing of UTF-8 encoded CirJSON content (and US-ASCII since it is
     * proper subset); other encodings are not supported at this point.
     *
     * @return Constructed parser
     *
     * @throws IOException If there are problems constructing parser
     */
    @Throws(IOException::class)
    abstract fun createNonBlockingByteBufferParser(): CirJsonParser

    /*
     *******************************************************************************************************************
     * Factory methods, generators
     *******************************************************************************************************************
     */

    /**
     * Method for constructing generator for writing content using specified [DataOutput] instance.
     *
     * @param output DataOutput to use for writing CirJSON content
     *
     * @return Constructed generator
     */
    @Throws(IOException::class)
    abstract fun createGenerator(output: DataOutput): CirJsonGenerator

    /**
     * Convenience method for constructing generator that uses default encoding of the format (UTF-8 for CirJSON and
     * most other data formats).
     *
     * Note: there are formats that use fixed encoding (like most binary data formats).
     *
     * @param output DataOutput to use for writing CirJSON content
     * @param encoding Character encoding to use
     *
     * @return Constructed generator
     */
    @Throws(IOException::class)
    abstract fun createGenerator(output: DataOutput, encoding: CirJsonEncoding): CirJsonGenerator

    /**
     * Method for constructing JSON generator for writing CirJSON content to specified file, overwriting contents it
     * might have (or creating it if such file does not yet exist). Encoding to use must be specified, and needs to be
     * one of available types (as per CirJSON specification).
     *
     * Underlying stream **is owned** by the generator constructed, i.e. generator will handle closing of file when
     * [CirJsonGenerator.close] is called.
     *
     * @param file File to write contents to
     * @param encoding Character encoding to use
     *
     * @return Constructed generator
     */
    @Throws(IOException::class)
    abstract fun createGenerator(file: File, encoding: CirJsonEncoding): CirJsonGenerator

    /**
     * Convenience method for constructing generator that uses default encoding of the format (UTF-8 for CirJSON and
     * most other data formats).
     *
     * Note: there are formats that use fixed encoding (like most binary data formats).
     *
     * @param output OutputStream to use for writing CirJSON content
     *
     * @return Constructed generator
     */
    @Throws(IOException::class)
    abstract fun createGenerator(output: OutputStream): CirJsonGenerator

    /**
     * Method for constructing JSON generator for writing CirJSON content using specified output stream. Encoding to use
     * must be specified, and needs to be one of available types (as per CirJSON specification).
     *
     * Underlying stream **is NOT owned** by the generator constructed, so that generator will NOT close the output
     * stream when [CirJsonGenerator.close] is called (unless auto-closing feature,
     * [CirJsonGenerator.Feature.AUTO_CLOSE_TARGET] is enabled). Using application needs to close it explicitly if this
     * is the case.
     *
     * Note: there are formats that use fixed encoding (like most binary data formats) and that ignore passed in
     * encoding.
     *
     * @param output OutputStream to use for writing CirJSON content
     * @param encoding Character encoding to use
     *
     * @return Constructed generator
     */
    @Throws(IOException::class)
    abstract fun createGenerator(output: OutputStream, encoding: CirJsonEncoding): CirJsonGenerator

    /**
     * Method for constructing CirJSON generator for writing CirJSON content using specified Writer.
     *
     * Underlying stream **is NOT owned** by the generator constructed, so that generator will NOT close the Reader when
     * [CirJsonGenerator.close] is called (unless auto-closing feature, [CirJsonGenerator.Feature.AUTO_CLOSE_TARGET] is
     * enabled). Using application needs to close it explicitly.
     *
     * @param writer Writer to use for writing CirJSON content
     *
     * @return Constructed generator
     */
    @Throws(IOException::class)
    abstract fun createGenerator(writer: Writer): CirJsonGenerator

    /*
     *******************************************************************************************************************
     * Internal factory methods, other
     *******************************************************************************************************************
     */

    /**
     * Method that creates an OutputStream from a DataOutput, so that all writing operations use the DataOutput
     *
     * @param output DataOutput to write to
     */
    protected fun createDataOutputWrapper(output: DataOutput): OutputStream {
        return DataOutputAsStream(output)
    }

    /**
     * Helper method used for constructing an optimal stream for parsers to use, when input is to be read from a URL.
     * This helps when reading file content via URL.
     *
     * @param url Source to read content to parse from
     *
     * @return InputStream constructed for given [URL]
     *
     * @throws IOException If there is a problem accessing content from specified [URL]
     */
    @Throws(IOException::class)
    protected fun optimizedStreamFromURL(url: URL): InputStream {
        if (url.protocol == "file") {
            val host = url.host

            if (host.isNullOrEmpty()) {
                val path = url.path

                if (path.indexOf('%') < 0) {
                    return Files.newInputStream(Paths.get(url.path))
                }
            }
        }

        return url.openStream()
    }

    /**
     * Helper methods used for constructing an [InputStream] for parsers to use, when input is to be read from given
     * [File].
     *
     * @param file File to open stream for
     *
     * @return [InputStream] constructed
     *
     * @throws IOException If there is a problem opening the stream
     */
    @Throws(IOException::class)
    protected fun fileInputStream(file: File): InputStream {
        return Files.newInputStream(file.toPath())
    }

    /**
     * Helper methods used for constructing an [OutputStream] for generator to use, when target is to be written into
     * given [File].
     *
     * @param file File to open stream for
     *
     * @return [OutputStream] constructed
     *
     * @throws IOException If there is a problem opening the stream
     */
    @Throws(IOException::class)
    protected fun fileOutputStream(file: File): OutputStream {
        return Files.newOutputStream(file.toPath())
    }

    /*
     *******************************************************************************************************************
     * Range check helper methods
     *******************************************************************************************************************
     */

    @Throws(IllegalArgumentException::class)
    protected fun checkRangeBoundsForByteArray(data: ByteArray?, offset: Int, len: Int) {
        data ?: reportRangeError("Invalid `data` argument: `null`")
        checkRange(data!!.size, offset, len)
    }

    @Throws(IllegalArgumentException::class)
    private fun checkRange(dataLen: Int, offset: Int, len: Int) {
        val end = offset + len

        // Note: we are checking that:
        //
        // !(offset < 0)
        // !(len < 0)
        // !((offset + len) < 0) // int overflow!
        // !((offset + len) > dataLen) == !((dataLen - (offset+len)) < 0)
        //
        // All can be optimized by doing bitwise OR and checking for negative:
        val anyNegs = offset or len or end or (dataLen - end)

        if (anyNegs < 0) {
            reportRangeError("Invalid 'offset' ($offset) and/or 'len' ($len) arguments for `data` of length $dataLen")
        }
    }

    @Throws(IllegalArgumentException::class)
    protected fun checkRangeBoundsForCharArray(data: CharArray?, offset: Int, len: Int) {
        data ?: reportRangeError("Invalid `data` argument: `null`")
        checkRange(data!!.size, offset, len)
    }

    /**
     * Method that throws an [IllegalArgumentException] with the given [message]
     *
     * @param message The message of the exception
     *
     * @throws IllegalArgumentException The exception thrown
     */
    @Throws(IllegalArgumentException::class)
    protected fun reportRangeError(message: String) {
        throw IllegalArgumentException(message)
    }

}