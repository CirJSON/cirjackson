package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.databind.ValueDeserializer
import java.util.*
import kotlin.reflect.KClass

/**
 * Container class for core pre-Java8 JDK date/time type deserializers.
 */
object JDKDateDeserializers {

    private val ourUtilClasses = hashSetOf("java.util.Calendar", "java.util.GregorianCalendar", "java.util.Date")

    fun find(rawType: KClass<*>, className: String): ValueDeserializer<*>? {
        if (className !in ourUtilClasses) {
            return null
        }

        return when (rawType) {
            Calendar::class -> JavaUtilCalendarDeserializer()
            Date::class -> JavaUtilDateDeserializer()
            GregorianCalendar::class -> JavaUtilCalendarDeserializer(GregorianCalendar::class)
            else -> null
        }
    }

    fun hasDeserializerFor(rawType: KClass<*>): Boolean {
        return rawType.qualifiedName in ourUtilClasses
    }

}