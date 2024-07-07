package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.PrettyPrinter

/**
 * Default [PrettyPrinter] implementation that uses 2-space indentation with platform-default linefeeds. Usually this
 * class is not instantiated directly, but instead instantiated by `CirJsonFactory` or databind level mapper.
 *
 * If you override this class, take note of [Instantiatable], as subclasses will still create an instance of
 * `DefaultPrettyPrinter`.
 *
 * This class is designed for the CirJSON data format. It works on other formats with same logical model (such as binary
 * `CBOR` and `Smile` formats), but may not work as-is for other data formats, most notably `XML`. It may be necessary
 * to use format-specific [PrettyPrinter] implementation specific to that format.
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

    /**
     * Interface that defines objects that can produce indentation used to separate object entries and array values.
     * Indentation in this context just means insertion of white space, independent of whether linefeeds are output.
     */
    interface Indenter {

        @Throws(CirJacksonException::class)
        fun writeIndentation(generator: CirJsonGenerator, level: Int)

        /**
         * Returns `true` if indenter is considered inline (does not add linefeeds), `false` otherwise
         */
        val isInline: Boolean

    }

    /**
     * Dummy implementation that adds no indentation whatsoever
     */
    open class NopIndenter protected constructor() : Indenter {

        override fun writeIndentation(generator: CirJsonGenerator, level: Int) {}

        override val isInline: Boolean = true

        companion object {

            private val INSTANCE = NopIndenter()

            fun instance(): NopIndenter = INSTANCE

        }

    }

    /**
     * This is a very simple indenter that only adds a single space for indentation. It is used as the default indenter
     * for array values.
     */
    class FixedSpaceIndenter : NopIndenter() {

        override fun writeIndentation(generator: CirJsonGenerator, level: Int) {
            generator.writeRaw(' ')
        }

        override val isInline: Boolean = true

    }

}