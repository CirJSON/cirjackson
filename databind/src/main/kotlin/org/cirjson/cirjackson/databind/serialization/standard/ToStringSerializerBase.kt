package org.cirjson.cirjackson.databind.serialization.standard

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import kotlin.reflect.KClass

/**
 * Intermediate base class that serves as base for standard [ToStringSerializer] as well as for custom subtypes that
 * want to add processing for converting from value to output into its `String` representation (whereas standard version
 * simply calls value object's `toString()` method).
 */
abstract class ToStringSerializerBase(handledType: KClass<*>) : StandardSerializer<Any>(handledType) {

    override fun isEmpty(provider: SerializerProvider, value: Any?): Boolean {
        return value == null || valueToString(value).isEmpty()
    }

    @Throws(CirJacksonException::class)
    override fun serialize(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider) {
        generator.writeString(valueToString(value))
    }

    /**
     * Default implementation will write type prefix, call regular serialization method (since assumption is that value
     * itself does not need CirJSON Array or Object start/end markers), and then write type suffix. This should work for
     * most cases; some subclasses may want to change this behavior.
     */
    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                typeSerializer.typeId(value, CirJsonToken.VALUE_STRING))
        serialize(value, generator, serializers)
        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitStringFormat(visitor, typeHint)
    }

    abstract fun valueToString(value: Any): String

}