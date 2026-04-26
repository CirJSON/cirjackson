package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.configuration.CoercionAction
import org.cirjson.cirjackson.databind.deserialization.standard.StandardScalarDeserializer
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.StandardDateFormat
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.reflect.KClass

abstract class DateBasedDeserializer<T : Any> : StandardScalarDeserializer<T> {

    /**
     * Specific format to use, if non-`null`; otherwise will just use default format.
     */
    protected val myCustomFormat: DateFormat?

    /**
     * Let's also keep format String for reference, to use for error messages
     */
    protected val myFormatString: String?

    protected constructor(clazz: KClass<*>) : super(clazz) {
        myCustomFormat = null
        myFormatString = null
    }

    protected constructor(base: DateBasedDeserializer<T>, format: DateFormat?, formatString: String?) : super(
            base.myValueClass) {
        myCustomFormat = format
        myFormatString = formatString
    }

    protected abstract fun withDateFormat(dateFormat: DateFormat?, formatString: String?): DateBasedDeserializer<T>

    override fun logicalType(): LogicalType {
        return LogicalType.DATE_TIME
    }

    override fun createContextual(context: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        val format = findFormatOverrides(context, property, handledType())

        val timeZone = format.timeZone
        val lenient = format.lenient

        if (format.hasPattern()) {
            val pattern = format.pattern
            val locale = format.takeIf { it.hasLocale() }?.locale ?: context.locale
            val dateFormat = SimpleDateFormat(pattern, locale)
            dateFormat.timeZone = timeZone ?: context.timeZone
            lenient?.let { dateFormat.isLenient = true }
            return withDateFormat(dateFormat, pattern)
        }

        if (timeZone != null) {
            val dateFormat = context.config.dateFormat.let { dateFormat ->
                if (dateFormat::class == StandardDateFormat::class) {
                    val locale = format.takeIf { it.hasLocale() }?.locale ?: context.locale
                    var standardDateFormat = dateFormat as StandardDateFormat
                    standardDateFormat = standardDateFormat.withTimeZone(timeZone)
                    standardDateFormat = standardDateFormat.withLocale(locale)

                    if (lenient != null) {
                        standardDateFormat = standardDateFormat.withLenient(lenient)
                    }

                    standardDateFormat
                } else {
                    (dateFormat.clone() as DateFormat).apply {
                        this.timeZone = timeZone

                        if (lenient != null) {
                            isLenient = lenient
                        }
                    }
                }
            }

            return withDateFormat(dateFormat, myFormatString)
        }

        lenient ?: return this

        val (dateFormat, pattern) = context.config.dateFormat.let { dateFormat ->
            if (dateFormat::class == StandardDateFormat::class) {
                var standardDateFormat = dateFormat as StandardDateFormat
                standardDateFormat = standardDateFormat.withLenient(lenient)
                dateFormat to standardDateFormat.toPattern()
            } else {
                (dateFormat.clone() as DateFormat).apply { isLenient = lenient }
                        .let { it.also { (it as? SimpleDateFormat)?.toPattern() } to (myFormatString ?: "[unknown]") }
            }
        }

        return withDateFormat(dateFormat, pattern)
    }

    @Throws(CirJacksonException::class)
    override fun parseDate(parser: CirJsonParser, context: DeserializationContext): Date? {
        myCustomFormat ?: return super.parseDate(parser, context)

        if (!parser.hasToken(CirJsonToken.VALUE_STRING)) {
            return super.parseDate(parser, context)
        }

        val string = parser.text!!.trim()

        return if (string.isEmpty()) {
            val action = checkFromStringCoercion(context, string)

            if (action == CoercionAction.AS_EMPTY) {
                Date(0L)
            } else {
                null
            }
        } else {
            synchronized(myCustomFormat) {
                try {
                    myCustomFormat.parse(string)
                } catch (_: ParseException) {
                    context.handleWeirdStringValue(handledType(), string,
                            "expected format \"$myFormatString\"") as Date?
                }
            }
        }
    }

}