package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.CirJsonGenerator

/**
 * Default linefeed-based indenter, used by [DefaultPrettyPrinter] (unless overridden). Uses system-specific linefeeds
 * and 2 spaces for indentation per level.
 *
 * @constructor Create an indenter which uses the `indent` string to indent one level and the `eol` string to separate
 * lines.
 *
 * @param indent Indentation String to prepend for a single level of indentation
 *
 * @param eol End-of-line marker to use after indented line
 *
 * @property eol End-of-line marker to use after indented line
 */
class DefaultIndenter(indent: String, val eol: String) : DefaultPrettyPrinter.NopIndenter() {

    private val myCharsPerLevel = indent.length

    private val myIndents = CharArray(indent.length * INDENT_LEVELS).apply {
        var offset = 0

        for (i in 0..<INDENT_LEVELS) {
            indent.toCharArray(this, offset, 0, indent.length)
            offset += indent.length
        }
    }

    val indent: String
        get() = String(myIndents, 0, myCharsPerLevel)

    constructor() : this("  ", SYS_LF)

    fun withLinefeed(lf: String): DefaultIndenter {
        return if (lf != eol) DefaultIndenter(indent, lf) else this
    }

    fun withIndent(indent: String): DefaultIndenter {
        return if (indent != this.indent) DefaultIndenter(indent, eol) else this
    }

    override fun writeIndentation(generator: CirJsonGenerator, level: Int) {
        var realLevel = level
        generator.writeRaw(eol)

        if (realLevel <= 0) {
            return
        }

        realLevel *= myCharsPerLevel

        while (realLevel > myIndents.size) {
            generator.writeRaw(myIndents, 0, myIndents.size)
            realLevel -= myIndents.size
        }

        generator.writeRaw(myIndents, 0, realLevel)
    }

    override val isInline: Boolean = false

    companion object {

        val SYS_LF = try {
            System.lineSeparator()
        } catch (e: Throwable) {
            "\n"
        }

        /**
         * We expect to rarely get indentation deeper than this number of levels, and try not to pre-generate more
         * indentations than needed.
         */
        private const val INDENT_LEVELS = 16

        val SYSTEM_LINEFEED_INSTANCE = DefaultIndenter()

    }

}