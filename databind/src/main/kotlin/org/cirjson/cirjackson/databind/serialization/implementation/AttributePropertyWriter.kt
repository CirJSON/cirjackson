package org.cirjson.cirjackson.databind.serialization.implementation

import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.AnnotatedClass
import org.cirjson.cirjackson.databind.introspection.BeanPropertyDefinition
import org.cirjson.cirjackson.databind.serialization.VirtualBeanPropertyWriter
import org.cirjson.cirjackson.databind.util.Annotations

open class AttributePropertyWriter(protected val myAttributeName: String, propertyDefinition: BeanPropertyDefinition,
        contextAnnotations: Annotations, declaredType: KotlinType, inclusion: CirJsonInclude.Value) :
        VirtualBeanPropertyWriter(propertyDefinition, contextAnnotations, declaredType, null, null, null, inclusion,
                null) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun withConfig(config: MapperConfig<*>, declaringClass: AnnotatedClass,
            propertyDefinition: BeanPropertyDefinition, type: KotlinType): VirtualBeanPropertyWriter {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Overrides for actual serialization, value access
     *******************************************************************************************************************
     */

    override fun value(bean: Any, generator: CirJsonGenerator, provider: SerializerProvider): Any? {
        TODO("Not yet implemented")
    }

    companion object {

        fun construct(attributeName: String, propertyDefinition: BeanPropertyDefinition,
                contextAnnotations: Annotations, declaredType: KotlinType): AttributePropertyWriter {
            TODO("Not yet implemented")
        }

    }

}