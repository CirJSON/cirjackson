package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.base.DecorableTSFactory
import org.cirjson.cirjackson.core.io.CharacterEscapes
import org.cirjson.cirjackson.core.io.SerializedString
import org.cirjson.cirjackson.core.util.DefaultPrettyPrinter
import kotlin.math.max

/**
 * [org.cirjson.cirjackson.core.TSFBuilder] implementation for constructing [CirJsonFactory] instances for
 * reading/writing CirJSON encoded content.
 */
open class CirJsonFactoryBuilder : DecorableTSFactory.DecorableTSFBuilder<CirJsonFactory, CirJsonFactoryBuilder> {

    var characterEscapes: CharacterEscapes?
        protected set

    var rootValueSeparator: SerializableString?
        protected set

    var highestNonEscapedCharCode: Int
        protected set

    /**
     * Character used for quoting Object Property names (if name quoting has not been disabled with
     * [CirJsonWriteFeature.QUOTE_PROPERTY_NAMES]) and CirJSON String values.
     */
    var quoteChar: Char
        protected set

    constructor() : super(StreamReadConstraints.defaults(), StreamWriteConstraints.defaults(),
            ErrorReportConfiguration.defaults(), CirJsonFactory.DEFAULT_CIRJSON_PARSER_FEATURE_FLAGS,
            CirJsonFactory.DEFAULT_CIRJSON_GENERATOR_FEATURE_FLAGS) {
        characterEscapes = null
        rootValueSeparator = DefaultPrettyPrinter.DEFAULT_ROOT_VALUE_SEPARATOR
        highestNonEscapedCharCode = 0
        quoteChar = CirJsonFactory.DEFAULT_QUOTE_CHAR
    }

    constructor(base: CirJsonFactory) : super(base) {
        characterEscapes = base.characterEscapes
        rootValueSeparator = base.rootValueSeparatorInternal
        highestNonEscapedCharCode = base.maximumNonEscapedCharCodeInternal
        quoteChar = base.quoteCharInternal
    }

    override fun build(): CirJsonFactory {
        return CirJsonFactory(this)
    }

    /*
     *******************************************************************************************************************
     * Mutators: CirJSON-parsing features: Parser
     *******************************************************************************************************************
     */

    fun enable(vararg features: CirJsonReadFeature): CirJsonFactoryBuilder {
        for (feature in features) {
            formatReadFeatures = formatReadFeatures or feature.mask
        }

        return this
    }

    fun disable(vararg features: CirJsonReadFeature): CirJsonFactoryBuilder {
        for (feature in features) {
            formatReadFeatures = formatReadFeatures and feature.mask.inv()
        }

        return this
    }

    fun configure(feature: CirJsonReadFeature, state: Boolean): CirJsonFactoryBuilder {
        return if (state) enable(feature) else disable(feature)
    }

    /*
     *******************************************************************************************************************
     * Mutators: CirJSON-parsing features: Generator
     *******************************************************************************************************************
     */

    fun enable(vararg features: CirJsonWriteFeature): CirJsonFactoryBuilder {
        for (feature in features) {
            formatWriteFeatures = formatWriteFeatures or feature.mask
        }

        return this
    }

    fun disable(vararg features: CirJsonWriteFeature): CirJsonFactoryBuilder {
        for (feature in features) {
            formatWriteFeatures = formatWriteFeatures and feature.mask.inv()
        }

        return this
    }

    fun configure(feature: CirJsonWriteFeature, state: Boolean): CirJsonFactoryBuilder {
        return if (state) enable(feature) else disable(feature)
    }

    /*
     *******************************************************************************************************************
     * Mutators: Other CirJSON-specific configuration
     *******************************************************************************************************************
     */

    /**
     * Method for defining custom escapes factory uses for [CirJsonGenerator]s it creates.
     *
     * @param characterEscapes CharacterEscapes to configure, if any; `null` if none
     *
     * @return This builder instance (to allow call chaining)
     */
    fun characterEscapes(characterEscapes: CharacterEscapes?): CirJsonFactoryBuilder {
        this.characterEscapes = characterEscapes
        return this
    }

    /**
     * Method that allows overriding String used for separating root-level CirJSON values (default is single space
     * character)
     *
     * @param separator Separator to use, if any; `null` means that no separator is automatically added
     *
     * @return This builder instance (to allow call chaining)
     */
    fun rootValueSeparator(separator: String?): CirJsonFactoryBuilder {
        rootValueSeparator = separator?.let { SerializedString(it) }
        return this
    }

    /**
     * Method that allows overriding String used for separating root-level CirJSON values (default is single space
     * character)
     *
     * @param separator Separator to use, if any; `null` means that no separator is automatically added
     *
     * @return This builder instance (to allow call chaining)
     */
    fun rootValueSeparator(separator: SerializableString?): CirJsonFactoryBuilder {
        rootValueSeparator = separator
        return this
    }

    /**
     * Method that allows specifying threshold beyond which all characters are automatically escaped (without checking
     * possible custom escaping settings a la [characterEscapes]: for example, to force escaping of all non-ASCII
     * characters (set to 127), or all non-Latin-1 character (set to 255). Default setting is "disabled", specified by
     * passing value of `0` (or negative numbers).
     *
     * NOTE! Lowest legal value (aside from marker 0) is 127: for ASCII range, other checks apply and this threshold is
     * ignored. If value between [1, 126] is specified, 127 will be used instead.
     *
     * @param code Highest character code that is NOT automatically escaped; if positive value above 0, or 0 to indicate
     * that no automatic escaping is applied beside from what CirJSON specification requires (and possible custom escape
     * settings). Values between 1 and 127 are all taken to behave as if 127 is specified: that is, no automatic
     * escaping is applied in ASCII range.
     *
     * @return This builder instance (to allow call chaining)
     */
    fun highestNonEscapedCharCode(code: Int): CirJsonFactoryBuilder {
        highestNonEscapedCharCode = if (code > 0) max(code, 127) else 0
        return this
    }

    /**
     * Method that allows specifying an alternate character used for quoting Object Property names (if name quoting has
     * not been disabled with [CirJsonWriteFeature.QUOTE_PROPERTY_NAMES]) and CirJSON String values.
     *
     * Default value is double-quote (`"`); typical alternative is single-quote/apostrophe (`'`).
     *
     * Only Unicode characters up to the one with the code `0x7F` are allowed, since escaping characters beyond 7-bit
     * ASCII set has deep overhead.
     *
     * @param char Character to use for quoting Object Property names and CirJSON String values.
     *
     * @return This builder instance (to allow call chaining)
     */
    fun quoteChar(char: Char): CirJsonFactoryBuilder {
        if (char.code > 0x7F) {
            throw IllegalArgumentException("Can only use Unicode characters up to 0x7F as quote characters")
        }

        quoteChar = char
        return this
    }

}