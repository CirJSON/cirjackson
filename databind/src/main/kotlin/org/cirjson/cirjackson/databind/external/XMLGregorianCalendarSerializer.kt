package org.cirjson.cirjackson.databind.external

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.serialization.jdk.JavaUtilCalendarSerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardSerializer
import java.util.*
import javax.xml.datatype.XMLGregorianCalendar

open class XMLGregorianCalendarSerializer protected constructor(delegate: ValueSerializer<*>) :
        StandardSerializer<XMLGregorianCalendar>(XMLGregorianCalendar::class) {

    @Suppress("UNCHECKED_CAST")
    protected val myDelegate: ValueSerializer<Any> = delegate as ValueSerializer<Any>

    constructor() : this(JavaUtilCalendarSerializer.INSTANCE)

    override val delegatee: ValueSerializer<*>?
        get() = super.delegatee

    override fun createContextual(provider: SerializerProvider,
            property: BeanProperty?): XMLGregorianCalendarSerializer {
        val serializer = provider.handlePrimaryContextualization(myDelegate, property) as ValueSerializer<*>

        if (serializer === myDelegate) {
            return this
        }

        return XMLGregorianCalendarSerializer(serializer)
    }

    override fun isEmpty(provider: SerializerProvider, value: XMLGregorianCalendar?): Boolean {
        return myDelegate.isEmpty(provider, convert(value))
    }

    @Throws(CirJacksonException::class)
    override fun serialize(value: XMLGregorianCalendar, generator: CirJsonGenerator, serializers: SerializerProvider) {
        myDelegate.serialize(convert(value)!!, generator, serializers)
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: XMLGregorianCalendar, generator: CirJsonGenerator,
            serializers: SerializerProvider, typeSerializer: TypeSerializer) {
        val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                typeSerializer.typeId(value, XMLGregorianCalendar::class, CirJsonToken.VALUE_STRING))
        serialize(value, generator, serializers)
        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

    protected open fun convert(input: XMLGregorianCalendar?): Calendar? {
        return input?.toGregorianCalendar()
    }

    companion object {

        val INSTANCE = XMLGregorianCalendarSerializer()

    }

}