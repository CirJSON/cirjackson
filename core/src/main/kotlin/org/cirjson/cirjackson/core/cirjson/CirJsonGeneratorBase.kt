package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.base.GeneratorBase
import org.cirjson.cirjackson.core.io.CharTypes
import org.cirjson.cirjackson.core.io.CharacterEscapes
import org.cirjson.cirjackson.core.io.IOContext
import org.cirjson.cirjackson.core.util.CirJacksonFeatureSet

/**
 * Intermediate base class shared by CirJSON-backed generators like [UTF8CirJsonGenerator] and
 * [WriterBasedCirJsonGenerator].
 *
 * @property formatWriteFeatures Bit flag composed of bits that indicate which [CirJsonWriteFeature]s are enabled.
 *
 * @property myRootValueSeparator Separator to use, if any, between root-level values.
 *
 * @property myConfigurationPrettyPrinter Object that handles pretty-printing (usually additional white space to make
 * results more human-readable) during output. If `null`, no pretty-printing is done.
 */
abstract class CirJsonGeneratorBase protected constructor(objectWriteContext: ObjectWriteContext, ioContext: IOContext,
        streamWriteFeatures: Int, val formatWriteFeatures: Int, protected val myRootValueSeparator: SerializableString,
        protected val myConfigurationPrettyPrinter: PrettyPrinter?, characterEscapes: CharacterEscapes?,
        maxNonEscaped: Int) : GeneratorBase(objectWriteContext, ioContext, streamWriteFeatures) {

    /**
     * Currently active set of output escape code definitions (whether and how to escape or not) for 7-bit ASCII range
     * (first 128 character codes). Defined separately to make potentially customizable
     */
    protected var myOutputEscapes = DEFAULT_OUTPUT_ESCAPES

    /**
     * Definition of custom character escapes to use for generators created by this factory, if any. If `null`, standard
     * data format specific escapes are used.
     *
     * NOTE: although typically set during construction (in constructor), can not be made final due to some edge use
     * cases (CirJSONP support).
     */
    protected var myCharacterEscapes: CharacterEscapes? = null

    override var characterEscapes: CharacterEscapes?
        get() = myCharacterEscapes
        set(value) {}

    /**
     * Value between 128 (0x80) and 65535 (0xFFFF) that indicates highest Unicode code point that will not need
     * escaping; or 0 to indicate that all characters can be represented without escaping. Typically used to force
     * escaping of some portion of character set; for example to always escape non-ASCII characters (if value was 127).
     *
     * NOTE: not all subclasses make use of this setting.
     */
    override val highestNonEscapedChar = if (CirJsonWriteFeature.ESCAPE_NON_ASCII.isEnabledIn(formatWriteFeatures)) {
        127
    } else {
        maxNonEscaped
    }

    /**
     * Flag that is set if quoting is not to be added around CirJSON Object property names.
     */
    protected val myConfigurationUnquoteNames =
            CirJsonWriteFeature.QUOTE_PROPERTY_NAMES.isEnabledIn(formatWriteFeatures)

    /**
     * Flag set to indicate that implicit conversion from number to JSON String is needed (as per
     * [CirJsonWriteFeature.WRITE_NUMBERS_AS_STRINGS]).
     */
    protected val myConfigurationNumbersAsStrings =
            CirJsonWriteFeature.WRITE_NUMBERS_AS_STRINGS.isEnabledIn(formatWriteFeatures)

    /**
     * Whether to write Hex values with upper-case letters (`true`) or lower-case (`false`)
     */
    protected val myConfigurationWriteHexUppercase =
            CirJsonWriteFeature.WRITE_HEX_UPPER_CASE.isEnabledIn(formatWriteFeatures)

    override var streamWriteContext: CirJsonWriteContext = CirJsonWriteContext.createRootContext(
            DuplicateDetector.rootDetector(this)
                    .takeIf { StreamWriteFeature.STRICT_DUPLICATE_DETECTION.isEnabledIn(streamWriteFeatures) })
        protected set

    init {
        this.characterEscapes = characterEscapes
    }

    /*
     *******************************************************************************************************************
     * Versioned, accessors, capabilities
     *******************************************************************************************************************
     */

    override fun version(): Version {
        return PackageVersion.VERSION
    }

    /*
     *******************************************************************************************************************
     * Basic configuration access
     *******************************************************************************************************************
     */

    fun isEnabled(feature: CirJsonWriteFeature): Boolean {
        return feature.isEnabledIn(formatWriteFeatures)
    }

    override val streamWriteCapabilities: CirJacksonFeatureSet<StreamWriteCapability>
        get() = DEFAULT_WRITE_CAPABILITIES

    /*
     *******************************************************************************************************************
     * Overridden output state handling methods
     *******************************************************************************************************************
     */

    override fun currentValue(): Any? {
        return streamWriteContext.currentValue()
    }

    override fun assignCurrentValue(value: Any?) {
        streamWriteContext.assignCurrentValue(value)
    }

    /*
     *******************************************************************************************************************
     * Partial API, structural
     *******************************************************************************************************************
     */

    override fun writeStartArray(currentValue: Any?, size: Int): CirJsonGenerator {
        writeStartArray(currentValue)
        return this
    }

    override fun writeStartObject(currentValue: Any?, size: Int): CirJsonGenerator {
        writeStartObject(currentValue)
        return this
    }

    /*
     *******************************************************************************************************************
     * Partial API, Object property names/ids
     *******************************************************************************************************************
     */

    override val idName: String
        get() = ID_NAME

    override fun writePropertyId(id: Long): CirJsonGenerator {
        writeName(id.toString())
        return this
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected fun verifyPrettyValueWrite(typeMessage: String, status: Int) {
        when (status) {
            CirJsonWriteContext.STATUS_OK_AFTER_COMMA -> {
                myConfigurationPrettyPrinter!!.writeArrayValueSeparator(this)
            }

            CirJsonWriteContext.STATUS_OK_AFTER_COLON -> {
                myConfigurationPrettyPrinter!!.writeObjectNameValueSeparator(this)
            }

            CirJsonWriteContext.STATUS_OK_AFTER_SPACE -> {
                myConfigurationPrettyPrinter!!.writeRootValueSeparator(this)
            }

            CirJsonWriteContext.STATUS_OK_AS_IS -> {
                if (streamWriteContext.isInArray) {
                    myConfigurationPrettyPrinter!!.beforeArrayValues(this)
                } else if (streamWriteContext.isInObject) {
                    myConfigurationPrettyPrinter!!.beforeObjectEntries(this)
                }
            }

            CirJsonWriteContext.STATUS_EXPECT_NAME -> {
                return reportCannotWriteValueExpectName(typeMessage)
            }

            else -> {
                throwInternal()
            }
        }
    }

    @Throws(CirJacksonException::class)
    protected fun <T> reportCannotWriteValueExpectName(typeMessage: String): T {
        throw constructWriteException(
                "Cannot $typeMessage, expecting a property name (context: ${streamWriteContext.typeDescription})")
    }

    companion object {

        const val ID_NAME = "__cirJsonId__"

        /**
         * This is the default set of escape codes, over 7-bit ASCII range (first 128 character codes), used for
         * single-byte UTF-8 characters.
         */
        val DEFAULT_OUTPUT_ESCAPES = CharTypes.sevenBitOutputEscapes

    }

}