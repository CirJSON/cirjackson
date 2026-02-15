package org.cirjson.cirjackson.databind.serialization.standard

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import kotlin.reflect.KClass

abstract class StandardScalarSerializer<T : Any> : StandardSerializer<T> {

    protected constructor(type: KClass<T>) : super(type)

    /**
     * Basic copy-constructor
     *
     * @param source Original instance to copy settings from
     */
    protected constructor(source: StandardScalarSerializer<T>) : super(source)

    /**
     * Default implementation will write type prefix, call regular serialization method (since assumption is that value
     * itself does not need CirJSON Array or Object start/end markers), and then write type suffix. This should work for
     * most cases; some subclasses may want to change this behavior.
     */
    override fun serializeWithType(value: T, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val typeIdDefinition = typeSerializer.writeTypePrefix(generator, serializers,
                typeSerializer.typeId(value, CirJsonToken.VALUE_STRING))
        serialize(value, generator, serializers)
        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        visitStringFormat(visitor, typeHint)
    }

}