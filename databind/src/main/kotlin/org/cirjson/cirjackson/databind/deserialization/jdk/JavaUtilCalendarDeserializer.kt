package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.util.findConstructor
import java.text.DateFormat
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.kotlinFunction

@CirJacksonStandardImplementation
open class JavaUtilCalendarDeserializer : DateBasedDeserializer<Calendar> {

    protected val myDefaultConstructor: KFunction<Calendar>?

    constructor() : super(Calendar::class) {
        myDefaultConstructor = null
    }

    constructor(clazz: KClass<out Calendar>) : super(clazz) {
        myDefaultConstructor = clazz.findConstructor(false)?.kotlinFunction
    }

    constructor(base: JavaUtilCalendarDeserializer, format: DateFormat?, formatString: String?) : super(base, format,
            formatString) {
        myDefaultConstructor = base.myDefaultConstructor
    }

    override fun withDateFormat(dateFormat: DateFormat?, formatString: String?): JavaUtilCalendarDeserializer {
        return JavaUtilCalendarDeserializer(this, dateFormat, formatString)
    }

    override fun getEmptyValue(context: DeserializationContext): Any? {
        return GregorianCalendar().apply { timeInMillis = 0 }
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Calendar? {
        val date = parseDate(parser, context) ?: return null
        myDefaultConstructor ?: return context.constructCalendar(date)

        return try {
            return myDefaultConstructor.call().apply {
                timeInMillis = date.time
                timeZone = context.timeZone
            }
        } catch (e: Exception) {
            context.handleInstantiationProblem(handledType(), date, e) as Calendar?
        }
    }

}