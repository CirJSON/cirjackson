package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.AnnotatedClass
import org.cirjson.cirjackson.databind.introspection.BeanPropertyDefinition
import org.cirjson.cirjackson.databind.util.Annotations
import kotlin.reflect.KClass

abstract class VirtualBeanPropertyWriter : BeanPropertyWriter {

    protected constructor() : super()

    protected constructor(propertyDefinition: BeanPropertyDefinition, contextAnnotations: Annotations,
            declaredType: KotlinType, serializer: ValueSerializer<*>?, typeSerializer: TypeSerializer?,
            serializerType: KotlinType?, inclusion: CirJsonInclude.Value?, includeInViews: Array<KClass<*>>?) : super(
            propertyDefinition, propertyDefinition.primaryMember, contextAnnotations, declaredType, serializer,
            typeSerializer, serializerType, suppressNulls(inclusion), suppressableValue(inclusion), includeInViews)

    /*
     *******************************************************************************************************************
     * Abstract methods for subclasses to define
     *******************************************************************************************************************
     */

    abstract fun withConfig(config: MapperConfig<*>, declaringClass: AnnotatedClass,
            propertyDefinition: BeanPropertyDefinition, type: KotlinType): VirtualBeanPropertyWriter

    companion object {

        fun suppressNulls(inclusion: CirJsonInclude.Value?): Boolean {
            TODO("Not yet implemented")
        }

        fun suppressableValue(inclusion: CirJsonInclude.Value?): Any? {
            TODO("Not yet implemented")
        }

    }

}