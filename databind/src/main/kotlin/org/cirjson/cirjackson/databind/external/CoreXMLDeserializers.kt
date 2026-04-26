package org.cirjson.cirjackson.databind.external

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.DatabindException
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.deserialization.standard.FromStringDeserializer
import java.util.*
import javax.xml.datatype.DatatypeConfigurationException
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.Duration
import javax.xml.datatype.XMLGregorianCalendar
import javax.xml.namespace.QName
import kotlin.reflect.KClass

/**
 * Container deserializers that handle "core" XML types: ones included in standard JDK 1.5. Types are directly needed by
 * JAXB, but may be unavailable on some limited platforms; hence separate out from basic deserializer factory.
 */
object CoreXMLDeserializers {

    private val EMPTY_QNAME = QName.valueOf("")

    /**
     * Data type factories are thread-safe after instantiation (and configuration, if any); and since instantiation
     * (esp. implementation introspection) can be expensive we better reuse the instance.
     */
    private val ourDatatypeFactory = try {
        DatatypeFactory.newInstance()
    } catch (e: DatatypeConfigurationException) {
        throw RuntimeException(e)
    }

    fun findBeanDeserializer(type: KotlinType): ValueDeserializer<*>? {
        val raw = type.rawClass

        return when (raw) {
            QName::class -> Standard(raw, TYPE_QNAME)
            XMLGregorianCalendar::class -> Standard(raw, TYPE_GREGORIAN_CALENDAR)
            Duration::class -> Standard(raw, TYPE_DURATION)
            else -> null
        }
    }

    fun hasDeserializerFor(valueType: KClass<*>): Boolean {
        return valueType == QName::class || valueType == XMLGregorianCalendar::class || valueType == Duration::class
    }

    /*
     *******************************************************************************************************************
     * Concrete deserializers
     *******************************************************************************************************************
     */

    const val TYPE_DURATION = 1

    const val TYPE_GREGORIAN_CALENDAR = 2

    const val TYPE_QNAME = 3

    /**
     * Combo-deserializer that supports deserialization of somewhat optional javax.xml types [QName], [Duration], and
     * [XMLGregorianCalendar]. Combined into a single class to eliminate a bunch of one-off implementation classes, to
     * reduce resulting jar size (mostly).
     */
    open class Standard(raw: KClass<*>, protected val myKind: Int) : FromStringDeserializer<Any>(raw) {

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
            return if (myKind == TYPE_GREGORIAN_CALENDAR && parser.hasToken(CirJsonToken.VALUE_NUMBER_INT)) {
                gregorianFromDate(context, parseDate(parser, context))
            } else {
                super.deserialize(parser, context)
            }
        }

        @Throws(CirJacksonException::class)
        override fun deserialize(value: String, context: DeserializationContext): Any? {
            return when (myKind) {
                TYPE_DURATION -> ourDatatypeFactory.newDuration(value)
                TYPE_QNAME -> QName.valueOf(value)
                TYPE_GREGORIAN_CALENDAR -> try {
                    gregorianFromDate(context, parseDate(value, context))
                } catch (_: DatabindException) {
                    ourDatatypeFactory.newXMLGregorianCalendar(value)
                }

                else -> throw IllegalStateException()
            }
        }

        override fun deserializeFromEmptyString(context: DeserializationContext): Any? {
            if (myKind == TYPE_QNAME) {
                return EMPTY_QNAME
            } else {
                return super.deserializeFromEmptyString(context)
            }
        }

        protected open fun gregorianFromDate(context: DeserializationContext, date: Date?): XMLGregorianCalendar? {
            date ?: return null
            val calendar = GregorianCalendar()
            calendar.time = date
            calendar.timeZone = calendar.timeZone
            return ourDatatypeFactory.newXMLGregorianCalendar(calendar)
        }

    }

}