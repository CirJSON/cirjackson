package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.PrettyPrinter

/**
 * Default {@link PrettyPrinter} implementation that uses 2-space
 * indentation with platform-default linefeeds.
 * Usually this class is not instantiated directly, but instead
 * instantiated by {@code CirJsonFactory} or databind level mapper.
 *
 * If you override this class, take note of {@link Instantiatable},
 * as subclasses will still create an instance of DefaultPrettyPrinter.
 *
 * This class is designed for the CirJSON data format. It works on other formats
 * with same logical model (such as binary {@code CBOR} and {@code Smile} formats),
 * but may not work as-is for other data formats, most notably {@code XML}.
 * It may be necessary to use format-specific {@link PrettyPrinter}
 * implementation specific to that format.
 */
open class DefaultPrettyPrinter : PrettyPrinter, Instantiatable<DefaultPrettyPrinter> {

    override fun writeRootValueSeparator(generator: CirJsonGenerator) {
        TODO("Not yet implemented")
    }

    override fun writeStartObject(generator: CirJsonGenerator) {
        TODO("Not yet implemented")
    }

    override fun writeEndObject(generator: CirJsonGenerator, numberOfEntries: Int) {
        TODO("Not yet implemented")
    }

    override fun writeObjectEntrySeparator(generator: CirJsonGenerator) {
        TODO("Not yet implemented")
    }

    override fun writeObjectNameValueSeparator(generator: CirJsonGenerator) {
        TODO("Not yet implemented")
    }

    override fun writeStartArray(generator: CirJsonGenerator) {
        TODO("Not yet implemented")
    }

    override fun writeEndArray(generator: CirJsonGenerator, numberOfEntries: Int) {
        TODO("Not yet implemented")
    }

    override fun writeArrayValueSeparator(generator: CirJsonGenerator) {
        TODO("Not yet implemented")
    }

    override fun beforeArrayValues(generator: CirJsonGenerator) {
        TODO("Not yet implemented")
    }

    override fun beforeObjectEntries(generator: CirJsonGenerator) {
        TODO("Not yet implemented")
    }

    override fun createInstance(): DefaultPrettyPrinter {
        TODO("Not yet implemented")
    }

}