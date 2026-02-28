package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonValueFormat
import org.cirjson.cirjackson.databind.serialization.standard.StandardScalarSerializer
import org.cirjson.cirjackson.databind.util.StandardDateFormat
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

/**
 * @property myUseTimestamp Flag that indicates that serialization must be done as the timestamp, regardless of other
 * settings.
 * 
 * @property myCustomFormat Specific format to use, if not default format: non-null value also indicates that
 * serialization is to be done as CirJSON String, not numeric timestamp, unless [myUseTimestamp] is `true`.
 */
abstract class DateTimeSerializerBase<T : Any> protected constructor(type: KClass<T>,
        protected val myUseTimestamp: Boolean?, protected val myCustomFormat: DateFormat?) :
        StandardScalarSerializer<T>(type) {

    /**
     * If [myCustomFormat] is used, we will try to reuse instances in the simplest possible form; thread-safe, but
     * without overhead of `ThreadLocal` (not from code, but wrt retaining of possibly large number of format instances
     * over all threads, properties with custom formats).
     */
    protected val myReusedCustomFormat = myCustomFormat?.let { AtomicReference<DateFormat?>() }

    abstract fun withFormat(timestamp: Boolean?, customFormat: DateFormat?): DateTimeSerializerBase<T>

    override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        val format = findFormatOverrides(provider, property, handledType()!!)
        val shape = format.shape

        if (shape.isNumeric) {
            return withFormat(true, null)
        }

        if (format.hasPattern()) {
            val locale = format.takeIf { it.hasLocale() }?.let { it.locale!! } ?: provider.locale
            val dateFormat = SimpleDateFormat(format.pattern, locale)
            val timeZone = format.takeIf { it.hasTimeZone() }?.let { it.timeZone!! } ?: provider.timeZone
            dateFormat.timeZone = timeZone
            return withFormat(false, dateFormat)
        }

        val hasLocale = format.hasLocale()
        val hasTimeZone = format.hasTimeZone()

        if (!hasLocale && !hasTimeZone && shape != CirJsonFormat.Shape.STRING) {
            return this
        }

        val dateFormat = provider.config.dateFormat

        if (dateFormat is StandardDateFormat) {
            var standardDateFormat = dateFormat

            if (hasLocale) {
                standardDateFormat = standardDateFormat.withLocale(format.locale!!)
            }

            if (hasTimeZone) {
                standardDateFormat = standardDateFormat.withTimeZone(format.timeZone!!)
            }

            return withFormat(false, standardDateFormat)
        }

        if (dateFormat !is SimpleDateFormat) {
            return provider.reportBadDefinition(handledType()!!,
                    "Configured `DateFormat` (${dateFormat::class.qualifiedName}) not a `SimpleDateFormat`; cannot configure `Locale` or `TimeZone`")
        }

        val simpleDateFormat = if (hasLocale) {
            SimpleDateFormat(dateFormat.toPattern(), format.locale!!)
        } else {
            dateFormat.clone() as SimpleDateFormat
        }

        val newTimeZone = format.timeZone

        if (newTimeZone != null && newTimeZone != simpleDateFormat.timeZone) {
            simpleDateFormat.timeZone = newTimeZone
        }

        return withFormat(true, simpleDateFormat)
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    override fun isEmpty(provider: SerializerProvider, value: T?): Boolean {
        return false
    }

    protected abstract fun timestamp(value: T?): Long

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        acceptCirJsonFormatVisitor(visitor, typeHint, asTimestamp(visitor.provider))
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    protected open fun asTimestamp(context: SerializerProvider?): Boolean {
        if (myUseTimestamp != null) {
            return myUseTimestamp
        }

        myCustomFormat ?: return false

        return context?.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) ?: throw IllegalArgumentException(
                "Null SerializerProvider passed for ${handledType()!!.qualifiedName}")
    }

    protected open fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType,
            asNumber: Boolean) {
        if (asNumber) {
            visitIntFormat(visitor, typeHint, CirJsonParser.NumberType.LONG, CirJsonValueFormat.UTC_MILLISEC)
        } else {
            visitStringFormat(visitor, typeHint, CirJsonValueFormat.DATE_TIME)
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun serializeAsString(value: Date, generator: CirJsonGenerator, context: SerializerProvider) {
        if (myCustomFormat == null) {
            context.defaultSerializeDateValue(value, generator)
            return
        }

        val format = myReusedCustomFormat!!.getAndSet(null) ?: myCustomFormat.clone() as DateFormat
        generator.writeString(format.format(value))
        myReusedCustomFormat.compareAndSet(null, format)
    }

}