package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.PrettyPrinter

/**
 * [PrettyPrinter] implementation that adds no indentation, just implements everything necessary for value output to
 * work as expected, and provide simpler extension points to allow for creating simple custom implementations that add
 * specific decoration or overrides. Since behavior then is very similar to using no pretty printer at all, usually
 * subclasses are used.
 *
 * Beyond purely minimal implementation, there is limited amount of configurability which may be useful for actual use:
 * for example, it is possible to redefine separator used between root-level values (default is single space; can be
 * changed to line-feed).
 *
 * Note: does NOT implement [Instantiatable] since this is a stateless implementation; that is, a single instance can be
 * shared between threads.
 */
open class MinimalPrettyPrinter(protected var myRootValueSeparator: String?) : PrettyPrinter {

    protected var mySeparators = PrettyPrinter.DEFAULT_SEPARATORS.withObjectNameValueSpacing(Separators.Spacing.NONE)

    /**
     * @param separator Root value separator definitions
     *
     * @return This pretty-printer instance to allow call chaining
     */
    fun setRootValueSeparator(separator: String?): MinimalPrettyPrinter {
        myRootValueSeparator = separator
        return this
    }

    /**
     * @param separators Separator definitions
     *
     * @return This pretty-printer instance to allow call chaining
     */
    fun setSeparator(separators: Separators): MinimalPrettyPrinter {
        mySeparators = separators
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRootValueSeparator(generator: CirJsonGenerator) {
        if (myRootValueSeparator != null) {
            generator.writeRaw(myRootValueSeparator!!)
        }
    }

    @Throws(CirJacksonException::class)
    override fun writeStartObject(generator: CirJsonGenerator) {
        generator.writeRaw('{')
    }

    @Throws(CirJacksonException::class)
    override fun writeEndObject(generator: CirJsonGenerator, numberOfEntries: Int) {
        generator.writeRaw('}')
    }

    @Throws(CirJacksonException::class)
    override fun writeObjectEntrySeparator(generator: CirJsonGenerator) {
        generator.writeRaw(mySeparators.objectEntrySeparator)
    }

    @Throws(CirJacksonException::class)
    override fun writeObjectNameValueSeparator(generator: CirJsonGenerator) {
        generator.writeRaw(mySeparators.objectNameValueSeparator)
    }

    @Throws(CirJacksonException::class)
    override fun writeStartArray(generator: CirJsonGenerator) {
        generator.writeRaw('[')
    }

    @Throws(CirJacksonException::class)
    override fun writeEndArray(generator: CirJsonGenerator, numberOfEntries: Int) {
        generator.writeRaw(']')
    }

    @Throws(CirJacksonException::class)
    override fun writeArrayValueSeparator(generator: CirJsonGenerator) {
        generator.writeRaw(mySeparators.arrayElementSeparator)
    }

    @Throws(CirJacksonException::class)
    override fun beforeArrayValues(generator: CirJsonGenerator) {
        // no-op
    }

    @Throws(CirJacksonException::class)
    override fun beforeObjectEntries(generator: CirJsonGenerator) {
        // no-op
    }

}