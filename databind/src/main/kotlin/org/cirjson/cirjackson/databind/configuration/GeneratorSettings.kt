package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.core.FormatSchema
import org.cirjson.cirjackson.core.PrettyPrinter
import org.cirjson.cirjackson.core.SerializableString
import org.cirjson.cirjackson.core.io.CharacterEscapes
import org.cirjson.cirjackson.core.io.SerializedString
import org.cirjson.cirjackson.core.util.Instantiatable

/**
 * Helper class used for containing settings specifically related to (re)configuring
 * [CirJsonGenerator][org.cirjson.cirjackson.core.CirJsonGenerator] constructed for writing output.
 *
 * @property prettyPrinter To allow for dynamic enabling/disabling of pretty printing, pretty printer can be optionally
 * configured for the writer as well
 *
 * @property schema When using data format that uses a schema, schema is passed to generator.
 *
 * @property characterEscapes Caller may want to specify character escaping details, either as defaults or on a
 * call-by-call basis.
 *
 * @property rootValueSeparator Caller may want to override so-called "root value separator", String added (verbatim,
 * with no quoting or escaping) between values in root context. The default value is a single space character, but this is often changed to linefeed.
 */
class GeneratorSettings(val prettyPrinter: PrettyPrinter?, val schema: FormatSchema?,
        val characterEscapes: CharacterEscapes?, val rootValueSeparator: SerializableString?) {

    fun with(prettyPrinter: PrettyPrinter?): GeneratorSettings {
        if (this.prettyPrinter === prettyPrinter) {
            return this
        }

        return GeneratorSettings(prettyPrinter, schema, characterEscapes, rootValueSeparator)
    }

    fun with(schema: FormatSchema?): GeneratorSettings {
        if (this.schema === schema) {
            return this
        }

        return GeneratorSettings(prettyPrinter, schema, characterEscapes, rootValueSeparator)
    }

    fun with(characterEscapes: CharacterEscapes?): GeneratorSettings {
        if (this.characterEscapes === characterEscapes) {
            return this
        }

        return GeneratorSettings(prettyPrinter, schema, characterEscapes, rootValueSeparator)
    }

    fun withRootValueSeparator(separator: String?): GeneratorSettings {
        if (separator == null) {
            if (rootValueSeparator === NULL_ROOT_VALUE_SEPARATOR) {
                return this
            }

            return GeneratorSettings(prettyPrinter, schema, characterEscapes, NULL_ROOT_VALUE_SEPARATOR)
        }

        if (separator == rootValueSeparatorAsString()) {
            return this
        }

        return GeneratorSettings(prettyPrinter, schema, characterEscapes, SerializedString(separator))
    }

    fun withRootValueSeparator(separator: SerializableString?): GeneratorSettings {
        if (separator == null) {
            if (rootValueSeparator == null) {
                return this
            }

            return GeneratorSettings(prettyPrinter, schema, characterEscapes, null)
        }

        if (separator == rootValueSeparator) {
            return this
        }

        return GeneratorSettings(prettyPrinter, schema, characterEscapes, separator)
    }

    private fun rootValueSeparatorAsString(): String? {
        return rootValueSeparator?.value
    }

    /*
     *******************************************************************************************************************
     * ObjectWriteContext support methods
     *******************************************************************************************************************
     */

    fun getSchema(): FormatSchema? {
        return schema
    }

    fun getCharacterEscapes(): CharacterEscapes? {
        return characterEscapes
    }

    fun getPrettyPrinter(): PrettyPrinter? {
        if (prettyPrinter == null) {
            return null
        }

        if (prettyPrinter is Instantiatable<*>) {
            return prettyPrinter.createInstance() as PrettyPrinter?
        }

        return prettyPrinter
    }

    fun hasPrettyPrinter(): Boolean {
        return prettyPrinter != null
    }

    fun getRootValueSeparator(defaultSeparator: SerializableString?): SerializableString? {
        if (rootValueSeparator == null) {
            return defaultSeparator
        }

        if (rootValueSeparator === NULL_ROOT_VALUE_SEPARATOR) {
            return null
        }

        return rootValueSeparator
    }

    companion object {

        private val EMPTY_GENERATOR = GeneratorSettings(null, null, null, null)

        /**
         * Also need to use a `null` marker for root value separator
         */
        private val NULL_ROOT_VALUE_SEPARATOR = SerializedString("")

        val EMPTY = GeneratorSettings(null, null, null, null)

    }

}