package org.cirjson.cirjackson.core.base

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.io.IOContext
import java.io.*
import kotlin.test.Test

class FactoryBaseImplementationsTest {

    @Test
    fun testBogus() {
        ToyBinaryFormatFactory()
        ToyTextualFormatFactory()
    }

    private class ToyBinaryFormatFactory :
            BinaryTSFactory(StreamReadConstraints.defaults(), StreamWriteConstraints.defaults(),
                    ErrorReportConfiguration.defaults(), 0, 0) {

        override fun copy(): TokenStreamFactory {
            return this
        }

        override fun createParser(readContext: ObjectReadContext, ioContext: IOContext, data: ByteArray?, offset: Int,
                length: Int): CirJsonParser {
            throw UnsupportedOperationException()
        }

        override fun createParser(readContext: ObjectReadContext, ioContext: IOContext,
                input: DataInput): CirJsonParser {
            throw UnsupportedOperationException()
        }

        override fun createParser(readContext: ObjectReadContext, ioContext: IOContext,
                input: InputStream): CirJsonParser {
            throw UnsupportedOperationException()
        }

        override fun createGenerator(writeContext: ObjectWriteContext, ioContext: IOContext,
                output: OutputStream): CirJsonGenerator {
            throw UnsupportedOperationException()
        }

        override fun snapshot(): TokenStreamFactory {
            return this
        }

        override fun rebuild(): TSFBuilder<*, *> {
            throw UnsupportedOperationException()
        }

        override val isParsingAsyncPossible: Boolean
            get() = false

        override fun canUseSchema(schema: FormatSchema): Boolean {
            return false
        }

        override val formatName: String
            get() = "Mock"

        override fun version(): Version {
            return Version.unknownVersion()
        }

    }

    private class ToyTextualFormatFactory :
            TextualTSFactory(StreamReadConstraints.defaults(), StreamWriteConstraints.defaults(),
                    ErrorReportConfiguration.defaults(), 0, 0) {

        override fun copy(): TokenStreamFactory {
            return this
        }

        override fun createParser(readContext: ObjectReadContext, context: IOContext, data: ByteArray?, offset: Int,
                len: Int): CirJsonParser {
            throw UnsupportedOperationException()
        }

        override fun createParser(readContext: ObjectReadContext, context: IOContext, content: CharArray?, offset: Int,
                len: Int, recyclable: Boolean): CirJsonParser {
            throw UnsupportedOperationException()
        }

        override fun createParser(readContext: ObjectReadContext, context: IOContext,
                input: DataInput): CirJsonParser {
            throw UnsupportedOperationException()
        }

        override fun createParser(readContext: ObjectReadContext, context: IOContext,
                input: InputStream): CirJsonParser {
            throw UnsupportedOperationException()
        }

        override fun createParser(readContext: ObjectReadContext, context: IOContext, reader: Reader): CirJsonParser {
            throw UnsupportedOperationException()
        }

        override fun createGenerator(writeContext: ObjectWriteContext, context: IOContext,
                writer: Writer): CirJsonGenerator {
            throw UnsupportedOperationException()
        }

        override fun createUTF8Generator(writeContext: ObjectWriteContext, context: IOContext,
                output: OutputStream): CirJsonGenerator {
            throw UnsupportedOperationException()
        }

        override fun snapshot(): TokenStreamFactory {
            return this
        }

        override fun rebuild(): TSFBuilder<*, *> {
            throw UnsupportedOperationException()
        }

        override val isParsingAsyncPossible: Boolean
            get() = false

        override fun canUseSchema(schema: FormatSchema): Boolean {
            return false
        }

        override val formatName: String
            get() = "Mock"

        override fun version(): Version {
            return Version.unknownVersion()
        }

    }

}