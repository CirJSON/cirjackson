package org.cirjson.cirjackson.databind.util

import org.cirjson.cirjackson.core.io.NumberInput
import java.text.*
import java.util.*
import kotlin.math.abs

/**
 * CirJackson's internal [DateFormat] implementation used by standard Date serializers and deserializers to implement
 * default behavior: does **NOT** fully implement all aspects expected by [DateFormat] and as a consequence **SHOULD
 * NOT** to be used by code outside core CirJackson databind functionality. In particular, `ParsePosition` argument of
 * [parse] and [format] methods is fully ignored; CirJackson itself never calls these methods. For serialization,
 * defaults to using an ISO-8601 compliant format (format String "yyyy-MM-dd'T'HH:mm:ss.SSSX") and for deserialization,
 * both ISO-8601 and RFC-1123.
 *
 * Note that `X` in format String refers to ISO-8601 timezone offset notation which produces values like "-08:00" --
 * that is, full minute/hour combo without a colon, not using `Z` as alias for "+00:00".
 *
 * Note also that to enable use of colon in timezone offset is possible by using method [withColonInTimeZone] for
 * creating new differently configured format instance.
 *
 * @property myTimeZone Caller may want to explicitly override timezone to use; if so, the value will be non-`null`.
 *
 * @property myLenient Explicit override for leniency, if specified.
 *
 * Cannot be `val` because [setLenient] returns `Unit`.
 */
open class StandardDateFormat protected constructor(protected var myTimeZone: TimeZone?, protected val myLocale: Locale,
        protected var myLenient: Boolean?, formatTzOffsetWithColon: Boolean) : DateFormat() {

    /**
     * Accessor for checking whether this instance would include colon within timezone serialization or not: if `true`,
     * timezone offset is serialized like `-06:00`; if `false` as `-0600`.
     *
     * NOTE: only relevant for serialization (formatting), as deserialization (parsing) always accepts optional colon
     * but does not require it, regardless of this setting.
     *
     * Default value is `true`.
     *
     * @return `true` if a colon is to be inserted between the hours and minutes of the TZ offset when serializing as
     * String, otherwise `false`
     */
    var isColonIncludedInTimeZone: Boolean = formatTzOffsetWithColon
        protected set

    /**
     * Lazily instantiated calendar used by this instance for serialization ([format]).
     */
    private val myCalendar by lazy { CALENDAR.clone() as Calendar }

    private var myFormatRFC1123: DateFormat? = null

    /*
     *******************************************************************************************************************
     * Life-cycle
     *******************************************************************************************************************
     */

    constructor() : this(null, DEFAULT_LOCALE, null, true)

    /**
     * Method used for creating a new instance with specified timezone; if no timezone specified, defaults to the
     * default timezone (UTC).
     */
    fun withTimeZone(timeZone: TimeZone?): StandardDateFormat {
        val realTimeZone = timeZone ?: DEFAULT_TIMEZONE

        if (myTimeZone == realTimeZone) {
            return this
        }

        return StandardDateFormat(realTimeZone, myLocale, myLenient, isColonIncludedInTimeZone)
    }

    /**
     * "Mutant factory" method that will return an instance that uses specified `Locale`: either `this` instance (if
     * setting would not change), or newly constructed instance with different `Locale` to use.
     */
    fun withLocale(locale: Locale): StandardDateFormat {
        if (locale == myLocale) {
            return this
        }

        return StandardDateFormat(myTimeZone, locale, myLenient, isColonIncludedInTimeZone)
    }

    /**
     * "Mutant factory" method that will return an instance that has specified leniency setting: either `this` instance
     * (if setting would not change), or newly constructed instance.
     */
    fun withLenient(lenient: Boolean?): StandardDateFormat {
        if (lenient == myLenient) {
            return this
        }

        return StandardDateFormat(myTimeZone, myLocale, lenient, isColonIncludedInTimeZone)
    }

    /**
     * "Mutant factory" method that will return an instance that has specified handling of colon when serializing
     * timezone (timezone either written like `+0500` or `+05:00`): either `this` instance (if setting would not
     * change), or newly constructed instance with desired setting for colon inclusion.
     *
     * NOTE: it does NOT affect deserialization as colon is optionally accepted but not required -- put another way,
     * this class accepts either serialization.
     */
    fun withColonInTimeZone(colonInTimeZone: Boolean): StandardDateFormat {
        if (colonInTimeZone == isColonIncludedInTimeZone) {
            return this
        }

        return StandardDateFormat(myTimeZone, myLocale, myLenient, colonInTimeZone)
    }

    override fun clone(): Any {
        return StandardDateFormat(myTimeZone, myLocale, myLenient, isColonIncludedInTimeZone)
    }

    /*
     *******************************************************************************************************************
     * Public API, configuration
     *******************************************************************************************************************
     */

    override fun getTimeZone(): TimeZone? {
        return myTimeZone
    }

    override fun setTimeZone(zone: TimeZone?) {
        if (zone == myTimeZone) {
            return
        }

        clearFormats()
        myTimeZone = zone
    }

    /**
     * Need to override since leniency must be kept track of locally, and not via underlying [Calendar] instance like
     * base class does.
     */
    override fun setLenient(lenient: Boolean) {
        if (lenient == myLenient) {
            return
        }

        myLenient = lenient
        clearFormats()
    }

    override fun isLenient(): Boolean {
        return myLenient ?: true
    }

    /*
     *******************************************************************************************************************
     * Public API, parsing
     *******************************************************************************************************************
     */

    override fun parse(source: String, pos: ParsePosition): Date? {
        return try {
            parseDate(source, pos)
        } catch (e: ParseException) {
            null
        }
    }

    @Throws(ParseException::class)
    override fun parse(source: String): Date {
        val dateString = source.trim()
        val pos = ParsePosition(0)
        val date = parseDate(dateString, pos)

        if (date != null) {
            return date
        }

        val stringBuilder = StringBuilder()

        for (format in ALL_FORMATS) {
            if (stringBuilder.isNotEmpty()) {
                stringBuilder.append("\", \"")
            } else {
                stringBuilder.append('"')
            }

            stringBuilder.append(format)
        }

        stringBuilder.append('"')
        throw ParseException(
                "Cannot parse date \"$dateString\": not compatible with any of standard forms ($stringBuilder)",
                pos.errorIndex)
    }

    @Throws(ParseException::class)
    protected open fun parseDate(source: String, pos: ParsePosition): Date? {
        if (looksLikeISO8601(source)) {
            return parseAsISO8601(source, pos)
        }

        var i = source.length

        while (--i >= 0) {
            val ch = source[i]

            if (ch !in '0'..'9') {
                if (i > 0 || ch != '-') {
                    break
                }
            }
        }

        if (i < 0 && (source[0] == '-' || NumberInput.inLongRange(source, false))) {
            return parseDateFromLong(source, pos)
        }

        return parseAsRFC1123(source, pos)
    }

    /*
     *******************************************************************************************************************
     * Public API, writing
     *******************************************************************************************************************
     */

    override fun format(date: Date, toAppendTo: StringBuffer, fieldPosition: FieldPosition): StringBuffer {
        val timeZone = myTimeZone ?: DEFAULT_TIMEZONE
        format(timeZone, myLocale, date, toAppendTo)
        return toAppendTo
    }

    protected open fun format(timeZone: TimeZone, locale: Locale, date: Date, buffer: StringBuffer) {
        val calendar = getCalendar(timeZone)
        calendar.time = date
        val year = calendar.get(Calendar.YEAR)

        if (calendar.get(Calendar.ERA) == GregorianCalendar.BC) {
            formatBCEYear(buffer, year)
        } else {
            if (year > 9999) {
                buffer.append('+')
            }

            pad4(buffer, year)
        }

        buffer.append('-')
        pad2(buffer, calendar.get(Calendar.MONTH) + 1)
        buffer.append('-')
        pad2(buffer, calendar.get(Calendar.DAY_OF_MONTH))
        buffer.append('T')
        pad2(buffer, calendar.get(Calendar.HOUR_OF_DAY))
        buffer.append(':')
        pad2(buffer, calendar.get(Calendar.MINUTE))
        buffer.append(':')
        pad2(buffer, calendar.get(Calendar.SECOND))
        buffer.append('.')
        pad3(buffer, calendar.get(Calendar.MILLISECOND))

        val offset = timeZone.getOffset(calendar.timeInMillis)

        if (offset == 0) {
            buffer.append('Z')
            return
        }

        val hours = abs(offset / (60 * 1000) / 60)
        val minutes = abs(offset / (60 * 1000) % 60)
        buffer.append(if (offset < 0) '-' else '+')
        pad2(buffer, hours)

        if (isColonIncludedInTimeZone) {
            buffer.append(':')
        }

        pad2(buffer, minutes)
    }

    protected open fun formatBCEYear(buffer: StringBuffer, bceYearNoSign: Int) {
        if (bceYearNoSign == 1) {
            buffer.append("+0000")
            return
        }

        val isoYear = bceYearNoSign - 1
        buffer.append('-')
        pad4(buffer, isoYear)
    }

    /*
     *******************************************************************************************************************
     * Standard overrides
     *******************************************************************************************************************
     */

    override fun toString(): String {
        return "DateFormat ${this::class.qualifiedName}: (timezone: $myTimeZone, locale: $myLocale, lenient: $myLenient)"
    }

    fun toPattern(): String {
        val stringBuilder = StringBuilder(100)
        stringBuilder.append("[one of: '").append(DATE_FORMAT_STRING_ISO8601).append("', '")
                .append(DATE_FORMAT_STRING_RFC1123).append("' (")

        if (myLenient == false) {
            stringBuilder.append("strict")
        } else {
            stringBuilder.append("lenient")
        }

        stringBuilder.append(")]")
        return stringBuilder.toString()
    }

    override fun equals(other: Any?): Boolean {
        return this === other
    }

    override fun hashCode(): Int {
        return System.identityHashCode(this)
    }

    /*
     *******************************************************************************************************************
     * Helper methods, parsing
     *******************************************************************************************************************
     */

    /**
     * Helper method used to figure out if input looks like valid ISO-8601 string.
     */
    protected open fun looksLikeISO8601(dateString: String): Boolean {
        return dateString.length >= 7 && dateString[0].isDigit() && dateString[3].isDigit() && dateString[4] == '-' &&
                dateString[5].isDigit()
    }

    @Throws(ParseException::class)
    private fun parseDateFromLong(source: String, pos: ParsePosition): Date {
        val timestamp = try {
            NumberInput.parseLong(source)
        } catch (e: NumberFormatException) {
            throw ParseException("Timestamp value $source out of 64-bit value range", pos.errorIndex)
        }

        return Date(timestamp)
    }

    @Throws(ParseException::class)
    protected open fun parseAsISO8601(source: String, pos: ParsePosition): Date {
        try {
            return parseAsISO8601Implementation(source, pos)
        } catch (e: IllegalArgumentException) {
            throw ParseException("Cannot parse date \"$source\", problem: ${e.message}", pos.errorIndex)
        }
    }

    @Throws(IllegalArgumentException::class, ParseException::class)
    protected open fun parseAsISO8601Implementation(source: String, pos: ParsePosition): Date {
        val totalLength = source.length
        val timeZone = myTimeZone?.takeIf { source[totalLength - 1] != 'Z' } ?: DEFAULT_TIMEZONE
        val calendar = getCalendar(timeZone)
        calendar.clear()

        if (totalLength <= 10) {
            if (!PATTERN_PLAIN.matches(source)) {
                throw ParseException(
                        "Cannot parse date \"$source\": while it seems to fit format '$DATE_FORMAT_STRING_PLAIN', parsing fails (leniency? $myLenient)",
                        pos.errorIndex)
            }

            val year = parse4D(source)
            val month = parse2D(source, 5) - 1
            val day = parse2D(source, 8)

            calendar.set(year, month, day, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.time
        }

        val match = PATTERN_ISO8601.matchEntire(source) ?: throw ParseException(
                "Cannot parse date \"$source\": while it seems to fit format '$DATE_FORMAT_STRING_ISO8601', parsing fails (leniency? $myLenient)",
                pos.errorIndex)

        var range = match.groups[2]!!.range
        var start = range.first
        var end = range.last
        val length = end - start

        if (length > 1) {
            var offsetSeconds = parse2D(source, start + 1) * 3600

            if (length >= 5) {
                offsetSeconds += parse2D(source, end - 2) * 60
            }

            if (source[start] == '-') {
                offsetSeconds *= 1000
            } else {
                offsetSeconds *= -1000
            }

            calendar.set(Calendar.ZONE_OFFSET, offsetSeconds)
            calendar.set(Calendar.DST_OFFSET, 0)
        }

        val year = parse4D(source)
        val month = parse2D(source, 5) - 1
        val day = parse2D(source, 8)
        val hour = parse2D(source, 11)
        val minute = parse2D(source, 14)
        val seconds = if (totalLength > 16 && source[16] != ':') {
            parse2D(source, 17)
        } else {
            0
        }
        calendar.set(year, month, day, hour, minute, seconds)

        range = match.groups[1]!!.range
        start = range.first + 1
        end = range.last

        if (start >= end) {
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.time
        }

        val fractionLength = end - start

        if (fractionLength > 9) {
            throw ParseException("Cannot parse date \"$source\": invalid fractional seconds '${
                match.groups[1]!!.value.substring(1)
            }'; can use at most 9 digits", start)
        }

        var milliseconds = 0

        if (fractionLength >= 3) {
            milliseconds += source[start + 2].code - '0'.code
        }

        if (fractionLength >= 2) {
            milliseconds += 10 * (source[start + 1].code - '0'.code)
        }

        if (fractionLength >= 1) {
            milliseconds += 100 * (source[start].code - '0'.code)
        }

        calendar.set(Calendar.MILLISECOND, milliseconds)
        return calendar.time
    }

    protected open fun parseAsRFC1123(source: String, pos: ParsePosition): Date? {
        if (myFormatRFC1123 == null) {
            myFormatRFC1123 = cloneFormat(myTimeZone, myLocale, myLenient)
        }

        return myFormatRFC1123!!.parse(source, pos)
    }

    /*
     *******************************************************************************************************************
     * Helper methods, other
     *******************************************************************************************************************
     */

    protected open fun clearFormats() {
        myFormatRFC1123 = null
    }

    protected open fun getCalendar(timeZone: TimeZone): Calendar {
        val calendar = myCalendar

        if (calendar.timeZone != timeZone) {
            calendar.timeZone = timeZone
        }

        calendar.isLenient = isLenient
        return calendar
    }

    companion object {

        const val PATTERN_PLAIN_STRING = "\\d\\d\\d\\d-\\d\\d-\\d\\d"

        val PATTERN_PLAIN = Regex(PATTERN_PLAIN_STRING)

        val PATTERN_ISO8601 = PATTERN_PLAIN_STRING.let {
            try {
                Regex(it + "T\\d\\d:\\d\\d(?::\\d\\d(\\.\\d+)?(Z|[+-]\\d\\d(?::?\\d\\d)?")
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        /**
         * Defines a commonly used date format that conforms to ISO-8601 date formatting standard, when it includes
         * basic undecorated timezone definition.
         */
        const val DATE_FORMAT_STRING_ISO8601 = "yyyy-MM-dd'T'HH:mm:ss.SSSX"

        /**
         * ISO-8601 with just the Date part, no time: needed for error messages
         */
        const val DATE_FORMAT_STRING_PLAIN = "yyyy-MM-dd"

        /**
         * This constant defines the date format specified by RFC 1123 / RFC 822. Used for parsing via
         * `SimpleDateFormat` as well as error messages.
         */
        const val DATE_FORMAT_STRING_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss zzz"

        /**
         * For error messages, a list of all formats is needed.
         */
        val ALL_FORMATS = arrayOf(DATE_FORMAT_STRING_ISO8601, "yyyy-MM-dd'T'HH:mm:ss.SSS", DATE_FORMAT_STRING_RFC1123,
                DATE_FORMAT_STRING_PLAIN)

        /**
         * By default, UTC is used for everything
         */
        val DEFAULT_TIMEZONE: TimeZone = TimeZone.getTimeZone("UTC")

        val DEFAULT_LOCALE: Locale = Locale.US

        val DATE_FORMAT_RFC1123: DateFormat =
                SimpleDateFormat(DATE_FORMAT_STRING_RFC1123, DEFAULT_LOCALE).apply { timeZone = DEFAULT_TIMEZONE }

        /**
         * A singleton instance can be used for cloning purposes, as a blueprint of sorts.
         */
        val instance = StandardDateFormat()

        /**
         * Blueprint "Calendar" instance for use during formatting. Cannot be used as-is, due to thread-safety issues,
         * but can be used for constructing actual instances more cheaply by cloning.
         */
        val CALENDAR = GregorianCalendar(DEFAULT_TIMEZONE, DEFAULT_LOCALE)

        /*
         ***************************************************************************************************************
         * Public API, writing
         ***************************************************************************************************************
         */

        private fun pad2(buffer: StringBuffer, toPad: Int) {
            var value = toPad
            val tens = value / 10

            if (tens == 0) {
                buffer.append('0')
            } else {
                buffer.append(('0'.code + tens).toChar())
                value -= 10 * tens
            }

            buffer.append(('0'.code + value).toChar())
        }

        private fun pad3(buffer: StringBuffer, toPad: Int) {
            var value = toPad
            val hundreds = value / 100

            if (hundreds == 0) {
                buffer.append('0')
            } else {
                buffer.append(('0'.code + hundreds).toChar())
                value -= 100 * hundreds
            }

            pad2(buffer, value)
        }

        private fun pad4(buffer: StringBuffer, toPad: Int) {
            var value = toPad
            val hundreds = value / 100

            if (hundreds == 0) {
                buffer.append('0').append('0')
            } else {
                if (hundreds > 99) {
                    buffer.append(hundreds)
                } else {
                    pad2(buffer, hundreds)
                }

                value -= 100 * hundreds
            }

            pad2(buffer, value)
        }

        /*
         ***************************************************************************************************************
         * Helper methods, parsing
         ***************************************************************************************************************
         */

        private fun parse2D(string: String, index: Int): Int {
            return 10 * (string[index].code - '0'.code) + string[index + 1].code - '0'.code
        }

        private fun parse4D(string: String): Int {
            return 1000 * (string[0].code - '0'.code) + 100 * (string[1].code - '0'.code) + 10 * (string[2].code - '0'.code) + string[3].code - '0'.code
        }

        /*
         ***************************************************************************************************************
         * Helper methods, other
         ***************************************************************************************************************
         */

        private fun cloneFormat(timeZone: TimeZone?, locale: Locale, lenient: Boolean?): DateFormat {
            var dateFormat = DATE_FORMAT_RFC1123

            if (locale != DEFAULT_LOCALE) {
                dateFormat = SimpleDateFormat(DATE_FORMAT_STRING_RFC1123, locale)
                dateFormat.timeZone = timeZone ?: DEFAULT_TIMEZONE
            } else {
                dateFormat = dateFormat.clone() as DateFormat
                timeZone?.let { dateFormat.timeZone = it }
            }

            lenient?.let { dateFormat.isLenient = it }
            return dateFormat
        }

    }

}