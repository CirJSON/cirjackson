package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.PrettyPrinter
import org.cirjson.cirjackson.core.SerializableString
import org.cirjson.cirjackson.core.io.SerializedString

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
 *
 * @property myArrayIndenter By default, uses only spaces to separate array values.
 *
 * @property myObjectIndenter By default, uses linefeed-adding indenter for separate object entries. We'll further
 * configure indenter to use system-specific linefeeds, and 2 spaces per level (as opposed to, say, single tabs).
 *
 * @property myRootValueSeparator String printed between root-level values, if any.
 *
 * @property myNesting Number of open levels of nesting. Used to determine amount of indentation to use.
 *
 * @property myObjectNameValueSeparator Separator between Object property entries, including possible before/after
 * spaces.
 *
 * @property myObjectEntrySeparator Separator between Object property names and values, including possible before/after
 * spaces.
 *
 * @property myArrayElementSeparator Separator between Array elements, including possible before/after spaces.
 */
open class DefaultPrettyPrinter protected constructor(protected var myArrayIndenter: Indenter,
        protected var myObjectIndenter: Indenter, protected val myRootValueSeparator: SerializableString?,
        @Transient protected var myNesting: Int, protected val mySeparators: Separators,
        protected val myObjectNameValueSeparator: String, protected val myObjectEntrySeparator: String,
        protected val myArrayElementSeparator: String) : PrettyPrinter, Instantiatable<DefaultPrettyPrinter> {

    /**
     * Default constructor for "vanilla" instance with default settings,
     * except for [Separators] overrides.
     *
     * @param separators Custom separator definition over defaults.
     */
    constructor(separators: Separators) : this(FixedSpaceIndenter.instance(), DefaultIndenter.SYSTEM_LINEFEED_INSTANCE,
            separators.rootSeparator?.let { SerializedString(it) }, 0, separators,
            separators.objectNameValueSpacing.apply(separators.objectNameValueSeparator),
            separators.objectEntrySpacing.apply(separators.objectEntrySeparator),
            separators.arrayElementSpacing.apply(separators.arrayElementSeparator))

    /**
     * Copy constructor.
     *
     * @param base DefaultPrettyPrinter being copied
     */
    constructor(base: DefaultPrettyPrinter) : this(base.myArrayIndenter, base.myObjectIndenter,
            base.myRootValueSeparator, base.myNesting, base.mySeparators, base.myObjectNameValueSeparator,
            base.myObjectEntrySeparator, base.myArrayElementSeparator)

    /**
     * Copy constructor with override
     *
     * @param base DefaultPrettyPrinter being copied
     * @param separators Separators to use instead of ones {@code base} had
     */
    constructor(base: DefaultPrettyPrinter, separators: Separators) : this(base.myArrayIndenter, base.myObjectIndenter,
            separators.rootSeparator?.let { SerializedString(it) }, base.myNesting, separators,
            separators.objectNameValueSpacing.apply(separators.objectNameValueSeparator),
            separators.objectEntrySpacing.apply(separators.objectEntrySeparator),
            separators.arrayElementSpacing.apply(separators.arrayElementSeparator))

    @Throws(CirJacksonException::class)
    override fun writeRootValueSeparator(generator: CirJsonGenerator) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeStartObject(generator: CirJsonGenerator) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeEndObject(generator: CirJsonGenerator, numberOfEntries: Int) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeObjectEntrySeparator(generator: CirJsonGenerator) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeObjectNameValueSeparator(generator: CirJsonGenerator) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeStartArray(generator: CirJsonGenerator) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeEndArray(generator: CirJsonGenerator, numberOfEntries: Int) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeArrayValueSeparator(generator: CirJsonGenerator) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun beforeArrayValues(generator: CirJsonGenerator) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun beforeObjectEntries(generator: CirJsonGenerator) {
        TODO("Not yet implemented")
    }

    override fun createInstance(): DefaultPrettyPrinter {
        return if (javaClass === DefaultPrettyPrinter::class.java) {
            DefaultPrettyPrinter(this)
        } else {
            throw IllegalStateException(
                    "Failed `createInstance()`: ${javaClass.name} does not override method; it has to")
        }
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

        companion object {

            private val INSTANCE = FixedSpaceIndenter()

            fun instance(): FixedSpaceIndenter = INSTANCE

        }

    }

}