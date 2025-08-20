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
 * @property myPrettyPrinter To allow for dynamic enabling/disabling of pretty printing, pretty printer can be optionally
 * configured for the writer as well
 *
 * @property mySchema When using data format that uses a schema, schema is passed to generator.
 *
 * @property myCharacterEscapes Caller may want to specify character escaping details, either as defaults or on a
 * call-by-call basis.
 *
 * @property myRootValueSeparator Caller may want to override so-called "root value separator", String added (verbatim,
 * with no quoting or escaping) between values in root context. The default value is a single space character, but this
 * is often changed to linefeed.
 */
class GeneratorSettings(val myPrettyPrinter: PrettyPrinter?, val mySchema: FormatSchema?,
        val myCharacterEscapes: CharacterEscapes?, val myRootValueSeparator: SerializableString?) {

    fun with(prettyPrinter: PrettyPrinter?): GeneratorSettings {
        if (this.myPrettyPrinter === prettyPrinter) {
            return this
        }

        return GeneratorSettings(prettyPrinter, mySchema, myCharacterEscapes, myRootValueSeparator)
    }

    fun with(schema: FormatSchema?): GeneratorSettings {
        if (this.mySchema === schema) {
            return this
        }

        return GeneratorSettings(myPrettyPrinter, schema, myCharacterEscapes, myRootValueSeparator)
    }

    fun with(characterEscapes: CharacterEscapes?): GeneratorSettings {
        if (this.myCharacterEscapes === characterEscapes) {
            return this
        }

        return GeneratorSettings(myPrettyPrinter, mySchema, characterEscapes, myRootValueSeparator)
    }

    fun withRootValueSeparator(separator: String?): GeneratorSettings {
        if (separator == null) {
            if (myRootValueSeparator === NULL_ROOT_VALUE_SEPARATOR) {
                return this
            }

            return GeneratorSettings(myPrettyPrinter, mySchema, myCharacterEscapes, NULL_ROOT_VALUE_SEPARATOR)
        }

        if (separator == rootValueSeparatorAsString()) {
            return this
        }

        return GeneratorSettings(myPrettyPrinter, mySchema, myCharacterEscapes, SerializedString(separator))
    }

    fun withRootValueSeparator(separator: SerializableString?): GeneratorSettings {
        if (separator == null) {
            if (myRootValueSeparator == null) {
                return this
            }

            return GeneratorSettings(myPrettyPrinter, mySchema, myCharacterEscapes, null)
        }

        if (separator == myRootValueSeparator) {
            return this
        }

        return GeneratorSettings(myPrettyPrinter, mySchema, myCharacterEscapes, separator)
    }

    private fun rootValueSeparatorAsString(): String? {
        return myRootValueSeparator?.value
    }

    /*
     *******************************************************************************************************************
     * ObjectWriteContext support methods
     *******************************************************************************************************************
     */

    fun getSchema(): FormatSchema? {
        return mySchema
    }

    fun getCharacterEscapes(): CharacterEscapes? {
        return myCharacterEscapes
    }

    fun getPrettyPrinter(): PrettyPrinter? {
        if (myPrettyPrinter == null) {
            return null
        }

        if (myPrettyPrinter is Instantiatable<*>) {
            return myPrettyPrinter.createInstance() as PrettyPrinter?
        }

        return myPrettyPrinter
    }

    fun hasPrettyPrinter(): Boolean {
        return myPrettyPrinter != null
    }

    fun getRootValueSeparator(defaultSeparator: SerializableString?): SerializableString? {
        if (myRootValueSeparator == null) {
            return defaultSeparator
        }

        if (myRootValueSeparator === NULL_ROOT_VALUE_SEPARATOR) {
            return null
        }

        return myRootValueSeparator
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